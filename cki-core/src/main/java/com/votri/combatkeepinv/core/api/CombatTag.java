package com.votri.combatkeepinv.core.api;

import java.util.UUID;

/**
 * Immutable public representation of a player's CombatTag.
 *
 * <p>The implementation is responsible for maintaining the
 * actual timer. External plugins only receive a read-only
 * representation.</p>
 */
public interface CombatTag {

    /**
     * Returns the player owning this CombatTag.
     *
     * @return player UUID
     */
    UUID getPlayerId();

    /**
     * Returns whether this tag is currently active.
     *
     * @return true if active
     */
    boolean isActive();

    /**
     * Returns remaining time in milliseconds.
     *
     * @return remaining milliseconds
     */
    long getRemainingMillis();

    /**
     * Returns remaining time in whole seconds.
     *
     * @return remaining seconds
     */
    default long getRemainingSeconds() {

        long millis =
                getRemainingMillis();

        if (millis <= 0L) {
            return 0L;
        }

        return (millis + 999L) / 1000L;
    }

    /**
     * Returns the absolute expiration timestamp.
     *
     * <p>The value uses {@link System#currentTimeMillis()}
     * semantics.</p>
     *
     * @return expiration timestamp
     */
    long getExpiresAt();

    /**
     * Returns the UUID of the player's most recent opponent.
     *
     * @return opponent UUID, or null when unavailable
     */
    UUID getLastOpponent();

    /**
     * Returns whether a last opponent is available.
     *
     * @return true if an opponent exists
     */
    default boolean hasOpponent() {
        return getLastOpponent() != null;
    }
}