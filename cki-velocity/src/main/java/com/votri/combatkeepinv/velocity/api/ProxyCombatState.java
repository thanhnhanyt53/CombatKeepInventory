package com.votri.combatkeepinv.velocity.api;

import com.votri.combatkeepinv.core.api.CombatTag;

import java.util.UUID;

/**
 * Immutable proxy-side mirror of a backend CombatTag.
 *
 * <p>
 * This class does not create combat state.
 * It only represents state received from Bukkit CKI.
 * </p>
 */
public final class ProxyCombatState
        implements CombatTag {

    private final UUID playerId;
    private final UUID opponentId;
    private final String backendServer;
    private final long expiresAt;

    public ProxyCombatState(
            UUID playerId,
            UUID opponentId,
            String backendServer,
            long expiresAt
    ) {

        this.playerId =
                playerId;

        this.opponentId =
                opponentId;

        this.backendServer =
                backendServer;

        this.expiresAt =
                expiresAt;
    }

    @Override
    public UUID getPlayerId() {
        return playerId;
    }

    @Override
    public UUID getLastOpponent() {
        return opponentId;
    }

    public String getBackendServer() {
        return backendServer;
    }

    @Override
    public boolean isActive() {
        return getRemainingMillis() > 0L;
    }

    @Override
    public long getRemainingMillis() {

        return Math.max(
                0L,
                expiresAt
                        - System.currentTimeMillis()
        );
    }

    @Override
    public long getExpiresAt() {
        return expiresAt;
    }
}