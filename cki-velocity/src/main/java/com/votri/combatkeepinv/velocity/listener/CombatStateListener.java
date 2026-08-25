package com.votri.combatkeepinv.velocity.listener;

import com.votri.combatkeepinv.velocity.api.ProxyCombatStateManager;
import com.votri.combatkeepinv.velocity.bridge.CombatStateProtocol;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * Receives authoritative combat-state updates from Bukkit CKI.
 *
 * <p>
 * Velocity never creates CombatTag state from damage events.
 * It only mirrors authenticated backend state.
 * </p>
 */
public final class CombatStateListener {

    private final ProxyCombatStateManager stateManager;

    public CombatStateListener(
            ProxyCombatStateManager stateManager
    ) {

        this.stateManager =
                stateManager;
    }

    @Subscribe
    public void onPluginMessage(
            PluginMessageEvent event
    ) {

        if (!MinecraftChannelIdentifier
                .forId(
                        CombatStateProtocol.CHANNEL
                )
                .equals(
                        event.getIdentifier()
                )) {

            return;
        }

        /*
         * SECURITY:
         *
         * Only backend ServerConnection is trusted.
         * A client Player source must never be allowed
         * to modify proxy combat state.
         */
        if (!(event.getSource()
                instanceof ServerConnection backend)) {

            event.setResult(
                    PluginMessageEvent.ForwardResult
                            .handled()
            );

            return;
        }

        event.setResult(
                PluginMessageEvent.ForwardResult
                        .handled()
        );

        String backendName =
                backend.getServerInfo()
                        .getName();

        try {

            DataInputStream input =
                    new DataInputStream(
                            event.dataAsInputStream()
                    );

            int version =
                    input.readInt();

            if (version
                    != CombatStateProtocol.VERSION) {

                return;
            }

            byte operation =
                    input.readByte();

            switch (operation) {

                case CombatStateProtocol.START ->
                        handlePair(
                                input,
                                backendName,
                                false
                        );

                case CombatStateProtocol.REFRESH ->
                        handlePair(
                                input,
                                backendName,
                                true
                        );

                case CombatStateProtocol.END -> {

                    UUID player =
                            readUuid(input);

                    stateManager.end(
                            player
                    );
                }

                case CombatStateProtocol.FORCE_END -> {

                    UUID player =
                            readUuid(input);

                    stateManager.forceEnd(
                            player
                    );
                }

                default -> {
                    // Unknown operation.
                }
            }

        } catch (IOException exception) {

            // Malformed plugin messages are ignored.
        }
    }

    private void handlePair(
            DataInputStream input,
            String backendName,
            boolean refresh
    ) throws IOException {

        UUID attacker =
                readUuid(input);

        UUID victim =
                readUuid(input);

        long expiresAt =
                input.readLong();

        if (refresh) {

            stateManager.refresh(
                    attacker,
                    victim,
                    backendName,
                    expiresAt
            );

        } else {

            stateManager.start(
                    attacker,
                    victim,
                    backendName,
                    expiresAt
            );
        }
    }

    private UUID readUuid(
            DataInputStream input
    ) throws IOException {

        return new UUID(
                input.readLong(),
                input.readLong()
        );
    }
}