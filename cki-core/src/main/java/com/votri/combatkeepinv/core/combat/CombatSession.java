package com.votri.combatkeepinv.core.combat;

import java.util.Optional;
import java.util.UUID;

public interface CombatSession {

    UUID getPlayerId();

    CombatState getState();

    long getStartedAt();

    long getLastActivityAt();

    long getExpiresAt();

    long getRemainingMillis();

    boolean isActive();

    boolean isExpired();

    Optional<UUID> getOpponentId();

    Optional<UUID> getLastAttackerId();

    Optional<UUID> getLastVictimId();

    Optional<CombatReason> getEndReason();
}