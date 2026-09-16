package com.votri.combatkeepinv.bukkit.api;

import com.votri.combatkeepinv.bukkit.CombatKeepInventory;
import com.votri.combatkeepinv.core.api.CombatAPI;
import com.votri.combatkeepinv.core.api.CombatKeepInventoryAPI;
import com.votri.combatkeepinv.core.api.DamageAPI;
import com.votri.combatkeepinv.core.api.DeathAPI;
import com.votri.combatkeepinv.core.api.InventoryAPI;
import com.votri.combatkeepinv.core.api.PlatformAPI;

import java.util.Objects;
import java.util.Optional;

/**
 * Bukkit adapter for the public CKI 1.2.0 core API.
 *
 * <p>The core contract deliberately does not own a global service
 * locator. The Bukkit adapter therefore provides the platform-level
 * registry used by other Bukkit plugins.</p>
 */
public final class BukkitCombatKeepInventoryAPI
        implements CombatKeepInventoryAPI {

    private static volatile BukkitCombatKeepInventoryAPI instance;

    private final CombatKeepInventory plugin;

    public BukkitCombatKeepInventoryAPI(
            CombatKeepInventory plugin
    ) {
        this.plugin = Objects.requireNonNull(
                plugin,
                "plugin"
        );
    }

    /**
     * Returns the currently registered Bukkit CKI API.
     */
    public static Optional<CombatKeepInventoryAPI> getRegistered() {
        return Optional.ofNullable(instance);
    }

    /**
     * Returns the currently registered Bukkit CKI API.
     *
     * @throws IllegalStateException when CKI is not registered
     */
    public static CombatKeepInventoryAPI requireRegistered() {
        BukkitCombatKeepInventoryAPI api = instance;

        if (api == null) {
            throw new IllegalStateException(
                    "CombatKeepInventory API is not registered."
            );
        }

        return api;
    }

    /**
     * Registers the Bukkit adapter.
     */
    public static synchronized void register(
            BukkitCombatKeepInventoryAPI api
    ) {
        Objects.requireNonNull(api, "api");

        if (instance != null && instance != api) {
            throw new IllegalStateException(
                    "CombatKeepInventory API is already registered."
            );
        }

        instance = api;
    }

    /**
     * Unregisters this exact adapter instance.
     */
    public static synchronized void unregister(
            BukkitCombatKeepInventoryAPI api
    ) {
        if (instance == api) {
            instance = null;
        }
    }

    @Override
    public CombatAPI getCombat() {
        return plugin.getCombatAPI();
    }

    @Override
    public DamageAPI getDamage() {
        return plugin.getDamageAPI();
    }

    @Override
    public DeathAPI getDeath() {
        return plugin.getDeathAPI();
    }

    @Override
    public InventoryAPI getInventory() {
        return plugin.getInventoryAPI();
    }

    @Override
    public PlatformAPI getPlatform() {
        return plugin.getPlatformAPI();
    }

    public CombatKeepInventory getPlugin() {
        return plugin;
    }
}
