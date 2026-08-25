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

/**
 * Bukkit implementation of the core CombatService contract.
 *
 * <p>
 * CombatManager owns the actual CombatTag state.
 * CombatListener determines when a Bukkit damage event
 * represents valid player-vs-player combat.
 * </p>
 *
 * <p>
 * CKI does not own a global PvP on/off switch.
 * </p>
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

    /**
     * Creates or refreshes CombatTag for both players.
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

        /*
         * This controls CKI CombatTag processing itself.
         * It is NOT a server PvP toggle.
         */
        if (!plugin.getConfig().getBoolean(
                "combat.enabled",
                true
        )) {
            return CombatResult.DISABLED;
        }

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

        if (!combatManager.isInCombat(player)) {
            return CombatResult.NOT_IN_COMBAT;
        }

        combatManager.remove(player);

        return CombatResult.SUCCESS;
    }

    @Override
    public boolean isInCombat(UUID player) {
        return player != null
                && combatManager.isInCombat(player);
    }

    @Override
    public CombatState getCombatState(UUID player) {
        if (!isInCombat(player)) {
            return CombatState.SAFE;
        }

        return CombatState.IN_COMBAT;
    }

    @Override
    public long getRemainingCombatMillis(UUID player) {
        return combatManager.getRemainingMillis(player);
    }

    /**
     * Evaluates death policy.
     *
     * <p>CombatTag is always checked before DeathContext.</p>
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
         * 1. COMBAT TAG FIRST
         * ======================================================
         *
         * If active, the death cause is irrelevant.
         */
        if (combatManager.isInCombat(player)) {

            boolean keepExperience =
                    plugin.getConfig().getBoolean(
                            "death.keep-experience",
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
         * 2. NO COMBAT TAG
         * ======================================================
         *
         * Only now can DeathContext determine the policy.
         */
        boolean pvpDeath =
                context == DeathContext.PLAYER
                        || context == DeathContext.PROJECTILE;

        if (pvpDeath) {

            boolean drop =
                    plugin.getConfig().getBoolean(
                            "death.pvp-kill-drops",
                            true
                    );

            boolean keepExperience =
                    plugin.getConfig().getBoolean(
                            "death.keep-experience",
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
         * 3. NO COMBAT TAG + PVE
         * ======================================================
         */
        boolean keep =
                plugin.getConfig().getBoolean(
                        "death.pve-keeps-inventory",
                        true
                );

        boolean keepExperience =
                plugin.getConfig().getBoolean(
                        "death.keep-experience",
                        true
                );

        return new DeathResult(
                keep
                        ? InventoryPolicy.KEEP
                        : InventoryPolicy.DROP,
                keepExperience,
                false
        );
    }

    /*
     * The current cki-core CombatService contract still
     * contains these legacy methods.
     *
     * They are kept only for API compatibility.
     * CombatTag does NOT use them.
     */

    @Override
    public boolean isPvPEnabled() {
        return true;
    }

    @Override
    public void setPvPEnabled(boolean enabled) {
        /*
         * Intentionally ignored.
         *
         * CKI does not own global PvP state.
         */
    }
}