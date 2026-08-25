package com.votri.combatkeepinv.core.api;

import com.votri.combatkeepinv.core.platform.PlatformInfo;

/**
 * Public entry point for CombatKeepInventory integrations.
 *
 * <p>External plugins should use this API instead of accessing
 * Bukkit implementation classes directly.</p>
 */
public interface CombatKeepInventoryAPI {

    /**
     * Returns the currently registered CKI API instance.
     *
     * @return registered API instance
     * @throws IllegalStateException if CKI has not registered its API
     */
    static CombatKeepInventoryAPI get() {
        return Provider.get();
    }

    /**
     * Returns the public combat service.
     *
     * @return combat service
     */
    CombatService getCombatService();

    /**
     * Returns whether CombatKeepInventory is currently enabled.
     *
     * @return true when CKI is enabled
     */
    boolean isEnabled();

    /**
     * Returns the installed CKI version.
     *
     * @return CKI version
     */
    String getVersion();

    /**
     * Returns information about the platform on which CKI is running.
     *
     * @return immutable platform information
     */
    PlatformInfo getPlatform();

    /**
     * Internal API provider used by the platform implementation.
     */
    final class Provider {

        private static CombatKeepInventoryAPI instance;

        private Provider() {
        }

        /**
         * Returns the registered API.
         *
         * @return registered API
         */
        public static CombatKeepInventoryAPI get() {

            CombatKeepInventoryAPI current =
                    instance;

            if (current == null) {

                throw new IllegalStateException(
                        "CombatKeepInventory API is not initialized."
                );
            }

            return current;
        }

        /**
         * Registers the CKI API.
         *
         * <p>Only one API instance may be registered at a time.</p>
         *
         * @param api API implementation
         */
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

        /**
         * Unregisters the currently registered API.
         *
         * @param api API instance to unregister
         */
        public static void unregister(
                CombatKeepInventoryAPI api
        ) {

            if (api == null) {
                return;
            }

            if (instance == api) {
                instance = null;
            }
        }

        /**
         * Returns whether an API instance is registered.
         *
         * @return true if registered
         */
        public static boolean isRegistered() {
            return instance != null;
        }
    }
}