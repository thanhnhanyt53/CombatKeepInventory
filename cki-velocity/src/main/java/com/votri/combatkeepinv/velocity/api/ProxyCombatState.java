package com.votri.combatkeepinv.velocity.api;

import com.votri.combatkeepinv.core.api.CombatTag;

import java.util.UUID;

/**
 * Immutable proxy-side mirror of an authoritative backend CombatTag.
 *
 * <p>
 * This class never creates or owns combat state.
 * It only represents CombatTag state received from the
 * authoritative Bukkit CombatKeepInventory instance.
 * </p>
 */
public final class ProxyCombatState implements CombatTag {

    private final UUID playerId;
    private final UUID opponentId;
    private final String backendServer;
    private final long expiresAt;
    private final long updatedAt;

    /**
     * Backwards-compatible constructor.
     *
     * <p>
     * Kept so existing code using the original four-argument
     * constructor remains source-compatible.
     * </p>
     */
    public ProxyCombatState(
            UUID playerId,
            UUID opponentId,
            String backendServer,
            long expiresAt
    ) {
        this(
                playerId,
                opponentId,
                backendServer,
                expiresAt,
                System.currentTimeMillis()
        );
    }

    /**
     * Full constructor used by ProxyCombatStateManager.
     *
     * @param playerId player represented by this state
     * @param opponentId last combat opponent
     * @param backendServer backend server where the state originated
     * @param expiresAt absolute expiration timestamp
     * @param updatedAt timestamp when the mirrored state was last updated
     */
    public ProxyCombatState(
            UUID playerId,
            UUID opponentId,
            String backendServer,
            long expiresAt,
            long updatedAt
    ) {
        this.playerId =
                playerId;

        this.opponentId =
                opponentId;

        this.backendServer =
                backendServer;

        this.expiresAt =
                expiresAt;

        this.updatedAt =
                updatedAt;
    }

    @Override
    public UUID getPlayerId() {
        return playerId;
    }

    @Override
    public UUID getLastOpponent() {
        return opponentId;
    }

    /**
     * Returns the backend server associated with this mirrored state.
     *
     * @return backend server name, or null if unknown
     */
    public String getBackendServer() {
        return backendServer;
    }

    /**
     * Returns the timestamp at which this mirrored state was updated.
     *
     * <p>
     * This timestamp is used by ProxyCombatStateManager to detect
     * stale bridge state independently from CombatTag expiration.
     * </p>
     *
     * @return update timestamp in milliseconds
     */
    public long getUpdatedAt() {
        return updatedAt;
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

    /**
     * Creates a copy with a different backend server.
     *
     * <p>
     * The CombatTag expiration and update timestamp are preserved.
     * A server switch therefore does not accidentally extend combat.
     * </p>
     *
     * @param backendServer new backend server
     * @return new immutable state
     */
    public ProxyCombatState withBackendServer(
            String backendServer
    ) {

        return new ProxyCombatState(
                playerId,
                opponentId,
                backendServer,
                expiresAt,
                updatedAt
        );
    }
}