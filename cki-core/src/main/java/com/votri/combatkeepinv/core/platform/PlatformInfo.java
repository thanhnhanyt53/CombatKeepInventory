package com.votri.combatkeepinv.core.platform;

public interface PlatformInfo {

    PlatformType getType();

    String getImplementationName();

    String getImplementationVersion();

    String getMinecraftVersion();

    String getApiVersion();

    boolean isProxy();

    boolean isBackend();

    boolean supports(PlatformCapability capability);
}