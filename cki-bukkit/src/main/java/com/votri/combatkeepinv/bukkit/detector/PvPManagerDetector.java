package com.votri.combatkeepinv.bukkit.detector;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Detects whether PvPManager is installed and enabled.
 *
 * <p>This class does NOT hook into PvPManager and does not call
 * any PvPManager API. Its only responsibility is to detect the
 * presence of the plugin so CombatKeepInventory can warn the
 * server administrator about possible combat/death handling
 * conflicts.</p>
 */
public final class PvPManagerDetector {

    private static final String PLUGIN_NAME = "PvPManager";

    private final JavaPlugin plugin;

    public PvPManagerDetector(
            JavaPlugin plugin
    ) {

        if (plugin == null) {
            throw new IllegalArgumentException(
                    "plugin cannot be null"
            );
        }

        this.plugin = plugin;
    }

    /**
     * Checks whether PvPManager is currently installed
     * and enabled.
     *
     * @return true when PvPManager is detected and enabled
     */
    public boolean isDetected() {

        PluginManager pluginManager =
                plugin.getServer()
                        .getPluginManager();

        Plugin pvpManager =
                pluginManager.getPlugin(
                        PLUGIN_NAME
                );

        return pvpManager != null
                && pvpManager.isEnabled();
    }

    /**
     * Returns the detected PvPManager plugin instance.
     *
     * <p>This method is intended for diagnostic purposes only.
     * CombatKeepInventory must not use the returned plugin to
     * control PvPManager.</p>
     *
     * @return PvPManager instance, or null if not detected
     */
    public Plugin getPlugin() {

        PluginManager pluginManager =
                plugin.getServer()
                        .getPluginManager();

        return pluginManager.getPlugin(
                PLUGIN_NAME
        );
    }

    /**
     * Returns the installed PvPManager version.
     *
     * @return version string, or null when PvPManager is not detected
     */
    public String getVersion() {

        Plugin pvpManager =
                getPlugin();

        if (pvpManager == null) {
            return null;
        }

        return pvpManager
                .getDescription()
                .getVersion();
    }

    /**
     * Logs a warning when PvPManager is detected.
     *
     * <p>No message is logged when PvPManager is absent.
     * This prevents the normal startup log from being polluted
     * with "NOT INSTALLED" messages for optional integrations.</p>
     */
    public void logWarningIfDetected() {

        if (!isDetected()) {
            return;
        }

        String version =
                getVersion();

        if (version == null
                || version.isBlank()) {

            version = "unknown";
        }

        plugin.getLogger().warning(
                "PvPManager DETECTED "
                        + "(version "
                        + version
                        + ")."
        );

        plugin.getLogger().warning(
                "CombatKeepInventory does not hook "
                        + "into PvPManager."
        );

        plugin.getLogger().warning(
                "PvPManager may independently handle "
                        + "combat logout and death processing."
        );

        plugin.getLogger().warning(
                "Ensure that PvPManager and CombatKeepInventory "
                        + "do not both control the same death/drop behavior."
        );
    }
}