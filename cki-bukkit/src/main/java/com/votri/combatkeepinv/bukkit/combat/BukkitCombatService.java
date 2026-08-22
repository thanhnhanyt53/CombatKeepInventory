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
 * Bukkit implementation of the platform-neutral CombatService API.
 *
 * <p>This class owns the CKI combat/death decision layer while
 * CombatManager remains responsible only for combat timing state.</p>
 */
public final class BukkitCombatService implements CombatService {

    private final CombatKeepInventory plugin;
    private final CombatManager combatManager;

    public BukkitCombatService(
            CombatKeepInventory plugin,
            CombatManager combatManager
    ) {
        this.plugin = Objects.requireNonNull(
                plugin,
                "plugin"
        );

        this.combatManager = Objects.requireNonNull(
                combatManager,
                "combatManager"
        );
    }

    @Override
    public CombatResult startCombat(
            UUID attacker,
            UUID victim
    ) {
        if (attacker == null || victim == null) {
            return CombatResult.INVALID_ARGUMENT;
        }

        if (attacker.equals(victim)) {
            return CombatResult.INVALID_ARGUMENT;
        }

        if (!plugin.isPvPEnabled()) {
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
    public boolean isInCombat(
            UUID player
    ) {
        if (player == null) {
            return false;
        }

        return combatManager.isInCombat(player);
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
        if (player == null) {
            return 0L;
        }

        return combatManager.getRemainingMillis(player);
    }

    /**
     * Evaluates the inventory policy for a death.
     *
     * <p>Important CKI rule:</p>
     *
     * <ul>
     *     <li>PLAYER = PvP death = DROP by default.</li>
     *     <li>PROJECTILE = only considered PvP when the listener
     *         explicitly classified the projectile as player-owned.</li>
     *     <li>MOB = PvE = KEEP by default.</li>
     *     <li>ENVIRONMENT = PvE = KEEP by default.</li>
     *     <li>VOID = PvE = KEEP by default.</li>
     *     <li>UNKNOWN = non-combat = KEEP by default.</li>
     * </ul>
     *
     * <p>The combat timer itself is deliberately NOT used to turn
     * a PvE death into a PvP death.</p>
     */
    @Override
    public DeathResult evaluateDeath(
            UUID player,
            DeathContext context
    ) {
        if (player == null || context == null) {
            return new DeathResult(
                    InventoryPolicy.DEFAULT,
                    shouldKeepExperience(),
                    false
            );
        }

        /*
         * Direct player kill.
         *
         * This is the primary CKI rule:
         *
         * PLAYER -> DROP
         */
        if (context == DeathContext.PLAYER) {

            boolean drop =
                    plugin.getConfig().getBoolean(
                            "death.player-kill-drops",
                            true
                    );

            return new DeathResult(
                    drop
                            ? InventoryPolicy.DROP
                            : InventoryPolicy.KEEP,
                    shouldKeepExperience(),
                    drop
            );
        }

        /*
         * A projectile classified as PLAYER by the Bukkit
         * listener is also a PvP death.
         *
         * The listener is responsible for deciding whether
         * the projectile belongs to a player.
         */
        if (context == DeathContext.PROJECTILE) {

            boolean drop =
                    plugin.getConfig().getBoolean(
                            "death.player-kill-drops",
                            true
                    );

            return new DeathResult(
                    drop
                            ? InventoryPolicy.DROP
                            : InventoryPolicy.KEEP,
                    shouldKeepExperience(),
                    drop
            );
        }

        /*
         * MOB / ENVIRONMENT / VOID / UNKNOWN
         *
         * These are not automatically converted to PvP deaths
         * just because the player happened to have an active
         * combat timer.
         */
        boolean keep =
                plugin.getConfig().getBoolean(
                        "death.non-combat-death-keeps-inventory",
                        true
                );

        return new DeathResult(
                keep
                        ? InventoryPolicy.KEEP
                        : InventoryPolicy.DROP,
                shouldKeepExperience(),
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

    private boolean shouldKeepExperience() {
        return plugin.getConfig().getBoolean(
                "death.keep-experience",
                true
        );
    }
}