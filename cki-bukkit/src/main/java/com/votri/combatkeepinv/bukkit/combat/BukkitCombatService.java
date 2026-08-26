package com.votri.combatkeepinv.bukkit.combat;

import com.votri.combatkeepinv.bukkit.CombatKeepInventory;
import com.votri.combatkeepinv.core.api.CombatResult;
import com.votri.combatkeepinv.core.api.CombatService;
import com.votri.combatkeepinv.core.api.CombatState;
import com.votri.combatkeepinv.core.api.CombatTag;
import com.votri.combatkeepinv.core.api.DeathContext;
import com.votri.combatkeepinv.core.api.DeathResult;
import com.votri.combatkeepinv.core.api.InventoryPolicy;

import java.util.Objects;
import java.util.UUID;

/**
 * Bukkit implementation of the public CKI CombatService.
 *
 * <p>CombatManager remains the authoritative CombatTag owner.</p>
 */
public final class BukkitCombatService
        implements CombatService {

    private final CombatKeepInventory plugin;
    private final CombatManager combatManager;

    public BukkitCombatService(
            CombatKeepInventory plugin,
            CombatManager combatManager
    ) {

        this.plugin =
                Objects.requireNonNull(
                        plugin,
                        "plugin"
                );

        this.combatManager =
                Objects.requireNonNull(
                        combatManager,
                        "combatManager"
                );
    }

    @Override
    public CombatResult startCombat(
            UUID attacker,
            UUID victim
    ) {

        if (!isValidPair(
                attacker,
                victim
        )) {

            return CombatResult.INVALID_ARGUMENT;
        }

        if (!isEnabled()) {

            return CombatResult.DISABLED;
        }

        /*
         * CombatManager decides START vs REFRESH.
         */
        combatManager.tag(
                attacker,
                victim
        );

        return CombatResult.SUCCESS;
    }

    @Override
    public CombatResult refreshCombat(
            UUID attacker,
            UUID victim
    ) {

        if (!isValidPair(
                attacker,
                victim
        )) {

            return CombatResult.INVALID_ARGUMENT;
        }

        if (!isEnabled()) {

            return CombatResult.DISABLED;
        }

        combatManager.refresh(
                attacker,
                victim
        );

        return CombatResult.SUCCESS;
    }

    @Override
    public CombatResult endCombat(
            UUID player
    ) {

        if (player == null) {

            return CombatResult.INVALID_ARGUMENT;
        }

        if (!combatManager.isInCombat(
                player
        )) {

            return CombatResult.NOT_IN_COMBAT;
        }

        return combatManager.remove(
                player
        )
                ? CombatResult.SUCCESS
                : CombatResult.NOT_IN_COMBAT;
    }

    @Override
    public CombatResult forceEndCombat(
            UUID player
    ) {

        if (player == null) {

            return CombatResult.INVALID_ARGUMENT;
        }

        if (!combatManager.isInCombat(
                player
        )) {

            return CombatResult.NOT_IN_COMBAT;
        }

        return combatManager.forceRemove(
                player
        )
                ? CombatResult.SUCCESS
                : CombatResult.NOT_IN_COMBAT;
    }

    @Override
    public boolean isInCombat(
            UUID player
    ) {

        return player != null
                && combatManager.isInCombat(
                player
        );
    }

    @Override
    public CombatState getCombatState(
            UUID player
    ) {

        return isInCombat(player)
                ? CombatState.IN_COMBAT
                : CombatState.SAFE;
    }

    @Override
    public CombatTag getCombatTag(
            UUID player
    ) {

        return combatManager.getCombatTag(
                player
        );
    }

    @Override
    public long getRemainingCombatMillis(
            UUID player
    ) {

        return combatManager.getRemainingMillis(
                player
        );
    }

    @Override
    public DeathResult evaluateDeath(
            UUID player,
            DeathContext context
    ) {

        if (player == null) {

            return new DeathResult(
                    InventoryPolicy.KEEP,
                    true,
                    false
            );
        }

        /*
         * ======================================================
         * PRIORITY 1 — ACTIVE COMBAT TAG
         * ======================================================
         *
         * Death cause is irrelevant while CombatTag is active.
         *
         * inventory.keep-experience controls this case.
         */

        if (combatManager.isInCombat(
                player
        )) {

            return new DeathResult(
                    InventoryPolicy.DROP,
                    plugin.shouldKeepCombatDeathExperience(),
                    true
            );
        }

        /*
         * ======================================================
         * PRIORITY 2 — DEATH CONTEXT
         * ======================================================
         */

        boolean pvpDeath =
                context == DeathContext.PLAYER
                        || context == DeathContext.PROJECTILE;

        if (pvpDeath) {

            boolean drop =
                    plugin.getConfig()
                            .getBoolean(
                                    "death.pvp-kill-drops",
                                    true
                            );

            return new DeathResult(
                    drop
                            ? InventoryPolicy.DROP
                            : InventoryPolicy.KEEP,
                    plugin.shouldKeepDeathExperience(),
                    true
            );
        }

        /*
         * ======================================================
         * PRIORITY 3 — PVE / ENVIRONMENT
         * ======================================================
         */

        boolean keep =
                plugin.getConfig()
                        .getBoolean(
                                "death.pve-keeps-inventory",
                                true
                        );

        return new DeathResult(
                keep
                        ? InventoryPolicy.KEEP
                        : InventoryPolicy.DROP,
                plugin.shouldKeepDeathExperience(),
                false
        );
    }

    @Override
    public boolean isEnabled() {

        return plugin.isCombatEnabled();
    }

    private boolean isValidPair(
            UUID attacker,
            UUID victim
    ) {

        return attacker != null
                && victim != null
                && !attacker.equals(victim);
    }
}