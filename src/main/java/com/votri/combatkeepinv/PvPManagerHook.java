package com.votri.combatkeepinv;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

public final class PvPManagerHook {

    private final CombatKeepInventory plugin;

    private boolean available;
    private Object pvpManager;

    public PvPManagerHook(
            CombatKeepInventory plugin
    ) {

        this.plugin = plugin;

        if (!plugin.getConfig().getBoolean(
                "pvpmanager.enabled",
                true
        )) {
            return;
        }

        Plugin pluginInstance =
                Bukkit.getPluginManager()
                        .getPlugin("PvPManager");

        if (pluginInstance == null
                || !pluginInstance.isEnabled()) {
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

        if (!available
                || player == null) {
            return false;
        }

        if (!plugin.getConfig().getBoolean(
                "pvpmanager.use-combat-state",
                true
        )) {
            return false;
        }

        try {

            /*
             * PvPManager API differs between releases.
             * Reflection keeps this hook optional.
             */

            Class<?> managerClass =
                    Class.forName(
                            "me.NoChance.PvPManager.PvPManager"
                    );

            Method getInstance =
                    managerClass.getMethod(
                            "getInstance"
                    );

            Object instance =
                    getInstance.invoke(null);

            for (String methodName : new String[]{
                    "isInCombat",
                    "isInCombatMode",
                    "hasCombatTag"
            }) {

                try {

                    Method method =
                            managerClass.getMethod(
                                    methodName,
                                    Player.class
                            );

                    Object result =
                            method.invoke(
                                    instance,
                                    player
                            );

                    if (result instanceof Boolean) {
                        return (Boolean) result;
                    }

                } catch (NoSuchMethodException ignored) {
                }
            }

        } catch (Throwable throwable) {

            if (plugin.getConfig().getBoolean(
                    "debug.pvpmanager",
                    false
            )) {

                plugin.getLogger().warning(
                        "PvPManager reflection failed: "
                                + throwable.getMessage()
                );
            }
        }

        /*
         * If PvPManager is unavailable through
         * its API, optionally fall back to our
         * own combat system.
         */
        return plugin.getConfig().getBoolean(
                "pvpmanager.fallback-to-own-combat",
                true
        ) && plugin.getCombatManager()
                .isInCombat(player);
    }
}
