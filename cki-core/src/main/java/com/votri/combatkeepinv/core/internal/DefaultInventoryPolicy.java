package com.votri.combatkeepinv.core.internal;

import com.votri.combatkeepinv.core.inventory.InventoryAction;
import com.votri.combatkeepinv.core.inventory.InventoryPolicy;

public final class DefaultInventoryPolicy
        implements InventoryPolicy {

    private final InventoryAction action;
    private final boolean keepMainInventory;
    private final boolean keepArmor;
    private final boolean keepOffhand;
    private final boolean keepHotbar;
    private final boolean keepExperience;
    private final boolean keepLevels;

    public DefaultInventoryPolicy(
            InventoryAction action,
            boolean keepMainInventory,
            boolean keepArmor,
            boolean keepOffhand,
            boolean keepHotbar,
            boolean keepExperience,
            boolean keepLevels
    ) {
        this.action = action;
        this.keepMainInventory = keepMainInventory;
        this.keepArmor = keepArmor;
        this.keepOffhand = keepOffhand;
        this.keepHotbar = keepHotbar;
        this.keepExperience = keepExperience;
        this.keepLevels = keepLevels;
    }

    @Override
    public InventoryAction getAction() {
        return action;
    }

    @Override
    public boolean keepMainInventory() {
        return keepMainInventory;
    }

    @Override
    public boolean keepArmor() {
        return keepArmor;
    }

    @Override
    public boolean keepOffhand() {
        return keepOffhand;
    }

    @Override
    public boolean keepHotbar() {
        return keepHotbar;
    }

    @Override
    public boolean keepExperience() {
        return keepExperience;
    }

    @Override
    public boolean keepLevels() {
        return keepLevels;
    }
}