package com.votri.combatkeepinv.velocity.platform;

import com.votri.combatkeepinv.core.platform.PlatformDetector;
import com.votri.combatkeepinv.core.platform.PlatformInfo;
import com.votri.combatkeepinv.core.platform.PlatformType;

public final class VelocityPlatformDetector {

    private VelocityPlatformDetector() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static PlatformInfo detect(
            String velocityVersion,
            String minecraftVersion
    ) {
        return PlatformDetector.create(
                PlatformType.VELOCITY,
                "Velocity",
                velocityVersion,
                minecraftVersion,
                "Velocity API"
        );
    }
}