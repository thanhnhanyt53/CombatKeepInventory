package com.votri.combatkeepinv.core.death;

import com.votri.combatkeepinv.core.damage.DamageSource;

import java.util.UUID;

public interface DeathService {

    DeathContext createContext(
            UUID playerId,
            DamageSource damageSource
    );

    DeathDecision evaluate(
            DeathContext context
    );

    DeathOutcome process(
            DeathContext context
    );
}