package com.votri.combatkeepinv.core.internal;

import com.votri.combatkeepinv.core.api.DeathAPI;
import com.votri.combatkeepinv.core.damage.DamageSource;
import com.votri.combatkeepinv.core.death.DeathContext;
import com.votri.combatkeepinv.core.death.DeathDecision;
import com.votri.combatkeepinv.core.death.DeathService;

import java.util.UUID;

public final class DefaultDeathAPI
        implements DeathAPI {

    private final DeathService service;

    public DefaultDeathAPI(
            DeathService service
    ) {
        this.service = service;
    }

    @Override
    public DeathService getDeathService() {
        return service;
    }

    @Override
    public DeathContext createContext(
            UUID playerId,
            DamageSource damageSource
    ) {
        return service.createContext(
                playerId,
                damageSource
        );
    }

    @Override
    public DeathDecision evaluate(
            DeathContext context
    ) {
        return service.evaluate(context);
    }
}