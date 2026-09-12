package com.votri.combatkeepinv.core.death;

import com.votri.combatkeepinv.core.inventory.InventoryPolicy;

public interface DeathDecision {

    boolean shouldKeepInventory();

    InventoryPolicy getInventoryPolicy();

    boolean shouldKeepExperience();

    boolean shouldDropExperience();

    DeathReason getReason();

    String getRuleId();
}