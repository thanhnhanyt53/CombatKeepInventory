package com.votri.combatkeepinv.core.event;

import com.votri.combatkeepinv.core.api.CombatPlayer;

public final class CombatStartEvent {

    private final CombatPlayer attacker;
    private final CombatPlayer victim;

    public CombatStartEvent(
            CombatPlayer attacker,
            CombatPlayer victim
    ) {
        this.attacker = attacker;
        this.victim = victim;
    }

    public CombatPlayer getAttacker() {
        return attacker;
    }

    public CombatPlayer getVictim() {
        return victim;
    }
}