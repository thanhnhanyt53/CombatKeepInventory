package com.votri.combatkeepinv.core.internal;

import com.votri.combatkeepinv.core.damage.DamageAttributionResult;
import com.votri.combatkeepinv.core.damage.DamageAttributionService;
import com.votri.combatkeepinv.core.damage.DamageSource;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultDamageAttributionService
        implements DamageAttributionService {

    private final Map<UUID, DamageSource> lastDamage =
            new ConcurrentHashMap<>();

    @Override
    public DamageAttributionResult resolve(
            UUID victimId,
            DamageSource rawSource
    ) {
        recordDamage(rawSource);

        UUID killer =
                rawSource
                        .getResponsiblePlayerId()
                        .orElse(null);

        boolean playerCaused =
                rawSource.isPlayerCaused();

        boolean directPvP =
                playerCaused
                        && rawSource.getCauseType()
                        == com.votri.combatkeepinv.core.damage
                        .DamageCauseType.PLAYER;

        boolean indirectPvP =
                playerCaused && !directPvP;

        return new DefaultDamageAttributionResult(
                victimId,
                rawSource,
                killer,
                playerCaused,
                directPvP,
                indirectPvP
        );
    }

    @Override
    public Optional<DamageSource> getLastDamage(
            UUID victimId
    ) {
        return Optional.ofNullable(
                lastDamage.get(victimId)
        );
    }

    @Override
    public void recordDamage(
            DamageSource source
    ) {
        source.getVictimId()
                .ifPresent(
                        victim ->
                                lastDamage.put(
                                        victim,
                                        source
                                )
                );
    }

    @Override
    public void clearDamageHistory(
            UUID victimId
    ) {
        lastDamage.remove(victim);
    }

    @Override
    public void clearAll() {
        lastDamage.clear();
    }
}