package com.votri.combatkeepinv.velocity.session;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a player's session across the Velocity cluster.
 *
 * <p>This object intentionally tracks proxy-level state only.
 * It does not contain Bukkit or Paper classes.</p>
 */
public final class PlayerSession {

    private final UUID playerId;

    private String currentServer;
    private String previousServer;

    private long connectedAt;
    private long lastServerSwitchAt;

    private boolean connected;

    public PlayerSession(
            UUID playerId
    ) {
        this.playerId =
                Objects.requireNonNull(
                        playerId,
                        "playerId"
                );

        this.connectedAt =
                System.currentTimeMillis();

        this.connected = true;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getCurrentServer() {
        return currentServer;
    }

    public String getPreviousServer() {
        return previousServer;
    }

    public long getConnectedAt() {
        return connectedAt;
    }

    public long getLastServerSwitchAt() {
        return lastServerSwitchAt;
    }

    public boolean isConnected() {
        return connected;
    }

    public void connectToServer(
            String serverName
    ) {
        Objects.requireNonNull(
                serverName,
                "serverName"
        );

        if (Objects.equals(
                currentServer,
                serverName
        )) {
            return;
        }

        previousServer =
                currentServer;

        currentServer =
                serverName;

        lastServerSwitchAt =
                System.currentTimeMillis();

        connected = true;
    }

    public void disconnect() {
        connected = false;
    }

    @Override
    public String toString() {
        return "PlayerSession{" +
                "playerId=" + playerId +
                ", currentServer='" +
                currentServer + '\'' +
                ", previousServer='" +
                previousServer + '\'' +
                ", connectedAt=" +
                connectedAt +
                ", lastServerSwitchAt=" +
                lastServerSwitchAt +
                ", connected=" +
                connected +
                '}';
    }
}