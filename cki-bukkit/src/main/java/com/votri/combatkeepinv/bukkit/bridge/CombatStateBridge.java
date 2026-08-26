package com.votri.combatkeepinv.bukkit.bridge;

import com.votri.combatkeepinv.bukkit.CombatKeepInventory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.Messenger;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * Sends authoritative Bukkit CombatTag state to Velocity.
 *
 * <p>Bukkit is authoritative. Velocity is only a mirror.</p>
 */
public final class CombatStateBridge {

    public static final String CHANNEL =
            "votri:combat";

    public static final int PROTOCOL_VERSION = 1;

    public static final byte START = 1;
    public static final byte REFRESH = 2;
    public static final byte END = 3;
    public static final byte FORCE_END = 4;

    private final CombatKeepInventory plugin;

    private volatile boolean shutdown;

    public CombatStateBridge(
            CombatKeepInventory plugin
    ) {

        if (plugin == null) {
            throw new IllegalArgumentException(
                    "plugin cannot be null"
            );
        }

        this.plugin = plugin;

        Messenger messenger =
                plugin.getServer()
                        .getMessenger();

        messenger.registerOutgoingPluginChannel(
                plugin,
                CHANNEL
        );
    }

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

    public boolean publishEnd(
            UUID player
    ) {

        return sendSingleState(
                END,
                player
        );
    }

    public boolean publishForceEnd(
            UUID player
    ) {

        return sendSingleState(
                FORCE_END,
                player
        );
    }

    public void shutdown() {

        if (shutdown) {
            return;
        }

        shutdown = true;

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

        if (shutdown
                || attacker == null
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
                    "Cannot send "
                            + operationName(operation)
                            + ": no online source."
            );

            return false;
        }

        return send(
                source,
                operation,
                output -> {

                    writeUUID(
                            output,
                            attacker
                    );

                    writeUUID(
                            output,
                            victim
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

        if (shutdown
                || player == null) {

            return false;
        }

        Player source =
                plugin.getServer()
                        .getPlayer(player);

        if (source == null
                || !source.isOnline()) {

            debug(
                    "Cannot send "
                            + operationName(operation)
                            + " for "
                            + player
                            + ": player offline."
            );

            return false;
        }

        return send(
                source,
                operation,
                output ->
                        writeUUID(
                                output,
                                player
                        )
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

            writer.write(
                    output
            );

            output.flush();

            source.sendPluginMessage(
                    plugin,
                    CHANNEL,
                    bytes.toByteArray()
            );

            debug(
                    "Sent "
                            + operationName(operation)
                            + " via "
                            + source.getName()
            );

            return true;

        } catch (IOException exception) {

            plugin.getLogger().warning(
                    "Failed to encode CombatStateBridge "
                            + operationName(operation)
                            + ": "
                            + exception.getMessage()
            );

            return false;
        }
    }

    private static void writeUUID(
            DataOutputStream output,
            UUID uuid
    ) throws IOException {

        output.writeLong(
                uuid.getMostSignificantBits()
        );

        output.writeLong(
                uuid.getLeastSignificantBits()
        );
    }

    private void debug(
            String message
    ) {

        if (!plugin.getConfig()
                .getBoolean(
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