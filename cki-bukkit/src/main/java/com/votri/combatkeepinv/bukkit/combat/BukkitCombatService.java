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
 * Bukkit implementation of the CombatService API.
 *
 * <p>CombatKeepInventory does not own or control the server's
 * PvP enable/disable state. PvP may be controlled by another
 * plugin such as PvPManager, WorldGuard, or a custom PvP system.</p>
 *
 * <p>The two PvP toggle methods required by the current Core API
 * are therefore compatibility methods only.</p>
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
     * Combat
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

        /*
         * CKI no longer owns PvP enable/disable state.
         *
         * Another plugin may control whether PvP damage is
         * actually allowed. CombatListener is responsible for
         * deciding whether the damage event represents valid PvP.
         *
         * Therefore there is intentionally NO:
         *
         *     plugin.isPvPEnabled()
         *
         * check here.
         */

        if (!plugin.getConfig().getBoolean(
                "combat.enabled",
                true
        )) {

            return CombatResult.DISABLED;
        }

        /*
         * Refresh the combat timer for both players.
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

        if (!combatManager.isInCombat(player)) {
            return CombatResult.NOT_IN_COMBAT;
        }

        combatManager.remove(player);

        return CombatResult.SUCCESS;
    }

    @Override
    public boolean isInCombat(
            UUID player
    ) {

        return player != null
                && combatManager.isInCombat(player);
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

        if (player == null) {
            return 0L;
        }

        return combatManager.getRemainingMillis(
                player
        );
    }

    /*
     * ==========================================================
     * Death evaluation
     * ==========================================================
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
         * ACTIVE COMBAT TAG
         * ======================================================
         *
         * CombatTag is authoritative while active.
         *
         * This means:
         *
         *   PvP hit
         *   -> CombatTag active
         *   -> player dies to ANYTHING
         *   -> inventory DROPS
         *
         * Examples:
         *
         *   Player hit -> falls into void
         *   Player hit -> lava
         *   Player hit -> mob
         *   Player hit -> End Crystal
         *   Player hit -> TNT
         *   Player hit -> fall damage
         *
         * All of these result in DROP while the tag is active.
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
         * NO ACTIVE COMBAT TAG
         * ======================================================
         *
         * CombatTag has expired.
         *
         * At this point the final death attribution becomes
         * authoritative.
         */

        boolean pvpDeath =
                context == DeathContext.PLAYER
                        || context == DeathContext.PROJECTILE;

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
         * PVE / ENVIRONMENT
         * ======================================================
         *
         * No CombatTag and no final PvP attribution.
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
     * Legacy Core API compatibility
     * ==========================================================
     *
     * These methods currently exist in CombatService.
     *
     * CKI does NOT use them to control PvP.
     *
     * They remain only so that the current Core API contract
     * continues to compile.
     */

    @Override
    public boolean isPvPEnabled() {

        /*
         * CKI does not own PvP state.
         *
         * Returning true means:
         *
         * "CombatService is capable of processing PvP combat."
         *
         * It does NOT enable PvP on the server.
         */
        return true;
    }

    @Override
    public void setPvPEnabled(
            boolean enabled
    ) {

        /*
         * Intentionally empty.
         *
         * PvP state belongs to another plugin/system.
         *
         * Do NOT call:
         *
         *     plugin.setPvPEnabled(...)
         *
         * because that method has intentionally been removed
         * from CombatKeepInventory.
         */
    }
}