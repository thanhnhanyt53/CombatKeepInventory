package com.votri.combatkeepinv;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
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

        if (!journalDirectory.exists()) {
            journalDirectory.mkdirs();
        }

        loadJournals();
    }

    public boolean isProcessing(UUID uuid) {
        return uuid != null &&
                processing.containsKey(uuid);
    }

    public boolean hasJournal(UUID uuid) {
        return uuid != null &&
                journals.containsKey(uuid);
    }

    public InventoryJournal get(UUID uuid) {
        return uuid == null
                ? null
                : journals.get(uuid);
    }

    /**
     * Creates the journal BEFORE modifying the live inventory.
     */
    public boolean createSnapshot(Player player) {

        if (player == null) {
            return false;
        }

        UUID uuid =
                player.getUniqueId();

        if (!processing.putIfAbsent(
                uuid,
                Boolean.TRUE
        )) {
            return false;
        }

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

        save(journal);

        return true;
    }

    /**
     * Drops the snapshotted inventory and clears the
     * live player inventory so Bukkit cannot save the old
     * inventory back during disconnect.
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

        journal.setState(
                InventoryJournal.State.PROCESSING
        );

        save(journal);

        dropItems(
                player,
                journal
        );

        /*
         * CRITICAL:
         *
         * Clear the live inventory after snapshot.
         * This prevents old inventory data from being
         * written back into player-data during logout.
         */
        player.getInventory()
                .clear();

        journal.setState(
                InventoryJournal.State.PROCESSED
        );

        save(journal);

        return true;
    }

    /**
     * Called on death. A real death is the authoritative
     * inventory transaction, therefore a pending logout
     * journal must not be restored.
     */
    public void cancelForDeath(UUID uuid) {

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

    /**
     * Player joined again. Never restore the old snapshot.
     *
     * The journal only exists to prove that the previous
     * disconnect transaction was already processed.
     */
    public void handleJoin(Player player) {

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

        journal.setState(
                InventoryJournal.State.JOIN_PENDING
        );

        save(journal);

        /*
         * Wait until Bukkit has finished loading the
         * player's persistent state.
         */
        Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> confirmJoin(uuid),
                2L
        );
    }

    private void confirmJoin(UUID uuid) {

        InventoryJournal journal =
                journals.remove(uuid);

        processing.remove(uuid);

        if (journal != null) {
            deleteFile(uuid);
        }
    }

    private void dropItems(
            Player player,
            InventoryJournal journal
    ) {
        Location location =
                journal.getLocation();

        if (location == null) {
            location =
                    player.getLocation();
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
        if (location == null ||
                location.getWorld() == null ||
                items == null) {
            return;
        }

        World world =
                location.getWorld();

        for (ItemStack item : items) {

            if (item == null ||
                    item.getType().isAir()) {
                continue;
            }

            world.dropItemNaturally(
                    location,
                    item.clone()
            );
        }
    }

    private void save(
            InventoryJournal journal
    ) {
        File file =
                getFile(
                        journal.getUuid()
                );

        YamlConfiguration config =
                new YamlConfiguration();

        config.set(
                "uuid",
                journal.getUuid().toString()
        );

        config.set(
                "state",
                journal.getState().name()
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

        if (location != null &&
                location.getWorld() != null) {

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
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe(
                    "Could not save inventory journal " +
                            journal.getUuid() +
                            ": " +
                            exception.getMessage()
            );
        }
    }

    private void loadJournals() {

        File[] files =
                journalDirectory.listFiles(
                        (dir, name) ->
                                name.endsWith(".yml")
                );

        if (files == null) {
            return;
        }

        for (File file : files) {

            try {
                YamlConfiguration config =
                        YamlConfiguration
                                .loadConfiguration(file);

                String uuidText =
                        config.getString("uuid");

                if (uuidText == null) {
                    continue;
                }

                UUID uuid =
                        UUID.fromString(uuidText);

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
                        loadLocation(config);

                InventoryJournal journal =
                        new InventoryJournal(
                                uuid,
                                storage,
                                armor,
                                extra,
                                location
                        );

                String state =
                        config.getString(
                                "state",
                                "SNAPSHOTTED"
                        );

                try {
                    journal.setState(
                            InventoryJournal.State
                                    .valueOf(state)
                    );
                } catch (IllegalArgumentException ignored) {
                    journal.setState(
                            InventoryJournal.State
                                    .SNAPSHOTTED
                    );
                }

                journals.put(
                        uuid,
                        journal
                );

            } catch (Throwable throwable) {

                plugin.getLogger().warning(
                        "Could not load inventory journal " +
                                file.getName() +
                                ": " +
                                throwable.getClass()
                                        .getSimpleName()
                );
            }
        }
    }

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
                Bukkit.getWorld(worldName);

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

    private File getFile(UUID uuid) {
        return new File(
                journalDirectory,
                uuid + ".yml"
        );
    }

    private void deleteFile(UUID uuid) {
        File file =
                getFile(uuid);

        if (file.exists() &&
                !file.delete()) {

            plugin.getLogger().warning(
                    "Could not delete inventory journal: " +
                            file.getName()
            );
        }
    }

    public void shutdown() {
        /*
         * Do NOT delete journals here.
         *
         * A journal may represent an unfinished combat
         * logout transaction and must survive a restart.
         */
        processing.clear();
    }
}