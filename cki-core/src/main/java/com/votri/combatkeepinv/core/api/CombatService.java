package com.votri.combatkeepinv.core.api;

import java.util.UUID;

public interface CombatService {

    CombatResult startCombat(
            UUID attacker,
            UUID victim
    );

    CombatResult endCombat(
            UUID player
    );

    boolean isInCombat(
            UUID player
    );

    CombatState getCombatState(
            UUID player
    );

    long getRemainingCombatMillis(
            UUID player
    );

    DeathResult evaluateDeath(
            UUID player,
            DeathContext context
    );

    boolean isPvPEnabled();

    void setPvPEnabled(
            boolean enabled
    );
}