package com.votri.combatkeepinv.velocity;

import com.google.inject.Inject;
import com.votri.combatkeepinv.core.platform.PlatformInfo;
import com.votri.combatkeepinv.velocity.listener.VelocitySessionListener;
import com.votri.combatkeepinv.velocity.platform.VelocityPlatformDetector;
import com.votri.combatkeepinv.velocity.session.PlayerSessionManager;
import com.votri.combatkeepinv.velocity.session.SessionTransition;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
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

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    private final PlayerSessionManager sessionManager;

    private PlatformInfo platformInfo;

    private VelocitySessionListener sessionListener;

    @Inject
    public CombatKeepInventoryVelocity(
            ProxyServer proxy,
            Logger logger
    ) {
        this.proxy = proxy;
        this.logger = logger;

        this.dataDirectory =
                proxy.getPluginsDirectory()
                        .resolve("CombatKeepInventory");

        this.sessionManager =
                new PlayerSessionManager();
    }

    @Subscribe
    public void onProxyInitialization(
            ProxyInitializeEvent event
    ) {

        initializeDataFiles();

        platformInfo =
                VelocityPlatformDetector.detect(
                        proxy
                );

        sessionListener =
                new VelocitySessionListener(
                        sessionManager,
                        this::handleTransition
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

        if (sessionManager != null) {
            sessionManager.clear();
        }

        logger.info(
                "CombatKeepInventory Velocity module disabled."
        );
    }

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

            Files.move(
                    temporary,
                    target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        }
    }

    private void handleTransition(
            SessionTransition transition
    ) {

        switch (transition.getType()) {

            case CONNECT -> logger.debug(
                    "Player {} connected to {}.",
                    transition.getPlayerId(),
                    transition.getToServer()
            );

            case SERVER_SWITCH -> logger.debug(
                    "Player {} switched from {} to {}.",
                    transition.getPlayerId(),
                    transition.getFromServer(),
                    transition.getToServer()
            );

            case CLUSTER_EXIT -> logger.info(
                    "Player {} left the cluster from server {}.",
                    transition.getPlayerId(),
                    transition.getFromServer()
            );
        }
    }

    private void logStartupInformation() {

        logger.info(
                "CombatKeepInventory Velocity module enabled."
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
    }

    public ProxyServer getProxy() {
        return proxy;
    }

    public Logger getLogger() {
        return logger;
    }

    public Path getDataDirectory() {
        return dataDirectory;
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
}