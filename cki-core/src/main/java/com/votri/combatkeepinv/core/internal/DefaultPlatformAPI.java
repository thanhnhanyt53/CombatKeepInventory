package com.votri.combatkeepinv.core.internal;

import com.votri.combatkeepinv.core.api.PlatformAPI;
import com.votri.combatkeepinv.core.platform.PlatformCapability;
import com.votri.combatkeepinv.core.platform.PlatformInfo;

public final class DefaultPlatformAPI
        implements PlatformAPI {

    private final PlatformInfo platform;

    public DefaultPlatformAPI(
            PlatformInfo platform
    ) {
        this.platform = platform;
    }

    @Override
    public PlatformInfo getPlatform() {
        return platform;
    }

    @Override
    public boolean supports(
            PlatformCapability capability
    ) {
        return platform.supports(capability);
    }
}