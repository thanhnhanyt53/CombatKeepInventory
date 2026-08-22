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
 * <p>This class is only used by the Bukkit module.
 * No Bukkit-specific classes are exposed through the core API.</p>
 */
public final class BukkitPlatformDetector {

    private BukkitPlatformDetector() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Detects the currently running Bukkit-family platform.
     *
     * @return immutable platform information
     */
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
         * Purpur must be checked before Paper because Purpur
         * is a Paper-based server implementation.
         */
        if (containsAny(normalizedName, "purpur")
                || containsAny(normalizedVersion, "purpur")) {
            return PlatformType.PURPUR;
        }

        if (containsAny(normalizedName, "paper")
                || containsAny(normalizedVersion, "paper")) {
            return PlatformType.PAPER;
        }

        if (containsAny(normalizedName, "spigot")
                || containsAny(normalizedVersion, "spigot")) {
            return PlatformType.SPIGOT;
        }

        if (containsAny(normalizedName, "bukkit")
                || containsAny(normalizedVersion, "bukkit")) {
            return PlatformType.BUKKIT;
        }

        return PlatformType.UNKNOWN;
    }

    private static boolean containsAny(
            String value,
            String target
    ) {
        return value.contains(target);
    }

    private static String safe(String value) {
        return value == null || value.isBlank()
                ? "Unknown"
                : value;
    }
}