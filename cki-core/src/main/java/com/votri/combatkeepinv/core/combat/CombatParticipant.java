package com.votri.combatkeepinv.core.combat;

import java.util.UUID;

public interface CombatParticipant {

    UUID getPlayerId();

    long getFirstHitAt();

    long getLastHitAt();

    long getExpiresAt();

    default boolean isExpired(long currentTimeMillis) {
        return currentTimeMillis >= getExpiresAt();
    }
}