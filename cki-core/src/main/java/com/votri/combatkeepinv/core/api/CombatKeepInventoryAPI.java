package com.votri.combatkeepinv.core.api;

public interface CombatKeepInventoryAPI {

    CombatAPI getCombat();

    DamageAPI getDamage();

    DeathAPI getDeath();

    InventoryAPI getInventory();

    PlatformAPI getPlatform();
}