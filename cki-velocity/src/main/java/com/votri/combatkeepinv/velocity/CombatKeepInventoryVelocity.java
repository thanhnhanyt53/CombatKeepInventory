package com.votri.combatkeepinv.velocity;

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

import javax.inject.Inject;

/**
 * CombatKeepInventory Velocity module.
 *
 * <p>This module is responsible for proxy-level player session tracking.
 * Combat and inventory manipulation remain backend responsibilities.</p>
 */
@Plugin(
        id = "combatkeepinventory",
        name = "CombatKeepInventory",
        version = "1.1.0-SNAPSHOT-build2",
        authors = {
                "Vô Tri"
        }
)
public final class CombatKeepInventoryVelocity {

    private final ProxyServer proxy;
    private final Logger logger;

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

        this.sessionManager =
                new PlayerSessionManager();
    }

    @Subscribe
    public void onProxyInitialization(
            ProxyInitializeEvent event
    ) {
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

    private void handleTransition(
            SessionTransition transition
    ) {

        switch (transition.getType()) {

            case CONNECT -> {

                logger.debug(
                        "Player {} connected to {}.",
                        transition.getPlayerId(),
                        transition.getToServer()
                );
            }

            case SERVER_SWITCH -> {

                logger.debug(
                        "Player {} switched from {} to {}.",
                        transition.getPlayerId(),
                        transition.getFromServer(),
                        transition.getToServer()
                );
            }

            case CLUSTER_EXIT -> {

                logger.info(
                        "Player {} left the cluster from server {}.",
                        transition.getPlayerId(),
                        transition.getFromServer()
                );

                /*
                 * This is the point where the future CKI proxy
                 * combat-logout service will inspect combat state
                 * received from the backend.
                 */
            }
        }
    }

    private void logStartupInformation() {

        logger.info(
                "CombatKeepInventory Velocity module enabled."
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