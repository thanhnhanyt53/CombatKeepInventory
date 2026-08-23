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

        if (!plugin.getConfig().getBoolean(
                "combat.enabled",
                true
        )) {
            return CombatResult.DISABLED;
        }

        /*
         * Every valid PvP hit refreshes the combat timer
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
        return combatManager.getRemainingMillis(player);
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
         * ACTIVE COMBAT TAG
         * ======================================================
         *
         * While CombatTag is active, ANY death causes
         * inventory loss.
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
         * Only the final PvP attribution matters now.
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
         */

        boolean keep =
                plugin.getConfig().getBoolean(
                        "death.pve-keeps-inventory",
                        true
                );

        return new DeathResult(
                keep
                        ? InventoryPolicy.KEEP
                        : InventoryPolicy.DROP,
                plugin.getConfig().getBoolean(
                        "inventory.keep-experience",
                        true
                ),
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
        plugin.setPvPEnabled(enabled);
    }
}