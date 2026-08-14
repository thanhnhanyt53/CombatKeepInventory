package com.votri.combatkeepinv;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class WorldGuardHook {

    private final CombatKeepInventory plugin;

    private boolean available;

    private Object worldGuard;

    public WorldGuardHook(
            CombatKeepInventory plugin
    ) {
        this.plugin = plugin;
        detect();
    }

    private void detect() {
        if (!plugin.getConfig().getBoolean(
                "worldguard.enabled",
                true
        )) {
            available = false;
            return;
        }

        Plugin wg =
                plugin.getServer()
                        .getPluginManager()
                        .getPlugin("WorldGuard");

        if (wg == null ||
                !wg.isEnabled()) {
            available = false;
            return;
        }

        try {
            Class<?> clazz =
                    Class.forName(
                            "com.sk89q.worldguard.WorldGuard"
                    );

            Method getInstance =
                    clazz.getMethod(
                            "getInstance"
                    );

            worldGuard =
                    getInstance.invoke(null);

            available = worldGuard != null;

        } catch (Throwable throwable) {
            available = false;

            if (plugin.getConfig().getBoolean(
                    "debug.worldguard",
                    false
            )) {
                plugin.getLogger().warning(
                        "Failed to initialize WorldGuard hook: " +
                                throwable.getClass().getSimpleName()
                );
            }
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean canPvP(
            Player attacker,
            Player victim
    ) {
        if (!available) {
            return plugin.getConfig().getBoolean(
                    "worldguard.fail-open",
                    true
            );
        }

        if (attacker == null ||
                victim == null) {
            return false;
        }

        if (!plugin.getConfig().getBoolean(
                "worldguard.enabled",
                true
        )) {
            return true;
        }

        boolean attackerAllowed =
                regionAllows(
                        attacker.getLocation()
                );

        boolean victimAllowed =
                regionAllows(
                        victim.getLocation()
                );

        if (plugin.getConfig().getBoolean(
                "worldguard.require-both-players-in-region",
                true
        )) {
            return attackerAllowed &&
                    victimAllowed;
        }

        return attackerAllowed ||
                victimAllowed;
    }

    public boolean deathIsInAllowedRegion(
            Player player
    ) {
        if (!available) {
            return plugin.getConfig().getBoolean(
                    "worldguard.fail-open",
                    true
            );
        }

        if (player == null) {
            return false;
        }

        return regionAllows(
                player.getLocation()
        );
    }

    private boolean regionAllows(
            Location location
    ) {
        try {
            Set<String> regions =
                    getRegionNames(location);

            if (regions.isEmpty()) {
                return !plugin.getConfig().getBoolean(
                        "worldguard.restrict-to-enabled-regions",
                        false
                );
            }

            Set<String> excluded =
                    new HashSet<>(
                            plugin.getConfig()
                                    .getStringList(
                                            "worldguard.excluded-regions"
                                    )
                    );

            for (String id : regions) {
                if (containsIgnoreCase(
                        excluded,
                        id
                )) {
                    continue;
                }

                if (plugin.getConfig().getBoolean(
                        "worldguard.restrict-to-enabled-regions",
                        false
                )) {
                    if (containsIgnoreCase(
                            plugin.getConfig()
                                    .getStringList(
                                            "worldguard.enabled-regions"
                                    ),
                            id
                    )) {
                        return true;
                    }
                } else {
                    return true;
                }
            }

            return !plugin.getConfig().getBoolean(
                    "worldguard.restrict-to-enabled-regions",
                    false
            );

        } catch (Throwable throwable) {
            if (plugin.getConfig().getBoolean(
                    "debug.worldguard",
                    false
            )) {
                plugin.getLogger().warning(
                        "WorldGuard region check failed: " +
                                throwable.getClass().getSimpleName()
                );
            }

            return plugin.getConfig().getBoolean(
                    "worldguard.fail-open",
                    true
            );
        }
    }

    private Set<String> getRegionNames(
            Location location
    ) throws Exception {

        Set<String> result =
                new HashSet<>();

        if (worldGuard == null) {
            return result;
        }

        Method getPlatform =
                worldGuard.getClass().getMethod(
                        "getPlatform"
                );

        Object platform =
                getPlatform.invoke(
                        worldGuard
                );

        Method getRegionContainer =
                platform.getClass().getMethod(
                        "getRegionContainer"
                );

        Object container =
                getRegionContainer.invoke(
                        platform
                );

        Class<?> adapter =
                Class.forName(
                        "com.sk89q.worldguard.bukkit.BukkitAdapter"
                );

        Method adaptWorld =
                adapter.getMethod(
                        "adapt",
                        org.bukkit.World.class
                );

        Object adaptedWorld =
                adaptWorld.invoke(
                        null,
                        location.getWorld()
                );

        Method get =
                container.getClass().getMethod(
                        "get",
                        Class.forName(
                                "com.sk89q.worldedit.world.World"
                        )
                );

        Object regionManager =
                get.invoke(
                        container,
                        adaptedWorld
                );

        if (regionManager == null) {
            return result;
        }

        Method adaptLocation =
                adapter.getMethod(
                        "asBlockVector",
                        Location.class
                );

        Object vector =
                adaptLocation.invoke(
                        null,
                        location
                );

        Method getApplicableRegions =
                regionManager.getClass().getMethod(
                        "getApplicableRegions",
                        Class.forName(
                                "com.sk89q.worldedit.math.BlockVector3"
                        )
                );

        Object applicable =
                getApplicableRegions.invoke(
                        regionManager,
                        vector
                );

        Method getRegions =
                applicable.getClass().getMethod(
                        "getRegions"
                );

        Object regions =
                getRegions.invoke(
                        applicable
                );

        if (regions instanceof Collection<?> collection) {
            for (Object region : collection) {
                Method getId =
                        region.getClass().getMethod(
                                "getId"
                        );

                Object id =
                        getId.invoke(region);

                if (id != null) {
                    result.add(
                            String.valueOf(id)
                    );
                }
            }
        }

        return result;
    }

    private boolean containsIgnoreCase(
            Collection<String> values,
            String target
    ) {
        for (String value : values) {
            if (value.equalsIgnoreCase(target)) {
                return true;
            }
        }

        return false;
    }
}