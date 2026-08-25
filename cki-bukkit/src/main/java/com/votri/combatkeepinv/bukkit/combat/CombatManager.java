package com.votri.combatkeepinv.bukkit.combat;

import com.votri.combatkeepinv.bukkit.bridge.CombatStateBridge;
import com.votri.combatkeepinv.core.api.CombatTag;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the authoritative Bukkit CombatTag state.
 *
 * <p>
 * CombatTag is per-player state.
 * Every valid player-vs-player hit resets the expiration time.
 * </p>
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

        this.stateBridge =
                stateBridge;

        setDurationMillis(
                durationMillis
        );
    }

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
     * Creates a new combat state.
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

        stateBridge.publishStart(
                attacker,
                victim,
                expiresAt
        );
    }

    /**
     * Refreshes an existing combat state.
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

        stateBridge.publishRefresh(
                attacker,
                victim,
                expiresAt
        );
    }

    /**
     * Creates or refreshes a tag.
     *
     * <p>
     * This method is kept as the central operation used by
     * the Bukkit combat layer.
     * </p>
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
                && victimInCombat) {

            refresh(
                    attacker,
                    victim
            );

            return;
        }

        start(
                attacker,
                victim
        );
    }

    /**
     * Removes a normal combat state.
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
     * Forcefully removes a combat state.
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
     * Checks whether the player has an active CombatTag.
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
     * Returns the current public CombatTag.
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
     * Removes expired states and synchronizes END to Velocity.
     */
    public void cleanupExpired() {

        long now =
                System.currentTimeMillis();

        for (Map.Entry<UUID, CombatEntry> entry
                : combat.entrySet()) {

            CombatEntry state =
                    entry.getValue();

            if (state.expiresAt > now) {
                continue;
            }

            if (combat.remove(
                    entry.getKey(),
                    state
            )) {

                stateBridge.publishEnd(
                        entry.getKey()
                );
            }
        }
    }

    public int size() {

        cleanupExpired();

        return combat.size();
    }

    public void clear() {

        combat.clear();
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

    private static final class CombatTagView
            implements CombatTag {

        private final CombatEntry entry;

        private CombatTagView(
                CombatEntry entry
        ) {

            this.entry =
                    entry;
        }

        @Override
        public UUID getPlayerId() {
            return entry.playerId;
        }

        @Override
        public boolean isActive() {

            return getRemainingMillis()
                    > 0L;
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