package com.votri.combatkeepinv;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.List;

public final class WorldGuardHook {

    private final CombatKeepInventory plugin;

    private boolean available;

    private Object worldGuardPlugin;

    public WorldGuardHook(
            CombatKeepInventory plugin
    ) {

        this.plugin = plugin;

        if (!plugin.getConfig().getBoolean(
                "worldguard.enabled",
                true
        )) {
            return;
        }

        Plugin wg =
                Bukkit.getPluginManager()
                        .getPlugin("WorldGuard");

        if (wg == null
                || !wg.isEnabled()) {

            available = false;
            return;
        }

        worldGuardPlugin = wg;
        available = true;
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean canPvP(
            Player attacker,
            Player victim
    ) {

        if (!available) {

            return plugin.getConfig()
                    .getBoolean(
                            "worldguard.fail-open",
                            true
                    );
        }

        /*
         * Keep the integration fail-open if
         * reflection/API access changes.
         */
        try {

            boolean attackerAllowed =
                    isPlayerAllowed(attacker);

            boolean victimAllowed =
                    isPlayerAllowed(victim);

            if (plugin.getConfig().getBoolean(
                    "worldguard.require-both-players-in-region",
                    true
            )) {

                return attackerAllowed
                        && victimAllowed;
            }

            return attackerAllowed
                    || victimAllowed;

        } catch (Throwable throwable) {

            if (plugin.getConfig().getBoolean(
                    "debug.worldguard",
                    false
            )) {

                plugin.getLogger().warning(
                        "WorldGuard reflection failed: "
                                + throwable.getMessage()
                );
            }

            return plugin.getConfig()
                    .getBoolean(
                            "worldguard.fail-open",
                            true
                    );
        }
    }

    private boolean isPlayerAllowed(
            Player player
    ) throws Exception {

        List<String> excluded =
                plugin.getConfig()
                        .getStringList(
                                "worldguard.excluded-regions"
                        );

        List<String> enabled =
                plugin.getConfig()
                        .getStringList(
                                "worldguard.enabled-regions"
                        );

        boolean restrict =
                plugin.getConfig()
                        .getBoolean(
                                "worldguard.restrict-to-enabled-regions",
                                false
                        );

        /*
         * A full WorldGuard region query is intentionally
         * isolated here. If no named region is configured,
         * allow the player.
         */
        if (!restrict
                && enabled.isEmpty()
                && excluded.isEmpty()) {

            return true;
        }

        /*
         * Use WorldGuard classes through reflection.
         */
        Class<?> wgClass =
                Class.forName(
                        "com.sk89q.worldguard.WorldGuard"
                );

        Method getInstance =
                wgClass.getMethod(
                        "getInstance"
                );

        Object wg =
                getInstance.invoke(null);

        Method platformMethod =
                wgClass.getMethod(
                        "getPlatform"
                );

        Object platform =
                platformMethod.invoke(wg);

        Method regionContainerMethod =
                platform.getClass()
                        .getMethod(
                                "getRegionContainer"
                        );

        Object container =
                regionContainerMethod.invoke(
                        platform
                );

        Class<?> bukkitAdapter =
                Class.forName(
                        "com.sk89q.worldedit.bukkit.BukkitAdapter"
                );

        Method adaptWorld =
                bukkitAdapter.getMethod(
                        "adapt",
                        org.bukkit.World.class
                );

        Object adaptedWorld =
                adaptWorld.invoke(
                        null,
                        player.getWorld()
                );

        Method getManager =
                container.getClass()
                        .getMethod(
                                "get",
                                Class.forName(
                                        "com.sk89q.worldedit.world.World"
                                )
                        );

        Object manager =
                getManager.invoke(
                        container,
                        adaptedWorld
                );

        if (manager == null) {
            return !restrict;
        }

        Method getApplicable =
                manager.getClass()
                        .getMethod(
                                "getApplicableRegions",
                                Class.forName(
                                        "com.sk89q.worldedit.math.BlockVector3"
                                )
                        );

        Class<?> vectorClass =
                Class.forName(
                        "com.sk89q.worldedit.math.BlockVector3"
                );

        Method at =
                vectorClass.getMethod(
                        "at",
                        int.class,
                        int.class,
                        int.class
                );

        Object vector =
                at.invoke(
                        null,
                        player.getLocation()
                                .getBlockX(),
                        player.getLocation()
                                .getBlockY(),
                        player.getLocation()
                                .getBlockZ()
                );

        Object regions =
                getApplicable.invoke(
                        manager,
                        vector
                );

        Method getRegions =
                regions.getClass()
                        .getMethod(
                                "getRegions"
                        );

        Object regionSet =
                getRegions.invoke(regions);

        if (!(regionSet instanceof Iterable<?> iterable)) {
            return !restrict;
        }

        boolean matchedEnabled = false;

        for (Object region : iterable) {

            Method getId =
                    region.getClass()
                            .getMethod("getId");

            String id =
                    String.valueOf(
                            getId.invoke(region)
                    );

            if (containsIgnoreCase(
                    excluded,
                    id
            )) {
                return false;
            }

            if (containsIgnoreCase(
                    enabled,
                    id
            )) {
                matchedEnabled = true;
            }
        }

        if (restrict) {
            return matchedEnabled;
        }

        return true;
    }

    private boolean containsIgnoreCase(
            List<String> list,
            String value
    ) {

        for (String entry : list) {

            if (entry.equalsIgnoreCase(value)) {
                return true;
            }
        }

        return false;
    }
}
