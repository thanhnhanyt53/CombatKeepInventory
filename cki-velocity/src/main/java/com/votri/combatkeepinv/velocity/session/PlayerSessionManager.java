package com.votri.combatkeepinv.velocity.session;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains player sessions across the Velocity cluster.
 */
public final class PlayerSessionManager {

    private final Map<UUID, PlayerSession> sessions =
            new ConcurrentHashMap<>();

    /**
     * Creates and stores a new session.
     *
     * @param playerId player UUID
     * @return created session
     */
    public PlayerSession create(
            UUID playerId
    ) {
        return sessions.computeIfAbsent(
                playerId,
                PlayerSession::new
        );
    }

    /**
     * Returns an existing session.
     *
     * @param playerId player UUID
     * @return session or null
     */
    public PlayerSession get(
            UUID playerId
    ) {
        return sessions.get(playerId);
    }

    /**
     * Returns an existing session or creates one.
     *
     * @param playerId player UUID
     * @return session
     */
    public PlayerSession getOrCreate(
            UUID playerId
    ) {
        return sessions.computeIfAbsent(
                playerId,
                PlayerSession::new
        );
    }

    /**
     * Removes a player's session from memory.
     *
     * @param playerId player UUID
     * @return removed session or null
     */
    public PlayerSession remove(
            UUID playerId
    ) {
        return sessions.remove(
                playerId
        );
    }

    /**
     * Marks a player as connected to a backend.
     */
    public SessionTransition serverSwitch(
            UUID playerId,
            String targetServer
    ) {
        PlayerSession session =
                getOrCreate(playerId);

        String previousServer =
                session.getCurrentServer();

        if (previousServer == null) {

            session.connectToServer(
                    targetServer
            );

            return SessionTransition.connect(
                    playerId,
                    targetServer
            );
        }

        if (previousServer.equals(
                targetServer
        )) {
            return SessionTransition.connect(
                    playerId,
                    targetServer
            );
        }

        session.connectToServer(
                targetServer
        );

        return SessionTransition.serverSwitch(
                playerId,
                previousServer,
                targetServer
        );
    }

    /**
     * Marks a player as having left the entire proxy cluster.
     *
     * <p>The session is removed only after the transition has been
     * generated, allowing listeners/API consumers to inspect the
     * final state.</p>
     */
    public SessionTransition clusterExit(
            UUID playerId
    ) {
        PlayerSession session =
                sessions.get(playerId);

        if (session == null) {

            return SessionTransition.clusterExit(
                    playerId,
                    null
            );
        }

        String currentServer =
                session.getCurrentServer();

        session.disconnect();

        SessionTransition transition =
                SessionTransition.clusterExit(
                        playerId,
                        currentServer
                );

        sessions.remove(
                playerId
        );

        return transition;
    }

    public int size() {
        return sessions.size();
    }

    public Collection<PlayerSession> getSessions() {
        return Collections.unmodifiableCollection(
                sessions.values()
        );
    }

    public void clear() {
        sessions.clear();
    }
}