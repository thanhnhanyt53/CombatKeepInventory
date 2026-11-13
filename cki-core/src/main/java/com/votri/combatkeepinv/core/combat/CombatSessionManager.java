package com.votri.combatkeepinv.core.combat;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CombatSessionManager {

    Optional<CombatSession> getSession(UUID playerId);

    boolean isInCombat(UUID playerId);

    CombatSession startCombat(UUID playerId);

    CombatSession startCombat(
            UUID playerId,
            UUID opponentId
    );

    CombatSession refreshCombat(UUID playerId);

    CombatSession refreshCombat(
            UUID playerId,
            UUID opponentId
    );

    /** Records the direction of the latest player-versus-player hit. */
    void recordDamage(UUID attackerId, UUID victimId, long timestamp);

    boolean endCombat(
            UUID playerId,
            CombatReason reason
    );

    boolean forceEndCombat(
            UUID playerId,
            CombatReason reason
    );

    void removeSession(UUID playerId);

    Collection<CombatSession> getActiveSessions();

    void cleanupExpiredSessions(long currentTimeMillis);

    /**
     * Atomically transitions every session whose time is up to
     * EXPIRED and removes it from the manager, returning exactly
     * the player IDs that were swept.
     *
     * <p>Unlike calling {@link #getActiveSessions()} before
     * {@link #cleanupExpiredSessions(long)}, this never misses an
     * entry: a session that is already past its expiry no longer
     * shows up in {@code getActiveSessions()} (its {@code isActive()}
     * is time-based), so a caller snapshotting that collection
     * first would see nothing to sweep and the session would stay
     * in the manager forever, taking memory with it, unless a
     * player happens to be looked up by that exact ID again
     * later.</p>
     */
    List<UUID> sweepExpired(long currentTimeMillis);
}