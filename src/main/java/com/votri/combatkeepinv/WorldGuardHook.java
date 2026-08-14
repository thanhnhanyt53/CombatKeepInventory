package com.votri.combatkeepinv;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * WorldGuard is optional and deliberately not a compile-time dependency.
 * Reflection keeps this class usable across supported WorldGuard/Paper API generations.
 */
public final class WorldGuardHook {
    private final CombatKeepInventory plugin;
    private final boolean available;

    private Object worldGuard;
    private Method getPlatform;
    private Method getRegionContainer;
    private Method createQuery;
    private Method getApplicableRegions;
    private Method adaptLocation;
    private Method getId;

    public WorldGuardHook(CombatKeepInventory plugin) {
        this.plugin = plugin;
        this.available = initialize();
    }

    private boolean initialize() {
        Plugin wg = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
        if (wg == null) return false;

        try {
            ClassLoader loader = wg.getClass().getClassLoader();

            Class<?> worldGuardClass = Class.forName(
                    "com.sk89q.worldguard.WorldGuard", true, loader);
            Class<?> adapterClass = Class.forName(
                    "com.sk89q.worldedit.bukkit.BukkitAdapter", true, loader);

            worldGuard = worldGuardClass.getMethod("getInstance").invoke(null);
            getPlatform = worldGuardClass.getMethod("getPlatform");

            Object platform = getPlatform.invoke(worldGuard);
            getRegionContainer = platform.getClass().getMethod("getRegionContainer");
            Object container = getRegionContainer.invoke(platform);
            createQuery = container.getClass().getMethod("createQuery");
            Object query = createQuery.invoke(container);

            adaptLocation = adapterClass.getMethod("adapt", org.bukkit.Location.class);
            getApplicableRegions = findApplicableRegionsMethod(query.getClass());

            Class<?> protectedRegion = Class.forName(
                    "com.sk89q.worldguard.protection.regions.ProtectedRegion", true, loader);
            getId = protectedRegion.getMethod("getId");

            debug("Reflection integration initialized.");
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("[WorldGuard] Detected but reflection initialization failed: "
                    + rootCause(t));
            return false;
        }
    }

    private Method findApplicableRegionsMethod(Class<?> queryClass) {
        for (Method method : queryClass.getMethods()) {
            if (!method.getName().equals("getApplicableRegions") || method.getParameterCount() != 1) continue;
            return method;
        }
        throw new IllegalStateException("RegionQuery#getApplicableRegions was not found");
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isAllowed(Player player) {
        if (!available) {
            return plugin.getConfig().getBoolean("worldguard.fail-open", true);
        }
        if (!plugin.getConfig().getBoolean("worldguard.enabled", true)) return true;

        Set<String> names = regionNames(player);
        List<String> excluded = plugin.getConfig().getStringList("worldguard.excluded-regions");

        for (String id : excluded) {
            if (names.contains(id.toLowerCase(Locale.ROOT))) return false;
        }

        if (!plugin.getConfig().getBoolean("worldguard.restrict-to-enabled-regions", false)) {
            return true;
        }

        List<String> enabled = plugin.getConfig().getStringList("worldguard.enabled-regions");
        if (enabled.isEmpty()) return true;

        for (String id : enabled) {
            if (names.contains(id.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    public boolean isPvPAllowed(Player attacker, Player victim) {
        if (!plugin.getConfig().getBoolean("worldguard.enabled", true)) return true;

        boolean both = plugin.getConfig().getBoolean(
                "worldguard.require-both-players-in-region", true);

        return both ? isAllowed(attacker) && isAllowed(victim)
                    : isAllowed(attacker) || isAllowed(victim);
    }

    private Set<String> regionNames(Player player) {
        Set<String> names = new HashSet<>();
        try {
            Object platform = getPlatform.invoke(worldGuard);
            Object container = getRegionContainer.invoke(platform);
            Object query = createQuery.invoke(container);
            Object wgLocation = adaptLocation.invoke(null, player.getLocation());
            Object applicable = getApplicableRegions.invoke(query, wgLocation);

            if (applicable instanceof Iterable<?> iterable) {
                for (Object region : iterable) {
                    Object id = getId.invoke(region);
                    if (id != null) names.add(id.toString().toLowerCase(Locale.ROOT));
                }
            }
        } catch (Throwable t) {
            debug("Region query failed for " + player.getName() + ": " + rootCause(t));
            // fail-open is intentional when configured.
            if (plugin.getConfig().getBoolean("worldguard.fail-open", true)) {
                names.add("__worldguard_fail_open__");
            }
        }
        return names;
    }

    private void debug(String message) {
        if (plugin.getConfig().getBoolean("debug.worldguard", false)) {
            plugin.getLogger().info("[WorldGuard] " + message);
        }
    }

    private String rootCause(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null) c = c.getCause();
        return c.getClass().getSimpleName() + ": " +
                (c.getMessage() == null ? "no message" : c.getMessage());
    }
}
