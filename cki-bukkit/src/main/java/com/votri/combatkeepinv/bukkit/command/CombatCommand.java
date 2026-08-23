package com.votri.combatkeepinv.bukkit.command;

import com.votri.combatkeepinv.bukkit.CombatKeepInventory;
import com.votri.combatkeepinv.core.platform.PlatformInfo;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Administrative commands for CombatKeepInventory.
 *
 * <p>PvP on/off commands intentionally do not exist here.
 * PvP control belongs to another plugin.</p>
 */
public final class CombatCommand
        implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERMISSION =
            "combatkeepinventory.admin";

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

        String subCommand =
                args[0].toLowerCase(
                        Locale.ROOT
                );

        return switch (subCommand) {

            case "reload" ->
                    reload(sender);

            case "info" ->
                    info(sender);

            default -> {
                sendUsage(sender);
                yield true;
            }
        };
    }

    private boolean reload(
            CommandSender sender
    ) {

        if (!sender.hasPermission(
                ADMIN_PERMISSION
        )) {

            sendNoPermission(sender);

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
                ADMIN_PERMISSION
        )) {

            sendNoPermission(sender);

            return true;
        }

        PlatformInfo platform =
                plugin.getPlatform();

        sender.sendMessage(
                plugin.getMessage(
                        "command.info.header",
                        "&8&m-----------------------------"
                )
        );

        sender.sendMessage(
                plugin.getMessage(
                        "command.info.title",
                        "&cCombatKeepInventory &f"
                                + CombatKeepInventory.PLUGIN_VERSION
                )
        );

        sender.sendMessage(
                plugin.getMessage(
                        "command.info.platform",
                        "&7Platform: &f%platform%"
                ).replace(
                        "%platform%",
                        platformName(platform)
                )
        );

        sender.sendMessage(
                plugin.getMessage(
                        "command.info.minecraft-version",
                        "&7Minecraft: &f%version%"
                ).replace(
                        "%version%",
                        platform.getMinecraftVersion()
                )
        );

        sender.sendMessage(
                plugin.getMessage(
                        "command.info.implementation-version",
                        "&7Implementation: &f%version%"
                ).replace(
                        "%version%",
                        platform.getImplementationVersion()
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

        /*
         * PvP is intentionally not displayed as an internal
         * CKI toggle anymore.
         */

        sender.sendMessage(
                plugin.getMessage(
                        "command.info.worldguard",
                        "&7WorldGuard: &f%worldguard%"
                ).replace(
                        "%worldguard%",
                        plugin.getWorldGuardHook()
                                .isAvailable()
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

    private void sendUsage(
            CommandSender sender
    ) {

        List<String> lines =
                plugin.getMessageList(
                        "command.usage"
                );

        if (lines.isEmpty()) {

            sender.sendMessage(
                    plugin.color(
                            "&e/cki reload"
                    )
            );

            sender.sendMessage(
                    plugin.color(
                            "&e/cki info"
                    )
            );

            return;
        }

        for (String line : lines) {
            sender.sendMessage(line);
        }
    }

    private void sendNoPermission(
            CommandSender sender
    ) {

        sender.sendMessage(
                plugin.getMessage(
                        "general.no-permission",
                        "&cYou don't have permission."
                )
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

            if (!sender.hasPermission(
                    ADMIN_PERMISSION
            )) {
                return Collections.emptyList();
            }

            return filter(
                    List.of(
                            "reload",
                            "info"
                    ),
                    args[0]
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

        String lowerInput =
                input == null
                        ? ""
                        : input.toLowerCase(
                                Locale.ROOT
                        );

        for (String value : values) {

            if (value.toLowerCase(
                    Locale.ROOT
            ).startsWith(
                    lowerInput
            )) {

                result.add(value);
            }
        }

        return result;
    }

    private String platformName(
            PlatformInfo platform
    ) {

        if (platform == null) {
            return "unknown";
        }

        return platform
                .getType()
                .name()
                .toLowerCase(
                        Locale.ROOT
                );
    }
}