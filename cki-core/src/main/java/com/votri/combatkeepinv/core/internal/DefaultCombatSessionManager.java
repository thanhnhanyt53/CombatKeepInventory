package com.votri.combatkeepinv.core.internal;

import com.votri.combatkeepinv.core.combat.CombatReason;
import com.votri.combatkeepinv.core.combat.CombatSession;
import com.votri.combatkeepinv.core.combat.CombatSessionManager;
import com.votri.combatkeepinv.core.combat.CombatState;

import java.util.ArrayList;
import java.util.List;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultCombatSessionManager
        implements CombatSessionManager {

    private final Map<UUID, DefaultCombatSession> sessions =
            new ConcurrentHashMap<>();

    private final long durationMillis;

    public DefaultCombatSessionManager(
            long durationMillis
    ) {
        if (durationMillis <= 0) {
            throw new IllegalArgumentException(
                    "durationMillis must be greater than zero"
            );
        }

        this.durationMillis = durationMillis;
    }

    @Override
    public Optional<CombatSession> getSession(UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    @Override
    public boolean isInCombat(UUID playerId) {
        DefaultCombatSession session = sessions.get(playerId);

        return session != null && session.isActive();
    }

    @Override
    public CombatSession startCombat(UUID playerId) {
        return startCombat(playerId, null);
    }

    @Override
    public CombatSession startCombat(
            UUID playerId,
            UUID opponentId
    ) {
        long now = System.currentTimeMillis();

        DefaultCombatSession session =
                sessions.compute(
                        playerId,
                        (id, existing) -> {

                            if (existing == null
                                    || !existing.isActive()) {

                                return new DefaultCombatSession(
                                        id,
                                        now,
                                        now + durationMillis
                                );
                            }

                            existing.refresh(
                                    now,
                                    now + durationMillis
                            );

                            return existing;
                        }
                );

        if (opponentId != null) {
            session.setOpponentId(opponentId);
        }

        return session;
    }

    @Override
    public CombatSession refreshCombat(
            UUID playerId
    ) {
        return refreshCombat(playerId, null);
    }

    @Override
    public CombatSession refreshCombat(
            UUID playerId,
            UUID opponentId
    ) {
        long now = System.currentTimeMillis();

        DefaultCombatSession session =
                sessions.computeIfAbsent(
                        playerId,
                        id -> new DefaultCombatSession(
                                id,
                                now,
                                now + durationMillis
                        )
                );

        session.refresh(
                now,
                now + durationMillis
        );

        if (opponentId != null) {
            session.setOpponentId(opponentId);
        }

        return session;
    }

    @Override
    public void recordDamage(
            UUID attackerId,
            UUID victimId,
            long timestamp
    ) {
        if (attackerId == null || victimId == null || attackerId.equals(victimId)) {
            return;
        }

        DefaultCombatSession attacker = sessions.get(attackerId);
        DefaultCombatSession victim = sessions.get(victimId);

        if (attacker != null && attacker.isActive()) {
            attacker.setOpponentId(victimId);
            attacker.setLastVictimId(victimId);
            attacker.recordActivity(timestamp, timestamp + durationMillis);
        }

        if (victim != null && victim.isActive()) {
            victim.setOpponentId(attackerId);
            victim.setLastAttackerId(attackerId);
            victim.recordActivity(timestamp, timestamp + durationMillis);
        }
    }

    @Override
    public boolean endCombat(
            UUID playerId,
            CombatReason reason
    ) {
        return end(
                playerId,
                reason,
                CombatState.ENDED
        );
    }

    @Override
    public boolean forceEndCombat(
            UUID playerId,
            CombatReason reason
    ) {
        return end(
                playerId,
                reason,
                CombatState.FORCED_END
        );
    }

    private boolean end(
            UUID playerId,
            CombatReason reason,
            CombatState state
    ) {
        DefaultCombatSession session =
                sessions.get(playerId);

        if (session == null) {
            return false;
        }

        session.end(state, reason);

        return true;
    }

    @Override
    public void removeSession(UUID playerId) {
        sessions.remove(playerId);
    }

    @Override
    public Collection<CombatSession> getActiveSessions() {
        return new ArrayList<>(
                sessions.values()
                        .stream()
                        .filter(DefaultCombatSession::isActive)
                        .map(session -> (CombatSession) session)
                        .toList()
        );
    }

    @Override
    public void cleanupExpiredSessions(
            long currentTimeMillis
    ) {
        sweepExpired(currentTimeMillis);
    }

    @Override
    public List<UUID> sweepExpired(
            long currentTimeMillis
    ) {

        List<UUID> expired =
                new ArrayList<>();

        /*
         * One pass, one decision per entry: computeIfPresent holds
         * the bucket lock for that single key only (ConcurrentHashMap),
         * so this is safe to run every tick alongside start/refresh
         * calls for other players without a global lock and without
         * the two-step "snapshot then sweep" race that used to leave
         * expired sessions in the map forever.
         */
        for (UUID playerId : sessions.keySet()) {

            sessions.computeIfPresent(
                    playerId,
                    (id, session) -> {

                        if (session.getState() != CombatState.ACTIVE
                                || currentTimeMillis
                                < session.getExpiresAt()) {

                            return session;
                        }

                        session.end(
                                CombatState.EXPIRED,
                                CombatReason.TIMEOUT
                        );

                        expired.add(id);

                        /*
                         * Returning null removes the entry: nothing
                         * else cleans up a timed-out session that no
                         * one looks up again, so leaving it here
                         * would grow the map without bound on a busy
                         * server.
                         */
                        return null;
                    }
            );
        }

        return expired;
    }
}