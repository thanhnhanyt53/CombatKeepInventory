package com.votri.combatkeepinv.velocity.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central configuration for the Velocity module.
 *
 * <p>No other Velocity class should parse config.yml directly.</p>
 */
public final class VelocityConfig {

    private final Path file;

    private volatile Map<String, Object> root =
            Map.of();

    public VelocityConfig(Path dataDirectory) {

        this.file =
                dataDirectory.resolve("config.yml");
    }

    public Path getFile() {
        return file;
    }

    public synchronized void load() {

        if (!Files.exists(file)) {

            throw new IllegalStateException(
                    "Velocity config.yml does not exist: "
                            + file
            );
        }

        try (InputStream input =
                     Files.newInputStream(file)) {

            Object value =
                    new Yaml().load(input);

            if (!(value instanceof Map<?, ?> map)) {

                root = Map.of();

                return;
            }

            root =
                    normalizeMap(map);

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Could not load Velocity config.yml.",
                    exception
            );
        }
    }

    public synchronized void reload() {
        load();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeMap(
            Map<?, ?> source
    ) {

        Map<String, Object> result =
                new LinkedHashMap<>();

        for (Map.Entry<?, ?> entry :
                source.entrySet()) {

            if (!(entry.getKey()
                    instanceof String key)) {

                continue;
            }

            Object value =
                    entry.getValue();

            if (value instanceof Map<?, ?> child) {

                value =
                        normalizeMap(child);

            } else if (value instanceof List<?> list) {

                value =
                        normalizeList(list);
            }

            result.put(
                    key,
                    value
            );
        }

        return result;
    }

    private List<Object> normalizeList(
            List<?> source
    ) {

        List<Object> result =
                new ArrayList<>(
                        source.size()
                );

        for (Object value : source) {

            if (value instanceof Map<?, ?> map) {

                result.add(
                        normalizeMap(map)
                );

            } else if (value instanceof List<?> list) {

                result.add(
                        normalizeList(list)
                );

            } else {

                result.add(value);
            }
        }

        return List.copyOf(result);
    }

    /*
     * ==========================================================
     * GENERAL
     * ==========================================================
     */

    public int getConfigVersion() {

        return getInt(
                "config-version",
                1
        );
    }

    public boolean isEnabled() {

        return getBoolean(
                "general.enabled",
                getBoolean(
                        "enabled",
                        true
                )
        );
    }

    public boolean isDebug() {

        return getBoolean(
                "general.debug",
                getBoolean(
                        "debug",
                        false
                )
        );
    }

    /*
     * ==========================================================
     * BRIDGE
     * ==========================================================
     */

    public boolean isBridgeEnabled() {

        return getBoolean(
                "bridge.enabled",
                true
        );
    }

    public String getBridgeChannel() {

        return getString(
                "bridge.channel",
                "votri:combat"
        );
    }

    public boolean verifyBridgeSourceServer() {

        return getBoolean(
                "bridge.verify-source-server",
                true
        );
    }

    public List<String> getAllowedBridgeServers() {

        return getStringList(
                "bridge.allowed-servers"
        );
    }

    /*
     * ==========================================================
     * COMBAT
     * ==========================================================
     */

    public long getStateTimeoutSeconds() {

        return Math.max(
                1L,
                getLong(
                        "combat.state-timeout-seconds",
                        15L
                )
        );
    }

    public boolean clearOnDisconnect() {

        return getBoolean(
                "combat.clear-on-disconnect",
                true
        );
    }

    public boolean clearOnServerSwitch() {

        return getBoolean(
                "combat.clear-on-server-switch",
                false
        );
    }

    /*
     * ==========================================================
     * SERVER SWITCH
     * ==========================================================
     */

    public boolean isServerSwitchEnabled() {

        return getBoolean(
                "server-switch.enabled",
                true
        );
    }

    public boolean trackServer() {

        return getBoolean(
                "server-switch.track-server",
                true
        );
    }

    public boolean checkCombatOnServerSwitch() {

        return getBoolean(
                "server-switch.check-combat-on-switch",
                true
        );
    }

    public boolean requireActiveCombatOnServerSwitch() {

        return getBoolean(
                "server-switch.require-active-combat",
                true
        );
    }

    /*
     * ==========================================================
     * CLUSTER EXIT
     * ==========================================================
     */

    public boolean isClusterExitEnabled() {

        return getBoolean(
                "cluster-exit.enabled",
                true
        );
    }

    public boolean checkCombatOnClusterExit() {

        return getBoolean(
                "cluster-exit.check-combat",
                true
        );
    }

    public boolean requireActiveCombatOnClusterExit() {

        return getBoolean(
                "cluster-exit.require-active-combat",
                true
        );
    }

    /*
     * ==========================================================
     * PUNISHMENT
     * ==========================================================
     */

    public boolean isPunishmentEnabled() {

        return getBoolean(
                "punishment.enabled",
                true
        );
    }

    public boolean isSwitchPunishmentEnabled() {

        return getBoolean(
                "punishment.on-server-switch.enabled",
                false
        );
    }

    public List<String> getSwitchPunishmentCommands() {

        return getStringList(
                "punishment.on-server-switch.commands"
        );
    }

    public boolean isClusterExitPunishmentEnabled() {

        return getBoolean(
                "punishment.on-cluster-exit.enabled",
                false
        );
    }

    public List<String> getClusterExitPunishmentCommands() {

        return getStringList(
                "punishment.on-cluster-exit.commands"
        );
    }

    /*
     * ==========================================================
     * CONDITIONS
     * ==========================================================
     */

    public boolean requireCombatTag() {

        return getBoolean(
                "conditions.require-combat-tag",
                true
        );
    }

    public String getBypassPermission() {

        return getString(
                "conditions.bypass-permission",
                "combatkeepinventory.bypass"
        );
    }

    public boolean requireUnexpiredTag() {

        return getBoolean(
                "conditions.require-unexpired-tag",
                true
        );
    }

    /*
     * ==========================================================
     * API
     * ==========================================================
     */

    public boolean isApiEnabled() {

        return getBoolean(
                "api.enabled",
                true
        );
    }

    public boolean exposeApi() {

        return getBoolean(
                "api.expose-api",
                true
        );
    }

    /*
     * ==========================================================
     * LOGGING
     * ==========================================================
     */

    public boolean logStart() {
        return getBoolean(
                "logging.start",
                false
        );
    }

    public boolean logRefresh() {
        return getBoolean(
                "logging.refresh",
                false
        );
    }

    public boolean logEnd() {
        return getBoolean(
                "logging.end",
                false
        );
    }

    public boolean logForceEnd() {
        return getBoolean(
                "logging.force-end",
                true
        );
    }

    public boolean logServerSwitch() {
        return getBoolean(
                "logging.server-switch",
                false
        );
    }

    public boolean logClusterExit() {
        return getBoolean(
                "logging.cluster-exit",
                true
        );
    }

    public boolean logPunishment() {
        return getBoolean(
                "logging.punishment",
                true
        );
    }

    public boolean logInvalidMessage() {
        return getBoolean(
                "logging.invalid-message",
                true
        );
    }

    /*
     * ==========================================================
     * GENERIC ACCESS
     * ==========================================================
     */

    public Object get(
            String path
    ) {

        String[] parts =
                path.split("\\.");

        Object current =
                root;

        for (String part : parts) {

            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }

            current =
                    map.get(part);
        }

        return current;
    }

    public String getString(
            String path,
            String fallback
    ) {

        Object value =
                get(path);

        if (value == null) {
            return fallback;
        }

        return String.valueOf(value);
    }

    public boolean getBoolean(
            String path,
            boolean fallback
    ) {

        Object value =
                get(path);

        if (value instanceof Boolean bool) {
            return bool;
        }

        if (value instanceof String string) {

            return Boolean.parseBoolean(
                    string
            );
        }

        return fallback;
    }

    public long getLong(
            String path,
            long fallback
    ) {

        Object value =
                get(path);

        if (value instanceof Number number) {
            return number.longValue();
        }

        if (value instanceof String string) {

            try {

                return Long.parseLong(
                        string
                );

            } catch (NumberFormatException ignored) {
            }
        }

        return fallback;
    }

    public int getInt(
            String path,
            int fallback
    ) {

        Object value =
                get(path);

        if (value instanceof Number number) {
            return number.intValue();
        }

        if (value instanceof String string) {

            try {

                return Integer.parseInt(
                        string
                );

            } catch (NumberFormatException ignored) {
            }
        }

        return fallback;
    }

    public List<String> getStringList(
            String path
    ) {

        Object value =
                get(path);

        if (!(value instanceof List<?> list)) {
            return List.of();
        }

        List<String> result =
                new ArrayList<>(
                        list.size()
                );

        for (Object item : list) {

            if (item != null) {

                result.add(
                        String.valueOf(item)
                );
            }
        }

        return List.copyOf(result);
    }
}