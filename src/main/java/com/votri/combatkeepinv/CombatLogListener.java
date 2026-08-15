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

    private static final String BYPASS =
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

    private void handleDisconnect(
            Player player
    ) {
        if (player == null) {
            return;
        }

        UUID uuid =
                player.getUniqueId();

        /*
         * HARD BYPASS.
         *
         * Must happen before journal creation.
         */
        if (player.hasPermission(BYPASS)) {
            combatManager.remove(uuid);
            return;
        }

        if (plugin.isWorldDisabled(
                player.getWorld()
        )) {
            combatManager.remove(uuid);
            return;
        }

        if (!plugin.getConfig().getBoolean(
                "pvp.combat-log.enabled",
                true
        )) {
            return;
        }

        /*
         * Duplicate protection.
         */
        if (journalManager.isProcessing(uuid) ||
                journalManager.hasJournal(uuid)) {
            return;
        }

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
         * Only apply minimum-remaining to our own timer.
         * PvPManager-only combat does not have our timer.
         */
        if (ownCombat &&
                remaining < minimum) {
            return;
        }

        /*
         * Snapshot FIRST.
         */
        if (!journalManager.createSnapshot(
                player
        )) {
            return;
        }

        /*
         * Process the logout directly.
         *
         * No player.kill command.
         * No fake death.
         */
        journalManager.processCombatLogout(
                player
        );

        punish(
                player
        );

        combatManager.remove(uuid);
    }

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
                    plugin.color(message)
            );
        }

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