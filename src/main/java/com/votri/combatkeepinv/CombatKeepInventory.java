package com.votri.combatkeepinv;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CombatKeepInventory extends JavaPlugin {

    private CombatManager combatManager;
    private WorldGuardHook worldGuardHook;
    private PvPManagerHook pvpManagerHook;

    private boolean pvpEnabled;

    @Override
    public void onEnable() {

        saveDefaultConfig();

        initializeComponents();

        getServer().getPluginManager().registerEvents(
                new CombatListener(
                        this,
                        combatManager,
                        worldGuardHook,
                        pvpManagerHook
                ),
                this
        );

        getServer().getPluginManager().registerEvents(
                new CombatLogListener(this),
                this
        );

        registerCommand();

        getLogger().info(
                "CombatKeepInventory V3 enabled."
        );

        getLogger().info(
                "Combat duration: "
                        + getCombatDurationSeconds()
                        + " seconds"
        );

        getLogger().info(
                "PvP: "
                        + (pvpEnabled
                        ? "ENABLED"
                        : "DISABLED")
        );

        getLogger().info(
                "WorldGuard: "
                        + (worldGuardHook.isAvailable()
                        ? "ENABLED"
                        : "NOT INSTALLED")
        );

        getLogger().info(
                "PvPManager: "
                        + (pvpManagerHook.isAvailable()
                        ? "ENABLED"
                        : "NOT INSTALLED")
        );
    }

    private void initializeComponents() {

        long seconds =
                getCombatDurationSeconds();

        combatManager =
                new CombatManager(
                        seconds * 1000L
                );

        worldGuardHook =
                new WorldGuardHook(this);

        pvpManagerHook =
                new PvPManagerHook(this);

        pvpEnabled =
                getConfig().getBoolean(
                        "pvp.enabled",
                        true
                );
    }

    private void registerCommand() {

        PluginCommand command =
                getCommand("cki");

        if (command == null) {

            getLogger().severe(
                    "Command 'cki' is missing from plugin.yml!"
            );

            return;
        }

        CombatCommand handler =
                new CombatCommand(this);

        command.setExecutor(handler);
        command.setTabCompleter(handler);
    }

    public void reloadPluginConfig() {

        reloadConfig();

        if (combatManager != null) {
            combatManager.clear();
        }

        long seconds =
                getCombatDurationSeconds();

        combatManager =
                new CombatManager(
                        seconds * 1000L
                );

        pvpEnabled =
                getConfig().getBoolean(
                        "pvp.enabled",
                        true
                );

        /*
         * Recreate optional hooks so their
         * configuration is refreshed too.
         */
        worldGuardHook =
                new WorldGuardHook(this);

        pvpManagerHook =
                new PvPManagerHook(this);

        getLogger().info(
                "CombatKeepInventory configuration reloaded."
        );
    }

    public long getCombatDurationSeconds() {

        return Math.max(
                1L,
                getConfig().getLong(
                        "combat.duration-seconds",
                        10L
                )
        );
    }

    public boolean isPvPEnabled() {
        return pvpEnabled;
    }

    public void setPvPEnabled(
            boolean enabled
    ) {

        pvpEnabled = enabled;

        getConfig().set(
                "pvp.enabled",
                enabled
        );

        saveConfig();

        getLogger().info(
                "PvP has been "
                        + (enabled
                        ? "ENABLED"
                        : "DISABLED")
        );
    }

    public boolean canTogglePvP(
            org.bukkit.command.CommandSender sender
    ) {

        boolean requirePermission =
                getConfig().getBoolean(
                        "pvp.command.require-permission",
                        true
                );

        if (!requirePermission) {
            return true;
        }

        String permission =
                getConfig().getString(
                        "pvp.command.permission",
                        "combatkeepinventory.pvp"
                );

        return sender.hasPermission(permission);
    }

    public boolean isWorldDisabled(
            org.bukkit.World world
    ) {

        if (world == null) {
            return true;
        }

        if (!getConfig().getBoolean(
                "worlds.enabled",
                true
        )) {
            return false;
        }

        String worldName =
                world.getName();

        return getConfig()
                .getStringList(
                        "worlds.disabled-worlds"
                )
                .stream()
                .anyMatch(
                        worldName::equalsIgnoreCase
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

    @Override
    public void onDisable() {

        if (combatManager != null) {
            combatManager.clear();
        }

        getLogger().info(
                "CombatKeepInventory V3 disabled."
        );
    }
}
