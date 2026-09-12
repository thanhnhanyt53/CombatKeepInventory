package com.votri.combatkeepinv.core.damage;

import java.util.Optional;
import java.util.UUID;

public interface DamageAttributionService {

    DamageAttributionResult resolve(
            UUID victimId,
            DamageSource rawSource
    );

    Optional<DamageSource> getLastDamage(
            UUID victimId
    );

    void recordDamage(DamageSource source);

    void clearDamageHistory(UUID victimId);

    void clearAll();
}