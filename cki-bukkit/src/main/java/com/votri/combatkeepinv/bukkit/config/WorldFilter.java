package com.votri.combatkeepinv.bukkit.config;

import java.util.List;

/**
 * The {@code worlds.enabled} / {@code worlds.disabled-worlds}
 * decision, as plain values with no Bukkit types involved, so it
 * can be unit tested directly instead of only indirectly through
 * {@code CombatKeepInventory#isWorldDisabled(World)}.
 */
public final class WorldFilter {

    private WorldFilter() {
    }

    /**
     * @param checkingEnabled {@code worlds.enabled}; false means
     *                        the list below is not consulted at
     *                        all, so nothing is ever "disabled" by
     *                        it (the one easy-to-misread case: it
     *                        is a switch for the exclusion
     *                        feature, not for CKI itself)
     * @param disabledWorlds  {@code worlds.disabled-worlds}
     * @param worldName       the world to check, compared
     *                        case-insensitively
     */
    public static boolean isDisabled(
            boolean checkingEnabled,
            List<String> disabledWorlds,
            String worldName
    ) {

        if (worldName == null) {
            return true;
        }

        if (!checkingEnabled
                || disabledWorlds == null) {

            return false;
        }

        for (String disabled : disabledWorlds) {

            if (worldName.equalsIgnoreCase(disabled)) {
                return true;
            }
        }

        return false;
    }
}
