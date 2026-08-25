package com.votri.combatkeepinv.velocity.listener;

import com.votri.combatkeepinv.velocity.CombatKeepInventoryVelocity;
import com.votri.combatkeepinv.velocity.api.ProxyCombatStateManager;
import com.votri.combatkeepinv.velocity.session.PlayerSessionManager;
import com.votri.combatkeepinv.velocity.session.SessionTransition;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;

import org.slf4j.Logger;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Velocity-side listener for:
 *
 * <ul>
 *     <li>player session transitions</li>
 *     <li>authoritative Bukkit CombatStateBridge messages</li>
 * </ul>
 *
 * <p>
 * Velocity never creates combat state from damage or movement.
 * Bukkit is the authoritative CombatTag owner.
 * </p>
 */
public final class VelocitySessionListener {

    private static final int PROTOCOL_VERSION =
            CombatKeepInventoryVelocity.PROTOCOL_VERSION;

    private static final byte START = 1;
    private static final byte REFRESH = 2;
    private static final byte END = 3;
    private static final byte FORCE_END = 4;

    private final PlayerSessionManager sessionManager;
    private final ProxyCombatStateManager combatStateManager;

    private final Consumer<SessionTransition>
            transitionConsumer;

    private final Logger logger;

    public VelocitySessionListener(
            PlayerSessionManager sessionManager,
            ProxyCombatStateManager combatStateManager,
            Consumer<SessionTransition> transitionConsumer,
            Logger logger
    ) {

        this.sessionManager =
                sessionManager;

        this.combatStateManager =
                combatStateManager;

        this.transitionConsumer =
                transitionConsumer;

        this.logger =
                logger;
    }

    /*
     * ==========================================================
     * PLAYER SESSION
     * ==========================================================
     */

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

    /*
     * ==========================================================
     * COMBAT STATE BRIDGE
     * ==========================================================
     */

    @Subscribe
    public void onPluginMessage(
            PluginMessageEvent event
    ) {

        /*
         * Only accept our own channel.
         */
        if (!(event.getIdentifier()
                instanceof MinecraftChannelIdentifier)) {

            return;
        }

        if (!event.getIdentifier().equals(
                CombatKeepInventoryVelocity
                        .CHANNEL_IDENTIFIER
        )) {

            return;
        }

        /*
         * SECURITY:
         *
         * CombatStateBridge messages must originate
         * from a backend server.
         *
         * A client/player is never trusted as an
         * authoritative combat-state source.
         */

        if (!(event.getSource()
                instanceof ServerConnection serverConnection)) {

            debug(
                    "Rejected combat bridge message: "
                            + "source is not ServerConnection."
            );

            event.setResult(
                    PluginMessageEvent
                            .ForwardResult
                            .handled()
            );

            return;
        }

        /*
         * Prevent the internal combat-state message from
         * being forwarded to the client/backend.
         */

        event.setResult(
                PluginMessageEvent
                        .ForwardResult
                        .handled()
        );

        String backendServer =
                serverConnection
                        .getServerInfo()
                        .getName();

        try {

            processCombatMessage(
                    event,
                    backendServer
            );

        } catch (IOException exception) {

            logger.warn(
                    "Invalid CombatStateBridge message "
                            + "received from backend {}.",
                    backendServer,
                    exception
            );
        }
    }

    private void processCombatMessage(
            PluginMessageEvent event,
            String backendServer
    ) throws IOException {

        DataInputStream input =
                new DataInputStream(
                        event.dataAsInputStream()
                );

        /*
         * Protocol version.
         */

        int protocol =
                input.readInt();

        if (protocol != PROTOCOL_VERSION) {

            debug(
                    "Rejected CombatStateBridge message: "
                            + "unsupported protocol "
                            + protocol
                            + " from "
                            + backendServer
            );

            return;
        }

        /*
         * Operation.
         */

        byte operation =
                input.readByte();

        switch (operation) {

            case START -> {

                UUID attacker =
                        readUUID(input);

                UUID victim =
                        readUUID(input);

                long expiresAt =
                        input.readLong();

                if (!validPair(
                        attacker,
                        victim
                )) {

                    debug(
                            "Rejected START: invalid player pair."
                    );

                    return;
                }

                combatStateManager.start(
                        attacker,
                        victim,
                        backendServer,
                        expiresAt
                );

                debug(
                        "START "
                                + attacker
                                + " <-> "
                                + victim
                                + " @ "
                                + backendServer
                                + " expires="
                                + expiresAt
                );
            }

            case REFRESH -> {

                UUID attacker =
                        readUUID(input);

                UUID victim =
                        readUUID(input);

                long expiresAt =
                        input.readLong();

                if (!validPair(
                        attacker,
                        victim
                )) {

                    debug(
                            "Rejected REFRESH: "
                                    + "invalid player pair."
                    );

                    return;
                }

                combatStateManager.refresh(
                        attacker,
                        victim,
                        backendServer,
                        expiresAt
                );

                debug(
                        "REFRESH "
                                + attacker
                                + " <-> "
                                + victim
                                + " @ "
                                + backendServer
                                + " expires="
                                + expiresAt
                );
            }

            case END -> {

                UUID player =
                        readUUID(input);

                if (player == null) {
                    return;
                }

                combatStateManager.end(
                        player
                );

                debug(
                        "END "
                                + player
                                + " @ "
                                + backendServer
                );
            }

            case FORCE_END -> {

                UUID player =
                        readUUID(input);

                if (player == null) {
                    return;
                }

                combatStateManager.forceEnd(
                        player
                );

                debug(
                        "FORCE_END "
                                + player
                                + " @ "
                                + backendServer
                );
            }

            default -> {

                debug(
                        "Rejected CombatStateBridge message: "
                                + "unknown operation "
                                + operation
                );
            }
        }
    }

    /*
     * ==========================================================
     * UUID DECODING
     * ==========================================================
     */

    private UUID readUUID(
            DataInputStream input
    ) throws IOException {

        long most =
                input.readLong();

        long least =
                input.readLong();

        return new UUID(
                most,
                least
        );
    }

    private boolean validPair(
            UUID first,
            UUID second
    ) {

        return first != null
                && second != null
                && !first.equals(second);
    }

    /*
     * ==========================================================
     * DEBUG
     * ==========================================================
     */

    private void debug(
            String message
    ) {

        /*
         * Velocity config handling is intentionally not
         * hard-coded here.
         *
         * Keep these messages at DEBUG level so the proxy
         * administrator can enable them through the logging
         * configuration when required.
         */

        logger.debug(
                "[CombatBridge] {}",
                message
        );
    }
}