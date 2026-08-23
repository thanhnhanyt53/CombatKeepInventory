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
 * Bukkit implementation of the platform-neutral CombatService.
 *
 * <p>CombatManager is the source of truth for combat-tag state.</p>
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

        if (attacker == null
                || victim == null
                || attacker.equals(victim)) {

            return CombatResult.INVALID_ARGUMENT;
        }

        if (!plugin.isPvPEnabled()) {
            return CombatResult.DISABLED;
        }

        boolean attackerWasInCombat =
                combatManager.isInCombat(attacker);

        boolean victimWasInCombat =
                combatManager.isInCombat(victim);

        combatManager.tag(
                attacker,
                victim
        );

        if (attackerWasInCombat
                || victimWasInCombat) {

            return CombatResult.ALREADY_IN_COMBAT;
        }

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

        return combatManager.isInCombat(
                player
        );
    }

    @Override
    public CombatState getCombatState(
            UUID player
    ) {

        if (player == null) {
            return CombatState.SAFE;
        }

        if (!combatManager.isInCombat(player)) {
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

    /**
     * Evaluates the final inventory policy.
     *
     * <p>Important rule:</p>
     *
     * <ul>
     *     <li>If the player is still combat-tagged, the death is treated
     *     as a combat death regardless of whether the final damage came
     *     from PvE, fall damage, explosion, fire, etc.</li>
     *
     *     <li>If the combat tag has already expired, the actual final
     *     death context decides whether the death is PvP.</li>
     * </ul>
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

        boolean useCombatState =
                plugin.getConfig().getBoolean(
                        "pvp.use-combat-state",
                        true
                );

        boolean inCombat =
                combatManager.isInCombat(
                        player
                );

        /*
         * ==========================================================
         * COMBAT TAG HAS PRIORITY
         * ==========================================================
         *
         * While the combat tag is active, ANY death causes item loss.
         */
        if (useCombatState && inCombat) {

            boolean keepExperience =
                    !plugin.getConfig().getBoolean(
                            "inventory.drop-experience",
                            true
                    );

            return new DeathResult(
                    InventoryPolicy.DROP,
                    keepExperience,
                    true
            );
        }

        /*
         * ==========================================================
         * NO COMBAT TAG
         * ==========================================================
         *
         * Once the combat tag has expired, only the final death
         * context determines whether this is a PvP death.
         */
        boolean pvpDeath =
                context == DeathContext.PLAYER
                        || context == DeathContext.PROJECTILE;

        if (pvpDeath) {

            boolean keepExperience =
                    !plugin.getConfig().getBoolean(
                            "inventory.drop-experience",
                            true
                    );

            boolean keepInventory =
                    plugin.getConfig().getBoolean(
                            "death.keep-on-pvp",
                            false
                    );

            return new DeathResult(
                    keepInventory
                            ? InventoryPolicy.KEEP
                            : InventoryPolicy.DROP,
                    keepExperience,
                    true
            );
        }

        /*
         * ==========================================================
         * PVE / ENVIRONMENT
         * ==========================================================
         */

        boolean keepInventory =
                plugin.getConfig().getBoolean(
                        "death.keep-on-pve",
                        true
                );

        return new DeathResult(
                keepInventory
                        ? InventoryPolicy.KEEP
                        : InventoryPolicy.DROP,
                true,
                false
        );
    }

    @Override
    public boolean isPvPEnabled() {

        return plugin.isPvPEnabled();
    }

    @Override
    public void setPvPEnabled(
            boolean enabled
    ) {

        plugin.setPvPEnabled(
                enabled
        );
    }
}