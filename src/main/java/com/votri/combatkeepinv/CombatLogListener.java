package com.votri.combatkeepinv;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;

public final class CombatLogListener
        implements Listener {

    private final CombatKeepInventory plugin;

    public CombatLogListener(
            CombatKeepInventory plugin
    ) {
        this.plugin = plugin;
    }

    @EventHandler(
            priority = EventPriority.MONITOR
    )
    public void onPlayerQuit(
            PlayerQuitEvent event
    ) {

        if (!plugin.getConfig().getBoolean(
                "pvp.combat-log.enabled",
                false
        )) {
            return;
        }

        Player player =
                event.getPlayer();

        /*
         * Bypass.
         */
        if (player.hasPermission(
                "combatkeepinventory.bypass"
        )) {
            return;
        }

        /*
         * Disabled world.
         */
        if (plugin.isWorldDisabled(
                player.getWorld()
        )) {
            return;
        }

        boolean ownCombat =
                plugin.getConfig().getBoolean(
                        "pvp.combat-log.check-own-combat",
                        true
                )
                        && plugin.getCombatManager()
                        .isInCombat(player);

        boolean managerCombat =
                plugin.getConfig().getBoolean(
                        "pvp.combat-log.check-pvpmanager",
                        true
                )
                        && plugin.getPvpManagerHook()
                        .isInCombat(player);

        boolean inCombat =
                ownCombat || managerCombat;

        if (!inCombat) {
            return;
        }

        long minimumRemaining =
                plugin.getConfig().getLong(
                        "pvp.combat-log.minimum-remaining-seconds",
                        0L
                );

        if (minimumRemaining > 0
                && ownCombat
                && !plugin.getCombatManager()
                .hasRemainingCombatTime(
                        player,
                        minimumRemaining * 1000L
                )) {

            return;
        }

        punish(player);
    }

    private void punish(
            Player player
    ) {

        String name =
                player.getName();

        String uuid =
                player.getUniqueId()
                        .toString();

        /*
         * Execute configured commands.
         */
        List<String> commands =
                plugin.getConfig()
                        .getStringList(
                                "pvp.combat-log.commands"
                        );

        for (String command : commands) {

            if (command == null
                    || command.isBlank()) {
                continue;
            }

            command =
                    command.replace(
                            "%player%",
                            name
                    ).replace(
                            "%uuid%",
                            uuid
                    );

            Bukkit.dispatchCommand(
                    Bukkit.getConsoleSender(),
                    command
            );
        }

        /*
         * Broadcast.
         */
        if (plugin.getConfig().getBoolean(
                "pvp.combat-log.broadcast",
                true
        )) {

            String message =
                    plugin.getConfig().getString(
                            "pvp.combat-log.broadcast-message",
                            "&c%player% đã combat-log!"
                    );

            message =
                    message.replace(
                            "%player%",
                            name
                    ).replace(
                            "%uuid%",
                            uuid
                    );

            Bukkit.broadcastMessage(
                    ChatColor.translateAlternateColorCodes(
                            '&',
                            message
                    )
            );
        }

        /*
         * Console log.
         */
        if (plugin.getConfig().getBoolean(
                "pvp.combat-log.log",
                true
        )) {

            plugin.getLogger().warning(
                    name
                            + " combat-logged."
            );
        }
    }
}
