package com.votri.combatkeepinv.bukkit.hook;

import com.votri.combatkeepinv.bukkit.CombatKeepInventory;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Reflection-based bridge to WorldGuard (kept reflective so
 * cki-bukkit does not need WorldGuard/WorldEdit as a compile-time
 * dependency).
 *
 * <p>Every {@code Class}/{@code Method}/{@code Field} this needs is
 * resolved exactly once, in {@link #detect()}, and reused for every
 * later PvP check. Resolving them again on every hit (as this class
 * used to) is the single most expensive thing a reflective
 * integration can do on a busy server -- a world with constant PvP
 * can mean thousands of these checks a second, and each one used to
 * do roughly a dozen {@code Class.forName}/{@code getMethod} calls.
 * A failure during {@link #detect()} is treated exactly like
 * WorldGuard not being installed: {@link #isAvailable()} is false
 * and every check falls back to {@code worldguard.fail-open}.</p>
 */
public final class WorldGuardHook {

    private final CombatKeepInventory plugin;

    private volatile boolean available;

    private Object worldGuard;
    private Object pvpFlag;

    /** Reusable {@code StateFlag[]{Flags.PVP}}; never mutated after detect(). */
    private Object pvpFlagArray;

    private Method getPlatform;
    private Method getRegionContainer;
    private Method createQuery;
    private Method containerGet;
    private Method adaptWorld;
    private Method adaptLocation;
    private Method asBlockVector;
    private Method getApplicableRegions;
    private Method getRegions;
    private Method regionGetId;
    private Method testState;

    public WorldGuardHook(
            CombatKeepInventory plugin
    ) {

        this.plugin = plugin;

        detect();
    }

    /**
     * Looks up WorldGuard and resolves every reflective handle this
     * class will ever need. Called once from the constructor; never
     * again on the hot path.
     */
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

            worldGuard =
                    worldGuardClass.getMethod("getInstance")
                            .invoke(null);

            if (worldGuard == null) {
                available = false;
                return;
            }

            getPlatform =
                    worldGuard.getClass().getMethod("getPlatform");

            Object platform =
                    getPlatform.invoke(worldGuard);

            getRegionContainer =
                    platform.getClass()
                            .getMethod("getRegionContainer");

            Object container =
                    getRegionContainer.invoke(platform);

            createQuery =
                    container.getClass().getMethod("createQuery");

            /*
             * BukkitAdapter lives in WorldEdit (a WorldGuard
             * dependency), NOT in WorldGuard's own "bukkit"
             * package. A previous version of this class looked for
             * "com.sk89q.worldguard.bukkit.BukkitAdapter", which
             * does not exist on any real install, so every call
             * always threw ClassNotFoundException -- silently
             * caught, always falling back to worldguard.fail-open.
             * In practice this meant region checks never ran at
             * all, regardless of how regions were configured.
             */
            Class<?> adapterClass =
                    Class.forName(
                            "com.sk89q.worldedit.bukkit.BukkitAdapter"
                    );

            Class<?> weWorldClass =
                    Class.forName(
                            "com.sk89q.worldedit.world.World"
                    );

            Class<?> weLocationClass =
                    Class.forName(
                            "com.sk89q.worldedit.util.Location"
                    );

            Class<?> blockVectorClass =
                    Class.forName(
                            "com.sk89q.worldedit.math.BlockVector3"
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

            Class<?> protectedRegionClass =
                    Class.forName(
                            "com.sk89q.worldguard.protection.regions.ProtectedRegion"
                    );

            adaptWorld =
                    adapterClass.getMethod(
                            "adapt",
                            org.bukkit.World.class
                    );

            adaptLocation =
                    adapterClass.getMethod(
                            "adapt",
                            Location.class
                    );

            asBlockVector =
                    adapterClass.getMethod(
                            "asBlockVector",
                            Location.class
                    );

            containerGet =
                    container.getClass().getMethod(
                            "get",
                            weWorldClass
                    );

            getApplicableRegions =
                    Class.forName(
                            "com.sk89q.worldguard.protection.managers.RegionManager"
                    ).getMethod(
                            "getApplicableRegions",
                            blockVectorClass
                    );

            getRegions =
                    Class.forName(
                            "com.sk89q.worldguard.protection.ApplicableRegionSet"
                    ).getMethod("getRegions");

            regionGetId =
                    protectedRegionClass.getMethod("getId");

            testState =
                    Class.forName(
                            "com.sk89q.worldguard.protection.regions.RegionQuery"
                    ).getMethod(
                            "testState",
                            weLocationClass,
                            regionAssociableClass,
                            Array.newInstance(stateFlagClass, 0).getClass()
                    );

            pvpFlag =
                    flagsClass.getField("PVP").get(null);

            pvpFlagArray =
                    Array.newInstance(stateFlagClass, 1);

            Array.set(pvpFlagArray, 0, pvpFlag);

            available = true;

        } catch (Throwable throwable) {

            available = false;
            worldGuard = null;

            plugin.getLogger().warning(
                    "WorldGuard was found but its API could not be "
                            + "linked ("
                            + throwable
                            + "); PvP region checks are disabled "
                            + "(worldguard.fail-open decides the "
                            + "outcome). This usually means an "
                            + "unsupported WorldGuard/WorldEdit "
                            + "build."
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

            Boolean pvpAllowed =
                    queryPvpFlag(
                            location
                    );

            if (pvpAllowed != null
                    && !pvpAllowed) {

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
                getPlatform.invoke(worldGuard);

        Object container =
                getRegionContainer.invoke(platform);

        Object query =
                createQuery.invoke(container);

        Object weLocation =
                adaptLocation.invoke(null, location);

        Object result =
                testState.invoke(
                        query,
                        weLocation,
                        null,
                        pvpFlagArray
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

        Object platform =
                getPlatform.invoke(worldGuard);

        Object container =
                getRegionContainer.invoke(platform);

        Object adaptedWorld =
                adaptWorld.invoke(
                        null,
                        location.getWorld()
                );

        Object regionManager =
                containerGet.invoke(
                        container,
                        adaptedWorld
                );

        if (regionManager == null) {
            return result;
        }

        Object vector =
                asBlockVector.invoke(
                        null,
                        location
                );

        Object applicable =
                getApplicableRegions.invoke(
                        regionManager,
                        vector
                );

        Object regions =
                getRegions.invoke(applicable);

        if (regions instanceof Collection<?> collection) {

            for (Object region : collection) {

                Object id =
                        regionGetId.invoke(region);

                if (id != null) {
                    result.add(String.valueOf(id));
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
