package com.votri.combatkeepinv.bukkit.platform;

import com.votri.combatkeepinv.core.platform.PlatformDetector;
import com.votri.combatkeepinv.core.platform.PlatformInfo;
import com.votri.combatkeepinv.core.platform.PlatformType;
import org.bukkit.Bukkit;
import org.bukkit.Server;

import java.util.Locale;

public final class BukkitPlatformDetector {

    private BukkitPlatformDetector() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static PlatformInfo detect() {
        Server server = Bukkit.getServer();

        String name = safe(server.getName());
        String version = safe(server.getVersion());
        String bukkitVersion = safe(server.getBukkitVersion());
        String minecraftVersion = safe(server.getMinecraftVersion());

        PlatformType type = detectType(name, version);

        return PlatformDetector.create(
                type,
                name,
                version,
                minecraftVersion,
                bukkitVersion
        );
    }

    private static PlatformType detectType(
            String name,
            String version
    ) {
        String normalizedName = name.toLowerCase(Locale.ROOT);
        String normalizedVersion = version.toLowerCase(Locale.ROOT);

        /*
         * Check forks first.
         *
         * Purpur is based on Paper, so Paper must not be detected first.
         */
        if (normalizedName.contains("purpur")
                || normalizedVersion.contains("purpur")) {
            return PlatformType.PURPUR;
        }

        if (normalizedName.contains("paper")
                || normalizedVersion.contains("paper")) {
            return PlatformType.PAPER;
        }

        if (normalizedName.contains("spigot")
                || normalizedVersion.contains("spigot")) {
            return PlatformType.SPIGOT;
        }

        if (normalizedName.contains("bukkit")
                || normalizedVersion.contains("bukkit")) {
            return PlatformType.BUKKIT;
        }

        /*
         * Some Bukkit-compatible implementations may not expose a
         * recognizable implementation name. In that case we retain
         * UNKNOWN instead of making an unsafe assumption.
         */
        return PlatformType.UNKNOWN;
    }

    private static String safe(String value) {
        return value == null || value.isBlank()
                ? "Unknown"
                : value;
    }
}