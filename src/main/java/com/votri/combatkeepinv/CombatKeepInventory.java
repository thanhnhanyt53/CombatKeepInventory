package com.votri.combatkeepinv;

import org.bukkit.plugin.java.JavaPlugin;

public final class CombatKeepInventory extends JavaPlugin {
    private CombatManager combatManager;
    private WorldGuardHook worldGuardHook;
    private PvPManagerHook pvpManagerHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        long seconds = Math.max(1L, getConfig().getLong("combat.duration-seconds", 10L));
        combatManager = new CombatManager(seconds * 1000L);
        worldGuardHook = new WorldGuardHook(this);
        pvpManagerHook = new PvPManagerHook(this);

        getServer().getPluginManager().registerEvents(
                new CombatListener(this, combatManager, worldGuardHook, pvpManagerHook),
                this
        );

        getLogger().info("CombatKeepInventory V3 enabled.");
        getLogger().info("Combat duration: " + seconds + " seconds");
        getLogger().info("WorldGuard integration: " +
                (worldGuardHook.isAvailable() ? "ENABLED" : "NOT INSTALLED"));
        getLogger().info("PvPManager integration: " +
                (pvpManagerHook.isAvailable() ? "ENABLED" : "NOT INSTALLED"));
    }

    @Override
    public void onDisable() {
        if (combatManager != null) combatManager.clear();
        getLogger().info("CombatKeepInventory V3 disabled.");
    }

    public CombatManager getCombatManager() { return combatManager; }
    public WorldGuardHook getWorldGuardHook() { return worldGuardHook; }
    public PvPManagerHook getPvpManagerHook() { return pvpManagerHook; }
}
