package com.votri.combatkeepinv.core.api;

import com.votri.combatkeepinv.core.platform.PlatformCapability;
import com.votri.combatkeepinv.core.platform.PlatformInfo;

public interface PlatformAPI {

    PlatformInfo getPlatform();

    boolean supports(PlatformCapability capability);
}