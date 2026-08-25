package com.votri.combatkeepinv.core.api;

/**
 * Result of a CombatKeepInventory combat operation.
 */
public enum CombatResult {

    /**
     * Operation completed successfully.
     */
    SUCCESS,

    /**
     * CombatTag was already active and the operation
     * did not require creating a new combat state.
     */
    ALREADY_IN_COMBAT,

    /**
     * Player does not currently have an active CombatTag.
     */
    NOT_IN_COMBAT,

    /**
     * Player UUID could not be resolved by the implementation.
     */
    PLAYER_NOT_FOUND,

    /**
     * One or more supplied arguments were invalid.
     */
    INVALID_ARGUMENT,

    /**
     * Combat system is disabled.
     */
    DISABLED,

    /**
     * Combat is disabled in the relevant world.
     */
    WORLD_DISABLED,

    /**
     * The player is immune to combat tagging.
     */
    IMMUNE,

    /**
     * Operation failed for an implementation-specific reason.
     */
    FAILED
}