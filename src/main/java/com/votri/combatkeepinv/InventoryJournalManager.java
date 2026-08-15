package com.votri.combatkeepinv;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InventoryJournalManager {

    private final CombatKeepInventory plugin;

    private final Map<UUID, InventoryJournal> journals =
            new ConcurrentHashMap<>();

    /*
     * UUID -> transaction lock
     */
    private final Map<UUID, Boolean> processing =
            new ConcurrentHashMap<>();

    private final File journalDirectory;

    public InventoryJournalManager(
            CombatKeepInventory plugin
    ) {

        this.plugin = plugin;

        journalDirectory =
                new File(
                        plugin.getDataFolder(),
                        "journal"
                );

        if (!journalDirectory.exists()
                && !journalDirectory.mkdirs()) {

            plugin.getLogger().warning(
                    "Could not create journal directory: "
                            + journalDirectory.getAbsolutePath()
            );
        }

        loadJournals();
    }

    /*
     * ============================================================
     * QUERY
     * ============================================================
     */

    public boolean isProcessing(
            UUID uuid
    ) {

        return uuid != null
                && processing.containsKey(uuid);
    }

    public boolean hasJournal(
            UUID uuid
    ) {

        return uuid != null
                && journals.containsKey(uuid);
    }

    public InventoryJournal get(
            UUID uuid
    ) {

        return uuid == null
                ? null
                : journals.get(uuid);
    }

    /*
     * ============================================================
     * CREATE SNAPSHOT
     * ============================================================
     *
     * This is the FIRST step of the transaction.
     */

    public boolean createSnapshot(
            Player player
    ) {

        if (player == null) {
            return false;
        }

        UUID uuid =
                player.getUniqueId();

        /*
         * Existing transaction.
         */
        if (journals.containsKey(uuid)) {
            return false;
        }

        /*
         * Acquire transaction lock.
         */
        if (processing.putIfAbsent(
                uuid,
                Boolean.TRUE
        ) != null) {

            return false;
        }

        /*
         * Double-check after acquiring lock.
         */
        if (journals.containsKey(uuid)) {

            processing.remove(uuid);

            return false;
        }

        InventoryJournal journal =
                new InventoryJournal(
                        uuid,
                        player.getInventory()
                                .getStorageContents(),
                        player.getInventory()
                                .getArmorContents(),
                        player.getInventory()
                                .getExtraContents(),
                        player.getLocation()
                );

        journals.put(
                uuid,
                journal
        );

        /*
         * Persist snapshot BEFORE modifying live inventory.
         */
        if (!save(journal)) {

            journals.remove(
                    uuid,
                    journal
            );

            processing.remove(
                    uuid
            );

            return false;
        }

        return true;
    }

    /*
     * ============================================================
     * PROCESS COMBAT LOGOUT
     * ============================================================
     */

    public boolean processCombatLogout(
            Player player
    ) {

        if (player == null) {
            return false;
        }

        UUID uuid =
                player.getUniqueId();

        InventoryJournal journal =
                journals.get(uuid);

        if (journal == null) {
            return false;
        }

        /*
         * If already processed, NEVER drop again.
         */
        if (journal.getState()
                == InventoryJournal.State.PROCESSED
                || journal.getState()
                == InventoryJournal.State.JOIN_PENDING) {

            return false;
        }

        journal.setState(
                InventoryJournal.State.PROCESSING
        );

        if (!save(journal)) {

            plugin.getLogger().severe(
                    "Could not persist PROCESSING state for "
                            + uuid
            );

            return false;
        }

        /*
         * ========================================================
         * DROP SNAPSHOT
         * ========================================================
         */

        dropItems(
                player,
                journal
        );

        /*
         * ========================================================
         * CLEAR LIVE INVENTORY
         * ========================================================
         *
         * This is critical.
         *
         * The player's live Bukkit inventory must no longer
         * contain the old inventory when the server saves the
         * player-data after disconnect.
         */

        player.getInventory().clear();

        /*
         * ========================================================
         * MARK TRANSACTION COMPLETE
         * ========================================================
         */

        journal.setState(
                InventoryJournal.State.PROCESSED
        );

        if (!save(journal)) {

            plugin.getLogger().severe(
                    "Could not persist PROCESSED state for "
                            + uuid
            );

            /*
             * Keep the journal in memory and on disk as far as
             * possible. It is safer to retain a journal than
             * accidentally restore an old inventory.
             */
            return true;
        }

        return true;
    }

    /*
     * ============================================================
     * DEATH
     * ============================================================
     *
     * Real death is authoritative.
     *
     * Never restore a logout snapshot because of death.
     */

    public void cancelForDeath(
            UUID uuid
    ) {

        if (uuid == null) {
            return;
        }

        InventoryJournal journal =
                journals.remove(uuid);

        processing.remove(uuid);

        if (journal != null) {
            deleteFile(uuid);
        }
    }

    /*
     * ============================================================
     * JOIN
     * ============================================================
     *
     * NEVER restore the snapshot.
     */

    public void handleJoin(
            Player player
    ) {

        if (player == null) {
            return;
        }

        UUID uuid =
                player.getUniqueId();

        InventoryJournal journal =
                journals.get(uuid);

        if (journal == null) {

            processing.remove(uuid);

            return;
        }

        /*
         * The player is reconnecting after a transaction.
         *
         * We explicitly DO NOT call:
         *
         * setStorageContents(...)
         * setArmorContents(...)
         * setExtraContents(...)
         */

        journal.setState(
                InventoryJournal.State.JOIN_PENDING
        );

        save(journal);

        /*
         * Wait until Bukkit has completed the normal player
         * loading/join sequence.
         */
        Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> confirmJoin(uuid),
                2L
        );
    }

    /*
     * ============================================================
     * CONFIRM JOIN
     * ============================================================
     */

    private void confirmJoin(
            UUID uuid
    ) {

        InventoryJournal journal =
                journals.remove(uuid);

        processing.remove(uuid);

        if (journal != null) {

            deleteFile(
                    uuid
            );
        }
    }

    /*
     * ============================================================
     * DROP SNAPSHOT
     * ============================================================
     */

    private void dropItems(
            Player player,
            InventoryJournal journal
    ) {

        Location location =
                journal.getLocation();

        if (location == null
                || location.getWorld() == null) {

            location =
                    player.getLocation();
        }

        if (location == null
                || location.getWorld() == null) {

            plugin.getLogger().warning(
                    "Could not determine drop location for "
                            + journal.getUuid()
            );

            return;
        }

        dropArray(
                location,
                journal.getStorage()
        );

        dropArray(
                location,
                journal.getArmor()
        );

        dropArray(
                location,
                journal.getExtra()
        );
    }

    private void dropArray(
            Location location,
            ItemStack[] items
    ) {

        if (location == null
                || location.getWorld() == null
                || items == null) {

            return;
        }

        World world =
                location.getWorld();

        for (ItemStack item : items) {

            if (item == null) {
                continue;
            }

            if (item.getType().isAir()) {
                continue;
            }

            world.dropItemNaturally(
                    location,
                    item.clone()
            );
        }
    }

    /*
     * ============================================================
     * SAVE JOURNAL
     * ============================================================
     */

    private boolean save(
            InventoryJournal journal
    ) {

        if (journal == null) {
            return false;
        }

        File file =
                getFile(
                        journal.getUuid()
                );

        YamlConfiguration config =
                new YamlConfiguration();

        config.set(
                "uuid",
                journal.getUuid()
                        .toString()
        );

        config.set(
                "state",
                journal.getState()
                        .name()
        );

        config.set(
                "created-at",
                journal.getCreatedAt()
        );

        config.set(
                "storage",
                toList(
                        journal.getStorage()
                )
        );

        config.set(
                "armor",
                toList(
                        journal.getArmor()
                )
        );

        config.set(
                "extra",
                toList(
                        journal.getExtra()
                )
        );

        Location location =
                journal.getLocation();

        if (location != null
                && location.getWorld() != null) {

            config.set(
                    "location.world",
                    location.getWorld()
                            .getName()
            );

            config.set(
                    "location.x",
                    location.getX()
            );

            config.set(
                    "location.y",
                    location.getY()
            );

            config.set(
                    "location.z",
                    location.getZ()
            );

            config.set(
                    "location.yaw",
                    location.getYaw()
            );

            config.set(
                    "location.pitch",
                    location.getPitch()
            );
        }

        try {

            config.save(
                    file
            );

            return true;

        } catch (IOException exception) {

            plugin.getLogger().severe(
                    "Could not save inventory journal "
                            + journal.getUuid()
                            + ": "
                            + exception.getMessage()
            );

            return false;
        }
    }

    /*
     * ============================================================
     * LOAD JOURNALS
     * ============================================================
     */

    private void loadJournals() {

        File[] files =
                journalDirectory.listFiles(
                        (dir, name) ->
                                name.toLowerCase()
                                        .endsWith(".yml")
                );

        if (files == null) {
            return;
        }

        for (File file : files) {

            try {

                YamlConfiguration config =
                        YamlConfiguration
                                .loadConfiguration(
                                        file
                                );

                String uuidText =
                        config.getString(
                                "uuid"
                        );

                if (uuidText == null) {
                    continue;
                }

                UUID uuid =
                        UUID.fromString(
                                uuidText
                        );

                ItemStack[] storage =
                        fromList(
                                config.getList(
                                        "storage"
                                )
                        );

                ItemStack[] armor =
                        fromList(
                                config.getList(
                                        "armor"
                                )
                        );

                ItemStack[] extra =
                        fromList(
                                config.getList(
                                        "extra"
                                )
                        );

                Location location =
                        loadLocation(
                                config
                        );

                long createdAt =
                        config.getLong(
                                "created-at",
                                System.currentTimeMillis()
                        );

                InventoryJournal journal =
                        new InventoryJournal(
                                uuid,
                                storage,
                                armor,
                                extra,
                                location,
                                createdAt
                        );

                String state =
                        config.getString(
                                "state",
                                "SNAPSHOTTED"
                        );

                try {

                    journal.setState(
                            InventoryJournal.State
                                    .valueOf(
                                            state
                                    )
                    );

                } catch (
                        IllegalArgumentException ignored
                ) {

                    journal.setState(
                            InventoryJournal.State
                                    .SNAPSHOTTED
                    );
                }

                /*
                 * Any persisted journal is considered a pending
                 * transaction marker.
                 *
                 * We NEVER restore it.
                 */
                journals.put(
                        uuid,
                        journal
                );

            } catch (Throwable throwable) {

                plugin.getLogger().warning(
                        "Could not load inventory journal "
                                + file.getName()
                                + ": "
                                + throwable.getClass()
                                        .getSimpleName()
                );
            }
        }
    }

    /*
     * ============================================================
     * LOAD LOCATION
     * ============================================================
     */

    private Location loadLocation(
            YamlConfiguration config
    ) {

        String worldName =
                config.getString(
                        "location.world"
                );

        if (worldName == null) {
            return null;
        }

        World world =
                Bukkit.getWorld(
                        worldName
                );

        if (world == null) {
            return null;
        }

        return new Location(
                world,
                config.getDouble(
                        "location.x"
                ),
                config.getDouble(
                        "location.y"
                ),
                config.getDouble(
                        "location.z"
                ),
                (float) config.getDouble(
                        "location.yaw"
                ),
                (float) config.getDouble(
                        "location.pitch"
                )
        );
    }

    /*
     * ============================================================
     * SERIALIZATION
     * ============================================================
     */

    private List<ItemStack> toList(
            ItemStack[] items
    ) {

        List<ItemStack> result =
                new ArrayList<>();

        if (items == null) {
            return result;
        }

        for (ItemStack item : items) {

            result.add(
                    item == null
                            ? null
                            : item.clone()
            );
        }

        return result;
    }

    private ItemStack[] fromList(
            List<?> list
    ) {

        if (list == null) {
            return new ItemStack[0];
        }

        List<ItemStack> result =
                new ArrayList<>();

        for (Object value : list) {

            if (value instanceof ItemStack item) {

                result.add(
                        item.clone()
                );

            } else {

                result.add(null);
            }
        }

        return result.toArray(
                new ItemStack[0]
        );
    }

    /*
     * ============================================================
     * FILE MANAGEMENT
     * ============================================================
     */

    private File getFile(
            UUID uuid
    ) {

        return new File(
                journalDirectory,
                uuid + ".yml"
        );
    }

    private void deleteFile(
            UUID uuid
    ) {

        File file =
                getFile(
                        uuid
                );

        if (file.exists()
                && !file.delete()) {

            plugin.getLogger().warning(
                    "Could not delete inventory journal: "
                            + file.getName()
            );
        }
    }

    /*
     * ============================================================
     * SHUTDOWN
     * ============================================================
     */

    public void shutdown() {

        /*
         * DO NOT delete journal files.
         *
         * An unfinished transaction must survive a restart.
         */
        processing.clear();
    }
}