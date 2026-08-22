package com.votri.combatkeepinv.core.event;

import com.votri.combatkeepinv.core.api.CombatPlayer;
import com.votri.combatkeepinv.core.api.DeathContext;
import com.votri.combatkeepinv.core.api.DeathResult;

public final class CombatDeathEvent {

    private final CombatPlayer player;
    private final DeathContext context;
    private final DeathResult result;

    public CombatDeathEvent(
            CombatPlayer player,
            DeathContext context,
            DeathResult result
    ) {
        this.player = player;
        this.context = context;
        this.result = result;
    }

    public CombatPlayer getPlayer() {
        return player;
    }

    public DeathContext getContext() {
        return context;
    }

    public DeathResult getResult() {
        return result;
    }
}