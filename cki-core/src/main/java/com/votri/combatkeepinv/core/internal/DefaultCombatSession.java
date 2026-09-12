package com.votri.combatkeepinv.core.internal;

import com.votri.combatkeepinv.core.combat.CombatReason;
import com.votri.combatkeepinv.core.combat.CombatSession;
import com.votri.combatkeepinv.core.combat.CombatState;

import java.util.Optional;
import java.util.UUID;

public final class DefaultCombatSession
        implements CombatSession {

    private final UUID playerId;
    private final long startedAt;
    private long lastActivityAt;
    private long expiresAt;

    private UUID opponentId;
    private UUID lastAttackerId;
    private UUID lastVictimId;

    private CombatState state;
    private CombatReason endReason;

    public DefaultCombatSession(
            UUID playerId,
            long startedAt,
            long expiresAt
    ) {
        this.playerId = playerId;
        this.startedAt = startedAt;
        this.lastActivityAt = startedAt;
        this.expiresAt = expiresAt;
        this.state = CombatState.ACTIVE;
    }

    @Override
    public UUID getPlayerId() {
        return playerId;
    }

    @Override
    public CombatState getState() {
        return state;
    }

    @Override
    public long getStartedAt() {
        return startedAt;
    }

    @Override
    public long getLastActivityAt() {
        return lastActivityAt;
    }

    @Override
    public long getExpiresAt() {
        return expiresAt;
    }

    @Override
    public long getRemainingMillis() {
        return Math.max(
                0L,
                expiresAt - System.currentTimeMillis()
        );
    }

    @Override
    public boolean isActive() {
        return state == CombatState.ACTIVE
                && !isExpired();
    }

    @Override
    public boolean isExpired() {
        return state == CombatState.EXPIRED
                || System.currentTimeMillis() >= expiresAt;
    }

    @Override
    public Optional<UUID> getOpponentId() {
        return Optional.ofNullable(opponentId);
    }

    @Override
    public Optional<UUID> getLastAttackerId() {
        return Optional.ofNullable(lastAttackerId);
    }

    @Override
    public Optional<UUID> getLastVictimId() {
        return Optional.ofNullable(lastVictimId);
    }

    @Override
    public Optional<CombatReason> getEndReason() {
        return Optional.ofNullable(endReason);
    }

    public void refresh(
            long timestamp,
            long expiresAt
    ) {
        this.lastActivityAt = timestamp;
        this.expiresAt = expiresAt;
        this.state = CombatState.ACTIVE;
        this.endReason = null;
    }

    public void setOpponentId(UUID opponentId) {
        this.opponentId = opponentId;
    }

    public void setLastAttackerId(UUID attackerId) {
        this.lastAttackerId = attackerId;
    }

    public void setLastVictimId(UUID victimId) {
        this.lastVictimId = victimId;
    }

    public void end(
            CombatState state,
            CombatReason reason
    ) {
        this.state = state;
        this.endReason = reason;
    }
}