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

    public WorldGuardHook(CombatKeepInventory plugin) {
        this.plugin = plugin;
        detect();
    }

    /**
     * Detect WorldGuard without a compile-time dependency.
     */
    private void detect() {

        if (!plugin.getConfig().getBoolean(
                "worldguard.enabled",
                true
        )) {
            available = false;
            return;
        }

        Plugin wg = plugin.getServer()
                .getPluginManager()
                .getPlugin("WorldGuard");

        if (wg == null || !wg.isEnabled()) {
            available = false;
            return;
        }

        try {
            Class<?> worldGuardClass = Class.forName(
                    "com.sk89q.worldguard.WorldGuard"
            );

            Method getInstance = worldGuardClass.getMethod(
                    "getInstance"
            );

            worldGuard = getInstance.invoke(null);

            available = worldGuard != null;

        } catch (Throwable throwable) {

            available = false;
            worldGuard = null;

            debug(
                    "Failed to initialize WorldGuard hook: "
                            + throwable.getClass().getSimpleName()
            );
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Checks whether PvP is allowed for both players.
     */
    public boolean canPvP(
            Player attacker,
            Player victim
    ) {

        if (!plugin.getConfig().getBoolean(
                "worldguard.enabled",
                true
        )) {
            return true;
        }

        if (!available) {
            return plugin.getConfig().getBoolean(
                    "worldguard.fail-open",
                    true
            );
        }

        if (attacker == null || victim == null) {
            return false;
        }

        boolean attackerAllowed =
                regionAllows(attacker.getLocation());

        boolean victimAllowed =
                regionAllows(victim.getLocation());

        if (plugin.getConfig().getBoolean(
                "worldguard.require-both-players-in-region",
                true
        )) {
            return attackerAllowed && victimAllowed;
        }

        return attackerAllowed || victimAllowed;
    }

    /**
     * Checks whether death handling is allowed at
     * the player's current location.
     */
    public boolean deathIsInAllowedRegion(
            Player player
    ) {

        if (!plugin.getConfig().getBoolean(
                "worldguard.enabled",
                true
        )) {
            return true;
        }

        if (!available) {
            return plugin.getConfig().getBoolean(
                    "worldguard.fail-open",
                    true
            );
        }

        if (player == null) {
            return false;
        }

        return regionAllows(player.getLocation());
    }

    /**
     * Evaluates configured WorldGuard region rules.
     */
    private boolean regionAllows(
            Location location
    ) {

        if (location == null || location.getWorld() == null) {
            return false;
        }

        try {

            Set<String> regions =
                    getRegionNames(location);

            /*
             * No applicable region.
             */
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

            boolean restrict =
                    plugin.getConfig().getBoolean(
                            "worldguard.restrict-to-enabled-regions",
                            false
                    );

            Collection<String> enabledRegions =
                    plugin.getConfig().getStringList(
                            "worldguard.enabled-regions"
                    );

            for (String regionId : regions) {

                /*
                 * Explicitly excluded regions are ignored.
                 */
                if (containsIgnoreCase(
                        excluded,
                        regionId
                )) {
                    continue;
                }

                /*
                 * Restricted mode:
                 * only configured enabled regions are accepted.
                 */
                if (restrict) {

                    if (containsIgnoreCase(
                            enabledRegions,
                            regionId
                    )) {
                        return true;
                    }

                    continue;
                }

                /*
                 * Normal mode:
                 * any non-excluded region is accepted.
                 */
                return true;
            }

            /*
             * If restricted mode is enabled and none
             * of the applicable regions matched, deny.
             */
            return !restrict;

        } catch (Throwable throwable) {

            debug(
                    "WorldGuard region check failed: "
                            + throwable.getClass().getSimpleName()
            );

            return plugin.getConfig().getBoolean(
                    "worldguard.fail-open",
                    true
            );
        }
    }

    /**
     * Gets applicable WorldGuard region IDs using reflection.
     */
    private Set<String> getRegionNames(
            Location location
    ) throws Exception {

        Set<String> result =
                new HashSet<>();

        if (worldGuard == null) {
            return result;
        }

        /*
         * WorldGuard#getPlatform()
         */
        Method getPlatform =
                worldGuard.getClass()
                        .getMethod("getPlatform");

        Object platform =
                getPlatform.invoke(worldGuard);

        /*
         * Platform#getRegionContainer()
         */
        Method getRegionContainer =
                platform.getClass()
                        .getMethod("getRegionContainer");

        Object container =
                getRegionContainer.invoke(platform);

        /*
         * BukkitAdapter
         */
        Class<?> adapter =
                Class.forName(
                        "com.sk89q.worldguard.bukkit.BukkitAdapter"
                );

        /*
         * Bukkit world -> WorldEdit world
         */
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

        /*
         * RegionContainer#get(World)
         */
        Method get =
                container.getClass()
                        .getMethod(
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

        /*
         * Bukkit location -> BlockVector3
         */
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

        /*
         * RegionManager#getApplicableRegions(BlockVector3)
         */
        Method getApplicableRegions =
                regionManager.getClass()
                        .getMethod(
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

        /*
         * ApplicableRegionSet#getRegions()
         */
        Method getRegions =
                applicable.getClass()
                        .getMethod("getRegions");

        Object regions =
                getRegions.invoke(applicable);

        if (regions instanceof Collection<?> collection) {

            for (Object region : collection) {

                Method getId =
                        region.getClass()
                                .getMethod("getId");

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

        if (values == null || target == null) {
            return false;
        }

        for (String value : values) {

            if (value != null &&
                    value.equalsIgnoreCase(target)) {
                return true;
            }
        }

        return false;
    }

    private void debug(String message) {

        if (!plugin.getConfig().getBoolean(
                "debug.worldguard",
                false
        )) {
            return;
        }

        plugin.getLogger().warning(
                "[WorldGuard] " + message
        );
    }
}