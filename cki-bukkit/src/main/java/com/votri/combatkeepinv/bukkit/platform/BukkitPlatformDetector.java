package com.votri.combatkeepinv.bukkit.platform;

import com.votri.combatkeepinv.core.platform.PlatformDetector;
import com.votri.combatkeepinv.core.platform.PlatformInfo;
import com.votri.combatkeepinv.core.platform.PlatformType;
import org.bukkit.Bukkit;
import org.bukkit.Server;

import java.util.Locale;

/**
 * Detects the Bukkit-family server implementation at runtime.
 *
 * <p>This class belongs exclusively to the Bukkit module and does not
 * expose Bukkit-specific types through the core API.</p>
 */
public final class BukkitPlatformDetector {

    private BukkitPlatformDetector() {
        throw new UnsupportedOperationException(
                "Utility class"
        );
    }

    /**
     * Detects the currently running Bukkit-family platform.
     *
     * @return immutable platform information
     */
    public static PlatformInfo detect() {

        Server server = Bukkit.getServer();

        String name =
                safe(server.getName());

        String version =
                safe(server.getVersion());

        String bukkitVersion =
                safe(server.getBukkitVersion());

        String minecraftVersion =
                safe(server.getMinecraftVersion());

        PlatformType type =
                detectType(
                        name,
                        version
                );

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
        String normalizedName =
                name.toLowerCase(
                        Locale.ROOT
                );

        String normalizedVersion =
                version.toLowerCase(
                        Locale.ROOT
                );

        /*
         * Purpur must be checked before Paper because Purpur
         * is based on Paper.
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

        return PlatformType.UNKNOWN;
    }

    private static String safe(
            String value
    ) {
        return value == null || value.isBlank()
                ? "Unknown"
                : value;
    }
}