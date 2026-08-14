package com.votri.combatkeepinv;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.UUID;

public final class CombatLogListener implements Listener {

    private final CombatKeepInventory plugin;
    private final CombatManager combatManager;
    private final PvPManagerHook pvpManager;

    public CombatLogListener(
            CombatKeepInventory plugin,
            CombatManager combatManager,
            PvPManagerHook pvpManager
    ) {
        this.plugin = plugin;
        this.combatManager = combatManager;
        this.pvpManager = pvpManager;
    }

    @EventHandler(
            priority = EventPriority.MONITOR
    )
    public void onQuit(
            PlayerQuitEvent event
    ) {
        handleQuit(event.getPlayer());
    }

    @EventHandler(
            priority = EventPriority.MONITOR
    )
    public void onKick(
            PlayerKickEvent event
    ) {
        handleQuit(event.getPlayer());
    }

    private void handleQuit(
            Player player
    ) {
        if (player == null) {
            return;
        }

        if (plugin.isWorldDisabled(
                player.getWorld()
        )) {
            return;
        }

        if (player.hasPermission(
                "combatkeepinventory.bypass"
        )) {
            combatManager.remove(
                    player.getUniqueId()
            );
            return;
        }

        if (!plugin.getConfig().getBoolean(
                "pvp.combat-log.enabled",
                true
        )) {
            return;
        }

        boolean ownCombat = false;

        if (plugin.getConfig().getBoolean(
                "pvp.combat-log.check-own-combat",
                true
        )) {
            ownCombat =
                    combatManager.isInCombat(
                            player.getUniqueId()
                    );
        }

        boolean managerCombat = false;

        if (plugin.getConfig().getBoolean(
                "pvp.combat-log.check-pvpmanager",
                true
        )) {
            managerCombat =
                    pvpManager.isInCombat(player);
        }

        boolean inCombat =
                ownCombat || managerCombat;

        if (!inCombat) {
            return;
        }

        long ownRemaining =
                combatManager.getRemainingSeconds(
                        player.getUniqueId()
                );

        long minimum =
                Math.max(
                        0L,
                        plugin.getConfig().getLong(
                                "pvp.combat-log.minimum-remaining-seconds",
                                0L
                        )
                );

        if (ownCombat &&
                ownRemaining < minimum) {
            return;
        }

        punish(player);

        combatManager.remove(
                player.getUniqueId()
        );
    }

    private void punish(
            Player player
    ) {
        String playerName =
                player.getName();

        String uuid =
                player.getUniqueId().toString();

        List<String> commands =
                plugin.getConfig().getStringList(
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
