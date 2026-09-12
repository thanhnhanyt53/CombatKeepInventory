package com.votri.combatkeepinv.core.combat;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface CombatSessionManager {

    Optional<CombatSession> getSession(UUID playerId);

    boolean isInCombat(UUID playerId);

    CombatSession startCombat(UUID playerId);

    CombatSession startCombat(
            UUID playerId,
            UUID opponentId
    );

    CombatSession refreshCombat(UUID playerId);

    CombatSession refreshCombat(
            UUID playerId,
            UUID opponentId
    );

    boolean endCombat(
            UUID playerId,
            CombatReason reason
    );

    boolean forceEndCombat(
            UUID playerId,
            CombatReason reason
    );

    void removeSession(UUID playerId);

    Collection<CombatSession> getActiveSessions();

    void cleanupExpiredSessions(long currentTimeMillis);
}