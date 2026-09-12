package com.votri.combatkeepinv.core.event;

import com.votri.combatkeepinv.core.death.DeathContext;
import com.votri.combatkeepinv.core.death.DeathDecision;

import java.util.Objects;

public final class DeathEvaluateEvent {

    private final DeathContext context;
    private DeathDecision decision;

    public DeathEvaluateEvent(
            DeathContext context,
            DeathDecision decision
    ) {
        this.context = Objects.requireNonNull(context, "context");
        this.decision = Objects.requireNonNull(decision, "decision");
    }

    public DeathContext getContext() {
        return context;
    }

    public DeathDecision getDecision() {
        return decision;
    }

    public void setDecision(DeathDecision decision) {
        this.decision = Objects.requireNonNull(
                decision,
                "decision"
        );
    }
}