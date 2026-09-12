package com.votri.combatkeepinv.core.internal;

import com.votri.combatkeepinv.core.combat.CombatReason;
import com.votri.combatkeepinv.core.combat.CombatSession;
import com.votri.combatkeepinv.core.combat.CombatSessionManager;
import com.votri.combatkeepinv.core.combat.CombatState;

import java.util.ArrayList;
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
            session.setLastAttackerId(opponentId);
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
            session.setLastAttackerId(opponentId);
        }

        return session;
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
        sessions.forEach(
                (playerId, session) -> {

                    if (session.isActive()
                            && currentTimeMillis
                            >= session.getExpiresAt()) {

                        session.end(
                                CombatState.EXPIRED,
                                CombatReason.TIMEOUT
                        );
                    }
                }
        );
    }
}