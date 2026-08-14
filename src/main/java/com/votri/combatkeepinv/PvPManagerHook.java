package com.votri.combatkeepinv;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Optional PvPManager integration.
 * No PvPManager classes are referenced at compile time.
 */
public final class PvPManagerHook {
    private static final String COMBAT_PLAYER_CLASS =
            "me.chancesd.pvpmanager.player.CombatPlayer";

    private final CombatKeepInventory plugin;
    private boolean available;

    private Method getCombatPlayer;
    private Method isInCombat;
    private Method getTagTimeLeft;
    private Method wasLastDeathPvP;

    public PvPManagerHook(CombatKeepInventory plugin) {
        this.plugin = plugin;
        initialize();
    }

    private void initialize() {
        Plugin pvp = plugin.getServer().getPluginManager().getPlugin("PvPManager");
        if (pvp == null) {
            debug("PvPManager not installed.");
            return;
        }

        try {
            ClassLoader loader = pvp.getClass().getClassLoader();
            Class<?> cp = Class.forName(COMBAT_PLAYER_CLASS, true, loader);

            getCombatPlayer = cp.getMethod("get", Player.class);
            isInCombat = cp.getMethod("isInCombat");
            getTagTimeLeft = cp.getMethod("getTagTimeLeft");
            wasLastDeathPvP = cp.getMethod("wasLastDeathPvP");

            available = true;
            plugin.getLogger().info("[PvPManager] Reflection integration enabled.");
        } catch (Throwable t) {
            available = false;
            plugin.getLogger().warning("[PvPManager] Detected but compatible API was not found: "
                    + rootCause(t));
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isInCombat(Player player) {
        Object cp = combatPlayer(player);
        if (cp == null) return false;
        try {
            Object result = isInCombat.invoke(cp);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable t) {
            debug("isInCombat failed: " + rootCause(t));
            return false;
        }
    }

    public long getTagTimeLeft(Player player) {
        Object cp = combatPlayer(player);
        if (cp == null) return 0L;
        try {
            Object result = getTagTimeLeft.invoke(cp);
            return result instanceof Number ? Math.max(0L, ((Number) result).longValue()) : 0L;
        } catch (Throwable t) {
            debug("getTagTimeLeft failed: " + rootCause(t));
            return 0L;
        }
    }

    public boolean hasActiveCombat(Player player) {
        return isInCombat(player) && getTagTimeLeft(player) > 0L;
    }

    public boolean wasLastDeathPvP(Player player) {
        Object cp = combatPlayer(player);
        if (cp == null) return false;
        try {
            Object result = wasLastDeathPvP.invoke(cp);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable t) {
            debug("wasLastDeathPvP failed: " + rootCause(t));
            return false;
        }
    }

    private Object combatPlayer(Player player) {
        if (!available || player == null) return null;
        try {
            return getCombatPlayer.invoke(null, player);
        } catch (Throwable t) {
            debug("CombatPlayer.get failed for " + player.getName() + ": " + rootCause(t));
            return null;
        }
    }

    private void debug(String message) {
        if (plugin.getConfig().getBoolean("debug.pvpmanager", false)) {
            plugin.getLogger().info("[PvPManager] " + message);
        }
    }

    private String rootCause(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null) c = c.getCause();
        return c.getClass().getSimpleName() + ": " +
                (c.getMessage() == null ? "no message" : c.getMessage());
    }
}
