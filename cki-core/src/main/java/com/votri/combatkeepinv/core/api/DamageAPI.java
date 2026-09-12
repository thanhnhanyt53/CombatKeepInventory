package com.votri.combatkeepinv.core.api;

import com.votri.combatkeepinv.core.damage.DamageAttributionService;
import com.votri.combatkeepinv.core.damage.DamageSource;

import java.util.Optional;
import java.util.UUID;

public interface DamageAPI {

    DamageAttributionService getAttributionService();

    Optional<DamageSource> getLastDamage(UUID playerId);
}