package com.votri.combatkeepinv.core.api;

public final class DeathResult {

    private final InventoryPolicy inventoryPolicy;
    private final boolean keepExperience;
    private final boolean wasCombatDeath;

    public DeathResult(
            InventoryPolicy inventoryPolicy,
            boolean keepExperience,
            boolean wasCombatDeath
    ) {
        this.inventoryPolicy = inventoryPolicy;
        this.keepExperience = keepExperience;
        this.wasCombatDeath = wasCombatDeath;
    }

    public InventoryPolicy getInventoryPolicy() {
        return inventoryPolicy;
    }

    public boolean shouldKeepInventory() {
        return inventoryPolicy == InventoryPolicy.KEEP;
    }

    public boolean shouldDropInventory() {
        return inventoryPolicy == InventoryPolicy.DROP;
    }

    public boolean shouldKeepExperience() {
        return keepExperience;
    }

    public boolean wasCombatDeath() {
        return wasCombatDeath;
    }
}