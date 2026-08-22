package com.votri.combatkeepinv.velocity.platform;

import com.votri.combatkeepinv.core.platform.PlatformDetector;
import com.votri.combatkeepinv.core.platform.PlatformInfo;
import com.votri.combatkeepinv.core.platform.PlatformType;
import com.velocitypowered.api.proxy.ProxyServer;

import java.util.Objects;

/**
 * Detects the Velocity proxy runtime.
 */
public final class VelocityPlatformDetector {

    private VelocityPlatformDetector() {
        throw new UnsupportedOperationException(
                "Utility class"
        );
    }

    /**
     * Detects the current Velocity runtime.
     *
     * @param proxy Velocity proxy instance
     * @return immutable platform information
     */
    public static PlatformInfo detect(
            ProxyServer proxy
    ) {
        Objects.requireNonNull(
                proxy,
                "proxy"
        );

        String implementationName =
                "Velocity";

        String implementationVersion =
                proxy.getVersion()
                        .getVersion();

        /*
         * Velocity is a proxy, therefore there is no single
         * Minecraft backend version that can be assigned here.
         */
        String minecraftVersion =
                "Unknown";

        /*
         * Velocity does not expose a Bukkit-style API version.
         */
        String apiVersion =
                implementationVersion;

        return PlatformDetector.create(
                PlatformType.VELOCITY,
                implementationName,
                implementationVersion,
                minecraftVersion,
                apiVersion
        );
    }
}