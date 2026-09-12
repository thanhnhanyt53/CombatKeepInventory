package com.votri.combatkeepinv.core.api;

import com.votri.combatkeepinv.core.combat.CombatReason;
import com.votri.combatkeepinv.core.combat.CombatSession;
import com.votri.combatkeepinv.core.combat.CombatSessionManager;

import java.util.Optional;
import java.util.UUID;

public interface CombatAPI {

    CombatSessionManager getSessionManager();

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
}