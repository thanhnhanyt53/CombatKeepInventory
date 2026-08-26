package com.votri.combatkeepinv.velocity;

import com.google.inject.Inject;

import com.votri.combatkeepinv.core.platform.PlatformInfo;
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

    public static final String CHANNEL =
            "votri:combat";

    public static final int PROTOCOL_VERSION =
            1;

    public static final MinecraftChannelIdentifier
            CHANNEL_IDENTIFIER =
            MinecraftChannelIdentifier.from(
                    CHANNEL
            );

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    private final PlayerSessionManager sessionManager;

    private final VelocityConfig velocityConfig;

    private final ProxyCombatStateManager combatStateManager;

    private PlatformInfo platformInfo;

    private VelocityCombatAPIImpl combatAPI;

    private VelocitySessionListener sessionListener;

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
         * Session manager does not depend on config.
         */
        this.sessionManager =
                new PlayerSessionManager();

        /*
         * Create the central configuration object.
         *
         * The actual YAML is loaded during proxy initialization
         * after the default config file has been created.
         */
        this.velocityConfig =
                new VelocityConfig(
                        dataDirectory
                );

        /*
         * The state manager receives the same configuration
         * instance used by the rest of the Velocity module.
         */
        this.combatStateManager =
                new ProxyCombatStateManager(
                        velocityConfig
                );
    }

    @Subscribe
    public void onProxyInitialization(
            ProxyInitializeEvent event
    ) {

        /*
         * ==========================================================
         * DATA
         * ==========================================================
         */

        initializeDataFiles();

        /*
         * ==========================================================
         * CONFIGURATION
         * ==========================================================
         */

        velocityConfig.load();

        if (!velocityConfig.isEnabled()) {

            logger.warn(
                    "CombatKeepInventory Velocity module "
                            + "is disabled by config.yml."
            );

            return;
        }

        /*
         * ==========================================================
         * PLATFORM
         * ==========================================================
         */

        platformInfo =
                VelocityPlatformDetector.detect(
                        proxy
                );

        /*
         * ==========================================================
         * COMBAT API
         * ==========================================================
         */

        if (velocityConfig.isApiEnabled()
                && velocityConfig.exposeApi()) {

            combatAPI =
                    new VelocityCombatAPIImpl(
                            proxy,
                            combatStateManager
                    );

            VelocityCombatAPI.Provider.register(
                    combatAPI
            );
        }

        /*
         * ==========================================================
         * COMBAT CHANNEL
         * ==========================================================
         */

        if (velocityConfig.isBridgeEnabled()) {

            proxy.getChannelRegistrar()
                    .register(
                            CHANNEL_IDENTIFIER
                    );
        }

        /*
         * ==========================================================
         * SESSION + COMBAT LISTENER
         * ==========================================================
         */

        sessionListener =
                new VelocitySessionListener(
                        sessionManager,
                        combatStateManager,
                        this::handleTransition,
                        logger
                );

        proxy.getEventManager()
                .register(
                        this,
                        sessionListener
                );

        logStartupInformation();
    }

    @Subscribe
    public void onProxyShutdown(
            ProxyShutdownEvent event
    ) {

        /*
         * Disable API first so external plugins cannot
         * continue using a shutting-down instance.
         */
        if (combatAPI != null) {

            combatAPI.setEnabled(false);

            VelocityCombatAPI.Provider.unregister(
                    combatAPI
            );

            combatAPI = null;
        }

        /*
         * Remove channel registration.
         */
        if (velocityConfig.isBridgeEnabled()) {

            proxy.getChannelRegistrar()
                    .unregister(
                            CHANNEL_IDENTIFIER
                    );
        }

        /*
         * Clear mirrored combat state.
         */
        combatStateManager.clear();

        /*
         * Clear session state.
         */
        sessionManager.clear();

        sessionListener = null;

        logger.info(
                "CombatKeepInventory Velocity module disabled."
        );
    }

    /*
     * ==========================================================
     * DATA
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

                if (velocityConfig.logServerSwitch()) {

                    logger.debug(
                            "Player {} connected to {}.",
                            transition.getPlayerId(),
                            transition.getToServer()
                    );
                }
            }

            case SERVER_SWITCH -> {

                /*
                 * Combat state itself is NOT created here.
                 *
                 * Bukkit remains authoritative.
                 *
                 * Only backend metadata is updated when
                 * an active mirrored state already exists.
                 */
                if (velocityConfig.isServerSwitchEnabled()
                        && velocityConfig.trackServer()) {

                    combatStateManager.updateBackendServer(
                            transition.getPlayerId(),
                            transition.getToServer()
                    );
                }

                if (velocityConfig.logServerSwitch()) {

                    logger.debug(
                            "Player {} switched from {} to {}.",
                            transition.getPlayerId(),
                            transition.getFromServer(),
                            transition.getToServer()
                    );
                }

                /*
                 * Optional clearing behavior remains controlled
                 * by VelocityConfig.
                 */
                if (velocityConfig.clearOnServerSwitch()) {

                    combatStateManager.end(
                            transition.getPlayerId()
                    );
                }
            }

            case CLUSTER_EXIT -> {

                /*
                 * Cluster exit must never create combat state.
                 *
                 * The proxy only removes its mirrored state.
                 */
                if (velocityConfig.clearOnDisconnect()
                        || velocityConfig.checkCombatOnClusterExit()) {

                    combatStateManager.forceEnd(
                            transition.getPlayerId()
                    );
                }

                if (velocityConfig.logClusterExit()) {

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

    public VelocityConfig getVelocityConfig() {
        return velocityConfig;
    }

    public PlayerSessionManager getSessionManager() {
        return sessionManager;
    }

    public ProxyCombatStateManager
    getCombatStateManager() {

        return combatStateManager;
    }

    public VelocityCombatAPI getCombatAPI() {

        if (combatAPI == null) {

            throw new IllegalStateException(
                    "VelocityCombatAPI has not been initialized."
            );
        }

        return combatAPI;
    }

    public PlatformInfo getPlatform() {

        if (platformInfo == null) {

            throw new IllegalStateException(
                    "Platform information has not been initialized."
            );
        }

        return platformInfo;
    }

    /*
     * ==========================================================
     * STARTUP
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
                "Config version: {}",
                velocityConfig.getConfigVersion()
        );

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

        logger.info(
                "CombatStateBridge channel: {}",
                velocityConfig.getBridgeChannel()
        );

        logger.info(
                "CombatStateBridge protocol: {}",
                PROTOCOL_VERSION
        );

        logger.info(
                "CombatStateBridge: {}",
                velocityConfig.isBridgeEnabled()
                        ? "ENABLED"
                        : "DISABLED"
        );

        logger.info(
                "VelocityCombatAPI: {}",
                combatAPI != null
                        ? "ENABLED"
                        : "DISABLED"
        );
    }
}