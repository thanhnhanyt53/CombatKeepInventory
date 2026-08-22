package com.votri.combatkeepinv.velocity.listener;

import com.votri.combatkeepinv.velocity.session.PlayerSessionManager;
import com.votri.combatkeepinv.velocity.session.SessionTransition;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Tracks player transitions across the Velocity cluster.
 */
public final class VelocitySessionListener {

    private final PlayerSessionManager sessionManager;

    private final Consumer<SessionTransition> transitionConsumer;

    public VelocitySessionListener(
            PlayerSessionManager sessionManager,
            Consumer<SessionTransition> transitionConsumer
    ) {
        this.sessionManager =
                Objects.requireNonNull(
                        sessionManager,
                        "sessionManager"
                );

        this.transitionConsumer =
                Objects.requireNonNull(
                        transitionConsumer,
                        "transitionConsumer"
                );
    }

    @Subscribe
    public void onServerConnected(
            ServerConnectedEvent event
    ) {
        Player player =
                event.getPlayer();

        String serverName =
                event.getServer()
                        .getServerInfo()
                        .getName();

        SessionTransition transition =
                sessionManager.serverSwitch(
                        player.getUniqueId(),
                        serverName
                );

        transitionConsumer.accept(
                transition
        );
    }

    @Subscribe
    public void onDisconnect(
            DisconnectEvent event
    ) {
        Player player =
                event.getPlayer();

        SessionTransition transition =
                sessionManager.clusterExit(
                        player.getUniqueId()
                );

        transitionConsumer.accept(
                transition
        );
    }
}