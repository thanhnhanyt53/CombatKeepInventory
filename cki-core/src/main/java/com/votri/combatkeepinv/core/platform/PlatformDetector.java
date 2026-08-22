package com.votri.combatkeepinv.core.platform;

import java.util.Objects;

/**
 * Resolves platform information from a platform-specific adapter.
 */
public final class PlatformDetector {

    private PlatformDetector() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static PlatformInfo create(
            PlatformType type,
            String implementationName,
            String implementationVersion,
            String minecraftVersion,
            String apiVersion
    ) {
        return new PlatformInfo(
                Objects.requireNonNull(type, "type"),
                implementationName,
                implementationVersion,
                minecraftVersion,
                apiVersion
        );
    }
}