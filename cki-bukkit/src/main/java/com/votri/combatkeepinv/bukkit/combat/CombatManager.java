package com.votri.combatkeepinv.bukkit.combat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores the active Combat Tag state of players.
 *
 * <p>The Combat Tag is the source of truth for CKI's
 * death policy.</p>
 *
 * <p>Rules:</p>
 * <ul>
 *     <li>A valid PvP hit creates or refreshes the tag.</li>
 *     <li>Every subsequent valid PvP hit refreshes the tag.</li>
 *     <li>When the tag expires, the player is no longer in combat.</li>
 *     <li>Death handling is performed by BukkitCombatService.</li>
 * </ul>
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
                Math.max(
                        1000L,
                        durationMillis
                );
    }

    /**
     * Creates or refreshes the Combat Tag.
     */
    public void tag(UUID uuid) {

        if (uuid == null) {
            return;
        }

        long until =
                System.currentTimeMillis()
                        + durationMillis;

        combatUntil.put(
                uuid,
                until
        );
    }

    /**
     * Creates or refreshes Combat Tags for both players.
     */
    public void tag(
            UUID first,
            UUID second
    ) {
        tag(first);
        tag(second);
    }

    /**
     * Returns whether the player currently has an active
     * Combat Tag.
     */
    public boolean isInCombat(UUID uuid) {

        if (uuid == null) {
            return false;
        }

        Long until =
                combatUntil.get(uuid);

        if (until == null) {
            return false;
        }

        if (until <= System.currentTimeMillis()) {

            combatUntil.remove(
                    uuid,
                    until
            );

            return false;
        }

        return true;
    }

    /**
     * Returns the remaining Combat Tag duration.
     */
    public long getRemainingMillis(UUID uuid) {

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
     * Returns the remaining Combat Tag duration in seconds,
     * rounded upward.
     */
    public long getRemainingSeconds(UUID uuid) {

        long millis =
                getRemainingMillis(uuid);

        if (millis <= 0L) {
            return 0L;
        }

        return (
                millis + 999L
        ) / 1000L;
    }

    /**
     * Removes a player from Combat Tag state.
     */
    public void remove(UUID uuid) {

        if (uuid != null) {
            combatUntil.remove(uuid);
        }
    }

    /**
     * Removes expired Combat Tags.
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
     * Returns the number of currently active tags.
     */
    public int size() {

        cleanupExpired();

        return combatUntil.size();
    }

    /**
     * Clears all Combat Tags.
     */
    public void clear() {
        combatUntil.clear();
    }
}