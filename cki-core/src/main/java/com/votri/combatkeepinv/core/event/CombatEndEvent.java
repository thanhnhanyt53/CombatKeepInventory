package com.votri.combatkeepinv.core.event;

import com.votri.combatkeepinv.core.combat.CombatReason;
import com.votri.combatkeepinv.core.combat.CombatSession;

import java.util.Objects;

public final class CombatEndEvent {

    private final CombatSession session;
    private final CombatReason reason;

    public CombatEndEvent(
            CombatSession session,
            CombatReason reason
    ) {
        this.session = Objects.requireNonNull(session, "session");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public CombatSession getSession() {
        return session;
    }

    public CombatReason getReason() {
        return reason;
    }
}