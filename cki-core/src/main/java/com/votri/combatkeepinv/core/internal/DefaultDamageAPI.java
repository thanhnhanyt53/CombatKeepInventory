package com.votri.combatkeepinv.core.internal;

import com.votri.combatkeepinv.core.api.DamageAPI;
import com.votri.combatkeepinv.core.damage.DamageAttributionService;
import com.votri.combatkeepinv.core.damage.DamageSource;

import java.util.Optional;
import java.util.UUID;

public final class DefaultDamageAPI
        implements DamageAPI {

    private final DamageAttributionService service;

    public DefaultDamageAPI(
            DamageAttributionService service
    ) {
        this.service = service;
    }

    @Override
    public DamageAttributionService getAttributionService() {
        return service;
    }

    @Override
    public Optional<DamageSource> getLastDamage(
            UUID playerId
    ) {
        return service.getLastDamage(playerId);
    }
}