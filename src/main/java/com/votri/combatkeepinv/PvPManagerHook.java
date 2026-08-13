package com.votri.combatkeepinv;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * Optional PvPManager integration.
 *
 * Designed for PvPManager 4.1.71.
 *
 * PvPManager is NOT a compile-time dependency.
 * Reflection is used so CombatKeepInventory can run
 * normally even when PvPManager is not installed.
 */
public final class PvPManagerHook {

    private static final String PVP_MANAGER_PLUGIN =
            "PvPManager";

    private static final String COMBAT_PLAYER_CLASS =
            "me.chancesd.pvpmanager.player.CombatPlayer";

    private final CombatKeepInventory plugin;

    private boolean available;

    private Class<?> combatPlayerClass;

    private Method getCombatPlayerMethod;
    private Method isInCombatMethod;
    private Method getTagTimeLeftMethod;
    private Method wasLastDeathPvPMethod;
    private Method getEnemyMethod;

    public PvPManagerHook(CombatKeepInventory plugin) {

        this.plugin = plugin;

        initialize();
    }

    /**
     * Detect PvPManager and prepare Reflection methods.
     */
    private void initialize() {

        /*
         * ----------------------------------------------------
         * Check plugin
         * ----------------------------------------------------
         */

        if (plugin.getServer()
                .getPluginManager()
                .getPlugin(PVP_MANAGER_PLUGIN) == null) {

            debug(
                    "PvPManager is not installed. "
                            + "Integration disabled."
            );

            available = false;
            return;
        }

        /*
         * ----------------------------------------------------
         * Load CombatPlayer class
         * ----------------------------------------------------
         */

        try {

            combatPlayerClass =
                    Class.forName(
                            COMBAT_PLAYER_CLASS,
                            false,
                            plugin.getClass()
                                    .getClassLoader()
                    );

        } catch (ClassNotFoundException exception) {

            /*
             * The class may be loaded by another plugin
             * classloader. Try the server/plugin classloader.
             */

            try {

                combatPlayerClass =
                        Class.forName(
                                COMBAT_PLAYER_CLASS
                        );

            } catch (ClassNotFoundException secondException) {

                plugin.getLogger().warning(
                        "[PvPManager] PvPManager was detected, "
                                + "but CombatPlayer class could not be loaded."
                );

                available = false;
                return;
            }
        }

        /*
         * ----------------------------------------------------
         * Resolve PvPManager 4.1.71 methods
         * ----------------------------------------------------
         */

        try {

            /*
             * public static CombatPlayer get(Player)
             */
            getCombatPlayerMethod =
                    combatPlayerClass.getMethod(
                            "get",
                            Player.class
                    );

            /*
             * public final boolean isInCombat()
             */
            isInCombatMethod =
                    combatPlayerClass.getMethod(
                            "isInCombat"
                    );

            /*
             * public long getTagTimeLeft()
             */
            getTagTimeLeftMethod =
                    combatPlayerClass.getMethod(
                            "getTagTimeLeft"
                    );

            /*
             * public boolean wasLastDeathPvP()
             */
            wasLastDeathPvPMethod =
                    combatPlayerClass.getMethod(
                            "wasLastDeathPvP"
                    );

            /*
             * public CombatPlayer getEnemy()
             */
            getEnemyMethod =
                    combatPlayerClass.getMethod(
                            "getEnemy"
                    );

            available = true;

            plugin.getLogger().info(
                    "[PvPManager] Integration enabled "
                            + "(PvPManager 4.1.71 API detected)."
            );

        } catch (NoSuchMethodException exception) {

            available = false;

            plugin.getLogger().warning(
                    "[PvPManager] PvPManager detected, "
                            + "but required API methods are missing."
            );

            debug(
                    "Reflection initialization failed: "
                            + exception.getMessage()
            );
        }
    }

    /**
     * Returns true if PvPManager integration is available.
     */
    public boolean isAvailable() {

        return available;
    }

    /**
     * Get CombatPlayer object from PvPManager.
     *
     * PvPManager 4.1.71:
     *
     * CombatPlayer.get(Player)
     */
    private Object getCombatPlayer(Player player) {

        if (!available || player == null) {
            return null;
        }

        try {

            return getCombatPlayerMethod.invoke(
                    null,
                    player
            );

        } catch (Throwable throwable) {

            debug(
                    "Failed to get CombatPlayer for "
                            + player.getName()
                            + ": "
                            + getRootCause(throwable)
            );

            return null;
        }
    }

    /**
     * Check whether PvPManager currently considers
     * the player to be in combat.
     */
    public boolean isInCombat(Player player) {

        if (!available || player == null) {
            return false;
        }

        Object combatPlayer =
                getCombatPlayer(player);

        if (combatPlayer == null) {
            return false;
        }

        try {

            Object result =
                    isInCombatMethod.invoke(
                            combatPlayer
                    );

            return result instanceof Boolean
                    && (Boolean) result;

        } catch (Throwable throwable) {

            debug(
                    "Failed to read isInCombat() for "
                            + player.getName()
                            + ": "
                            + getRootCause(throwable)
            );

            return false;
        }
    }

    /**
     * Returns remaining PvPManager combat time.
     *
     * PvPManager 4.1.71:
     * getTagTimeLeft()
     *
     * The method returns milliseconds.
     */
    public long getTagTimeLeft(Player player) {

        if (!available || player == null) {
            return 0L;
        }

        Object combatPlayer =
                getCombatPlayer(player);

        if (combatPlayer == null) {
            return 0L;
        }

        try {

            Object result =
                    getTagTimeLeftMethod.invoke(
                            combatPlayer
                    );

            if (result instanceof Number number) {

                return Math.max(
                        0L,
                        number.longValue()
                );
            }

        } catch (Throwable throwable) {

            debug(
                    "Failed to read getTagTimeLeft() for "
                            + player.getName()
                            + ": "
                            + getRootCause(throwable)
            );
        }

        return 0L;
    }

    /**
     * Check whether PvPManager considers the player's
     * last death to have been caused by PvP.
     *
     * PvPManager 4.1.71:
     * wasLastDeathPvP()
     */
    public boolean wasLastDeathPvP(Player player) {

        if (!available || player == null) {
            return false;
        }

        Object combatPlayer =
                getCombatPlayer(player);

        if (combatPlayer == null) {
            return false;
        }

        try {

            Object result =
                    wasLastDeathPvPMethod.invoke(
                            combatPlayer
                    );

            return result instanceof Boolean
                    && (Boolean) result;

        } catch (Throwable throwable) {

            debug(
                    "Failed to read wasLastDeathPvP() for "
                            + player.getName()
                            + ": "
                            + getRootCause(throwable)
            );

            return false;
        }
    }

    /**
     * Get current PvPManager enemy.
     *
     * Returns null if:
     * - PvPManager unavailable
     * - player has no CombatPlayer
     * - player has no enemy
     * - reflection failed
     */
    public Player getEnemy(Player player) {

        if (!available || player == null) {
            return null;
        }

        Object combatPlayer =
                getCombatPlayer(player);

        if (combatPlayer == null) {
            return null;
        }

        try {

            Object enemy =
                    getEnemyMethod.invoke(
                            combatPlayer
                    );

            /*
             * CombatPlayer itself is returned by
             * PvPManager's getEnemy().
             */

            if (enemy == null) {
                return null;
            }

            /*
             * Try to access Bukkit Player through
             * the CombatPlayer object's inherited methods.
             *
             * EcoPlayer -> internal parent class.
             *
             * The safest route is to inspect public methods
             * for a Player-returning accessor.
             */

            for (Method method :
                    enemy.getClass().getMethods()) {

                if (method.getParameterCount() != 0) {
                    continue;
                }

                if (!Player.class.isAssignableFrom(
                        method.getReturnType())) {
                    continue;
                }

                try {

                    Object result =
                            method.invoke(enemy);

                    if (result instanceof Player target) {
                        return target;
                    }

                } catch (Throwable ignored) {
                    // Continue searching.
                }
            }

        } catch (Throwable throwable) {

            debug(
                    "Failed to read getEnemy() for "
                            + player.getName()
                            + ": "
                            + getRootCause(throwable)
            );
        }

        return null;
    }

    /**
     * Returns true if PvPManager reports that the player
     * has an active combat timer.
     *
     * This combines:
     *
     * isInCombat()
     *
     * and
     *
     * getTagTimeLeft()
     */
    public boolean hasActiveCombat(Player player) {

        if (!available || player == null) {
            return false;
        }

        if (!isInCombat(player)) {
            return false;
        }

        return getTagTimeLeft(player) > 0L;
    }

    /**
     * Debug logging.
     */
    private void debug(String message) {

        if (plugin.getConfig()
                .getBoolean(
                        "debug.pvpmanager",
                        false
                )) {

            plugin.getLogger().info(
                    "[PvPManager] " + message
            );
        }
    }

    /**
     * Extract the actual exception cause.
     */
    private String getRootCause(Throwable throwable) {

        Throwable cause = throwable;

        while (cause.getCause() != null) {
            cause = cause.getCause();
        }

        String message =
                cause.getMessage();

        if (message == null
                || message.isBlank()) {

            return cause.getClass()
                    .getSimpleName();
        }

        return cause.getClass()
                .getSimpleName()
                + ": "
                + message;
    }
}
