package com.votri.combatkeepinv.velocity.api;

import com.votri.combatkeepinv.velocity.config.VelocityConfig;

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

    private final VelocityConfig config;

    public ProxyCombatStateManager(
            VelocityConfig config
    ) {
        this.config =
                config;
    }

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

        long now =
                System.currentTimeMillis();

        states.put(
                attacker,
                new ProxyCombatState(
                        attacker,
                        victim,
                        backendServer,
                        expiresAt,
                        now
                )
        );

        states.put(
                victim,
                new ProxyCombatState(
                        victim,
                        attacker,
                        backendServer,
                        expiresAt,
                        now
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

        return getCombatState(player) != null;
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

        long staleTimeout =
                config.getStateTimeoutSeconds()
                        * 1000L;

        if (staleTimeout > 0L
                && System.currentTimeMillis()
                - state.getUpdatedAt()
                > staleTimeout) {

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
                state.withBackendServer(
                        backendServer
                )
        );
    }

    public void cleanupExpired() {

        long now =
                System.currentTimeMillis();

        long staleTimeout =
                config.getStateTimeoutSeconds()
                        * 1000L;

        states.entrySet()
                .removeIf(
                        entry -> {

                            ProxyCombatState state =
                                    entry.getValue();

                            if (!state.isActive()) {
                                return true;
                            }

                            return staleTimeout > 0L
                                    && now
                                    - state.getUpdatedAt()
                                    > staleTimeout;
                        }
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