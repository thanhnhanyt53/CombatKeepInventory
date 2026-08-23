package com.votri.combatkeepinv.bukkit.hook;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

/**
 * Optional integration detector for PvPManager.
 *
 * <p>This class intentionally does not depend on the PvPManager API.
 * It only detects the plugin at runtime and provides compatibility
 * information to CombatKeepInventory.</p>
 */
public final class PvPManagerHook {

    private static final String PLUGIN_NAME = "PvPManager";

    private final Plugin owner;

    private boolean available;

    public PvPManagerHook(
            Plugin owner
    ) {
        this.owner =
                Objects.requireNonNull(
                        owner,
                        "owner"
                );

        refresh();
    }

    /**
     * Re-checks whether PvPManager is currently installed and enabled.
     */
    public void refresh() {

        Plugin plugin =
                Bukkit.getPluginManager()
                        .getPlugin(
                                PLUGIN_NAME
                        );

        available =
                plugin != null
                        && plugin.isEnabled();
    }

    /**
     * Returns true when PvPManager is installed and enabled.
     */
    public boolean isAvailable() {

        return available;
    }

    /**
     * Returns the detected PvPManager plugin instance.
     *
     * @return PvPManager instance or null
     */
    public Plugin getPlugin() {

        return Bukkit.getPluginManager()
                .getPlugin(
                        PLUGIN_NAME
                );
    }

    /**
     * Logs a compatibility warning when PvPManager is present.
     */
    public void logCompatibilityWarning() {

        if (!available) {
            return;
        }

        owner.getLogger().warning(
                "PvPManager detected."
        );

        owner.getLogger().warning(
                "PvPManager may handle combat-logout "
                        + "and player death processing."
        );

        owner.getLogger().warning(
                "CombatKeepInventory will keep using its own "
                        + "CombatManager as the source of truth "
                        + "for combat-tag state."
        );

        owner.getLogger().warning(
                "Do not use PvPManager combat state as the "
                        + "CombatKeepInventory death-rule state."
        );
    }
}