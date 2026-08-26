package com.votri.combatkeepinv.velocity.api;

import com.votri.combatkeepinv.velocity.config.VelocityConfig;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores the proxy-side mirror of authoritative Bukkit
 * CombatTag state.
 *
 * <p>
 * Velocity never creates combat state by itself.
 * This manager only stores state received from the
 * Bukkit CombatStateBridge.
 * </p>
 */
public final class ProxyCombatStateManager {

    private final Map<UUID, ProxyCombatState> states =
            new ConcurrentHashMap<>();

    private final VelocityConfig config;

    public ProxyCombatStateManager(
            VelocityConfig config
    ) {

        if (config == null) {
            throw new IllegalArgumentException(
                    "VelocityConfig cannot be null."
            );
        }

        this.config =
                config;
    }

    /**
     * Creates/replaces combat state for both players.
     *
     * @param attacker attacker UUID
     * @param victim victim UUID
     * @param backendServer authoritative backend server
     * @param expiresAt absolute expiration timestamp
     */
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

    /**
     * Refreshes combat state.
     *
     * <p>
     * Refresh is authoritative: the expiration timestamp
     * received from Bukkit is used directly.
     * </p>
     */
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

    /**
     * Ends combat state for a player.
     *
     * <p>
     * This removes only the specified player's mirrored state.
     * The opposite player's state will be removed when its own
     * END/FORCE_END message is received.
     * </p>
     */
    public void end(
            UUID player
    ) {

        if (player != null) {
            states.remove(player);
        }
    }

    /**
     * Forcefully ends combat state for a player.
     */
    public void forceEnd(
            UUID player
    ) {

        if (player != null) {
            states.remove(player);
        }
    }

    /**
     * Checks whether a player has an active mirrored CombatTag.
     */
    public boolean isInCombat(
            UUID player
    ) {

        return getCombatState(player) != null;
    }

    /**
     * Returns active mirrored combat state.
     */
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

        /*
         * CombatTag expiration is authoritative.
         */
        if (!state.isActive()) {

            states.remove(
                    player,
                    state
            );

            return null;
        }

        /*
         * Bridge-state timeout protects Velocity from
         * retaining stale state if the backend stops sending
         * synchronization messages.
         */
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

    /**
     * Returns remaining CombatTag duration.
     */
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

    /**
     * Returns the last combat opponent.
     */
    public UUID getOpponent(
            UUID player
    ) {

        ProxyCombatState state =
                getCombatState(player);

        return state == null
                ? null
                : state.getLastOpponent();
    }

    /**
     * Returns the backend server associated with the state.
     */
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
     * Updates backend metadata without changing the CombatTag timer.
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
                state.withBackendServer(
                        backendServer
                )
        );
    }

    /**
     * Removes expired or stale mirrored states.
     */
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

                            /*
                             * CombatTag itself expired.
                             */
                            if (!state.isActive()) {
                                return true;
                            }

                            /*
                             * Bridge synchronization became stale.
                             */
                            return staleTimeout > 0L
                                    && now
                                    - state.getUpdatedAt()
                                    > staleTimeout;
                        }
                );
    }

    /**
     * Clears all mirrored states.
     */
    public void clear() {
        states.clear();
    }

    /**
     * Returns the number of active/non-cleaned states.
     */
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