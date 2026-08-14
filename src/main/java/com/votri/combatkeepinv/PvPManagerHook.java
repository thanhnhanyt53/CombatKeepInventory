package com.votri.combatkeepinv;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Locale;

public final class PvPManagerHook {

    private final CombatKeepInventory plugin;
    private final CombatManager ownCombatManager;

    private boolean available;
    private Plugin pvpManager;

    public PvPManagerHook(
            CombatKeepInventory plugin,
            CombatManager ownCombatManager
    ) {
        this.plugin = plugin;
        this.ownCombatManager = ownCombatManager;

        detect();
    }

    private void detect() {
        if (!plugin.getConfig().getBoolean(
                "pvpmanager.enabled",
                true
        )) {
            available = false;
            return;
        }

        Plugin pluginInstance =
                plugin.getServer()
                        .getPluginManager()
                        .getPlugin("PvPManager");

        if (pluginInstance == null ||
                !pluginInstance.isEnabled()) {
            available = false;
            return;
        }

        pvpManager = pluginInstance;
        available = true;
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isInCombat(
            Player player
    ) {
        if (player == null) {
            return false;
        }

        if (!available) {
            return fallback(player);
        }

        if (!plugin.getConfig().getBoolean(
                "pvpmanager.use-combat-state",
                true
        )) {
            return fallback(player);
        }

        Boolean result =
                invokeCombatMethod(player);

        if (result != null) {
            return result;
        }

        return fallback(player);
    }

    private Boolean invokeCombatMethod(
            Player player
    ) {
        String[] methodNames = {
                "isInCombat",
                "isInCombatMode",
                "hasCombatTag",
                "isTagged"
        };

        /*
         * Try public methods on the plugin instance.
         */
        for (String name : methodNames) {
            try {
                Method method =
                        pvpManager.getClass()
                                .getMethod(
                                        name,
                                        Player.class
                                );

                Object result =
                        method.invoke(
                                pvpManager,
                                player
                        );

                if (result instanceof Boolean value) {
                    return value;
                }

            } catch (Throwable ignored) {
            }
        }

        /*
         * Try manager-like objects exposed by PvPManager.
         */
        String[] accessors = {
                "getManager",
                "getCombatManager",
                "getTagManager",
                "getPvpManager"
        };

        for (String accessor : accessors) {
            try {
                Method getter =
                        pvpManager.getClass()
                                .getMethod(
                                        accessor
                                );

                Object manager =
                        getter.invoke(
                                pvpManager
                        );

                if (manager == null) {
                    continue;
                }

                for (String name : methodNames) {
                    try {
                        Method method =
                                manager.getClass()
                                        .getMethod(
                                                name,
                                                Player.class
                                        );

                        Object result =
                                method.invoke(
                                        manager,
                                        player
                                );

                        if (result instanceof Boolean value) {
                            return value;
                        }

                    } catch (Throwable ignored) {
                    }
                }

            } catch (Throwable ignored) {
            }
        }

        if (plugin.getConfig().getBoolean(
                "debug.pvpmanager",
                false
        )) {
            plugin.getLogger().warning(
                    "PvPManager combat API could not be resolved by reflection."
            );
        }

        return null;
    }

    private boolean fallback(
            Player player
    ) {
        if (!plugin.getConfig().getBoolean(
                "pvpmanager.fallback-to-own-combat",
                true
        )) {
            return false;
        }

        return ownCombatManager.isInCombat(
                player.getUniqueId()
        );
    }
}