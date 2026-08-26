package com.votri.combatkeepinv.velocity.api;

import com.votri.combatkeepinv.velocity.config.VelocityConfig;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Velocity-side punishment service.
 *
 * <p>This service executes punishment primitives configured
 * in config.yml. The decision to call it still belongs to
 * the Velocity module / external integrations.</p>
 */
public final class CombatPunishmentService {

    private final ProxyServer proxy;
    private final VelocityConfig config;

    public CombatPunishmentService(
            ProxyServer proxy,
            VelocityConfig config
    ) {

        this.proxy =
                proxy;

        this.config =
                config;
    }

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

    /**
     * Executes configured Velocity console commands.
     *
     * <p>Supported placeholders:</p>
     * <ul>
     *     <li>{player}</li>
     *     <li>{uuid}</li>
     *     <li>{reason}</li>
     * </ul>
     */
    public boolean executeCommands(
            UUID playerId,
            List<String> commands,
            String reason
    ) {

        if (!config.isPunishmentEnabled()) {
            return false;
        }

        if (playerId == null
                || commands == null
                || commands.isEmpty()) {

            return false;
        }

        String playerName =
                proxy.getPlayer(playerId)
                        .map(Player::getUsername)
                        .orElse(
                                playerId.toString()
                        );

        boolean executed =
                false;

        for (String command :
                commands) {

            if (command == null
                    || command.isBlank()) {

                continue;
            }

            String parsed =
                    command
                            .replace(
                                    "{player}",
                                    playerName
                            )
                            .replace(
                                    "{uuid}",
                                    playerId.toString()
                            )
                            .replace(
                                    "{reason}",
                                    reason == null
                                            ? ""
                                            : reason
                            );

            proxy.getCommandManager()
                    .executeImmediatelyAsync(
                            proxy.getConsoleCommandSource(),
                            parsed
                    );

            executed = true;

            if (config.logPunishment()) {

                // Logging is intentionally kept lightweight here.
                // The main module controls higher-level event logs.
                System.out.println(
                        "[CombatKeepInventory] "
                                + "Velocity punishment command executed: "
                                + parsed
                );
            }
        }

        return executed;
    }
}