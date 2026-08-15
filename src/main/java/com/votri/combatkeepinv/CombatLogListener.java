package com.votri.combatkeepinv;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.UUID;

public final class CombatLogListener
        implements Listener {

    private static final String BYPASS_PERMISSION =
            "combatkeepinventory.bypass";

    private final CombatKeepInventory plugin;
    private final CombatManager combatManager;
    private final PvPManagerHook pvpManager;
    private final InventoryJournalManager journalManager;

    public CombatLogListener(
            CombatKeepInventory plugin,
            CombatManager combatManager,
            PvPManagerHook pvpManager,
            InventoryJournalManager journalManager
    ) {

        this.plugin = plugin;
        this.combatManager = combatManager;
        this.pvpManager = pvpManager;
        this.journalManager = journalManager;
    }

    /*
     * ============================================================
     * QUIT
     * ============================================================
     */

    @EventHandler(
            priority = EventPriority.MONITOR
    )
    public void onQuit(
            PlayerQuitEvent event
    ) {

        handleDisconnect(
                event.getPlayer()
        );
    }

    /*
     * ============================================================
     * KICK
     * ============================================================
     */

    @EventHandler(
            priority = EventPriority.MONITOR
    )
    public void onKick(
            PlayerKickEvent event
    ) {

        handleDisconnect(
                event.getPlayer()
        );
    }

    /*
     * ============================================================
     * JOIN
     * ============================================================
     */

    @EventHandler(
            priority = EventPriority.MONITOR
    )
    public void onJoin(
            PlayerJoinEvent event
    ) {

        journalManager.handleJoin(
                event.getPlayer()
        );
    }

    /*
     * ============================================================
     * DISCONNECT TRANSACTION
     * ============================================================
     */

    private void handleDisconnect(
            Player player
    ) {

        if (player == null) {
            return;
        }

        UUID uuid =
                player.getUniqueId();

        /*
         * ========================================================
         * HARD BYPASS
         * ========================================================
         *
         * Must happen BEFORE:
         *
         * - journal creation
         * - inventory snapshot
         * - punishment
         * - combat-log processing
         */

        if (player.hasPermission(
                BYPASS_PERMISSION
        )) {

            combatManager.remove(
                    uuid
            );

            return;
        }

        /*
         * ========================================================
         * WORLD CHECK
         * ========================================================
         */

        if (plugin.isWorldDisabled(
                player.getWorld()
        )) {

            combatManager.remove(
                    uuid
            );

            return;
        }

        /*
         * ========================================================
         * COMBAT LOG ENABLED?
         * ========================================================
         */

        if (!plugin.getConfig().getBoolean(
                "pvp.combat-log.enabled",
                true
        )) {
            return;
        }

        /*
         * ========================================================
         * DUPLICATE PROTECTION
         * ========================================================
         *
         * PlayerKickEvent and PlayerQuitEvent can both reach
         * this listener.
         *
         * The journal acts as the transaction lock.
         */

        if (journalManager.isProcessing(uuid)
                || journalManager.hasJournal(uuid)) {

            return;
        }

        /*
         * ========================================================
         * OUR COMBAT STATE
         * ========================================================
         */

        boolean ownCombat = false;

        if (plugin.getConfig().getBoolean(
                "pvp.combat-log.check-own-combat",
                true
        )) {

            ownCombat =
                    combatManager.isInCombat(
                            uuid
                    );
        }

        /*
         * ========================================================
         * PVPMANAGER STATE
         * ========================================================
         */

        boolean managerCombat = false;

        if (plugin.getConfig().getBoolean(
                "pvp.combat-log.check-pvpmanager",
                true
        )) {

            managerCombat =
                    pvpManager.isInCombat(
                            player
                    );
        }

        boolean inCombat =
                ownCombat ||
                        managerCombat;

        if (!inCombat) {
            return;
        }

        /*
         * ========================================================
         * MINIMUM REMAINING TIME
         * ========================================================
         */

        long remaining =
                combatManager.getRemainingSeconds(
                        uuid
                );

        long minimum =
                Math.max(
                        0L,
                        plugin.getConfig().getLong(
                                "pvp.combat-log.minimum-remaining-seconds",
                                0L
                        )
                );

        /*
         * Only our own timer has a meaningful remaining value.
         */
        if (ownCombat &&
                remaining < minimum) {

            return;
        }

        /*
         * ========================================================
         * CREATE SNAPSHOT
         * ========================================================
         *
         * Snapshot MUST happen before the live inventory is
         * modified.
         */

        if (!journalManager.createSnapshot(
                player
        )) {

            return;
        }

        /*
         * ========================================================
         * PROCESS LOGOUT
         * ========================================================
         *
         * No /kill.
         * No fake PlayerDeathEvent.
         * No restoration on relog.
         */

        boolean processed =
                journalManager.processCombatLogout(
                        player
                );

        if (!processed) {
            return;
        }

        /*
         * ========================================================
         * PUNISH
         * ========================================================
         */

        punish(
                player
        );

        /*
         * ========================================================
         * REMOVE COMBAT STATE
         * ========================================================
         */

        combatManager.remove(
                uuid
        );
    }

    /*
     * ============================================================
     * PUNISHMENT
     * ============================================================
     */

    private void punish(
            Player player
    ) {

        String playerName =
                player.getName();

        String uuid =
                player.getUniqueId()
                        .toString();

        List<String> commands =
                plugin.getConfig()
                        .getStringList(
                                "pvp.combat-log.commands"
                        );

        for (String command : commands) {

            if (command == null ||
                    command.trim().isEmpty()) {

                continue;
            }

            String parsed =
                    command
                            .replace(
                                    "%player%",
                                    playerName
                            )
                            .replace(
                                    "%uuid%",
                                    uuid
                            );

            Bukkit.dispatchCommand(
                    Bukkit.getConsoleSender(),
                    parsed
            );
        }

        /*
         * ========================================================
         * BROADCAST
         * ========================================================
         */

        if (plugin.getConfig().getBoolean(
                "pvp.combat-log.broadcast",
                true
        )) {

            String message =
                    plugin.getConfig().getString(
                            "pvp.combat-log.broadcast-message",
                            "&c%player% combat-logged!"
                    );

            message =
                    message.replace(
                            "%player%",
                            playerName
                    );

            Bukkit.broadcastMessage(
                    plugin.color(
                            message
                    )
            );
        }

        /*
         * ========================================================
         * LOG
         * ========================================================
         */

        if (plugin.getConfig().getBoolean(
                "pvp.combat-log.log",
                false
        )) {

            plugin.getLogger().warning(
                    playerName +
                            " combat-logged."
            );
        }
    }
}