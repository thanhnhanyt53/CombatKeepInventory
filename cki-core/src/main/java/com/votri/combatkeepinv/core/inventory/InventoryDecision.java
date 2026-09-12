package com.votri.combatkeepinv.core.inventory;

public interface InventoryDecision {

    InventoryAction getAction();

    InventoryPolicy getPolicy();

    boolean keepMainInventory();

    boolean keepArmor();

    boolean keepOffhand();

    boolean keepExperience();
}