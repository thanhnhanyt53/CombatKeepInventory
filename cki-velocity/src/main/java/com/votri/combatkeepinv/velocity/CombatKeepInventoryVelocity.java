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
        version = "1.1.0-SNAPSHOT-build5",
        authors = {
                "ThanhNhan"
        }
)
public final class CombatKeepInventoryVelocity {

    public static final int PROTOCOL_VERSION = 1;

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    private final PlayerSessionManager sessionManager;
    private ProxyCombatStateManager combatStateManager;

    private PlatformInfo platformInfo;

    private VelocityConfig config;
    private VelocityCombatAPIImpl combatAPI;
    private CombatPunishmentService punishmentService;
    private VelocitySessionListener sessionListener;

    private MinecraftChannelIdentifier channelIdentifier;

    private volatile boolean initialized;

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

        this.sessionManager =
                new PlayerSessionManager();

        this.combatStateManager =
                new ProxyCombatStateManager();
    }

    @Subscribe
    public void onProxyInitialization(
            ProxyInitializeEvent event
    ) {

        initializeDataFiles();

        config =
        new VelocityConfig(
                dataDirectory
        );

config.load();

combatStateManager =
        new ProxyCombatStateManager(
                config
        );

        if (!config.isEnabled()) {

            logger.info(
                    "CombatKeepInventory Velocity is disabled by config."
            );

            return;
        }

        platformInfo =
                VelocityPlatformDetector.detect(
                        proxy
                );

        /*
         * ======================================================
         * COMBAT SERVICES
         * ======================================================
         */

        punishmentService =
                new CombatPunishmentService(
                        proxy,
                        config
                );

        combatAPI =
                new VelocityCombatAPIImpl(
                        proxy,
                        combatStateManager,
                        punishmentService,
                        config
                );

        if (config.isApiEnabled()
                && config.exposeApi()) {

            VelocityCombatAPI.Provider.register(
                    combatAPI
            );
        }

        /*
         * ======================================================
         * BRIDGE CHANNEL
         * ======================================================
         */

        if (config.isBridgeEnabled()) {

            channelIdentifier =
                    MinecraftChannelIdentifier.from(
                            config.getBridgeChannel()
                    );

            proxy.getChannelRegistrar()
                    .register(
                            channelIdentifier
                    );
        }

        /*
         * ======================================================
         * SESSION LISTENER
         * ======================================================
         */

        sessionListener =
                new VelocitySessionListener(
                        sessionManager,
                        combatStateManager,
                        this::handleTransition,
                        logger,
                        config,
                        this::getChannelIdentifier
                );

        proxy.getEventManager()
                .register(
                        this,
                        sessionListener
                );

        initialized = true;

        logStartupInformation();
    }

    @Subscribe
    public void onProxyShutdown(
            ProxyShutdownEvent event
    ) {

        initialized = false;

        if (combatAPI != null) {

            combatAPI.setEnabled(false);

            VelocityCombatAPI.Provider.unregister(
                    combatAPI
            );

            combatAPI = null;
        }

        if (channelIdentifier != null) {

            proxy.getChannelRegistrar()
                    .unregister(
                            channelIdentifier
                    );

            channelIdentifier = null;
        }

        combatStateManager.clear();

        sessionManager.clear();

        sessionListener = null;
        punishmentService = null;
        config = null;

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
                            resourceName + ".tmp"
                    );

            try (
                    OutputStream output =
                            Files.newOutputStream(
                                    temporary
                            )
            ) {

                input.transferTo(output);
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
     * RELOAD
     * ==========================================================
     */

    public synchronized void reloadConfiguration() {

        if (config == null) {
            return;
        }

        config.reload();

        logger.info(
                "CombatKeepInventory Velocity configuration reloaded."
        );
    }

    /*
     * ==========================================================
     * SESSION TRANSITIONS
     * ==========================================================
     */

    private void handleTransition(
            SessionTransition transition
    ) {

        if (transition == null
                || config == null) {

            return;
        }

        switch (transition.getType()) {

            case CONNECT -> {

                if (config.logServerSwitch()) {

                    logger.info(
                            "[CombatSession] {} connected to {}.",
                            transition.getPlayerId(),
                            transition.getToServer()
                    );
                }
            }

            case SERVER_SWITCH -> {

                if (!config.isServerSwitchEnabled()) {
                    return;
                }

                if (config.trackServer()) {

                    combatStateManager.updateBackendServer(
                            transition.getPlayerId(),
                            transition.getToServer()
                    );
                }

                boolean inCombat =
                        combatStateManager.isInCombat(
                                transition.getPlayerId()
                        );

                if (config.checkCombatOnServerSwitch()
                        && config.requireActiveCombatOnServerSwitch()
                        && inCombat
                        && config.isPunishmentEnabled()
                        && config.isSwitchPunishmentEnabled()) {

                    punishmentService.executeCommands(
                            transition.getPlayerId(),
                            config.getSwitchPunishmentCommands(),
                            "SERVER_SWITCH"
                    );
                }

                if (config.clearOnServerSwitch()) {

                    combatStateManager.forceEnd(
                            transition.getPlayerId()
                    );
                }

                if (config.logServerSwitch()) {

                    logger.info(
                            "[CombatSession] {} switched {} -> {}.",
                            transition.getPlayerId(),
                            transition.getFromServer(),
                            transition.getToServer()
                    );
                }
            }

            case CLUSTER_EXIT -> {

                if (!config.isClusterExitEnabled()) {
                    return;
                }

                boolean inCombat =
                        combatStateManager.isInCombat(
                                transition.getPlayerId()
                        );

                if (config.checkCombatOnClusterExit()
                        && config.requireActiveCombatOnClusterExit()
                        && inCombat
                        && config.isPunishmentEnabled()
                        && config.isClusterExitPunishmentEnabled()) {

                    punishmentService.executeCommands(
                            transition.getPlayerId(),
                            config.getClusterExitPunishmentCommands(),
                            "CLUSTER_EXIT"
                    );
                }

                if (config.clearOnDisconnect()) {

                    combatStateManager.forceEnd(
                            transition.getPlayerId()
                    );
                }

                if (config.logClusterExit()) {

                    logger.info(
                            "[CombatSession] {} left cluster from {}.",
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

        if (config == null) {

            throw new IllegalStateException(
                    "VelocityConfig has not been initialized."
            );
        }

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

    public ProxyCombatStateManager getCombatStateManager() {
        return combatStateManager;
    }

    public CombatPunishmentService getPunishmentService() {

        if (punishmentService == null) {

            throw new IllegalStateException(
                    "CombatPunishmentService has not been initialized."
            );
        }

        return punishmentService;
    }

    public VelocityCombatAPI getCombatAPI() {

        if (combatAPI == null) {

            throw new IllegalStateException(
                    "VelocityCombatAPI has not been initialized."
            );
        }

        return combatAPI;
    }

    public MinecraftChannelIdentifier
    getChannelIdentifier() {

        return channelIdentifier;
    }

    public boolean isInitialized() {
        return initialized;
    }

    private void logStartupInformation() {

        logger.info(
                "CombatKeepInventory Velocity module enabled."
        );

        logger.info(
                "Config version: {}",
                config.getConfigVersion()
        );

        logger.info(
                "Data directory: {}",
                dataDirectory
        );

        logger.info(
                "Platform: {}",
                platformInfo.getType()
        );

        logger.info(
                "Minecraft version: {}",
                platformInfo.getMinecraftVersion()
        );

        logger.info(
                "Bridge: {}",
                config.isBridgeEnabled()
                        ? "ENABLED"
                        : "DISABLED"
        );

        logger.info(
                "Bridge channel: {}",
                config.getBridgeChannel()
        );

        logger.info(
                "Velocity API: {}",
                config.isApiEnabled()
                        && config.exposeApi()
                        ? "ENABLED"
                        : "DISABLED"
        );
    }
}