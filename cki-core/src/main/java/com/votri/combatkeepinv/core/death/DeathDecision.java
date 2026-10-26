package com.votri.combatkeepinv.core.death;

import com.votri.combatkeepinv.core.inventory.InventoryPolicy;

public interface DeathDecision {

    boolean shouldKeepInventory();

    InventoryPolicy getInventoryPolicy();

    boolean shouldKeepExperience();

    boolean shouldDropExperience();

    DeathReason getReason();

    String getRuleId();

    /**
     * Priority of the rule that produced this decision (the
     * {@code priority} value of the winning inventory policy), or
     * {@link Integer#MIN_VALUE} when no priority applied.
     */
    default int getPriority() {
        return Integer.MIN_VALUE;
    }
}