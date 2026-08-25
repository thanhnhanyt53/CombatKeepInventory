package com.votri.combatkeepinv.bukkit.combat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the CombatTag state for every player.
 *
 * <p>CombatTag is per-player state, not a fixed attacker/victim pair.</p>
 *
 * <p>Every valid player-vs-player hit overwrites the player's
 * expiration timestamp.</p>
 */
public final class CombatManager {

    private final Map<UUID, Long> combatUntil =
            new ConcurrentHashMap<>();

    private volatile long durationMillis;

    public CombatManager(long durationMillis) {
        setDurationMillis(durationMillis);
    }

    public void setDurationMillis(long durationMillis) {
        this.durationMillis =
                Math.max(1000L, durationMillis);
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    /**
     * Creates or refreshes a CombatTag.
     */
    public void tag(UUID player) {
        if (player == null) {
            return;
        }

        long expiresAt =
                System.currentTimeMillis() + durationMillis;

        combatUntil.put(player, expiresAt);
    }

    /**
     * Creates or refreshes CombatTag for both players.
     *
     * <p>Both timestamps are independently reset.</p>
     */
    public void tag(UUID first, UUID second) {
        if (first == null || second == null) {
            return;
        }

        if (first.equals(second)) {
            return;
        }

        tag(first);
        tag(second);
    }

    /**
     * Returns true only while the player's CombatTag is active.
     */
    public boolean isInCombat(UUID player) {
        if (player == null) {
            return false;
        }

        Long expiresAt =
                combatUntil.get(player);

        if (expiresAt == null) {
            return false;
        }

        long now =
                System.currentTimeMillis();

        if (expiresAt <= now) {
            combatUntil.remove(player, expiresAt);
            return false;
        }

        return true;
    }

    /**
     * Returns remaining CombatTag time in milliseconds.
     */
    public long getRemainingMillis(UUID player) {
        if (player == null) {
            return 0L;
        }

        Long expiresAt =
                combatUntil.get(player);

        if (expiresAt == null) {
            return 0L;
        }

        long remaining =
                expiresAt - System.currentTimeMillis();

        if (remaining <= 0L) {
            combatUntil.remove(player, expiresAt);
            return 0L;
        }

        return remaining;
    }

    /**
     * Returns remaining CombatTag time in seconds,
     * rounded upward.
     */
    public long getRemainingSeconds(UUID player) {
        long remaining =
                getRemainingMillis(player);

        if (remaining <= 0L) {
            return 0L;
        }

        return (remaining + 999L) / 1000L;
    }

    /**
     * Removes a player's CombatTag.
     */
    public void remove(UUID player) {
        if (player != null) {
            combatUntil.remove(player);
        }
    }

    /**
     * Removes all expired entries.
     */
    public void cleanupExpired() {
        long now =
                System.currentTimeMillis();

        combatUntil.entrySet().removeIf(
                entry -> entry.getValue() <= now
        );
    }

    /**
     * Returns the number of active CombatTags.
     */
    public int size() {
        cleanupExpired();
        return combatUntil.size();
    }

    /**
     * Clears all CombatTags.
     */
    public void clear() {
        combatUntil.clear();
    }
}