package com.votri.combatkeepinv.velocity.session;

import java.util.Objects;
import java.util.UUID;

/**
 * Describes a player's transition inside the Velocity cluster.
 */
public final class SessionTransition {

    public enum Type {

        /**
         * Player connected to the proxy.
         */
        CONNECT,

        /**
         * Player switched from one backend to another.
         */
        SERVER_SWITCH,

        /**
         * Player left the entire proxy/cluster.
         */
        CLUSTER_EXIT
    }

    private final UUID playerId;
    private final Type type;

    private final String fromServer;
    private final String toServer;

    private final long timestamp;

    private SessionTransition(
            UUID playerId,
            Type type,
            String fromServer,
            String toServer
    ) {
        this.playerId =
                Objects.requireNonNull(
                        playerId,
                        "playerId"
                );

        this.type =
                Objects.requireNonNull(
                        type,
                        "type"
                );

        this.fromServer =
                fromServer;

        this.toServer =
                toServer;

        this.timestamp =
                System.currentTimeMillis();
    }

    public static SessionTransition connect(
            UUID playerId,
            String server
    ) {
        return new SessionTransition(
                playerId,
                Type.CONNECT,
                null,
                server
        );
    }

    public static SessionTransition serverSwitch(
            UUID playerId,
            String fromServer,
            String toServer
    ) {
        return new SessionTransition(
                playerId,
                Type.SERVER_SWITCH,
                fromServer,
                toServer
        );
    }

    public static SessionTransition clusterExit(
            UUID playerId,
            String fromServer
    ) {
        return new SessionTransition(
                playerId,
                Type.CLUSTER_EXIT,
                fromServer,
                null
        );
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Type getType() {
        return type;
    }

    public String getFromServer() {
        return fromServer;
    }

    public String getToServer() {
        return toServer;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isServerSwitch() {
        return type == Type.SERVER_SWITCH;
    }

    public boolean isClusterExit() {
        return type == Type.CLUSTER_EXIT;
    }

    @Override
    public String toString() {
        return "SessionTransition{" +
                "playerId=" + playerId +
                ", type=" + type +
                ", fromServer='" +
                fromServer + '\'' +
                ", toServer='" +
                toServer + '\'' +
                ", timestamp=" +
                timestamp +
                '}';
    }
}