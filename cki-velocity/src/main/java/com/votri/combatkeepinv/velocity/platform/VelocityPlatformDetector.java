package com.votri.combatkeepinv.velocity.platform;

import com.votri.combatkeepinv.core.platform.PlatformCapability;
import com.votri.combatkeepinv.core.platform.PlatformInfo;
import com.votri.combatkeepinv.core.platform.PlatformType;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.util.ProxyVersion;

public final class VelocityPlatformDetector {

    private VelocityPlatformDetector() {
        // Private constructor to prevent instantiation
    }

    /**
     * Detects and constructs platform information for Velocity proxy.
     */
    public static PlatformInfo detect(ProxyServer proxy) {
        ProxyVersion version = proxy.getVersion();

        return new PlatformInfo() {
            @Override
            public PlatformType getType() {
                return PlatformType.VELOCITY;
            }

            @Override
            public String getImplementationName() {
                return version.getName();
            }

            @Override
            public String getImplementationVersion() {
                return version.getVersion();
            }

            @Override
            public String getMinecraftVersion() {
                return version.getVersion();
            }

            @Override
            public String getApiVersion() {
                return version.getVersion();
            }

            @Override
            public boolean isProxy() {
                return true;
            }

            @Override
            public boolean isBackend() {
                return false;
            }

            @Override
            public boolean supports(PlatformCapability capability) {
                return false;
            }
        };
    }
}

