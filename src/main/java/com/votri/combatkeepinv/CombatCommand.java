package com.votri.combatkeepinv;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CombatCommand
        implements CommandExecutor, TabCompleter {

    private final CombatKeepInventory plugin;

    public CombatCommand(
            CombatKeepInventory plugin
    ) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {

        if (args.length == 0) {
            sendInfo(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "reload":

                if (!sender.hasPermission(
                        "combatkeepinventory.admin"
                )) {

                    sender.sendMessage(
                            ChatColor.RED
                                    + "Bạn không có quyền."
                    );

                    return true;
                }

                try {

                    plugin.reloadPluginConfig();

                    sender.sendMessage(
                            ChatColor.GREEN
                                    + "CombatKeepInventory đã reload."
                    );

                } catch (Exception exception) {

                    sender.sendMessage(
                            ChatColor.RED
                                    + "Reload thất bại. Kiểm tra console."
                    );

                    plugin.getLogger().severe(
                            "Failed to reload configuration."
                    );

                    exception.printStackTrace();
                }

                return true;

            case "info":

                sendInfo(sender);
                return true;

            case "pvp":

                return handlePvP(
                        sender,
                        args
                );

            default:

                sender.sendMessage(
                        ChatColor.YELLOW
                                + "/cki <reload|info|pvp>"
                );

                return true;
        }
    }

    private boolean handlePvP(
            CommandSender sender,
            String[] args
    ) {

        if (!plugin.canTogglePvP(sender)) {

            sender.sendMessage(
                    ChatColor.RED
                            + "Bạn không có quyền bật/tắt PvP."
            );

            return true;
        }

        if (args.length < 2) {

            sender.sendMessage(
                    ChatColor.YELLOW
                            + "/cki pvp <on|off>"
            );

            return true;
        }

        if (args[1].equalsIgnoreCase("on")) {

            plugin.setPvPEnabled(true);

            sender.sendMessage(
                    ChatColor.GREEN
                            + "PvP đã được BẬT."
            );

            return true;
        }

        if (args[1].equalsIgnoreCase("off")) {

            plugin.setPvPEnabled(false);

            sender.sendMessage(
                    ChatColor.RED
                            + "PvP đã được TẮT."
            );

            return true;
        }

        sender.sendMessage(
                ChatColor.YELLOW
                        + "/cki pvp <on|off>"
        );

        return true;
    }

    private void sendInfo(
            CommandSender sender
    ) {

        sender.sendMessage(
                ChatColor.DARK_GRAY
                        + "━━━━━━━━━━━━━━━━━━━━━━━━"
        );

        sender.sendMessage(
                ChatColor.GOLD
                        + " CombatKeepInventory V3"
        );

        sender.sendMessage(
                ChatColor.GRAY
                        + "Version: "
                        + ChatColor.WHITE
                        + plugin.getDescription()
                        .getVersion()
        );

        sender.sendMessage(
                ChatColor.GRAY
                        + "Combat: "
                        + ChatColor.WHITE
                        + plugin.getCombatDurationSeconds()
                        + " seconds"
        );

        sender.sendMessage(
                ChatColor.GRAY
                        + "PvP: "
                        + (plugin.isPvPEnabled()
                        ? ChatColor.GREEN + "ON"
                        : ChatColor.RED + "OFF")
        );

        sender.sendMessage(
                ChatColor.GRAY
                        + "WorldGuard: "
                        + ChatColor.WHITE
                        + (plugin.getWorldGuardHook()
                        .isAvailable()
                        ? "AVAILABLE"
                        : "NOT INSTALLED")
        );

        sender.sendMessage(
                ChatColor.GRAY
                        + "PvPManager: "
                        + ChatColor.WHITE
                        + (plugin.getPvpManagerHook()
                        .isAvailable()
                        ? "AVAILABLE"
                        : "NOT INSTALLED")
        );

        sender.sendMessage(
                ChatColor.DARK_GRAY
                        + "━━━━━━━━━━━━━━━━━━━━━━━━"
        );
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {

        if (args.length == 1) {

            String input =
                    args[0].toLowerCase();

            List<String> result =
                    new ArrayList<>();

            if ("reload".startsWith(input)) {
                result.add("reload");
            }

            if ("info".startsWith(input)) {
                result.add("info");
            }

            if ("pvp".startsWith(input)) {
                result.add("pvp");
            }

            return result;
        }

        if (args.length == 2
                && args[0].equalsIgnoreCase("pvp")) {

            return List.of(
                    "on",
                    "off"
            );
        }

        return Collections.emptyList();
    }
}
