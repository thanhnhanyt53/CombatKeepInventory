package com.votri.combatkeepinv.core.internal;

import com.votri.combatkeepinv.core.death.DeathDecision;
import com.votri.combatkeepinv.core.death.DeathReason;
import com.votri.combatkeepinv.core.inventory.InventoryPolicy;

import java.util.Objects;

public final class DefaultDeathDecision
        implements DeathDecision {

    private final boolean keepInventory;
    private final InventoryPolicy inventoryPolicy;
    private final DeathReason reason;
    private final String ruleId;

    public DefaultDeathDecision(
            boolean keepInventory,
            InventoryPolicy inventoryPolicy,
            DeathReason reason,
            String ruleId
    ) {
        this.keepInventory = keepInventory;
        this.inventoryPolicy =
                Objects.requireNonNull(inventoryPolicy);
        this.reason = Objects.requireNonNull(reason);
        this.ruleId =
                Objects.requireNonNull(ruleId);
    }

    @Override
    public boolean shouldKeepInventory() {
        return keepInventory;
    }

    @Override
    public InventoryPolicy getInventoryPolicy() {
        return inventoryPolicy;
    }

    @Override
    public boolean shouldKeepExperience() {
        return inventoryPolicy.keepExperience();
    }

    @Override
    public boolean shouldDropExperience() {
        return !inventoryPolicy.keepExperience();
    }

    @Override
    public DeathReason getReason() {
        return reason;
    }

    @Override
    public String getRuleId() {
        return ruleId;
    }
}