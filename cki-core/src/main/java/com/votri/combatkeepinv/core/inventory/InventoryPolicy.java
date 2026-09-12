package com.votri.combatkeepinv.core.inventory;

public interface InventoryPolicy {

    InventoryAction getAction();

    boolean keepMainInventory();

    boolean keepArmor();

    boolean keepOffhand();

    boolean keepHotbar();

    boolean keepExperience();

    boolean keepLevels();
}