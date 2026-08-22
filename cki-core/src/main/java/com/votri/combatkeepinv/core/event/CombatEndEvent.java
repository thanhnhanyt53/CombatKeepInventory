package com.votri.combatkeepinv.core.event;

import com.votri.combatkeepinv.core.api.CombatPlayer;

public final class CombatEndEvent {

    private final CombatPlayer player;

    public CombatEndEvent(
            CombatPlayer player
    ) {
        this.player = player;
    }

    public CombatPlayer getPlayer() {
        return player;
    }
}