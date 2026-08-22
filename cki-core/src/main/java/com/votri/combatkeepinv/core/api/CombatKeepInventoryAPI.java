package com.votri.combatkeepinv.core.api;

public interface CombatKeepInventoryAPI {

    /**
     * Returns the currently registered CKI API instance.
     */
    static CombatKeepInventoryAPI get() {
        return Provider.get();
    }

    CombatService getCombatService();

    boolean isEnabled();

    String getVersion();

    final class Provider {

        private static CombatKeepInventoryAPI instance;

        private Provider() {
        }

        public static CombatKeepInventoryAPI get() {
            if (instance == null) {
                throw new IllegalStateException(
                        "CombatKeepInventory API is not initialized."
                );
            }

            return instance;
        }

        public static void register(
                CombatKeepInventoryAPI api
        ) {
            if (api == null) {
                throw new IllegalArgumentException(
                        "API cannot be null."
                );
            }

            if (instance != null) {
                throw new IllegalStateException(
                        "CombatKeepInventory API is already registered."
                );
            }

            instance = api;
        }

        public static void unregister(
                CombatKeepInventoryAPI api
        ) {
            if (instance == api) {
                instance = null;
            }
        }
    }
}