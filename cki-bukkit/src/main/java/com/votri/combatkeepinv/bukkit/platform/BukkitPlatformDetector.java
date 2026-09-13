package com.votri.combatkeepinv.bukkit.platform;

import com.votri.combatkeepinv.core.internal.DefaultPlatformInfo;
import com.votri.combatkeepinv.core.platform.PlatformCapability;
import com.votri.combatkeepinv.core.platform.PlatformInfo;
import com.votri.combatkeepinv.core.platform.PlatformType;

import org.bukkit.Bukkit;
import org.bukkit.Server;

import java.util.EnumSet;
import java.util.Locale;

/**
 * Detects the Bukkit-family server implementation.
 */
public final class BukkitPlatformDetector {

    private BukkitPlatformDetector() {
        throw new UnsupportedOperationException(
                "Utility class"
        );
    }

    public static PlatformInfo detect() {
        Server server =
                Bukkit.getServer();

        String name =
                safe(
                        server.getName()
                );

        String implementationVersion =
                safe(
                        server.getVersion()
                );

        String minecraftVersion =
                safe(
                        server.getMinecraftVersion()
                );

        String apiVersion =
                safe(
                        server.getBukkitVersion()
                );

        PlatformType type =
                detectType(
                        name,
                        implementationVersion
                );

        EnumSet<PlatformCapability> capabilities =
                EnumSet.of(
                        PlatformCapability.COMBAT,
                        PlatformCapability.DEATH,
                        PlatformCapability.INVENTORY,
                        PlatformCapability.DAMAGE_ATTRIBUTION,
                        PlatformCapability.PLUGIN_MESSAGING,
                        PlatformCapability.EVENTS
                );

        return new DefaultPlatformInfo(
                type,
                name,
                implementationVersion,
                minecraftVersion,
                apiVersion,
                false,
                true,
                capabilities
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
         * Purpur must be checked before Paper.
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
        return value == null
                || value.isBlank()
                ? "Unknown"
                : value;
    }
}