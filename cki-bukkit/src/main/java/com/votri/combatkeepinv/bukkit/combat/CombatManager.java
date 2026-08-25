package com.votri.combatkeepinv.bukkit.combat;

import com.votri.combatkeepinv.core.api.CombatTag;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the runtime CombatTag state.
 *
 * <p>CombatManager is the single source of truth for Bukkit's
 * active CombatTag state.</p>
 *
 * <p>The manager does not know anything about Bukkit events,
 * PvPManager, WorldGuard or death handling.</p>
 */
public final class CombatManager {

    private final Map<UUID, CombatEntry> combatEntries =
            new ConcurrentHashMap<>();

    private volatile long durationMillis;

    public CombatManager(
            long durationMillis
    ) {
        setDurationMillis(durationMillis);
    }

    /**
     * Sets the duration used for newly created or refreshed tags.
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
     *
     * @return duration in milliseconds
     */
    public long getDurationMillis() {
        return durationMillis;
    }

    /**
     * Starts a new CombatTag for one player.
     *
     * <p>If the player already has a tag, the existing tag is
     * refreshed instead.</p>
     *
     * @param player player UUID
     * @param opponent opponent UUID
     */
    public void start(
            UUID player,
            UUID opponent
    ) {

        if (player == null) {
            return;
        }

        long expiresAt =
                System.currentTimeMillis()
                        + durationMillis;

        combatEntries.put(
                player,
                new CombatEntry(
                        player,
                        opponent,
                        expiresAt
                )
        );
    }

    /**
     * Refreshes an existing CombatTag.
     *
     * <p>If no active tag exists, this method creates one.
     * This makes the manager resilient to race/expiry boundaries.</p>
     *
     * @param player player UUID
     * @param opponent latest opponent UUID
     */
    public void refresh(
            UUID player,
            UUID opponent
    ) {

        if (player == null) {
            return;
        }

        long now =
                System.currentTimeMillis();

        long expiresAt =
                now + durationMillis;

        combatEntries.put(
                player,
                new CombatEntry(
                        player,
                        opponent,
                        expiresAt
                )
        );
    }

    /**
     * Starts or refreshes CombatTag for both players.
     *
     * <p>This is retained as the low-level operation used by
     * Bukkit combat handling.</p>
     *
     * @param first first player
     * @param second second player
     */
    public void tag(
            UUID first,
            UUID second
    ) {

        if (first == null
                || second == null
                || first.equals(second)) {

            return;
        }

        start(
                first,
                second
        );

        start(
                second,
                first
        );
    }

    /**
     * Returns whether the player currently has an active tag.
     *
     * @param player player UUID
     * @return true when active
     */
    public boolean isInCombat(
            UUID player
    ) {

        return getEntry(player) != null;
    }

    /**
     * Returns the public CombatTag representation.
     *
     * @param player player UUID
     * @return active tag, or null
     */
    public CombatTag getCombatTag(
            UUID player
    ) {

        CombatEntry entry =
                getEntry(player);

        if (entry == null) {
            return null;
        }

        return entry;
    }

    /**
     * Returns the remaining duration.
     *
     * @param player player UUID
     * @return remaining milliseconds
     */
    public long getRemainingMillis(
            UUID player
    ) {

        CombatEntry entry =
                getEntry(player);

        if (entry == null) {
            return 0L;
        }

        long remaining =
                entry.expiresAt
                        - System.currentTimeMillis();

        if (remaining <= 0L) {

            combatEntries.remove(
                    player,
                    entry
            );

            return 0L;
        }

        return remaining;
    }

    /**
     * Returns remaining duration in seconds, rounded up.
     *
     * @param player player UUID
     * @return remaining seconds
     */
    public long getRemainingSeconds(
            UUID player
    ) {

        long remaining =
                getRemainingMillis(
                        player
                );

        if (remaining <= 0L) {
            return 0L;
        }

        return (
                remaining + 999L
        ) / 1000L;
    }

    /**
     * Returns the last opponent.
     *
     * @param player player UUID
     * @return opponent UUID, or null
     */
    public UUID getLastOpponent(
            UUID player
    ) {

        CombatEntry entry =
                getEntry(player);

        if (entry == null) {
            return null;
        }

        return entry.lastOpponent;
    }

    /**
     * Removes a player's CombatTag.
     *
     * @param player player UUID
     */
    public void remove(
            UUID player
    ) {

        if (player != null) {

            combatEntries.remove(
                    player
            );
        }
    }

    /**
     * Removes all expired CombatTags.
     */
    public void cleanupExpired() {

        long now =
                System.currentTimeMillis();

        combatEntries.entrySet()
                .removeIf(
                        entry ->
                                entry.getValue()
                                        .expiresAt
                                        <= now
                );
    }

    /**
     * Returns the number of active CombatTags.
     *
     * @return active tag count
     */
    public int size() {

        cleanupExpired();

        return combatEntries.size();
    }

    /**
     * Removes every CombatTag.
     */
    public void clear() {

        combatEntries.clear();
    }

    /**
     * Gets an active entry.
     *
     * <p>Expired entries are removed atomically.</p>
     */
    private CombatEntry getEntry(
            UUID player
    ) {

        if (player == null) {
            return null;
        }

        CombatEntry entry =
                combatEntries.get(player);

        if (entry == null) {
            return null;
        }

        long now =
                System.currentTimeMillis();

        if (entry.expiresAt <= now) {

            combatEntries.remove(
                    player,
                    entry
            );

            return null;
        }

        return entry;
    }

    /**
     * Immutable runtime representation of a CombatTag.
     */
    private static final class CombatEntry
            implements CombatTag {

        private final UUID playerId;
        private final UUID lastOpponent;
        private final long expiresAt;

        private CombatEntry(
                UUID playerId,
                UUID lastOpponent,
                long expiresAt
        ) {

            this.playerId =
                    playerId;

            this.lastOpponent =
                    lastOpponent;

            this.expiresAt =
                    expiresAt;
        }

        @Override
        public UUID getPlayerId() {
            return playerId;
        }

        @Override
        public boolean isActive() {

            return expiresAt >
                    System.currentTimeMillis();
        }

        @Override
        public long getRemainingMillis() {

            long remaining =
                    expiresAt
                            - System.currentTimeMillis();

            return Math.max(
                    0L,
                    remaining
            );
        }

        @Override
        public long getExpiresAt() {
            return expiresAt;
        }

        @Override
        public UUID getLastOpponent() {
            return lastOpponent;
        }
    }
}