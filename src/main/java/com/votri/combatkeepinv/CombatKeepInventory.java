package com.votri.combatkeepinv;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CombatKeepInventory extends JavaPlugin {

    private CombatManager combatManager;
    private WorldGuardHook worldGuardHook;
    private PvPManagerHook pvpManagerHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        initializeComponents();

        CombatListener combatListener = new CombatListener(
                this,
                combatManager,
                worldGuardHook,
                pvpManagerHook
        );

        getServer().getPluginManager().registerEvents(
                combatListener,
                this
        );

        registerCommands();

        getLogger().info("CombatKeepInventory V3 enabled.");
        getLogger().info(
                "Combat duration: "
                        + getCombatDurationSeconds()
                        + " seconds"
        );

        getLogger().info(
                "WorldGuard integration: "
                        + (worldGuardHook.isAvailable()
                        ? "ENABLED"
                        : "NOT INSTALLED")
        );

        getLogger().info(
                "PvPManager integration: "
                        + (pvpManagerHook.isAvailable()
                        ? "ENABLED"
                        : "NOT INSTALLED")
        );
    }

    /**
     * Initialize/reinitialize plugin components.
     */
    private void initializeComponents() {

        long seconds = getCombatDurationSeconds();

        combatManager = new CombatManager(
                seconds * 1000L
        );

        worldGuardHook = new WorldGuardHook(this);
        pvpManagerHook = new PvPManagerHook(this);
    }

    /**
     * Register /cki command.
     */
    private void registerCommands() {

        PluginCommand command = getCommand("cki");

        if (command == null) {
            getLogger().severe(
                    "Command 'cki' is missing from plugin.yml!"
            );
            return;
        }

        CombatCommand commandHandler =
                new CombatCommand(this);

        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);
    }

    /**
     * Reload plugin configuration and rebuild
     * components that depend on configuration.
     */
    public void reloadPluginConfig() {

        reloadConfig();

        /*
         * Stop the old combat manager before replacing it.
         */
        if (combatManager != null) {
            combatManager.clear();
        }

        initializeComponents();

        getLogger().info(
                "CombatKeepInventory configuration reloaded."
        );

        getLogger().info(
                "Combat duration: "
                        + getCombatDurationSeconds()
                        + " seconds"
        );

        getLogger().info(
                "WorldGuard integration: "
                        + (worldGuardHook.isAvailable()
                        ? "ENABLED"
                        : "NOT INSTALLED")
        );

        getLogger().info(
                "PvPManager integration: "
                        + (pvpManagerHook.isAvailable()
                        ? "ENABLED"
                        : "NOT INSTALLED")
        );
    }

    /**
     * Get combat duration from config.
     */
    public long getCombatDurationSeconds() {

        return Math.max(
                1L,
                getConfig().getLong(
                        "combat.duration-seconds",
                        10L
                )
        );
    }

    /**
     * Plugin information used by /cki info.
     */
    public String getPluginInfo() {

        return "CombatKeepInventory V"
                + getDescription().getVersion();
    }

    @Override
    public void onDisable() {

        if (combatManager != null) {
            combatManager.clear();
        }

        getLogger().info(
                "CombatKeepInventory V3 disabled."
        );
    }

    public CombatManager getCombatManager() {
        return combatManager;
    }

    public WorldGuardHook getWorldGuardHook() {
        return worldGuardHook;
    }

    public PvPManagerHook getPvpManagerHook() {
        return pvpManagerHook;
    }
}
