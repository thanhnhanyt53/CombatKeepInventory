package com.votri.combatkeepinv.bukkit.command;

import com.votri.combatkeepinv.bukkit.CombatKeepInventory;
import com.votri.combatkeepinv.core.api.CombatService;
import com.votri.combatkeepinv.core.api.CombatTag;
import com.votri.combatkeepinv.core.damage.DamageSource;
import com.votri.combatkeepinv.core.platform.PlatformInfo;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

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

    private static final String DEBUG_PERMISSION =
            "combatkeepinventory.debug";

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

            case "debug" ->
                    debug(sender, args);

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
                plugin.color(
                        "&7Version adapter: &f"
                                + plugin.getVersionAdapter()
                                        .getLabel()
                                + " &7(TNT source tracking: &f"
                                + (
                                plugin.getVersionAdapter()
                                        .supportsTntSourceTracking()
                                        ? "enabled"
                                        : "disabled"
                        )
                                + "&7)"
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

    /*
     * ==========================================================
     * DEBUG (in-game, no console required)
     * ==========================================================
     */

    private boolean debug(
            CommandSender sender,
            String[] args
    ) {

        if (!sender.hasPermission(
                DEBUG_PERMISSION
        )) {

            sendNoPermission(sender);

            return true;
        }

        /*
         * /cki debug            -> toggle live watch for sender
         * /cki debug <player>   -> one-shot snapshot
         */
        if (args.length < 2) {

            if (!(sender instanceof Player admin)) {

                sender.sendMessage(
                        plugin.color(
                                "&cConsole already sees debug "
                                        + "output in the server log. "
                                        + "Use /cki debug <player> "
                                        + "for a snapshot."
                        )
                );

                return true;
            }

            boolean nowWatching =
                    plugin.getDebugManager().toggle(admin);

            sender.sendMessage(
                    plugin.color(
                            nowWatching
                                    ? "&aLive CombatKeepInventory "
                                            + "debug is now &lON&r&a. "
                                            + "Combat/damage/death "
                                            + "events will appear "
                                            + "in your chat."
                                    : "&7Live CombatKeepInventory "
                                            + "debug is now OFF."
                    )
            );

            return true;
        }

        String targetName =
                args[1];

        Player target =
                Bukkit.getPlayer(targetName);

        if (target == null) {

            sender.sendMessage(
                    plugin.color(
                            "&cPlayer '"
                                    + targetName
                                    + "' is not online."
                    )
            );

            return true;
        }

        sendDebugSnapshot(sender, target);

        return true;
    }

    private void sendDebugSnapshot(
            CommandSender sender,
            Player target
    ) {

        UUID id =
                target.getUniqueId();

        CombatService service =
                plugin.getCombatService();

        boolean inCombat =
                service.isInCombat(id);

        CombatTag tag =
                service.getCombatTag(id);

        sender.sendMessage(
                plugin.color(
                        "&8&m-----------------------------"
                )
        );

        sender.sendMessage(
                plugin.color(
                        "&bCKI Debug &f- "
                                + target.getName()
                )
        );

        sender.sendMessage(
                plugin.color(
                        "&7In combat: &f"
                                + inCombat
                )
        );

        if (tag != null
                && tag.isActive()) {

            sender.sendMessage(
                    plugin.color(
                            "&7Remaining: &f"
                                    + tag.getRemainingSeconds()
                                    + "s"
                    )
            );

            if (tag.hasOpponent()) {

                OfflinePlayer opponent =
                        Bukkit.getOfflinePlayer(
                                tag.getLastOpponent()
                        );

                sender.sendMessage(
                        plugin.color(
                                "&7Last opponent: &f"
                                        + opponent.getName()
                        )
                );
            }
        }

        Optional<DamageSource> lastDamage =
                service.getDamageAttributionService()
                        .getLastDamage(id);

        if (lastDamage.isPresent()) {

            DamageSource source =
                    lastDamage.get();

            sender.sendMessage(
                    plugin.color(
                            "&7Last damage cause: &f"
                                    + source.getCauseType()
                    )
            );

            sender.sendMessage(
                    plugin.color(
                            "&7Player-caused: &f"
                                    + source.isPlayerCaused()
                                    + " &7Indirect: &f"
                                    + source.isIndirect()
                    )
            );

            source.getResponsiblePlayerId().ifPresent(
                    responsibleId -> {

                        OfflinePlayer responsible =
                                Bukkit.getOfflinePlayer(
                                        responsibleId
                                );

                        sender.sendMessage(
                                plugin.color(
                                        "&7Responsible player: &f"
                                                + responsible.getName()
                                )
                        );
                    }
            );

        } else {

            sender.sendMessage(
                    plugin.color(
                            "&7Last damage cause: &fnone recorded"
                    )
            );
        }

        sender.sendMessage(
                plugin.color(
                        "&8&m-----------------------------"
                )
        );
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

            sender.sendMessage(
                    plugin.color(
                            "&e/cki debug [player]"
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
                        "command.no-permission",
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

            List<String> options =
                    new ArrayList<>();

            if (sender.hasPermission(
                    ADMIN_PERMISSION
            )) {

                options.add("reload");
                options.add("info");
            }

            if (sender.hasPermission(
                    DEBUG_PERMISSION
            )) {

                options.add("debug");
            }

            return filter(
                    options,
                    args[0]
            );
        }

        if (args.length == 2
                && "debug".equalsIgnoreCase(args[0])
                && sender.hasPermission(
                        DEBUG_PERMISSION
                )) {

            List<String> onlineNames =
                    new ArrayList<>();

            for (Player online : Bukkit.getOnlinePlayers()) {
                onlineNames.add(online.getName());
            }

            return filter(
                    onlineNames,
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