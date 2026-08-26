package com.votri.combatkeepinv.velocity;

import com.google.inject.Inject;
import com.votri.combatkeepinv.core.platform.PlatformInfo;
import com.votri.combatkeepinv.velocity.api.CombatPunishmentService;
import com.votri.combatkeepinv.velocity.api.ProxyCombatStateManager;
import com.votri.combatkeepinv.velocity.api.VelocityCombatAPI;
import com.votri.combatkeepinv.velocity.api.VelocityCombatAPIImpl;
import com.votri.combatkeepinv.velocity.config.VelocityConfig;
import com.votri.combatkeepinv.velocity.listener.VelocitySessionListener;
import com.votri.combatkeepinv.velocity.platform.VelocityPlatformDetector;
import com.votri.combatkeepinv.velocity.session.PlayerSessionManager;
import com.votri.combatkeepinv.velocity.session.SessionTransition;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Plugin(
        id = "combatkeepinventory",
        name = "CombatKeepInventory",
        version = "1.1.0-SNAPSHOT-build4",
        authors = {
                "ThanhNhan"
        }
)
public final class CombatKeepInventoryVelocity {

    /*
     * ==========================================================
     * CONSTANTS
     * ==========================================================
     */

    public static final String CHANNEL =
            "votri:combat";

    public static final int PROTOCOL_VERSION =
            1;

    /*
     * Default channel identifier.
     *
     * The actual runtime channel is resolved from VelocityConfig.
     */
    public static final MinecraftChannelIdentifier
            CHANNEL_IDENTIFIER =
            MinecraftChannelIdentifier.from(
                    CHANNEL
            );

    /*
     * ==========================================================
     * CORE DEPENDENCIES
     * ==========================================================
     */

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    /*
     * Configuration is shared by every Velocity component.
     */
    private final VelocityConfig config;

    private final PlayerSessionManager sessionManager;

    private final ProxyCombatStateManager combatStateManager;

    private final CombatPunishmentService punishmentService;

    /*
     * ==========================================================
     * RUNTIME STATE
     * ==========================================================
     */

    private PlatformInfo platformInfo;

    private VelocityCombatAPIImpl combatAPI;

    private VelocitySessionListener sessionListener;

    private MinecraftChannelIdentifier
            channelIdentifier;

    /*
     * ==========================================================
     * CONSTRUCTOR
     * ==========================================================
     */

    @Inject
    public CombatKeepInventoryVelocity(
            ProxyServer proxy,
            Logger logger,
            @DataDirectory Path dataDirectory
    ) {

        this.proxy =
                proxy;

        this.logger =
                logger;

        this.dataDirectory =
                dataDirectory;

        /*
         * ------------------------------------------------------
         * Configuration
         * ------------------------------------------------------
         *
         * VelocityConfig does not parse the file in its
         * constructor. The actual load happens during
         * ProxyInitializeEvent after the default config has
         * been created.
         */

        this.config =
                new VelocityConfig(
                        dataDirectory
                );

        /*
         * ------------------------------------------------------
         * Session state
         * ------------------------------------------------------
         */

        this.sessionManager =
                new PlayerSessionManager();

        /*
         * ------------------------------------------------------
         * Proxy combat state
         * ------------------------------------------------------
         *
         * Combat state manager receives the shared config.
         */

        this.combatStateManager =
                new ProxyCombatStateManager(
                        config
                );

        /*
         * ------------------------------------------------------
         * Punishment service
         * ------------------------------------------------------
         */

        this.punishmentService =
                new CombatPunishmentService(
                        proxy
                );
    }

    /*
     * ==========================================================
     * PROXY INITIALIZATION
     * ==========================================================
     */

    @Subscribe
    public void onProxyInitialization(
            ProxyInitializeEvent event
    ) {

        /*
         * ======================================================
         * DATA FILES
         * ======================================================
         */

        initializeDataFiles();

        /*
         * ======================================================
         * CONFIGURATION
         * ======================================================
         */

        config.load();

        /*
         * ======================================================
         * CHANNEL
         * ======================================================
         *
         * The configured channel is authoritative.
         *
         * If config.yml contains:
         *
         * bridge:
         *   channel: votri:combat
         *
         * that value is used.
         */

        channelIdentifier =
                createChannelIdentifier(
                        config.getBridgeChannel()
                );

        /*
         * ======================================================
         * PLATFORM
         * ======================================================
         */

        platformInfo =
                VelocityPlatformDetector.detect(
                        proxy
                );

        /*
         * ======================================================
         * CONFIG STATUS
         * ======================================================
         */

        if (!config.isEnabled()) {

            logger.warn(
                    "CombatKeepInventory Velocity module "
                            + "is disabled by configuration."
            );
        }

        /*
         * ======================================================
         * COMBAT API
         * ======================================================
         *
         * IMPORTANT:
         *
         * Do not use the old constructor:
         *
         * new VelocityCombatAPIImpl(
         *     proxy,
         *     combatStateManager
         * );
         *
         * The current contract requires:
         *
         * ProxyServer
         * ProxyCombatStateManager
         * CombatPunishmentService
         * VelocityConfig
         */

        combatAPI =
                new VelocityCombatAPIImpl(
                        proxy,
                        combatStateManager,
                        punishmentService,
                        config
                );

        /*
         * Expose API only when configuration allows it.
         */

        if (config.isApiEnabled()
                && config.exposeApi()) {

            VelocityCombatAPI.Provider.register(
                    combatAPI
            );

        } else {

            combatAPI.setEnabled(
                    false
            );

            logger.info(
                    "VelocityCombatAPI exposure is disabled "
                            + "by configuration."
            );
        }

        /*
         * ======================================================
         * COMBAT STATE BRIDGE CHANNEL
         * ======================================================
         */

        if (config.isBridgeEnabled()) {

            proxy.getChannelRegistrar()
                    .register(
                            channelIdentifier
                    );

            logger.info(
                    "CombatStateBridge channel registered: {}",
                    config.getBridgeChannel()
            );

        } else {

            logger.info(
                    "CombatStateBridge is disabled by configuration."
            );
        }

        /*
         * ======================================================
         * SESSION + COMBAT LISTENER
         * ======================================================
         */

        sessionListener =
                new VelocitySessionListener(
                        sessionManager,
                        combatStateManager,
                        this::handleTransition,
                        logger,
                        config,
                        () -> channelIdentifier
                );

        proxy.getEventManager()
                .register(
                        this,
                        sessionListener
                );

        /*
         * ======================================================
         * STARTUP INFORMATION
         * ======================================================
         */

        logStartupInformation();
    }

    /*
     * ==========================================================
     * PROXY SHUTDOWN
     * ==========================================================
     */

    @Subscribe
    public void onProxyShutdown(
            ProxyShutdownEvent event
    ) {

        /*
         * ======================================================
         * API
         * ======================================================
         */

        if (combatAPI != null) {

            combatAPI.setEnabled(
                    false
            );

            try {

                VelocityCombatAPI.Provider.unregister(
                        combatAPI
                );

            } catch (IllegalStateException ignored) {
                /*
                 * API was already unregistered.
                 */
            }

            combatAPI = null;
        }

        /*
         * ======================================================
         * CHANNEL
         * ======================================================
         */

        if (channelIdentifier != null) {

            try {

                proxy.getChannelRegistrar()
                        .unregister(
                                channelIdentifier
                        );

            } catch (IllegalArgumentException ignored) {
                /*
                 * Channel was not registered.
                 */
            }
        }

        /*
         * ======================================================
         * COMBAT STATE
         * ======================================================
         */

        combatStateManager.clear();

        /*
         * ======================================================
         * SESSION STATE
         * ======================================================
         */

        sessionManager.clear();

        sessionListener = null;

        logger.info(
                "CombatKeepInventory Velocity module disabled."
        );
    }

    /*
     * ==========================================================
     * DATA FILES
     * ==========================================================
     */

    private void initializeDataFiles() {

        try {

            Files.createDirectories(
                    dataDirectory
            );

            createDefaultResource(
                    "config.yml"
            );

            createDefaultResource(
                    "message.yml"
            );

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Could not initialize CombatKeepInventory "
                            + "Velocity data directory.",
                    exception
            );
        }
    }

    private void createDefaultResource(
            String resourceName
    ) throws IOException {

        Path target =
                dataDirectory.resolve(
                        resourceName
                );

        /*
         * Never overwrite an existing user configuration.
         */

        if (Files.exists(target)) {
            return;
        }

        try (
                InputStream input =
                        CombatKeepInventoryVelocity.class
                                .getClassLoader()
                                .getResourceAsStream(
                                        resourceName
                                )
        ) {

            if (input == null) {

                throw new IOException(
                        "Embedded resource not found: "
                                + resourceName
                );
            }

            Path temporary =
                    dataDirectory.resolve(
                            resourceName
                                    + ".tmp"
                    );

            try (
                    OutputStream output =
                            Files.newOutputStream(
                                    temporary
                            )
            ) {

                input.transferTo(
                        output
                );
            }

            try {

                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE
                );

            } catch (IOException ignored) {

                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        }
    }

    /*
     * ==========================================================
     * CHANNEL
     * ==========================================================
     */

    private MinecraftChannelIdentifier
    createChannelIdentifier(
            String channel
    ) {

        String value =
                channel;

        if (value == null
                || value.isBlank()) {

            value =
                    CHANNEL;
        }

        value =
                value.trim();

        try {

            return MinecraftChannelIdentifier.from(
                    value
            );

        } catch (IllegalArgumentException exception) {

            logger.warn(
                    "Invalid bridge.channel '{}'. "
                            + "Falling back to '{}'.",
                    value,
                    CHANNEL
            );

            return CHANNEL_IDENTIFIER;
        }
    }

    /*
     * ==========================================================
     * SESSION TRANSITIONS
     * ==========================================================
     */

    private void handleTransition(
            SessionTransition transition
    ) {

        if (transition == null) {
            return;
        }

        switch (transition.getType()) {

            case CONNECT -> {

                if (config.logServerSwitch()) {

                    logger.info(
                            "Player {} connected to {}.",
                            transition.getPlayerId(),
                            transition.getToServer()
                    );
                }
            }

            case SERVER_SWITCH -> {

                /*
                 * ==================================================
                 * SERVER SWITCH
                 * ==================================================
                 *
                 * Velocity does NOT create combat state.
                 *
                 * It only updates backend metadata for an
                 * already synchronized authoritative CombatTag.
                 */

                if (config.trackServer()) {

                    combatStateManager.updateBackendServer(
                            transition.getPlayerId(),
                            transition.getToServer()
                    );
                }

                if (config.logServerSwitch()) {

                    logger.info(
                            "Player {} switched from {} to {}.",
                            transition.getPlayerId(),
                            transition.getFromServer(),
                            transition.getToServer()
                    );
                }

                /*
                 * Optional clearing on server switch.
                 */

                if (config.clearOnServerSwitch()) {

                    combatStateManager.forceEnd(
                            transition.getPlayerId()
                    );
                }
            }

            case CLUSTER_EXIT -> {

                /*
                 * If configured, clear mirrored combat state when
                 * the player leaves the entire Velocity cluster.
                 */

                if (config.clearOnDisconnect()) {

                    combatStateManager.forceEnd(
                            transition.getPlayerId()
                    );
                }

                if (config.logClusterExit()) {

                    logger.info(
                            "Player {} left the cluster from server {}.",
                            transition.getPlayerId(),
                            transition.getFromServer()
                    );
                }
            }
        }
    }

    /*
     * ==========================================================
     * ACCESSORS
     * ==========================================================
     */

    public ProxyServer getProxy() {

        return proxy;
    }

    public Logger getLogger() {

        return logger;
    }

    public Path getDataDirectory() {

        return dataDirectory;
    }

    public VelocityConfig getConfig() {

        return config;
    }

    public PlatformInfo getPlatform() {

        if (platformInfo == null) {

            throw new IllegalStateException(
                    "Platform information has not been initialized."
            );
        }

        return platformInfo;
    }

    public PlayerSessionManager getSessionManager() {

        return sessionManager;
    }

    public ProxyCombatStateManager
    getCombatStateManager() {

        return combatStateManager;
    }

    public CombatPunishmentService
    getPunishmentService() {

        return punishmentService;
    }

    public MinecraftChannelIdentifier
    getChannelIdentifier() {

        if (channelIdentifier == null) {

            throw new IllegalStateException(
                    "CombatStateBridge channel has not "
                            + "been initialized."
            );
        }

        return channelIdentifier;
    }

    public VelocityCombatAPI getCombatAPI() {

        if (combatAPI == null) {

            throw new IllegalStateException(
                    "VelocityCombatAPI has not been initialized."
            );
        }

        return combatAPI;
    }

    /*
     * ==========================================================
     * STARTUP INFORMATION
     * ==========================================================
     */

    private void logStartupInformation() {

        logger.info(
                "CombatKeepInventory Velocity module enabled."
        );

        logger.info(
                "Data directory: {}",
                dataDirectory
        );

        logger.info(
                "Configuration version: {}",
                config.getConfigVersion()
        );

        logger.info(
                "Module enabled: {}",
                config.isEnabled()
        );

        logger.info(
                "Debug: {}",
                config.isDebug()
        );

        /*
         * Platform.
         */

        logger.info(
                "Platform: {}",
                platformInfo.getType()
        );

        logger.info(
                "Implementation: {}",
                platformInfo.getImplementationName()
        );

        logger.info(
                "Implementation version: {}",
                platformInfo.getImplementationVersion()
        );

        logger.info(
                "Minecraft version: {}",
                platformInfo.getMinecraftVersion()
        );

        /*
         * Bridge.
         */

        logger.info(
                "CombatStateBridge: {}",
                config.isBridgeEnabled()
                        ? "ENABLED"
                        : "DISABLED"
        );

        logger.info(
                "CombatStateBridge channel: {}",
                config.getBridgeChannel()
        );

        logger.info(
                "CombatStateBridge protocol: {}",
                PROTOCOL_VERSION
        );

        /*
         * Server switch.
         */

        logger.info(
                "Server switch tracking: {}",
                config.isServerSwitchEnabled()
                        && config.trackServer()
        );

        /*
         * Cluster exit.
         */

        logger.info(
                "Cluster exit tracking: {}",
                config.isClusterExitEnabled()
        );

        /*
         * API.
         */

        logger.info(
                "VelocityCombatAPI: {}",
                combatAPI != null
                        && combatAPI.isEnabled()
                        ? "ENABLED"
                        : "DISABLED"
        );

        /*
         * Punishment.
         */

        logger.info(
                "Punishment service: {}",
                config.isPunishmentEnabled()
                        ? "ENABLED"
                        : "DISABLED"
        );
    }
}