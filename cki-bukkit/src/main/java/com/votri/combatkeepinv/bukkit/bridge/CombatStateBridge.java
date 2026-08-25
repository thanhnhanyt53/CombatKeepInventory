package com.votri.combatkeepinv.bukkit.bridge;

import com.votri.combatkeepinv.bukkit.CombatKeepInventory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.Messenger;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * Bridges authoritative Bukkit CombatTag state to Velocity.
 *
 * <p>
 * Bukkit is the authoritative combat-state owner.
 * Velocity only mirrors the state received through this bridge.
 * </p>
 */
public final class CombatStateBridge {

    public static final String CHANNEL =
            "votri:combat";

    public static final int PROTOCOL_VERSION = 1;

    private static final byte START = 1;
    private static final byte REFRESH = 2;
    private static final byte END = 3;
    private static final byte FORCE_END = 4;

    private final CombatKeepInventory plugin;

    public CombatStateBridge(
            CombatKeepInventory plugin
    ) {
        this.plugin = plugin;

        Messenger messenger =
                plugin.getServer()
                        .getMessenger();

        messenger.registerOutgoingPluginChannel(
                plugin,
                CHANNEL
        );
    }

    /**
     * Sends a START state for both players.
     */
    public boolean publishStart(
            UUID attacker,
            UUID victim,
            long expiresAt
    ) {
        return sendPairState(
                START,
                attacker,
                victim,
                expiresAt
        );
    }

    /**
     * Sends a REFRESH state for both players.
     */
    public boolean publishRefresh(
            UUID attacker,
            UUID victim,
            long expiresAt
    ) {
        return sendPairState(
                REFRESH,
                attacker,
                victim,
                expiresAt
        );
    }

    /**
     * Ends combat for one player.
     */
    public boolean publishEnd(
            UUID player
    ) {
        return sendSingleState(
                END,
                player
        );
    }

    /**
     * Forcefully ends combat for one player.
     */
    public boolean publishForceEnd(
            UUID player
    ) {
        return sendSingleState(
                FORCE_END,
                player
        );
    }

    /**
     * Unregisters the outgoing plugin channel.
     */
    public void shutdown() {

        plugin.getServer()
                .getMessenger()
                .unregisterOutgoingPluginChannel(
                        plugin,
                        CHANNEL
                );
    }

    private boolean sendPairState(
            byte operation,
            UUID attacker,
            UUID victim,
            long expiresAt
    ) {

        if (attacker == null
                || victim == null
                || attacker.equals(victim)) {

            return false;
        }

        Player source =
                plugin.getServer()
                        .getPlayer(attacker);

        if (source == null
                || !source.isOnline()) {

            source =
                    plugin.getServer()
                            .getPlayer(victim);
        }

        if (source == null
                || !source.isOnline()) {

            debug(
                    "Could not bridge "
                            + operationName(operation)
                            + ": no online source player."
            );

            return false;
        }

        return send(
                source,
                operation,
                output -> {

                    output.writeLong(
                            attacker.getMostSignificantBits()
                    );

                    output.writeLong(
                            attacker.getLeastSignificantBits()
                    );

                    output.writeLong(
                            victim.getMostSignificantBits()
                    );

                    output.writeLong(
                            victim.getLeastSignificantBits()
                    );

                    output.writeLong(
                            expiresAt
                    );
                }
        );
    }

    private boolean sendSingleState(
            byte operation,
            UUID player
    ) {

        if (player == null) {
            return false;
        }

        Player source =
                plugin.getServer()
                        .getPlayer(player);

        if (source == null
                || !source.isOnline()) {

            debug(
                    "Could not bridge "
                            + operationName(operation)
                            + " for "
                            + player
                            + ": player is offline."
            );

            return false;
        }

        return send(
                source,
                operation,
                output -> {

                    output.writeLong(
                            player.getMostSignificantBits()
                    );

                    output.writeLong(
                            player.getLeastSignificantBits()
                    );
                }
        );
    }

    private boolean send(
            Player source,
            byte operation,
            PayloadWriter writer
    ) {

        try {

            ByteArrayOutputStream bytes =
                    new ByteArrayOutputStream();

            DataOutputStream output =
                    new DataOutputStream(bytes);

            output.writeInt(
                    PROTOCOL_VERSION
            );

            output.writeByte(
                    operation
            );

            writer.write(output);

            output.flush();

            source.sendPluginMessage(
                    plugin,
                    CHANNEL,
                    bytes.toByteArray()
            );

            debug(
                    "Sent "
                            + operationName(operation)
                            + " from "
                            + source.getName()
            );

            return true;

        } catch (IOException exception) {

            plugin.getLogger().warning(
                    "Could not encode CombatStateBridge message: "
                            + exception.getMessage()
            );

            return false;
        }
    }

    private void debug(
            String message
    ) {

        if (!plugin.getConfig().getBoolean(
                "debug.combat",
                false
        )) {
            return;
        }

        plugin.getLogger().info(
                "[CombatBridge] "
                        + message
        );
    }

    private String operationName(
            byte operation
    ) {

        return switch (operation) {

            case START ->
                    "START";

            case REFRESH ->
                    "REFRESH";

            case END ->
                    "END";

            case FORCE_END ->
                    "FORCE_END";

            default ->
                    "UNKNOWN";
        };
    }

    @FunctionalInterface
    private interface PayloadWriter {

        void write(
                DataOutputStream output
        ) throws IOException;
    }
}