package com.votri.combatkeepinv;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

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
            sendUsage(sender);
            return true;
        }

        switch (
                args[0].toLowerCase(Locale.ROOT)
        ) {
            case "reload":
                return reload(sender);

            case "info":
                return info(sender);

            case "pvp":
                return pvp(sender, args);

            default:
                sendUsage(sender);
                return true;
        }
    }

    private boolean reload(
            CommandSender sender
    ) {
        if (!sender.hasPermission(
                "combatkeepinventory.admin"
        )) {
            sender.sendMessage(
                    plugin.getMessage(
                            "general.no-permission",
                            "&cYou don't have permission."
                    )
            );
            return true;
        }

        plugin.reloadPlugin();

        sender.sendMessage(
                plugin.getMessage(
                        "command.reload.success",
                        "&aCombatKeepInventory has been reloaded."
                )
        );

        return true;
    }

    private boolean info(
            CommandSender sender
    ) {
        if (!sender.hasPermission(
                "combatkeepinventory.admin"
        )) {
            sender.sendMessage(
                    plugin.getMessage(
                            "general.no-permission",
                            "&cYou don't have permission."
                    )
            );
            return true;
        }

        sender.sendMessage(
                plugin.getMessage(
                        "command.info.header",
                        "&8&m-----------------------------"
                )
        );

        sender.sendMessage(
                plugin.getMessage(
                        "command.info.title",
                        "&cCombatKeepInventory &f1.0.0"
                )
        );

        sender.sendMessage(
                plugin.getMessage(
                        "command.info.platform",
                        "&7Platform: &f%platform%"
                ).replace(
                        "%platform%",
                        plugin.getDetectedPlatform()
                )
        );

        sender.sendMessage(
                plugin.getMessage(
                        "command.info.selected-platform",
                        "&7Selected mode: &f%mode%"
                ).replace(
                        "%mode%",
                        plugin.getSelectedPlatform()
                )
        );

        sender.sendMessage(
                plugin.getMessage(
                        "command.info.duration",
                        "&7Combat duration: &f%seconds% seconds"
                ).replace(
                        "%seconds%",
                        String.valueOf(
                                plugin.getCombatDurationSeconds()
                        )
                )
        );

        sender.sendMessage(
                plugin.getMessage(
                        "command.info.pvp",
                        "&7PvP: &f%pvp%"
                ).replace(
                        "%pvp%",
                        plugin.isPvPEnabled()
                                ? plugin.getMessage(
                                        "status.enabled",
                                        "&aENABLED"
                                )
                                : plugin.getMessage(
                                        "status.disabled",
                                        "&cDISABLED"
                                )
                )
        );

        sender.sendMessage(
                plugin.getMessage(
                        "command.info.worldguard",
                        "&7WorldGuard: &f%worldguard%"
                ).replace(
                        "%worldguard%",
                        plugin.getWorldGuardHook().isAvailable()
                                ? "ENABLED"
                                : "NOT INSTALLED"
                )
        );

        sender.sendMessage(
                plugin.getMessage(
                        "command.info.pvpmanager",
                        "&7PvPManager: &f%pvpmanager%"
                ).replace(
                        "%pvpmanager%",
                        plugin.getPvpManagerHook().isAvailable()
                                ? "ENABLED"
                                : "NOT INSTALLED"
                )
        );

        sender.sendMessage(
                plugin.getMessage(
                        "command.info.footer",
                        "&8&m-----------------------------"
                )
        );

        return true;
    }

    private boolean pvp(
            CommandSender sender,
            String[] args
    ) {
        if (!plugin.canTogglePvP(sender)) {
            sender.sendMessage(
                    plugin.getMessage(
                            "general.no-permission",
                            "&cYou don't have permission."
                    )
            );
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(
                    plugin.getMessage(
                            "command.pvp.usage",
                            "&eUsage: /cki pvp <on|off>"
                    )
            );
            return true;
        }

        String value =
                args[1].toLowerCase(Locale.ROOT);

        if ("on".equals(value)) {
            plugin.setPvPEnabled(true);

            sender.sendMessage(
                    plugin.getMessage(
                            "command.pvp.enabled",
                            "&aPvP has been enabled."
                    )
            );

            return true;
        }

        if ("off".equals(value)) {
            plugin.setPvPEnabled(false);

            sender.sendMessage(
                    plugin.getMessage(
                            "command.pvp.disabled",
                            "&cPvP has been disabled."
                    )
            );

            return true;
        }

        sender.sendMessage(
                plugin.getMessage(
                        "command.pvp.usage",
                        "&eUsage: /cki pvp <on|off>"
                )
        );

        return true;
    }

    private void sendUsage(
            CommandSender sender
    ) {
        List<String> lines =
                plugin.getMessageList(
                        "command.usage"
                );

        if (lines.isEmpty()) {
            sender.sendMessage(
                    "&e/cki reload"
            );
            sender.sendMessage(
                    "&e/cki info"
            );
            sender.sendMessage(
                    "&e/cki pvp <on|off>"
            );
            return;
        }

        for (String line : lines) {
            sender.sendMessage(line);
        }
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {
        if (args.length == 1) {
            List<String> result =
                    new ArrayList<>();

            if (sender.hasPermission(
                    "combatkeepinventory.admin"
            )) {
                result.add("reload");
                result.add("info");
            }

            if (plugin.canTogglePvP(sender)) {
                result.add("pvp");
            }

            return filter(
                    result,
                    args[0]
            );
        }

        if (args.length == 2 &&
                "pvp".equalsIgnoreCase(args[0])) {

            return filter(
                    List.of(
                            "on",
                            "off"
                    ),
                    args[1]
            );
        }

        return Collections.emptyList();
    }

    private List<String> filter(
            List<String> values,
            String input
    ) {
        List<String> result =
                new ArrayList<>();

        for (String value : values) {
            if (value.toLowerCase(
                    Locale.ROOT
            ).startsWith(
                    input.toLowerCase(
                            Locale.ROOT
                    )
            )) {
                result.add(value);
            }
        }

        return result;
    }
}