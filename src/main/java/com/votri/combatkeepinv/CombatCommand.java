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

    public CombatCommand(CombatKeepInventory plugin) {
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
                                    + "Bạn không có quyền sử dụng lệnh này."
                    );

                    return true;
                }

                try {

                    plugin.reloadPluginConfig();

                    sender.sendMessage(
                            ChatColor.GREEN
                                    + "CombatKeepInventory đã reload thành công."
                    );

                } catch (Exception exception) {

                    sender.sendMessage(
                            ChatColor.RED
                                    + "Reload thất bại. Kiểm tra console."
                    );

                    plugin.getLogger().severe(
                            "Failed to reload CombatKeepInventory."
                    );

                    exception.printStackTrace();
                }

                return true;

            case "info":

                sendInfo(sender);

                return true;

            default:

                sender.sendMessage(
                        ChatColor.YELLOW
                                + "Sử dụng: /cki <reload|info>"
                );

                return true;
        }
    }

    private void sendInfo(CommandSender sender) {

        sender.sendMessage(
                ChatColor.DARK_GRAY
                        + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );

        sender.sendMessage(
                ChatColor.GOLD
                        + " CombatKeepInventory"
        );

        sender.sendMessage(
                ChatColor.GRAY
                        + "Version: "
                        + ChatColor.WHITE
                        + plugin.getDescription().getVersion()
        );

        sender.sendMessage(
                ChatColor.GRAY
                        + "Combat duration: "
                        + ChatColor.WHITE
                        + plugin.getCombatDurationSeconds()
                        + " seconds"
        );

        sender.sendMessage(
                ChatColor.GRAY
                        + "WorldGuard: "
                        + ChatColor.WHITE
                        + (plugin.getWorldGuardHook() != null
                        && plugin.getWorldGuardHook().isAvailable()
                        ? "ENABLED"
                        : "NOT INSTALLED")
        );

        sender.sendMessage(
                ChatColor.GRAY
                        + "PvPManager: "
                        + ChatColor.WHITE
                        + (plugin.getPvpManagerHook() != null
                        && plugin.getPvpManagerHook().isAvailable()
                        ? "ENABLED"
                        : "NOT INSTALLED")
        );

        sender.sendMessage(
                ChatColor.GRAY
                        + "Bypass permission: "
                        + ChatColor.WHITE
                        + "combatkeepinventory.bypass"
        );

        sender.sendMessage(
                ChatColor.DARK_GRAY
                        + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
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

            String input = args[0].toLowerCase();

            List<String> result = new ArrayList<>();

            if ("reload".startsWith(input)) {
                result.add("reload");
            }

            if ("info".startsWith(input)) {
                result.add("info");
            }

            return result;
        }

        return Collections.emptyList();
    }
        }
