package com.votri.combatkeepinv.velocity.listener;

import com.votri.combatkeepinv.velocity.api.ProxyCombatStateManager;
import com.votri.combatkeepinv.velocity.config.VelocityConfig;
import com.votri.combatkeepinv.velocity.session.PlayerSessionManager;
import com.votri.combatkeepinv.velocity.session.PunishmentGuard;
import com.votri.combatkeepinv.velocity.session.SessionTransition;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;

import org.slf4j.Logger;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Velocity session and CombatStateBridge listener.
 *
 * <p>Bukkit remains the authoritative combat-state source.</p>
 */
public final class VelocitySessionListener {

    private static final int PROTOCOL_VERSION = 1;

    private static final byte START = 1;
    private static final byte REFRESH = 2;
    private static final byte END = 3;
    private static final byte FORCE_END = 4;

    private final PlayerSessionManager sessionManager;
    private final ProxyCombatStateManager combatStateManager;
    private final Consumer<SessionTransition> transitionConsumer;
    private final Logger logger;
    private final VelocityConfig config;

    private final Supplier<MinecraftChannelIdentifier>
            channelSupplier;

    private final PunishmentGuard punishmentGuard;

    public VelocitySessionListener(
            PlayerSessionManager sessionManager,
            ProxyCombatStateManager combatStateManager,
            Consumer<SessionTransition> transitionConsumer,
            Logger logger,
            VelocityConfig config,
            Supplier<MinecraftChannelIdentifier> channelSupplier,
            PunishmentGuard punishmentGuard
    ) {

        this.sessionManager =
                sessionManager;

        this.combatStateManager =
                combatStateManager;

        this.transitionConsumer =
                transitionConsumer;

        this.logger =
                logger;

        this.config =
                config;

        this.channelSupplier =
                channelSupplier;

        this.punishmentGuard =
                punishmentGuard;
    }

    /*
     * ==========================================================
     * SESSION
     * ==========================================================
     */

    @Subscribe
    public void onServerConnected(
            ServerConnectedEvent event
    ) {

        if (!config.isServerSwitchEnabled()) {
            return;
        }

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

    /**
     * A backend kicked the player (shutdown, crash, plugin kick)
     * and Velocity is about to redirect them somewhere else. The
     * resulting server switch is not the player's own choice, so
     * it must not count as combat logging.
     */
    @Subscribe
    public void onKickedFromServer(
            KickedFromServerEvent event
    ) {

        punishmentGuard.markInvoluntarySwitch(
                event.getPlayer().getUniqueId()
        );
    }

    @Subscribe
    public void onDisconnect(
            DisconnectEvent event
    ) {

        if (!config.isClusterExitEnabled()) {
            return;
        }

        Player player =
                event.getPlayer();

        /*
         * Only a normal, completed login that then ended counts as
         * the player leaving. A duplicate login, a connection the
         * proxy cancelled, or one cancelled before the player ever
         * reached a backend is not combat logging.
         */
        if (event.getLoginStatus()
                != DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN) {

            punishmentGuard.markNonPunishableExit(
                    player.getUniqueId()
            );
        }

        /*
         * The bypass permission has to be checked here, on the
         * event's own player object: by the time the transition
         * reaches the punishment gate the player is already gone
         * from proxy.getPlayer(...), so a lookup there always came
         * back empty and players with the bypass permission were
         * punished for quitting anyway.
         */
        String bypassPermission =
                config.getBypassPermission();

        if (bypassPermission != null
                && !bypassPermission.isBlank()) {

            boolean bypassed;

            try {
                bypassed =
                        player.hasPermission(
                                bypassPermission
                        );
            } catch (RuntimeException exception) {
                /*
                 * Operations on a disconnected player are
                 * documented as potentially undefined. When the
                 * check cannot be trusted, err on the side of not
                 * punishing.
                 */
                bypassed = true;
            }

            if (bypassed) {
                punishmentGuard.markNonPunishableExit(
                        player.getUniqueId()
                );
            }
        }

        SessionTransition transition =
                sessionManager.clusterExit(
                        player.getUniqueId()
                );

        transitionConsumer.accept(
                transition
        );

        punishmentGuard.forget(
                player.getUniqueId()
        );
    }

    /*
     * ==========================================================
     * COMBAT BRIDGE
     * ==========================================================
     */

    @Subscribe
    public void onPluginMessage(
            PluginMessageEvent event
    ) {

        if (!config.isBridgeEnabled()) {
            return;
        }

        MinecraftChannelIdentifier channel =
                channelSupplier.get();

        if (channel == null) {
            return;
        }

        if (!channel.equals(
                event.getIdentifier()
        )) {
            return;
        }

        /*
         * Client-originated messages are never trusted.
         */

        if (!(event.getSource()
                instanceof ServerConnection backend)) {

            event.setResult(
                    PluginMessageEvent
                            .ForwardResult
                            .handled()
            );

            if (config.logInvalidMessage()) {

                logger.warn(
                        "[CombatBridge] Rejected message: "
                                + "source is not a backend server."
                );
            }

            return;
        }

        event.setResult(
                PluginMessageEvent
                        .ForwardResult
                        .handled()
        );

        String backendServer =
                backend.getServerInfo()
                        .getName();

        if (!isAllowedBackend(
                backendServer
        )) {

            if (config.logInvalidMessage()) {

                logger.warn(
                        "[CombatBridge] Rejected message from "
                                + "unauthorized backend '{}'.",
                        backendServer
                );
            }

            return;
        }

        try {

            processCombatMessage(
                    event,
                    backendServer
            );

        } catch (IOException exception) {

            if (config.logInvalidMessage()) {

                logger.warn(
                        "[CombatBridge] Invalid message from '{}'.",
                        backendServer,
                        exception
                );
            }
        }
    }

    private boolean isAllowedBackend(
            String backendServer
    ) {

        if (!config.verifyBridgeSourceServer()) {
            return true;
        }

        List<String> allowed =
                config.getAllowedBridgeServers();

        if (allowed.isEmpty()) {
            return true;
        }

        return allowed.stream()
                .anyMatch(
                        backendServer::equalsIgnoreCase
                );
    }

    private void processCombatMessage(
            PluginMessageEvent event,
            String backendServer
    ) throws IOException {

        DataInputStream input =
                new DataInputStream(
                        event.dataAsInputStream()
                );

        int protocol =
                input.readInt();

        if (protocol != PROTOCOL_VERSION) {

            if (config.logInvalidMessage()) {

                logger.warn(
                        "[CombatBridge] Unsupported protocol "
                                + "{} from '{}'.",
                        protocol,
                        backendServer
                );
            }

            return;
        }

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
                    return;
                }

                combatStateManager.start(
                        attacker,
                        victim,
                        backendServer,
                        expiresAt
                );

                if (config.logStart()) {

                    logger.info(
                            "[CombatBridge] START {} <-> {} @ {}",
                            attacker,
                            victim,
                            backendServer
                    );
                }
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
                    return;
                }

                combatStateManager.refresh(
                        attacker,
                        victim,
                        backendServer,
                        expiresAt
                );

                if (config.logRefresh()) {

                    logger.info(
                            "[CombatBridge] REFRESH {} <-> {} @ {}",
                            attacker,
                            victim,
                            backendServer
                    );
                }
            }

            case END -> {

                UUID player =
                        readUUID(input);

                combatStateManager.end(
                        player
                );

                if (config.logEnd()) {

                    logger.info(
                            "[CombatBridge] END {} @ {}",
                            player,
                            backendServer
                    );
                }
            }

            case FORCE_END -> {

                UUID player =
                        readUUID(input);

                combatStateManager.forceEnd(
                        player
                );

                if (config.logForceEnd()) {

                    logger.info(
                            "[CombatBridge] FORCE_END {} @ {}",
                            player,
                            backendServer
                    );
                }
            }

            default -> {

                if (config.logInvalidMessage()) {

                    logger.warn(
                            "[CombatBridge] Unknown operation {} "
                                    + "from '{}'.",
                            operation,
                            backendServer
                    );
                }
            }
        }
    }

    /*
     * ==========================================================
     * HELPERS
     * ==========================================================
     */

    private UUID readUUID(
            DataInputStream input
    ) throws IOException {

        return new UUID(
                input.readLong(),
                input.readLong()
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
}