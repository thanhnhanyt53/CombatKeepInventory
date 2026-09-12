package com.votri.combatkeepinv.core.event;

import com.votri.combatkeepinv.core.combat.CombatSession;

import java.util.Objects;

public final class CombatStartEvent {

    private final CombatSession session;

    public CombatStartEvent(CombatSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    public CombatSession getSession() {
        return session;
    }
}