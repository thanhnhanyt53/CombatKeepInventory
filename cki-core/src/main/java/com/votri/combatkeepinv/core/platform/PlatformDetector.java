package com.votri.combatkeepinv.core.platform;

import java.util.Objects;

/**
 * Factory for creating immutable platform information.
 *
 * <p>This class contains no platform-specific dependencies.
 * Platform-specific modules are responsible for detecting their
 * own runtime platform and passing the detected information here.</p>
 */
public final class PlatformDetector {

    private PlatformDetector() {
        throw new UnsupportedOperationException(
                "Utility class"
        );
    }

    /**
     * Creates platform information from detected runtime values.
     *
     * @param type platform type
     * @param implementationName implementation name
     * @param implementationVersion implementation version
     * @param minecraftVersion Minecraft version
     * @param apiVersion platform API version
     * @return immutable platform information
     */
    public static PlatformInfo create(
            PlatformType type,
            String implementationName,
            String implementationVersion,
            String minecraftVersion,
            String apiVersion
    ) {
        return new PlatformInfo(
                Objects.requireNonNull(
                        type,
                        "type"
                ),
                implementationName,
                implementationVersion,
                minecraftVersion,
                apiVersion
        );
    }
}