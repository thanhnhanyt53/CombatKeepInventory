package com.votri.combatkeepinv.bukkit.combat;

import com.votri.combatkeepinv.bukkit.CombatKeepInventory;
import com.votri.combatkeepinv.core.api.CombatResult;
import com.votri.combatkeepinv.core.api.CombatService;
import com.votri.combatkeepinv.core.api.CombatState;
import com.votri.combatkeepinv.core.api.DeathContext;
import com.votri.combatkeepinv.core.api.DeathResult;
import com.votri.combatkeepinv.core.api.InventoryPolicy;

import java.util.Objects;
import java.util.UUID;

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

    /*
     * ==========================================================
     * COMBAT
     * ==========================================================
     */

    @Override
    public CombatResult startCombat(
            UUID attacker,
            UUID victim
    ) {

        if (attacker == null
                || victim == null
                || attacker.equals(victim)) {

            return CombatResult.INVALID_ARGUMENT;
        }

        if (!plugin.getConfig().getBoolean(
                "combat.enabled",
                true
        )) {
            return CombatResult.DISABLED;
        }

        /*
         * Every valid PvP hit refreshes the CombatTag
         * for both players.
         */
        combatManager.tag(
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

        combatManager.remove(
                player
        );

        return CombatResult.SUCCESS;
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

        if (!isInCombat(player)) {
            return CombatState.SAFE;
        }

        return CombatState.IN_COMBAT;
    }

    @Override
    public long getRemainingCombatMillis(
            UUID player
    ) {

        return combatManager.getRemainingMillis(
                player
        );
    }

    /*
     * ==========================================================
     * DEATH EVALUATION
     * ==========================================================
     *
     * IMPORTANT:
     *
     * CombatTag is ALWAYS checked FIRST.
     *
     * DeathContext is evaluated ONLY when there is
     * no active CombatTag.
     */

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
         * STEP 1
         * ======================================================
         *
         * CHECK COMBAT TAG FIRST.
         */

        boolean inCombat =
                combatManager.isInCombat(
                        player
                );

        /*
         * ======================================================
         * ACTIVE COMBAT TAG
         * ======================================================
         *
         * If CombatTag is active, the cause of death does
         * NOT matter.
         *
         * Player
         * Projectile
         * Mob
         * Zombie
         * Skeleton
         * Creeper
         * Lava
         * Fall
         * Void
         * Explosion
         * Fire
         * etc.
         *
         * ALL cause inventory DROP.
         */

        if (inCombat) {

            boolean keepExperience =
                    plugin.getConfig().getBoolean(
                            "inventory.keep-experience",
                            true
                    );

            return new DeathResult(
                    InventoryPolicy.DROP,
                    keepExperience,
                    true
            );
        }

        /*
         * ======================================================
         * STEP 2
         * ======================================================
         *
         * NO ACTIVE COMBAT TAG.
         *
         * Only now do we care about DeathContext.
         */

        DeathContext safeContext =
                context != null
                        ? context
                        : DeathContext.ENVIRONMENT;

        /*
         * ======================================================
         * PLAYER / PROJECTILE DEATH
         * ======================================================
         */

        if (safeContext == DeathContext.PLAYER
                || safeContext == DeathContext.PROJECTILE) {

            boolean keepExperience =
                    plugin.getConfig().getBoolean(
                            "inventory.keep-experience",
                            true
                    );

            boolean drop =
                    plugin.getConfig().getBoolean(
                            "death.pvp-kill-drops",
                            true
                    );

            return new DeathResult(
                    drop
                            ? InventoryPolicy.DROP
                            : InventoryPolicy.KEEP,
                    keepExperience,
                    true
            );
        }

        /*
         * ======================================================
         * STEP 3
         * ======================================================
         *
         * NO COMBAT TAG + PVE / ENVIRONMENT
         */

        boolean keepInventory =
                plugin.getConfig().getBoolean(
                        "death.pve-keeps-inventory",
                        true
                );

        boolean keepExperience =
                plugin.getConfig().getBoolean(
                        "inventory.keep-experience",
                        true
                );

        return new DeathResult(
                keepInventory
                        ? InventoryPolicy.KEEP
                        : InventoryPolicy.DROP,
                keepExperience,
                false
        );
    }
}