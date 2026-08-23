package com.votri.combatkeepinv.bukkit.detector;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class PvPManagerDetector {

    private static final String PLUGIN_NAME =
            "PvPManager";

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

    public Plugin getPlugin() {

        return plugin.getServer()
                .getPluginManager()
                .getPlugin(
                        PLUGIN_NAME
                );
    }

    public String getVersion() {

        Plugin pvpManager =
                getPlugin();

        if (pvpManager == null) {
            return null;
        }

        String version =
                pvpManager
                        .getDescription()
                        .getVersion();

        return version == null
                || version.isBlank()
                ? null
                : version;
    }

    public void logWarningIfDetected() {

        if (!isDetected()) {
            return;
        }

        String version =
                getVersion();

        if (version == null) {
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
                "CombatKeepInventory keeps its own "
                        + "CombatManager as the source of truth "
                        + "for combat state."
        );
    }
}