package com.votri.combatkeepinv.bukkit.combat;

import com.votri.combatkeepinv.bukkit.bridge.CombatStateBridge;
import com.votri.combatkeepinv.core.api.CombatTag;
import com.votri.combatkeepinv.core.combat.CombatReason;
import com.votri.combatkeepinv.core.combat.CombatSession;
import com.votri.combatkeepinv.core.combat.CombatSessionManager;
import com.votri.combatkeepinv.core.internal.DefaultCombatSessionManager;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Bukkit compatibility facade over the new core CombatSessionManager.
 *
 * <p>The actual combat state is owned by the core contract.
 * This class remains only as the Bukkit-facing compatibility layer
 * and Velocity bridge publisher.</p>
 */
public final class CombatManager {

    private final CombatStateBridge stateBridge;

    private volatile long durationMillis;
    private volatile CombatSessionManager sessionManager;

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
        setDurationMillis(durationMillis);
    }

    /**
     * Recreates the underlying core session manager using
     * the configured combat duration.
     *
     * <p>This method is mainly used during startup/reload.
     * Runtime callers should not normally change the duration.</p>
     */
    public synchronized void setDurationMillis(
            long durationMillis
    ) {
        long normalized =
                Math.max(
                        1000L,
                        durationMillis
                );

        this.durationMillis = normalized;

        CombatSessionManager oldManager =
                this.sessionManager;

        this.sessionManager =
                new DefaultCombatSessionManager(
                        normalized
                );

        /*
         * The old implementation did not expose a safe way
         * to mutate the duration of the core manager.
         *
         * Therefore the new manager is created here.
         *
         * This is intentionally done only when configuration
         * is initialized or reloaded.
         */
        if (oldManager != null) {
            // Existing sessions are intentionally not copied.
            // Combat state should not survive a configuration reload.
        }
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    /**
     * Returns the underlying core CombatSessionManager.
     */
    public CombatSessionManager getSessionManager() {
        return sessionManager;
    }

    /**
     * Starts combat for both participants.
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

        CombatSession attackerSession =
                sessionManager.startCombat(
                        attacker,
                        victim
                );

        sessionManager.startCombat(
                victim,
                attacker
        );

        stateBridge.publishStart(
                attacker,
                victim,
                attackerSession.getExpiresAt()
        );
    }

    /**
     * Refreshes combat for both participants.
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

        CombatSession attackerSession =
                sessionManager.refreshCombat(
                        attacker,
                        victim
                );

        sessionManager.refreshCombat(
                victim,
                attacker
        );

        stateBridge.publishRefresh(
                attacker,
                victim,
                attackerSession.getExpiresAt()
        );
    }

    /**
     * Handles a valid player-versus-player hit.
     *
     * <p>If one of the participants is already in combat,
     * the pair is refreshed. Otherwise a new session is created.</p>
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
     * Ends combat normally.
     */
    public boolean remove(
            UUID player
    ) {
        if (player == null) {
            return false;
        }

        boolean ended =
                sessionManager.endCombat(
                        player,
                        CombatReason.PLAYER_DEATH
                );

        if (!ended) {
            /*
             * If the session is no longer active but is still
             * present in the manager, remove it explicitly.
             */
            sessionManager.removeSession(player);
            return false;
        }

        stateBridge.publishEnd(player);

        sessionManager.removeSession(player);

        return true;
    }

    /**
     * Forcefully ends combat.
     */
    public boolean forceRemove(
            UUID player
    ) {
        if (player == null) {
            return false;
        }

        boolean ended =
                sessionManager.forceEndCombat(
                        player,
                        CombatReason.ADMIN
                );

        if (!ended) {
            sessionManager.removeSession(player);
            return false;
        }

        stateBridge.publishForceEnd(player);

        sessionManager.removeSession(player);

        return true;
    }

    /**
     * Returns whether the player currently has active combat.
     */
    public boolean isInCombat(
            UUID player
    ) {
        if (player == null) {
            return false;
        }

        boolean active =
                sessionManager.isInCombat(player);

        /*
         * Synchronize timeout state with Velocity.
         */
        if (!active) {
            Optional<CombatSession> session =
                    sessionManager.getSession(player);

            if (session.isPresent()
                    && session.get().isExpired()) {

                stateBridge.publishEnd(player);
                sessionManager.removeSession(player);
            }
        }

        return active;
    }

    /**
     * Returns a compatibility CombatTag backed by the core session.
     */
    public CombatTag getCombatTag(
            UUID player
    ) {
        if (!isInCombat(player)) {
            return null;
        }

        CombatSession session =
                sessionManager.getSession(player)
                        .orElse(null);

        if (session == null
                || !session.isActive()) {
            return null;
        }

        return new CoreCombatTagView(session);
    }

    /**
     * Returns remaining combat time.
     */
    public long getRemainingMillis(
            UUID player
    ) {
        if (player == null) {
            return 0L;
        }

        CombatSession session =
                sessionManager.getSession(player)
                        .orElse(null);

        if (session == null
                || !session.isActive()) {

            return 0L;
        }

        long remaining =
                session.getRemainingMillis();

        if (remaining <= 0L) {
            if (sessionManager.endCombat(
                    player,
                    CombatReason.TIMEOUT
            )) {
                stateBridge.publishEnd(player);
                sessionManager.removeSession(player);
            }

            return 0L;
        }

        return remaining;
    }

    /**
     * Returns remaining combat time in seconds.
     */
    public long getRemainingSeconds(
            UUID player
    ) {
        long remaining =
                getRemainingMillis(player);

        if (remaining <= 0L) {
            return 0L;
        }

        return (
                remaining + 999L
        ) / 1000L;
    }

    /**
     * Cleans up expired core sessions.
     */
    public void cleanupExpired() {
        long now =
                System.currentTimeMillis();

        Collection<CombatSession> before =
                sessionManager.getActiveSessions();

        sessionManager.cleanupExpiredSessions(now);

        for (CombatSession previous : before) {

            Optional<CombatSession> current =
                    sessionManager.getSession(
                            previous.getPlayerId()
                    );

            if (current.isPresent()
                    && current.get().getState()
                    == com.votri.combatkeepinv.core.combat.CombatState.EXPIRED) {

                stateBridge.publishEnd(
                        previous.getPlayerId()
                );

                sessionManager.removeSession(
                        previous.getPlayerId()
                );
            }
        }
    }

    /**
     * Returns number of active sessions.
     */
    public int size() {
        cleanupExpired();

        return sessionManager
                .getActiveSessions()
                .size();
    }

    /**
     * Clears all sessions and mirrors FORCE_END to Velocity.
     */
    public void clear() {
        Collection<CombatSession> sessions =
                sessionManager.getActiveSessions();

        for (CombatSession session : sessions) {

            stateBridge.publishForceEnd(
                    session.getPlayerId()
            );

            sessionManager.forceEndCombat(
                    session.getPlayerId(),
                    CombatReason.SYSTEM
            );

            sessionManager.removeSession(
                    session.getPlayerId()
            );
        }
    }

    private boolean isValidPair(
            UUID first,
            UUID second
    ) {
        return first != null
                && second != null
                && !first.equals(second);
    }

    /**
     * Compatibility view for the old Bukkit CombatTag API.
     */
    private static final class CoreCombatTagView
            implements CombatTag {

        private final CombatSession session;

        private CoreCombatTagView(
                CombatSession session
        ) {
            this.session = session;
        }

        @Override
        public UUID getPlayerId() {
            return session.getPlayerId();
        }

        @Override
        public boolean isActive() {
            return session.isActive();
        }

        @Override
        public long getRemainingMillis() {
            return session.getRemainingMillis();
        }

        @Override
        public long getExpiresAt() {
            return session.getExpiresAt();
        }

        @Override
        public UUID getLastOpponent() {
            return session.getOpponentId()
                    .orElse(null);
        }
    }
}