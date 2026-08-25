package com.votri.combatkeepinv.velocity.api;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Velocity-side punishment service for combat integrations.
 *
 * <p>
 * This service intentionally does not decide when a player
 * should be punished. External plugins make that decision.
 * </p>
 */
public final class CombatPunishmentService {

    private final ProxyServer proxy;

    public CombatPunishmentService(
            ProxyServer proxy
    ) {
        this.proxy = proxy;
    }

    /**
     * Disconnects a player from the network.
     */
    public boolean disconnect(
            UUID playerId,
            Component reason
    ) {

        if (playerId == null
                || reason == null) {
            return false;
        }

        Optional<Player> player =
                proxy.getPlayer(
                        playerId
                );

        if (player.isEmpty()) {
            return false;
        }

        player.get().disconnect(
                reason
        );

        return true;
    }

    /**
     * Disconnects a player using plain text.
     */
    public boolean disconnect(
            UUID playerId,
            String reason
    ) {

        return disconnect(
                playerId,
                Component.text(
                        reason == null
                                ? "Disconnected."
                                : reason
                )
        );
    }

    /**
     * Returns the online Velocity player.
     */
    public Optional<Player> getPlayer(
            UUID playerId
    ) {

        if (playerId == null) {
            return Optional.empty();
        }

        return proxy.getPlayer(
                playerId
        );
    }
}