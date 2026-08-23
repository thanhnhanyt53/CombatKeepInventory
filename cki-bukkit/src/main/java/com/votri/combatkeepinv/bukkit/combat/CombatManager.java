package com.votri.combatkeepinv.bukkit.combat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores the active CombatTag state.
 *
 * <p>CombatManager is the source of truth for whether a player
 * is currently in CombatTag.</p>
 */
public final class CombatManager {

    private final Map<UUID, Long> combatUntil =
            new ConcurrentHashMap<>();

    private volatile long durationMillis;

    public CombatManager(
            long durationMillis
    ) {
        setDurationMillis(durationMillis);
    }

    /**
     * Sets the CombatTag duration.
     *
     * @param durationMillis duration in milliseconds
     */
    public void setDurationMillis(
            long durationMillis
    ) {
        this.durationMillis =
                Math.max(
                        1000L,
                        durationMillis
                );
    }

    /**
     * Returns the configured CombatTag duration.
     */
    public long getDurationMillis() {
        return durationMillis;
    }

    /**
     * Creates or refreshes a CombatTag.
     */
    public void tag(
            UUID uuid
    ) {
        if (uuid == null) {
            return;
        }

        long now =
                System.currentTimeMillis();

        combatUntil.put(
                uuid,
                now + durationMillis
        );
    }

    /**
     * Creates or refreshes CombatTag for both players.
     */
    public void tag(
            UUID first,
            UUID second
    ) {
        if (first == null
                || second == null) {
            return;
        }

        if (first.equals(second)) {
            return;
        }

        tag(first);
        tag(second);
    }

    /**
     * Returns whether the player is currently CombatTagged.
     *
     * <p>Expired entries are removed atomically.</p>
     */
    public boolean isInCombat(
            UUID uuid
    ) {
        if (uuid == null) {
            return false;
        }

        Long until =
                combatUntil.get(uuid);

        if (until == null) {
            return false;
        }

        long now =
                System.currentTimeMillis();

        if (until <= now) {

            combatUntil.remove(
                    uuid,
                    until
            );

            return false;
        }

        return true;
    }

    /**
     * Returns the remaining CombatTag duration in milliseconds.
     */
    public long getRemainingMillis(
            UUID uuid
    ) {
        if (uuid == null) {
            return 0L;
        }

        Long until =
                combatUntil.get(uuid);

        if (until == null) {
            return 0L;
        }

        long remaining =
                until
                        - System.currentTimeMillis();

        if (remaining <= 0L) {

            combatUntil.remove(
                    uuid,
                    until
            );

            return 0L;
        }

        return remaining;
    }

    /**
     * Returns remaining CombatTag duration in seconds,
     * rounded up.
     */
    public long getRemainingSeconds(
            UUID uuid
    ) {
        long remaining =
                getRemainingMillis(uuid);

        if (remaining <= 0L) {
            return 0L;
        }

        return (
                remaining + 999L
        ) / 1000L;
    }

    /**
     * Removes a player's CombatTag.
     */
    public void remove(
            UUID uuid
    ) {
        if (uuid != null) {
            combatUntil.remove(uuid);
        }
    }

    /**
     * Removes all expired entries.
     */
    public void cleanupExpired() {

        long now =
                System.currentTimeMillis();

        combatUntil.entrySet()
                .removeIf(
                        entry ->
                                entry.getValue()
                                        <= now
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
     * Removes every CombatTag.
     */
    public void clear() {
        combatUntil.clear();
    }
}