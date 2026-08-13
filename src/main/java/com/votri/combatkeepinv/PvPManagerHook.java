package com.votri.combatkeepinv;

import me.chancesd.pvpmanager.player.CombatPlayer;
import org.bukkit.entity.Player;

public final class PvPManagerHook {

    private final CombatKeepInventory plugin;

    private boolean available;

    public PvPManagerHook(CombatKeepInventory plugin) {
        this.plugin = plugin;

        this.available =
                plugin.getServer()
                        .getPluginManager()
                        .getPlugin("PvPManager") != null;
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Kiểm tra PvPManager có coi player đang combat không.
     */
    public boolean isInCombat(Player player) {

        if (!available) {
            return false;
        }

        try {

            CombatPlayer combatPlayer =
                    CombatPlayer.get(player);

            if (combatPlayer == null) {
                return false;
            }

            return combatPlayer.isInCombat();

        } catch (Throwable throwable) {

            debug(
                    "Failed to read PvPManager combat state for "
                            + player.getName()
                            + ": "
                            + throwable.getClass().getSimpleName()
            );

            return false;
        }
    }

    /**
     * Kiểm tra PvPManager xác định death trước đó là PvP.
     */
    public boolean wasLastDeathPvP(Player player) {

        if (!available) {
            return false;
        }

        try {

            CombatPlayer combatPlayer =
                    CombatPlayer.get(player);

            if (combatPlayer == null) {
                return false;
            }

            return combatPlayer.wasLastDeathPvP();

        } catch (Throwable throwable) {

            debug(
                    "Failed to read PvPManager last death state for "
                            + player.getName()
            );

            return false;
        }
    }

    private void debug(String message) {

        if (plugin.getConfig()
                .getBoolean(
                        "debug.pvpmanager",
                        false
                )) {

            plugin.getLogger().info(
                    "[PvPManager] " + message
            );
        }
    }
}
