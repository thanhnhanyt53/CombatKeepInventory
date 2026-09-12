package com.votri.combatkeepinv.core;

import com.votri.combatkeepinv.core.api.CombatKeepInventoryAPI;
import com.votri.combatkeepinv.core.combat.CombatSessionManager;
import com.votri.combatkeepinv.core.damage.DamageAttributionService;
import com.votri.combatkeepinv.core.death.DeathService;
import com.votri.combatkeepinv.core.internal.DefaultCombatAPI;
import com.votri.combatkeepinv.core.internal.DefaultCombatKeepInventoryAPI;
import com.votri.combatkeepinv.core.internal.DefaultDamageAPI;
import com.votri.combatkeepinv.core.internal.DefaultDamageAttributionService;
import com.votri.combatkeepinv.core.internal.DefaultDeathAPI;
import com.votri.combatkeepinv.core.internal.DefaultDeathService;
import com.votri.combatkeepinv.core.internal.DefaultInventoryAPI;
import com.votri.combatkeepinv.core.internal.DefaultInventoryPolicy;
import com.votri.combatkeepinv.core.internal.DefaultPlatformAPI;
import com.votri.combatkeepinv.core.internal.DefaultPlatformInfo;
import com.votri.combatkeepinv.core.platform.PlatformCapability;
import com.votri.combatkeepinv.core.platform.PlatformInfo;
import com.votri.combatkeepinv.core.platform.PlatformType;
import com.votri.combatkeepinv.core.inventory.InventoryAction;
import com.votri.combatkeepinv.core.inventory.InventoryPolicy;

import java.util.EnumSet;

public final class CombatKeepInventoryCore {

    private CombatKeepInventoryCore() {
    }

    public static CombatKeepInventoryAPI create(
            long combatDurationMillis,
            PlatformInfo platform
    ) {
        CombatSessionManager combat =
                new com.votri.combatkeepinv.core.internal
                        .DefaultCombatSessionManager(
                                combatDurationMillis
                        );

        DamageAttributionService damage =
                new DefaultDamageAttributionService();

        InventoryPolicy inventoryPolicy =
                new DefaultInventoryPolicy(
                        InventoryAction.KEEP,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true
                );

        DeathService death =
                new DefaultDeathService(
                        combat,
                        damage,
                        platform,
                        inventoryPolicy
                );

        return new DefaultCombatKeepInventoryAPI(
                new DefaultCombatAPI(combat),
                new DefaultDamageAPI(damage),
                new DefaultDeathAPI(death),
                new DefaultInventoryAPI(inventoryPolicy),
                new DefaultPlatformAPI(platform)
        );
    }

    public static PlatformInfo createDefaultPlatform() {

        return new DefaultPlatformInfo(
                PlatformType.UNKNOWN,
                "Unknown",
                "Unknown",
                "Unknown",
                "1.2",
                false,
                false,
                EnumSet.noneOf(
                        PlatformCapability.class
                )
        );
    }
}