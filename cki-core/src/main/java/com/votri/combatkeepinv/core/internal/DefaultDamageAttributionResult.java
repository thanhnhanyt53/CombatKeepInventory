package com.votri.combatkeepinv.core.internal;

import com.votri.combatkeepinv.core.damage.DamageAttributionResult;
import com.votri.combatkeepinv.core.damage.DamageSource;

import java.util.Optional;
import java.util.UUID;

public final class DefaultDamageAttributionResult
        implements DamageAttributionResult {

    private final UUID victimId;
    private final DamageSource source;
    private final UUID killerId;
    private final boolean playerCaused;
    private final boolean directPvP;
    private final boolean indirectPvP;

    public DefaultDamageAttributionResult(
            UUID victimId,
            DamageSource source,
            UUID killerId,
            boolean playerCaused,
            boolean directPvP,
            boolean indirectPvP
    ) {
        this.victimId = victimId;
        this.source = source;
        this.killerId = killerId;
        this.playerCaused = playerCaused;
        this.directPvP = directPvP;
        this.indirectPvP = indirectPvP;
    }

    @Override
    public UUID getVictimId() {
        return victimId;
    }

    @Override
    public DamageSource getDamageSource() {
        return source;
    }

    @Override
    public Optional<UUID> getKillerId() {
        return Optional.ofNullable(killerId);
    }

    @Override
    public boolean isPlayerCaused() {
        return playerCaused;
    }

    @Override
    public boolean isDirectPvP() {
        return directPvP;
    }

    @Override
    public boolean isIndirectPvP() {
        return indirectPvP;
    }
}