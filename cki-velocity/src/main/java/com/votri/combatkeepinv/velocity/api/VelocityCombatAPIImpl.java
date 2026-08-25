package com.votri.combatkeepinv.velocity.api;

import com.votri.combatkeepinv.core.api.CombatTag;
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

    private volatile boolean enabled;

    public VelocityCombatAPIImpl(
            ProxyServer proxy,
            ProxyCombatStateManager stateManager
    ) {

        this.proxy =
                proxy;

        this.stateManager =
                stateManager;

        this.punishmentService =
                new CombatPunishmentService(
                        proxy
                );

        this.enabled = true;
    }

    @Override
    public boolean isInCombat(
            UUID player
    ) {

        return stateManager.isInCombat(
                player
        );
    }

    @Override
    public ProxyCombatState getCombatState(
            UUID player
    ) {

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

        return stateManager.getRemainingMillis(
                player
        );
    }

    @Override
    public UUID getOpponent(
            UUID player
    ) {

        return stateManager.getOpponent(
                player
        );
    }

    @Override
    public String getBackendServer(
            UUID player
    ) {

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
        this.enabled = enabled;
    }
}