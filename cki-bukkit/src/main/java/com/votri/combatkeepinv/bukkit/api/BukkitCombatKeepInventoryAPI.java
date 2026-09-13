package com.votri.combatkeepinv.bukkit.api;

import com.votri.combatkeepinv.bukkit.CombatKeepInventory;
import com.votri.combatkeepinv.core.api.CombatAPI;
import com.votri.combatkeepinv.core.api.CombatKeepInventoryAPI;
import com.votri.combatkeepinv.core.api.CombatService;
import com.votri.combatkeepinv.core.api.DamageAPI;
import com.votri.combatkeepinv.core.api.DeathAPI;
import com.votri.combatkeepinv.core.api.InventoryAPI;
import com.votri.combatkeepinv.core.api.PlatformAPI;
import com.votri.combatkeepinv.core.platform.PlatformInfo;

import java.util.Objects;

public final class BukkitCombatKeepInventoryAPI
        implements CombatKeepInventoryAPI {

    private final CombatKeepInventory plugin;

    public BukkitCombatKeepInventoryAPI(
            CombatKeepInventory plugin
    ) {
        this.plugin = Objects.requireNonNull(
                plugin,
                "plugin"
        );
    }

    /*
     * ==========================================================
     * NEW API 1.2+
     * ==========================================================
     */

    public CombatAPI getCombat() {
        return plugin.getCombatAPI();
    }

    public DamageAPI getDamage() {
        return plugin.getDamageAPI();
    }

    public DeathAPI getDeath() {
        return plugin.getDeathAPI();
    }

    public InventoryAPI getInventory() {
        return plugin.getInventoryAPI();
    }

    /*
     * ==========================================================
     * LEGACY API
     * ==========================================================
     */

    @Override
    public CombatService getCombatService() {
        return plugin.getCombatService();
    }

    @Override
    public boolean isEnabled() {
        return plugin.isEnabled();
    }

    @Override
    public String getVersion() {
        return CombatKeepInventory.PLUGIN_VERSION;
    }

    @Override
    public PlatformInfo getPlatform() {
        return plugin.getPlatform();
    }

    /*
     * ==========================================================
     * PLATFORM API
     * ==========================================================
     */

    public PlatformAPI getPlatformAPI() {
        return plugin.getPlatformAPI();
    }

    public CombatKeepInventory getPlugin() {
        return plugin;
    }
}