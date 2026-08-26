package com.votri.combatkeepinv.bukkit.combat;

import com.votri.combatkeepinv.bukkit.bridge.CombatStateBridge;
import com.votri.combatkeepinv.core.api.CombatTag;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authoritative Bukkit-side CombatTag manager.
 *
 * <p>Bukkit is the source of truth for combat state.</p>
 *
 * <p>Velocity never calculates combat state by itself.
 * It only mirrors state published by this manager through
 * {@link CombatStateBridge}.</p>
 */
public final class CombatManager {

    private final Map<UUID, CombatEntry> combat =
            new ConcurrentHashMap<>();

    private final CombatStateBridge stateBridge;

    private volatile long durationMillis;

    public CombatManager(
            long durationMillis,
            CombatStateBridge stateBridge
    ) {
        if (stateBridge == null) {
            throw new IllegalArgumentException(
                    "stateBridge cannot be null"
            );
        }

        this.stateBridge = stateBridge;

        setDurationMillis(
                durationMillis
        );
    }

    /**
     * Updates CombatTag duration.
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

    public long getDurationMillis() {
        return durationMillis;
    }

    /**
     * Creates a new CombatTag for both players.
     *
     * <p>This method replaces the existing state for both
     * participants and resets the expiration time.</p>
     */
    public void start(
            UUID attacker,
            UUID victim
    ) {

        if (!isValidPair(
                attacker,
                victim
        )) {
            return;
        }

        long expiresAt =
                System.currentTimeMillis()
                        + durationMillis;

        putPair(
                attacker,
                victim,
                expiresAt
        );

        stateBridge.publishStart(
                attacker,
                victim,
                expiresAt
        );
    }

    /**
     * Refreshes CombatTag for both players.
     */
    public void refresh(
            UUID attacker,
            UUID victim
    ) {

        if (!isValidPair(
                attacker,
                victim
        )) {
            return;
        }

        long expiresAt =
                System.currentTimeMillis()
                        + durationMillis;

        putPair(
                attacker,
                victim,
                expiresAt
        );

        stateBridge.publishRefresh(
                attacker,
                victim,
                expiresAt
        );
    }

    /**
     * Handles a valid player-vs-player hit.
     *
     * <p>This is the central CombatTag operation.</p>
     *
     * <ul>
     *     <li>If neither player is tagged: START.</li>
     *     <li>If at least one player is tagged: REFRESH.</li>
     * </ul>
     *
     * <p>Regardless of the previous state, both players receive
     * a fresh expiration timestamp.</p>
     */
    public void tag(
            UUID attacker,
            UUID victim
    ) {

        if (!isValidPair(
                attacker,
                victim
        )) {
            return;
        }

        boolean attackerInCombat =
                isInCombat(attacker);

        boolean victimInCombat =
                isInCombat(victim);

        if (attackerInCombat
                || victimInCombat) {

            refresh(
                    attacker,
                    victim
            );

        } else {

            start(
                    attacker,
                    victim
            );
        }
    }

    /**
     * Removes a player's normal CombatTag.
     */
    public boolean remove(
            UUID player
    ) {

        if (player == null) {
            return false;
        }

        CombatEntry removed =
                combat.remove(player);

        if (removed == null) {
            return false;
        }

        stateBridge.publishEnd(
                player
        );

        return true;
    }

    /**
     * Forcefully removes a player's CombatTag.
     *
     * <p>Used by integrations such as arenas, teleport systems,
     * administrative systems, reload/shutdown logic, etc.</p>
     */
    public boolean forceRemove(
            UUID player
    ) {

        if (player == null) {
            return false;
        }

        CombatEntry removed =
                combat.remove(player);

        if (removed == null) {
            return false;
        }

        stateBridge.publishForceEnd(
                player
        );

        return true;
    }

    /**
     * Checks whether a player has an active CombatTag.
     *
     * <p>Expired entries are removed immediately and END is
     * propagated to the bridge.</p>
     */
    public boolean isInCombat(
            UUID player
    ) {

        if (player == null) {
            return false;
        }

        CombatEntry entry =
                combat.get(player);

        if (entry == null) {
            return false;
        }

        long now =
                System.currentTimeMillis();

        if (entry.expiresAt <= now) {

            if (combat.remove(
                    player,
                    entry
            )) {

                stateBridge.publishEnd(
                        player
                );
            }

            return false;
        }

        return true;
    }

    /**
     * Returns the player's public CombatTag.
     */
    public CombatTag getCombatTag(
            UUID player
    ) {

        if (!isInCombat(player)) {
            return null;
        }

        CombatEntry entry =
                combat.get(player);

        if (entry == null) {
            return null;
        }

        return new CombatTagView(
                entry
        );
    }

    /**
     * Returns remaining CombatTag time.
     */
    public long getRemainingMillis(
            UUID player
    ) {

        if (player == null) {
            return 0L;
        }

        CombatEntry entry =
                combat.get(player);

        if (entry == null) {
            return 0L;
        }

        long remaining =
                entry.expiresAt
                        - System.currentTimeMillis();

        if (remaining <= 0L) {

            if (combat.remove(
                    player,
                    entry
            )) {

                stateBridge.publishEnd(
                        player
                );
            }

            return 0L;
        }

        return remaining;
    }

    /**
     * Returns remaining CombatTag time in seconds.
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
     * Removes all expired CombatTags.
     *
     * <p>This method should be executed periodically by the
     * Bukkit scheduler so Velocity receives END even when
     * nobody queries the player's state.</p>
     */
    public void cleanupExpired() {

        long now =
                System.currentTimeMillis();

        for (Map.Entry<UUID, CombatEntry> mapEntry
                : combat.entrySet()) {

            UUID player =
                    mapEntry.getKey();

            CombatEntry entry =
                    mapEntry.getValue();

            if (entry.expiresAt > now) {
                continue;
            }

            if (combat.remove(
                    player,
                    entry
            )) {

                stateBridge.publishEnd(
                        player
                );
            }
        }
    }

    /**
     * Returns the number of active CombatTags.
     */
    public int size() {

        cleanupExpired();

        return combat.size();
    }

    /**
     * Clears every CombatTag.
     *
     * <p>FORCE_END is sent before the local state is removed
     * so the proxy cannot retain stale combat state.</p>
     */
    public void clear() {

        for (UUID player
                : combat.keySet()) {

            stateBridge.publishForceEnd(
                    player
            );
        }

        combat.clear();
    }

    private void putPair(
            UUID attacker,
            UUID victim,
            long expiresAt
    ) {

        combat.put(
                attacker,
                new CombatEntry(
                        attacker,
                        victim,
                        expiresAt
                )
        );

        combat.put(
                victim,
                new CombatEntry(
                        victim,
                        attacker,
                        expiresAt
                )
        );
    }

    private boolean isValidPair(
            UUID first,
            UUID second
    ) {

        return first != null
                && second != null
                && !first.equals(second);
    }

    private static final class CombatEntry {

        private final UUID playerId;
        private final UUID opponentId;
        private final long expiresAt;

        private CombatEntry(
                UUID playerId,
                UUID opponentId,
                long expiresAt
        ) {

            this.playerId =
                    playerId;

            this.opponentId =
                    opponentId;

            this.expiresAt =
                    expiresAt;
        }
    }

    /**
     * Immutable public CombatTag view.
     */
    private static final class CombatTagView
            implements CombatTag {

        private final CombatEntry entry;

        private CombatTagView(
                CombatEntry entry
        ) {
            this.entry = entry;
        }

        @Override
        public UUID getPlayerId() {
            return entry.playerId;
        }

        @Override
        public boolean isActive() {

            return getRemainingMillis() > 0L;
        }

        @Override
        public long getRemainingMillis() {

            return Math.max(
                    0L,
                    entry.expiresAt
                            - System.currentTimeMillis()
            );
        }

        @Override
        public long getExpiresAt() {
            return entry.expiresAt;
        }

        @Override
        public UUID getLastOpponent() {
            return entry.opponentId;
        }
    }
}