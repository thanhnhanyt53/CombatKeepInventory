package com.votri.combatkeepinv;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

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

    /**
     * Detect PvPManager without a compile-time dependency.
     */
    private void detect() {

        if (!plugin.getConfig().getBoolean(
                "pvpmanager.enabled",
                true
        )) {
            available = false;
            pvpManager = null;
            return;
        }

        Plugin instance =
                plugin.getServer()
                        .getPluginManager()
                        .getPlugin("PvPManager");

        if (instance == null ||
                !instance.isEnabled()) {

            available = false;
            pvpManager = null;
            return;
        }

        pvpManager = instance;
        available = true;

        debug(
                "PvPManager integration enabled."
        );
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Determines whether a player is currently
     * combat-tagged by PvPManager.
     */
    public boolean isInCombat(
            Player player
    ) {

        if (player == null) {
            return false;
        }

        /*
         * Integration disabled/unavailable.
         */
        if (!available) {
            return fallback(player);
        }

        /*
         * Config explicitly disables reading
         * PvPManager combat state.
         */
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

        /*
         * PvPManager exists but its API could not
         * be resolved. Fall back to our own tracker.
         */
        return fallback(player);
    }

    /**
     * Tries several known/public PvPManager API
     * patterns without importing PvPManager classes.
     */
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
         * Try methods directly on PvPManager.
         */
        for (String methodName : methodNames) {

            Boolean result =
                    invokeBooleanMethod(
                            pvpManager,
                            methodName,
                            player
                    );

            if (result != null) {
                return result;
            }
        }

        /*
         * Try manager-style accessors.
         */
        String[] accessors = {
                "getManager",
                "getCombatManager",
                "getTagManager",
                "getPvpManager"
        };

        for (String accessor : accessors) {

            Object manager =
                    invokeNoArgMethod(
                            pvpManager,
                            accessor
                    );

            if (manager == null) {
                continue;
            }

            for (String methodName : methodNames) {

                Boolean result =
                        invokeBooleanMethod(
                                manager,
                                methodName,
                                player
                        );

                if (result != null) {
                    return result;
                }
            }
        }

        debug(
                "PvPManager combat API could not be resolved."
        );

        return null;
    }

    /**
     * Invoke:
     *
     * object.method(Player)
     *
     * and return Boolean if compatible.
     */
    private Boolean invokeBooleanMethod(
            Object object,
            String methodName,
            Player player
    ) {

        if (object == null) {
            return null;
        }

        try {

            Method method =
                    object.getClass()
                            .getMethod(
                                    methodName,
                                    Player.class
                            );

            Object result =
                    method.invoke(
                            object,
                            player
                    );

            if (result instanceof Boolean value) {
                return value;
            }

        } catch (Throwable ignored) {
            /*
             * Method does not exist or could not
             * be invoked. Continue trying alternatives.
             */
        }

        return null;
    }

    /**
     * Invoke a no-argument method.
     */
    private Object invokeNoArgMethod(
            Object object,
            String methodName
    ) {

        if (object == null) {
            return null;
        }

        try {

            Method method =
                    object.getClass()
                            .getMethod(methodName);

            return method.invoke(object);

        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Fallback to CombatKeepInventory's own
     * combat tracker.
     */
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

    private void debug(String message) {

        if (!plugin.getConfig().getBoolean(
                "debug.pvpmanager",
                false
        )) {
            return;
        }

        plugin.getLogger().warning(
                "[PvPManager] " + message
        );
    }
}