package com.votri.combatkeepinv.core.internal;

import com.votri.combatkeepinv.core.api.CombatAPI;
import com.votri.combatkeepinv.core.api.CombatKeepInventoryAPI;
import com.votri.combatkeepinv.core.api.DamageAPI;
import com.votri.combatkeepinv.core.api.DeathAPI;
import com.votri.combatkeepinv.core.api.InventoryAPI;
import com.votri.combatkeepinv.core.api.PlatformAPI;

import java.util.Objects;

public final class DefaultCombatKeepInventoryAPI
        implements CombatKeepInventoryAPI {

    private final CombatAPI combat;
    private final DamageAPI damage;
    private final DeathAPI death;
    private final InventoryAPI inventory;
    private final PlatformAPI platform;

    public DefaultCombatKeepInventoryAPI(
            CombatAPI combat,
            DamageAPI damage,
            DeathAPI death,
            InventoryAPI inventory,
            PlatformAPI platform
    ) {
        this.combat =
                Objects.requireNonNull(combat);
        this.damage =
                Objects.requireNonNull(damage);
        this.death =
                Objects.requireNonNull(death);
        this.inventory =
                Objects.requireNonNull(inventory);
        this.platform =
                Objects.requireNonNull(platform);
    }

    @Override
    public CombatAPI getCombat() {
        return combat;
    }

    @Override
    public DamageAPI getDamage() {
        return damage;
    }

    @Override
    public DeathAPI getDeath() {
        return death;
    }

    @Override
    public InventoryAPI getInventory() {
        return inventory;
    }

    @Override
    public PlatformAPI getPlatform() {
        return platform;
    }
}