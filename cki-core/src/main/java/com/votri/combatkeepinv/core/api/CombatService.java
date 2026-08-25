package com.votri.combatkeepinv.core.api;

import java.util.UUID;

/**
 * Public combat service exposed by CombatKeepInventory.
 *
 * <p>This interface represents the stable API contract for
 * external plugins. Implementations must not expose platform
 * specific classes.</p>
 */
public interface CombatService {

    /**
     * Starts combat between two players.
     *
     * <p>Both players receive or refresh a CombatTag.</p>
     *
     * @param attacker attacking player UUID
     * @param victim damaged player UUID
     * @return operation result
     */
    CombatResult startCombat(
            UUID attacker,
            UUID victim
    );

    /**
     * Refreshes combat between two players.
     *
     * <p>Unlike {@link #startCombat(UUID, UUID)}, this method
     * explicitly represents an already existing combat state
     * being refreshed.</p>
     *
     * @param attacker attacking player UUID
     * @param victim damaged player UUID
     * @return operation result
     */
    CombatResult refreshCombat(
            UUID attacker,
            UUID victim
    );

    /**
     * Ends combat for a player.
     *
     * @param player player UUID
     * @return operation result
     */
    CombatResult endCombat(
            UUID player
    );

    /**
     * Forcefully ends combat for a player.
     *
     * <p>This method exists for integrations such as arena
     * systems, teleport systems, or administrative systems
     * that explicitly need to clear CombatTag state.</p>
     *
     * @param player player UUID
     * @return operation result
     */
    CombatResult forceEndCombat(
            UUID player
    );

    /**
     * Checks whether a player currently has an active CombatTag.
     *
     * @param player player UUID
     * @return true if currently in combat
     */
    boolean isInCombat(
            UUID player
    );

    /**
     * Returns the current combat state.
     *
     * @param player player UUID
     * @return combat state
     */
    CombatState getCombatState(
            UUID player
    );

    /**
     * Returns the player's current CombatTag.
     *
     * @param player player UUID
     * @return active CombatTag, or null when no active tag exists
     */
    CombatTag getCombatTag(
            UUID player
    );

    /**
     * Returns remaining CombatTag duration.
     *
     * @param player player UUID
     * @return remaining milliseconds, or 0 when not in combat
     */
    long getRemainingCombatMillis(
            UUID player
    );

    /**
     * Returns remaining CombatTag duration in whole seconds.
     *
     * @param player player UUID
     * @return remaining seconds, or 0 when not in combat
     */
    default long getRemainingCombatSeconds(
            UUID player
    ) {

        long millis =
                getRemainingCombatMillis(
                        player
                );

        if (millis <= 0L) {
            return 0L;
        }

        return (millis + 999L) / 1000L;
    }

    /**
     * Evaluates the inventory policy for a death.
     *
     * @param player player UUID
     * @param context death context
     * @return calculated death result
     */
    DeathResult evaluateDeath(
            UUID player,
            DeathContext context
    );

    /**
     * Returns whether the combat system is enabled.
     *
     * @return true if combat system is enabled
     */
    boolean isEnabled();
}