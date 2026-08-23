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
 * Bukkit implementation of the core CombatService API.
 *
 * <p>
 * This class is intentionally responsible only for adapting
 * the platform-independent core combat API to Bukkit.
 * </p>
 *
 * <p>
 * CombatTag ownership belongs to {@link CombatManager}.
 * The Bukkit event layer ({@code CombatListener}) determines
 * when a valid player-originated attack should call
 * {@link #startCombat(UUID, UUID)}.
 * </p>
 *
 * <p>
 * CKI does NOT own a global PvP enable/disable state anymore.
 * PvPManager or another PvP plugin may manage that responsibility.
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

    /*
     * ==========================================================
     * COMBAT
     * ==========================================================
     */

    /**
     * Starts or refreshes CombatTag for both players.
     *
     * <p>
     * This method does NOT check:
     *
     * <ul>
     *     <li>PvPManager</li>
     *     <li>WorldGuard PvP state</li>
     *     <li>global CKI PvP toggle</li>
     *     <li>/cki pvp</li>
     * </ul>
     *
     * <p>
     * Those systems must not become a dependency of the
     * CombatTag state machine.
     * </p>
     */
    @Override
    public CombatResult startCombat(
            UUID attacker,
            UUID victim
    ) {

        /*
         * Validate arguments.
         */
        if (attacker == null
                || victim == null
                || attacker.equals(victim)) {

            return CombatResult.INVALID_ARGUMENT;
        }

        /*
         * Global combat system switch.
         *
         * This is different from a PvP toggle.
         *
         * combat.enabled controls whether CKI's
         * CombatTag system itself is enabled.
         */
        if (!plugin.getConfig().getBoolean(
                "combat.enabled",
                true
        )) {

            return CombatResult.DISABLED;
        }

        /*
         * Create / refresh the CombatTag.
         *
         * CombatManager is the single owner of the
         * CombatTag state.
         */
        combatManager.tag(
                attacker,
                victim
        );

        return CombatResult.SUCCESS;
    }

    /**
     * Ends CombatTag for a player.
     */
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

        combatManager.remove(
                player
        );

        return CombatResult.SUCCESS;
    }

    /**
     * Returns whether the player currently has
     * an active CombatTag.
     */
    @Override
    public boolean isInCombat(
            UUID player
    ) {

        return player != null
                && combatManager.isInCombat(player);
    }

    /**
     * Returns the current combat state.
     */
    @Override
    public CombatState getCombatState(
            UUID player
    ) {

        if (!isInCombat(player)) {

            return CombatState.SAFE;
        }

        return CombatState.IN_COMBAT;
    }

    /**
     * Returns remaining CombatTag duration.
     */
    @Override
    public long getRemainingCombatMillis(
            UUID player
    ) {

        if (player == null) {

            return 0L;
        }

        return combatManager.getRemainingMillis(
                player
        );
    }

    /*
     * ==========================================================
     * DEATH POLICY
     * ==========================================================
     */

    /**
     * Evaluates the inventory policy when a player dies.
     *
     * <p>
     * IMPORTANT:
     *
     * <pre>
     * 1. Check CombatTag first.
     * 2. If CombatTag is active -> DROP.
     * 3. Only if CombatTag is inactive -> inspect DeathContext.
     * </pre>
     *
     * <p>
     * Therefore an active CombatTag has absolute priority over
     * the final damage source.
     * </p>
     */
    @Override
    public DeathResult evaluateDeath(
            UUID player,
            DeathContext context
    ) {

        /*
         * ======================================================
         * INVALID PLAYER
         * ======================================================
         */

        if (player == null) {

            return new DeathResult(
                    InventoryPolicy.KEEP,
                    true,
                    false
            );
        }

        /*
         * ======================================================
         * STEP 1 — ACTIVE COMBAT TAG
         * ======================================================
         *
         * This MUST be evaluated before DeathContext.
         *
         * If the player is tagged, the reason for death does
         * not matter.
         *
         * Examples:
         *
         * Player
         * Mob
         * Projectile
         * Lava
         * Fall
         * Void
         * Fire
         * Explosion
         * etc.
         *
         * ALL result in inventory DROP.
         */
        if (combatManager.isInCombat(player)) {

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
         * STEP 2 — NO ACTIVE COMBAT TAG
         * ======================================================
         *
         * Only now may DeathContext influence the result.
         */

        boolean pvpDeath =
                context == DeathContext.PLAYER
                        || context == DeathContext.PROJECTILE;

        /*
         * ======================================================
         * PLAYER / PROJECTILE DEATH
         * ======================================================
         */

        if (pvpDeath) {

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
         * PVE / ENVIRONMENT DEATH
         * ======================================================
         */

        boolean keep =
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
                keep
                        ? InventoryPolicy.KEEP
                        : InventoryPolicy.DROP,
                keepExperience,
                false
        );
    }

    /*
     * ==========================================================
     * LEGACY / CORE API COMPATIBILITY
     * ==========================================================
     *
     * CombatService currently still exposes:
     *
     *     isPvPEnabled()
     *     setPvPEnabled(boolean)
     *
     * CKI no longer owns a PvP toggle.
     *
     * These methods therefore remain only because the core
     * interface requires them.
     *
     * They MUST NOT be used by CombatListener to decide whether
     * CombatTag should be created.
     */

    /**
     * Compatibility implementation for the current core API.
     *
     * <p>
     * CKI no longer has a global PvP switch.
     *
     * <p>
     * Returning true means the compatibility method does not
     * disable the combat service.
     */
    @Override
    public boolean isPvPEnabled() {

        return true;
    }

    /**
     * Compatibility implementation for the current core API.
     *
     * <p>
     * CKI no longer owns PvP state, so this is intentionally
     * a no-op.
     *
     * <p>
     * PvP enable/disable should be handled by another plugin,
     * such as PvPManager.
     */
    @Override
    public void setPvPEnabled(
            boolean enabled
    ) {

        /*
         * Intentionally empty.
         *
         * CKI does not own global PvP state.
         */
    }
}