package com.votri.combatkeepinv.core.event;

import com.votri.combatkeepinv.core.combat.CombatSession;

import java.util.Objects;

public final class CombatRefreshEvent {

    private final CombatSession session;

    public CombatRefreshEvent(CombatSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    public CombatSession getSession() {
        return session;
    }
}