package com.votri.combatkeepinv.velocity.api;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores the proxy-side mirror of authoritative Bukkit
 * CombatTag state.
 */
public final class ProxyCombatStateManager {

    private final Map<UUID, ProxyCombatState> states =
            new ConcurrentHashMap<>();

    public void start(
            UUID attacker,
            UUID victim,
            String backendServer,
            long expiresAt
    ) {

        if (!validPair(
                attacker,
                victim
        )) {
            return;
        }

        states.put(
                attacker,
                new ProxyCombatState(
                        attacker,
                        victim,
                        backendServer,
                        expiresAt
                )
        );

        states.put(
                victim,
                new ProxyCombatState(
                        victim,
                        attacker,
                        backendServer,
                        expiresAt
                )
        );
    }

    public void refresh(
            UUID attacker,
            UUID victim,
            String backendServer,
            long expiresAt
    ) {

        start(
                attacker,
                victim,
                backendServer,
                expiresAt
        );
    }

    public void end(
            UUID player
    ) {

        if (player != null) {
            states.remove(player);
        }
    }

    public void forceEnd(
            UUID player
    ) {

        if (player != null) {
            states.remove(player);
        }
    }

    public boolean isInCombat(
            UUID player
    ) {

        ProxyCombatState state =
                getCombatState(player);

        return state != null
                && state.isActive();
    }

    public ProxyCombatState getCombatState(
            UUID player
    ) {

        if (player == null) {
            return null;
        }

        ProxyCombatState state =
                states.get(player);

        if (state == null) {
            return null;
        }

        if (!state.isActive()) {

            states.remove(
                    player,
                    state
            );

            return null;
        }

        return state;
    }

    public long getRemainingMillis(
            UUID player
    ) {

        ProxyCombatState state =
                getCombatState(player);

        if (state == null) {
            return 0L;
        }

        return state.getRemainingMillis();
    }

    public UUID getOpponent(
            UUID player
    ) {

        ProxyCombatState state =
                getCombatState(player);

        return state == null
                ? null
                : state.getLastOpponent();
    }

    public String getBackendServer(
            UUID player
    ) {

        ProxyCombatState state =
                getCombatState(player);

        return state == null
                ? null
                : state.getBackendServer();
    }

    /**
     * Updates only the backend-server metadata.
     *
     * <p>
     * The combat timer itself is not changed.
     * </p>
     */
    public void updateBackendServer(
            UUID player,
            String backendServer
    ) {

        if (player == null
                || backendServer == null) {
            return;
        }

        ProxyCombatState state =
                states.get(player);

        if (state == null
                || !state.isActive()) {
            return;
        }

        states.put(
                player,
                new ProxyCombatState(
                        state.getPlayerId(),
                        state.getLastOpponent(),
                        backendServer,
                        state.getExpiresAt()
                )
        );
    }

    public void cleanupExpired() {

        states.entrySet()
                .removeIf(
                        entry ->
                                !entry.getValue()
                                        .isActive()
                );
    }

    public void clear() {
        states.clear();
    }

    public int size() {

        cleanupExpired();

        return states.size();
    }

    private boolean validPair(
            UUID first,
            UUID second
    ) {

        return first != null
                && second != null
                && !first.equals(second);
    }
}