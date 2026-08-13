package com.votri.combatkeepinv;

import org.bukkit.plugin.java.JavaPlugin;

public final class CombatKeepInventory extends JavaPlugin {

    private CombatManager combatManager;
    private WorldGuardHook worldGuardHook;
    private PvPManagerHook pvpManagerHook;

    @Override
    public void onEnable() {

        saveDefaultConfig();

        long durationSeconds =
                getConfig().getLong(
                        "combat.duration-seconds",
                        10L
                );

        if (durationSeconds <= 0) {
            durationSeconds = 10L;
        }

        long durationMillis =
                durationSeconds * 1000L;

        combatManager =
                new CombatManager(
                        this,
                        durationMillis
                );

        worldGuardHook =
                new WorldGuardHook(this);

        pvpManagerHook =
                new PvPManagerHook(this);

        getServer()
                .getPluginManager()
                .registerEvents(
                        new CombatListener(
                                this,
                                combatManager,
                                worldGuardHook,
                                pvpManagerHook
                        ),
                        this
                );

        getLogger().info(
                "CombatKeepInventory V2 enabled."
        );

        getLogger().info(
                "Combat duration: "
                        + durationSeconds
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

    @Override
    public void onDisable() {

        if (combatManager != null) {
            combatManager.clear();
        }

        getLogger().info(
                "CombatKeepInventory V2 disabled."
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
