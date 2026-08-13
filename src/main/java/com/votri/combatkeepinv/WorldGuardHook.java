package com.votri.combatkeepinv;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class WorldGuardHook {

    private final CombatKeepInventory plugin;

    private boolean available;

    public WorldGuardHook(CombatKeepInventory plugin) {
        this.plugin = plugin;

        this.available =
                plugin.getServer()
                        .getPluginManager()
                        .getPlugin("WorldGuard") != null;
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Kiểm tra player có nằm trong region phù hợp hay không.
     */
    public boolean isAllowed(Player player) {

        if (!available) {
            return plugin.getConfig()
                    .getBoolean("worldguard.fail-open", true);
        }

        if (!plugin.getConfig()
                .getBoolean("worldguard.enabled", true)) {
            return true;
        }

        List<String> enabledRegions =
                plugin.getConfig()
                        .getStringList("worldguard.enabled-regions");

        List<String> excludedRegions =
                plugin.getConfig()
                        .getStringList("worldguard.excluded-regions");

        Location location =
                BukkitAdapter.adapt(player.getLocation());

        RegionQuery query =
                WorldGuard.getInstance()
                        .getPlatform()
                        .getRegionContainer()
                        .createQuery();

        ApplicableRegionSet regions =
                query.getApplicableRegions(location);

        Set<String> names = new HashSet<>();

        for (ProtectedRegion region : regions) {
            names.add(region.getId().toLowerCase());
        }

        /*
         * Excluded region có priority cao hơn.
         */
        for (String excluded : excludedRegions) {

            if (names.contains(excluded.toLowerCase())) {

                debug(
                        "Player "
                                + player.getName()
                                + " blocked by excluded region "
                                + excluded
                );

                return false;
            }
        }

        /*
         * Nếu không giới hạn theo region name
         * thì tất cả region đều được phép.
         */
        boolean restrict =
                plugin.getConfig()
                        .getBoolean(
                                "worldguard.restrict-to-enabled-regions",
                                false
                        );

        if (!restrict) {
            return true;
        }

        /*
         * enabled-regions = [] => không giới hạn.
         */
        if (enabledRegions.isEmpty()) {
            return true;
        }

        for (String enabled : enabledRegions) {

            if (names.contains(enabled.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Kiểm tra cả hai player.
     */
    public boolean isPvPAllowed(Player attacker, Player victim) {

        if (!plugin.getConfig()
                .getBoolean(
                        "worldguard.require-both-players-in-region",
                        true
                )) {

            return isAllowed(attacker)
                    || isAllowed(victim);
        }

        return isAllowed(attacker)
                && isAllowed(victim);
    }

    private void debug(String message) {

        if (plugin.getConfig()
                .getBoolean(
                        "debug.worldguard",
                        false
                )) {

            plugin.getLogger().info(
                    "[WorldGuard] " + message
            );
        }
    }
}
