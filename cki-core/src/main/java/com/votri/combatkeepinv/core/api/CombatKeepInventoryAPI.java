package com.votri.combatkeepinv.core.api;

import com.votri.combatkeepinv.core.platform.PlatformInfo;

public interface CombatKeepInventoryAPI {

    /**
     * Returns the currently registered CKI API instance.
     *
     * @return registered API instance
     * @throws IllegalStateException if the API has not been initialized
     */
    static CombatKeepInventoryAPI get() {
        return Provider.get();
    }

    CombatService getCombatService();

    boolean isEnabled();

    String getVersion();

    /**
     * Returns information about the platform on which CKI is running.
     *
     * @return immutable platform information
     */
    PlatformInfo getPlatform();

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