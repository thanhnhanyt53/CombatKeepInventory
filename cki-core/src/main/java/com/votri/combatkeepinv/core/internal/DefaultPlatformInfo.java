package com.votri.combatkeepinv.core.internal;

import com.votri.combatkeepinv.core.platform.PlatformCapability;
import com.votri.combatkeepinv.core.platform.PlatformInfo;
import com.votri.combatkeepinv.core.platform.PlatformType;

import java.util.EnumSet;
import java.util.Set;

public final class DefaultPlatformInfo
        implements PlatformInfo {

    private final PlatformType type;
    private final String implementationName;
    private final String implementationVersion;
    private final String minecraftVersion;
    private final String apiVersion;
    private final boolean proxy;
    private final boolean backend;
    private final Set<PlatformCapability> capabilities;

    public DefaultPlatformInfo(
            PlatformType type,
            String implementationName,
            String implementationVersion,
            String minecraftVersion,
            String apiVersion,
            boolean proxy,
            boolean backend,
            Set<PlatformCapability> capabilities
    ) {
        this.type = type;
        this.implementationName = implementationName;
        this.implementationVersion = implementationVersion;
        this.minecraftVersion = minecraftVersion;
        this.apiVersion = apiVersion;
        this.proxy = proxy;
        this.backend = backend;
        this.capabilities =
                capabilities.isEmpty()
                        ? EnumSet.noneOf(PlatformCapability.class)
                        : EnumSet.copyOf(capabilities);
    }

    @Override
    public PlatformType getType() {
        return type;
    }

    @Override
    public String getImplementationName() {
        return implementationName;
    }

    @Override
    public String getImplementationVersion() {
        return implementationVersion;
    }

    @Override
    public String getMinecraftVersion() {
        return minecraftVersion;
    }

    @Override
    public String getApiVersion() {
        return apiVersion;
    }

    @Override
    public boolean isProxy() {
        return proxy;
    }

    @Override
    public boolean isBackend() {
        return backend;
    }

    @Override
    public boolean supports(
            PlatformCapability capability
    ) {
        return capabilities.contains(capability);
    }
}