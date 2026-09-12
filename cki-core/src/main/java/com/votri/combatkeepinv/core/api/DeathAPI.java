package com.votri.combatkeepinv.core.api;

import com.votri.combatkeepinv.core.damage.DamageSource;
import com.votri.combatkeepinv.core.death.DeathContext;
import com.votri.combatkeepinv.core.death.DeathDecision;
import com.votri.combatkeepinv.core.death.DeathService;

import java.util.UUID;

public interface DeathAPI {

    DeathService getDeathService();

    DeathContext createContext(
            UUID playerId,
            DamageSource damageSource
    );

    DeathDecision evaluate(DeathContext context);
}