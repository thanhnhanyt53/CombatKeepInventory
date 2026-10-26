package com.votri.combatkeepinv.bukkit.hook;

import com.votri.combatkeepinv.bukkit.CombatKeepInventory;
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

        Plugin worldGuardPlugin =
                plugin.getServer()
                        .getPluginManager()
                        .getPlugin(
                                "WorldGuard"
                        );

        if (worldGuardPlugin == null
                || !worldGuardPlugin.isEnabled()) {

            available = false;
            return;
        }

        try {

            Class<?> worldGuardClass =
                    Class.forName(
                            "com.sk89q.worldguard.WorldGuard"
                    );

            Method getInstance =
                    worldGuardClass.getMethod(
                            "getInstance"
                    );

            worldGuard =
                    getInstance.invoke(
                            null
                    );

            available =
                    worldGuard != null;

        } catch (Throwable throwable) {

            available = false;
            worldGuard = null;

            debug(
                    "Failed to initialize WorldGuard hook: "
                            + throwable.getClass()
                                    .getSimpleName()
            );
        }
    }

    public boolean isAvailable() {
        return available;
    }

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

        if (attacker == null
                || victim == null) {

            return false;
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

            return attackerAllowed
                    && victimAllowed;
        }

        return attackerAllowed
                || victimAllowed;
    }

    /**
     * Resolves whether PvP is allowed at a location, layering
     * three independent checks (first match that denies wins):
     *
     * <ol>
     *   <li>{@code worldguard.excluded-regions}: a CKI-specific
     *       always-deny list, regardless of WorldGuard's own PVP
     *       flag. Useful for marking a region as a CombatKeepInventory
     *       safe zone without touching the region's real PVP flag.</li>
     *   <li>{@code worldguard.restrict-to-enabled-regions}: an
     *       opt-in CKI-specific allowlist: if turned on, a location
     *       not covered by at least one region in
     *       {@code worldguard.enabled-regions} is denied outright.</li>
     *   <li>WorldGuard's own {@code PVP} region flag, queried
     *       through {@code RegionQuery#testState(...)}. This is
     *       WorldGuard's real, authoritative mechanism, and it
     *       already resolves overlapping regions correctly via its
     *       own priority/parent-child engine. CKI does not need
     *       (and previously incorrectly tried) to reimplement that
     *       resolution itself.</li>
     * </ol>
     */
    private boolean regionAllows(
            Location location
    ) {

        if (location == null
                || location.getWorld() == null) {

            return false;
        }

        try {

            Set<String> regions =
                    getRegionNames(
                            location
                    );

            Set<String> excluded =
                    new HashSet<>(
                            plugin.getConfig()
                                    .getStringList(
                                            "worldguard.excluded-regions"
                                    )
                    );

            for (String regionId : regions) {

                if (containsIgnoreCase(
                        excluded,
                        regionId
                )) {

                    debug(
                            "Denied: region '"
                                    + regionId
                                    + "' is in worldguard.excluded-regions."
                    );

                    return false;
                }
            }

            boolean restrict =
                    plugin.getConfig().getBoolean(
                            "worldguard.restrict-to-enabled-regions",
                            false
                    );

            if (restrict) {

                Collection<String> enabledRegions =
                        plugin.getConfig()
                                .getStringList(
                                        "worldguard.enabled-regions"
                                );

                boolean matched = false;

                for (String regionId : regions) {

                    if (containsIgnoreCase(
                            enabledRegions,
                            regionId
                    )) {

                        matched = true;
                        break;
                    }
                }

                if (!matched) {

                    debug(
                            "Denied: restrict-to-enabled-regions is "
                                    + "on and no covering region is "
                                    + "in worldguard.enabled-regions."
                    );

                    return false;
                }
            }

            Boolean pvpFlag =
                    queryPvpFlag(
                            location
                    );

            if (pvpFlag != null
                    && !pvpFlag) {

                debug(
                        "Denied: WorldGuard PVP flag resolves to "
                                + "deny at this location."
                );

                return false;
            }

            return true;

        } catch (Throwable throwable) {

            debug(
                    "WorldGuard region check failed: "
                            + throwable.getClass()
                                    .getSimpleName()
            );

            return plugin.getConfig().getBoolean(
                    "worldguard.fail-open",
                    true
            );
        }
    }

    /**
     * Queries WorldGuard's real, native {@code PVP} StateFlag at a
     * location via {@code RegionQuery#testState(...)}, which
     * resolves overlapping/nested regions by WorldGuard's own
     * priority and parent-inheritance rules: the same mechanism
     * WorldGuard itself uses to decide whether to cancel damage
     * events, so this stays consistent with what players actually
     * experience.
     *
     * @return true/false for an explicit resolution, or null if
     *         WorldGuard is not initialized (treated as
     *         "no opinion" by the caller, i.e. not a deny)
     */
    private Boolean queryPvpFlag(
            Location location
    ) throws Exception {

        if (worldGuard == null) {
            return null;
        }

        Object platform =
                worldGuard.getClass()
                        .getMethod("getPlatform")
                        .invoke(worldGuard);

        Object container =
                platform.getClass()
                        .getMethod("getRegionContainer")
                        .invoke(platform);

        Object query =
                container.getClass()
                        .getMethod("createQuery")
                        .invoke(container);

        Class<?> adapterClass =
                Class.forName(
                        "com.sk89q.worldedit.bukkit.BukkitAdapter"
                );

        Object weLocation =
                adapterClass.getMethod(
                        "adapt",
                        Location.class
                ).invoke(
                        null,
                        location
                );

        Class<?> weLocationClass =
                Class.forName(
                        "com.sk89q.worldedit.util.Location"
                );

        Class<?> regionAssociableClass =
                Class.forName(
                        "com.sk89q.worldguard.protection.association.RegionAssociable"
                );

        Class<?> stateFlagClass =
                Class.forName(
                        "com.sk89q.worldguard.protection.flags.StateFlag"
                );

        Class<?> flagsClass =
                Class.forName(
                        "com.sk89q.worldguard.protection.flags.Flags"
                );

        Object pvpFlag =
                flagsClass.getField("PVP")
                        .get(null);

        Object stateFlagArray =
                java.lang.reflect.Array.newInstance(
                        stateFlagClass,
                        1
                );

        java.lang.reflect.Array.set(
                stateFlagArray,
                0,
                pvpFlag
        );

        Method testState =
                query.getClass().getMethod(
                        "testState",
                        weLocationClass,
                        regionAssociableClass,
                        stateFlagArray.getClass()
                );

        Object result =
                testState.invoke(
                        query,
                        weLocation,
                        null,
                        stateFlagArray
                );

        return (Boolean) result;
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
                worldGuard.getClass()
                        .getMethod(
                                "getPlatform"
                        );

        Object platform =
                getPlatform.invoke(
                        worldGuard
                );

        Method getRegionContainer =
                platform.getClass()
                        .getMethod(
                                "getRegionContainer"
                        );

        Object container =
                getRegionContainer.invoke(
                        platform
                );

        /*
         * BukkitAdapter lives in WorldEdit (a WorldGuard
         * dependency), NOT in WorldGuard's own "bukkit" package.
         * The previous "com.sk89q.worldguard.bukkit.BukkitAdapter"
         * path does not exist on any real install, so every call
         * here always threw ClassNotFoundException -- silently
         * caught below, always falling back to
         * worldguard.fail-open (true by default). In practice
         * this meant WorldGuard region checks never ran at all,
         * regardless of how regions were configured.
         */
        Class<?> adapter =
                Class.forName(
                        "com.sk89q.worldedit.bukkit.BukkitAdapter"
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

        Method getRegions =
                applicable.getClass()
                        .getMethod(
                                "getRegions"
                        );

        Object regions =
                getRegions.invoke(
                        applicable
                );

        if (regions instanceof Collection<?> collection) {

            for (Object region : collection) {

                Method getId =
                        region.getClass()
                                .getMethod(
                                        "getId"
                                );

                Object id =
                        getId.invoke(
                                region
                        );

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

        if (values == null
                || target == null) {

            return false;
        }

        for (String value : values) {

            if (value != null
                    && value.equalsIgnoreCase(
                            target
                    )) {

                return true;
            }
        }

        return false;
    }

    private void debug(
            String message
    ) {

        if (!plugin.getConfig().getBoolean(
                "debug.worldguard",
                false
        )) {

            return;
        }

        plugin.getLogger().warning(
                "[WorldGuard] "
                        + message
        );
    }
}