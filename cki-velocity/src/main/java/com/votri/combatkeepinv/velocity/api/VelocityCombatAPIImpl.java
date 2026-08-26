package com.votri.combatkeepinv.velocity.api;

import com.votri.combatkeepinv.core.api.CombatTag;
import com.votri.combatkeepinv.velocity.config.VelocityConfig;

import com.velocitypowered.api.proxy.ProxyServer;

import java.util.UUID;

/**
 * Internal implementation of VelocityCombatAPI.
 */
public final class VelocityCombatAPIImpl
        implements VelocityCombatAPI {

    private final ProxyServer proxy;
    private final ProxyCombatStateManager stateManager;
    private final CombatPunishmentService punishmentService;
    private final VelocityConfig config;

    private volatile boolean enabled;

    public VelocityCombatAPIImpl(
            ProxyServer proxy,
            ProxyCombatStateManager stateManager,
            CombatPunishmentService punishmentService,
            VelocityConfig config
    ) {

        this.proxy =
                proxy;

        this.stateManager =
                stateManager;

        this.punishmentService =
                punishmentService;

        this.config =
                config;

        this.enabled =
                config.isApiEnabled()
                        && config.exposeApi();
    }

    @Override
    public boolean isInCombat(
            UUID player
    ) {

        return enabled
                && stateManager.isInCombat(
                player
        );
    }

    @Override
    public ProxyCombatState getCombatState(
            UUID player
    ) {

        if (!enabled) {
            return null;
        }

        return stateManager.getCombatState(
                player
        );
    }

    @Override
    public CombatTag getCombatTag(
            UUID player
    ) {

        return getCombatState(
                player
        );
    }

    @Override
    public long getRemainingCombatMillis(
            UUID player
    ) {

        if (!enabled) {
            return 0L;
        }

        return stateManager.getRemainingMillis(
                player
        );
    }

    @Override
    public UUID getOpponent(
            UUID player
    ) {

        if (!enabled) {
            return null;
        }

        return stateManager.getOpponent(
                player
        );
    }

    @Override
    public String getBackendServer(
            UUID player
    ) {

        if (!enabled) {
            return null;
        }

        return stateManager.getBackendServer(
                player
        );
    }

    @Override
    public CombatPunishmentService punishments() {

        return punishmentService;
    }

    @Override
    public boolean isEnabled() {

        return enabled;
    }

    public void setEnabled(
            boolean enabled
    ) {

        this.enabled =
                enabled;
    }
}