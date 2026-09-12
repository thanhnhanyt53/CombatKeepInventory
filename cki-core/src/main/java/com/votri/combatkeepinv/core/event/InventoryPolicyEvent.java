package com.votri.combatkeepinv.core.event;

import com.votri.combatkeepinv.core.inventory.InventoryPolicy;

import java.util.Objects;

public final class InventoryPolicyEvent {

    private final InventoryPolicy policy;

    public InventoryPolicyEvent(InventoryPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public InventoryPolicy getPolicy() {
        return policy;
    }
}