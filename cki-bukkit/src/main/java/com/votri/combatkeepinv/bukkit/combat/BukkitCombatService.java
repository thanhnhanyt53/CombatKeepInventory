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
 * Bukkit implementation of the public CombatService API.
 *
 * <p>This class adapts the platform-independent CKI API to
 * Bukkit while keeping CombatManager as the single owner of
 * CombatTag state.</p>
 *
 * <p>CKI does not own a PvP enable/disable switch.
 * PvPManager and other PvP plugins are independent systems.</p>
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
     * SERVICE STATE
     * ==========================================================
     */

    /**
     * Returns whether CKI's combat system is enabled.
     *
     * <p>This is NOT a PvP toggle.</p>
     */
    @Override
    public boolean isEnabled() {

        return plugin.getConfig().getBoolean(
                "combat.enabled",
                true
        );
    }

    /*
     * ==========================================================
     * START COMBAT
     * ==========================================================
     */

    /**
     * Starts CombatTag for both players.
     *
     * <p>This method is intended for a valid player-originated
     * attack detected by the Bukkit event layer.</p>
     */
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

        if (isWorldDisabled(
                attacker
        ) || isWorldDisabled(
                victim
        )) {

            return CombatResult.WORLD_DISABLED;
        }

        boolean attackerAlready =
                combatManager.isInCombat(
                        attacker
                );

        boolean victimAlready =
                combatManager.isInCombat(
                        victim
                );

        /*
         * New combat state for both players.
         *
         * Each player's latest opponent is the other player.
         */
        combatManager.start(
                attacker,
                victim
        );

        combatManager.start(
                victim,
                attacker
        );

        /*
         * If both were already tagged, this operation is
         * semantically a refresh rather than a new combat.
         *
         * The actual timer has still been reset.
         */
        if (attackerAlready
                && victimAlready) {

            return CombatResult.ALREADY_IN_COMBAT;
        }

        return CombatResult.SUCCESS;
    }

    /*
     * ==========================================================
     * REFRESH COMBAT
     * ==========================================================
     */

    /**
     * Refreshes CombatTag for both players.
     *
     * <p>Every valid player hit should call this method when
     * both participants are already in combat.</p>
     */
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

        if (isWorldDisabled(
                attacker
        ) || isWorldDisabled(
                victim
        )) {

            return CombatResult.WORLD_DISABLED;
        }

        combatManager.refresh(
                attacker,
                victim
        );

        combatManager.refresh(
                victim,
                attacker
        );

        return CombatResult.SUCCESS;
    }

    /*
     * ==========================================================
     * END COMBAT
     * ==========================================================
     */

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

    /**
     * Forcefully removes CombatTag.
     *
     * <p>This method intentionally behaves like endCombat,
     * but semantically communicates that the caller is
     * deliberately overriding the combat state.</p>
     */
    @Override
    public CombatResult forceEndCombat(
            UUID player
    ) {

        if (player == null) {
            return CombatResult.INVALID_ARGUMENT;
        }

        boolean active =
                combatManager.isInCombat(
                        player
                );

        combatManager.remove(
                player
        );

        return active
                ? CombatResult.SUCCESS
                : CombatResult.NOT_IN_COMBAT;
    }

    /*
     * ==========================================================
     * STATE
     * ==========================================================
     */

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

        if (!isInCombat(
                player
        )) {

            return CombatState.SAFE;
        }

        return CombatState.IN_COMBAT;
    }

    @Override
    public CombatTag getCombatTag(
            UUID player
    ) {

        if (player == null) {
            return null;
        }

        return combatManager.getCombatTag(
                player
        );
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
     * DEATH POLICY
     * ==========================================================
     */

    /**
     * Evaluates the inventory policy for a death.
     *
     * <p>The order is intentionally:</p>
     *
     * <ol>
     *     <li>Check active CombatTag.</li>
     *     <li>If active -> DROP.</li>
     *     <li>Only otherwise inspect DeathContext.</li>
     * </ol>
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
         * STEP 1 — COMBAT TAG
         * ======================================================
         */

        if (combatManager.isInCombat(
                player
        )) {

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
         * STEP 2 — FINAL DEATH CONTEXT
         * ======================================================
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
         * STEP 3 — PVE / ENVIRONMENT
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
     * HELPERS
     * ==========================================================
     */

    private boolean isValidPair(
            UUID attacker,
            UUID victim
    ) {

        return attacker != null
                && victim != null
                && !attacker.equals(victim);
    }

    /**
     * Checks whether the player's current Bukkit world is
     * disabled for CKI.
     *
     * <p>The UUID itself cannot determine the world, therefore
     * Bukkit's player registry is used here.</p>
     */
    private boolean isWorldDisabled(
            UUID player
    ) {

        if (player == null) {
            return true;
        }

        org.bukkit.entity.Player bukkitPlayer =
                plugin.getServer()
                        .getPlayer(
                                player
                        );

        if (bukkitPlayer == null) {
            return false;
        }

        return plugin.isWorldDisabled(
                bukkitPlayer.getWorld()
        );
    }
}