package com.votri.combatkeepinv.bukkit.api;

import com.votri.combatkeepinv.bukkit.CombatKeepInventory;
import com.votri.combatkeepinv.core.api.CombatKeepInventoryAPI;
import com.votri.combatkeepinv.core.api.CombatService;
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
}