package com.votri.combatkeepinv;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;

public final class CombatLogListener implements Listener {

    private final CombatKeepInventory plugin;

    public CombatLogListener(
            CombatKeepInventory plugin
    ) {
        this.plugin = plugin;
    }

    @EventHandler(
            priority = EventPriority.MONITOR
    )
    public void onPlayerQuit(PlayerQuitEvent event) {

        if (!plugin.getConfig().getBoolean(
                "pvp.combat-log.enabled",
                true
        )) {
            return;
        }

        Player player = event.getPlayer();

        /*
         * Bypass players are never punished.
         */
        if (player.hasPermission(
                "combatkeepinventory.bypass"
        )) {
            return;
        }

        /*
         * Disabled worlds are ignored.
         */
        if (plugin.isWorldDisabled(
                player.getWorld()
        )) {
            return;
        }

        /*
         * PvP must be enabled.
         */
        if (!plugin.isPvPEnabled()) {
            return;
        }

        boolean inCombat = false;

        /*
         * Own combat system.
         */
        if (plugin.getConfig().getBoolean(
                "pvp.combat-log.check-own-combat",
                true
        )) {

            inCombat =
                    plugin.getCombatManager()
                            .isInCombat(player);
        }

        /*
         * PvPManager integration.
         */
        if (!inCombat
                && plugin.getConfig().getBoolean(
                "pvp.combat-log.check-pvpmanager",
                true
        )
                && plugin.getPvpManagerHook() != null
                && plugin.getPvpManagerHook().isAvailable()) {

            inCombat =
                    plugin.getPvpManagerHook()
                            .hasActiveCombat(player);
        }

        if (!inCombat) {
            return;
        }

        /*
         * Check minimum remaining combat time.
         */
        long minimumRemaining =
                plugin.getConfig().getLong(
                        "pvp.combat-log.minimum-remaining-seconds",
                        0L
                );

        if (minimumRemaining > 0
                && !plugin.getCombatManager()
                .hasRemainingCombatTime(
                        player,
                        minimumRemaining * 1000L
                )) {

            return;
        }

        punish(player);
    }

    private void punish(Player player) {

        String playerName = player.getName();
        String uuid = player.getUniqueId().toString();

        /*
         * Console commands.
         */
        List<String> commands =
                plugin.getConfig().getStringList(
                        "pvp.combat-log.commands"
                );

        for (String command : commands) {

            if (command == null
                    || command.isBlank()) {
                continue;
            }

            command = command
                    .replace("%player%", playerName)
                    .replace("%uuid%", uuid);

            Bukkit.dispatchCommand(
                    Bukkit.getConsoleSender(),
                    command
            );
        }

        /*
         * Console logging.
         */
        if (plugin.getConfig().getBoolean(
                "pvp.combat-log.log",
                true
        )) {

            plugin.getLogger().warning(
                    playerName
                            + " combat-logged!"
            );
        }

        /*
         * Server broadcast.
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

            message = message
                    .replace("%player%", playerName)
                    .replace("%uuid%", uuid);

            Bukkit.broadcastMessage(
                    ChatColor.translateAlternateColorCodes(
                            '&',
                            message
                    )
            );
        }
    }
}
