package com.votri.combatkeepinv.core.internal;

import com.votri.combatkeepinv.core.inventory.InventoryAction;
import com.votri.combatkeepinv.core.inventory.InventoryDecision;
import com.votri.combatkeepinv.core.inventory.InventoryPolicy;

import java.util.Objects;

public final class DefaultInventoryDecision
        implements InventoryDecision {

    private final InventoryAction action;
    private final InventoryPolicy policy;

    public DefaultInventoryDecision(
            InventoryAction action,
            InventoryPolicy policy
    ) {
        this.action = Objects.requireNonNull(action);
        this.policy = Objects.requireNonNull(policy);
    }

    @Override
    public InventoryAction getAction() {
        return action;
    }

    @Override
    public InventoryPolicy getPolicy() {
        return policy;
    }

    @Override
    public boolean keepMainInventory() {
        return policy.keepMainInventory();
    }

    @Override
    public boolean keepArmor() {
        return policy.keepArmor();
    }

    @Override
    public boolean keepOffhand() {
        return policy.keepOffhand();
    }

    @Override
    public boolean keepExperience() {
        return policy.keepExperience();
    }
}