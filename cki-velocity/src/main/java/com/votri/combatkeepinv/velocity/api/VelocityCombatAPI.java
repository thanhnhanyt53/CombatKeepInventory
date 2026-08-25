package com.votri.combatkeepinv.velocity.api;

import com.votri.combatkeepinv.core.api.CombatTag;

import java.util.UUID;

/**
 * Public Velocity-side API of CombatKeepInventory.
 */
public interface VelocityCombatAPI {

    static VelocityCombatAPI get() {
        return Provider.get();
    }

    boolean isInCombat(
            UUID player
    );

    ProxyCombatState getCombatState(
            UUID player
    );

    CombatTag getCombatTag(
            UUID player
    );

    long getRemainingCombatMillis(
            UUID player
    );

    default long getRemainingCombatSeconds(
            UUID player
    ) {

        long millis =
                getRemainingCombatMillis(
                        player
                );

        if (millis <= 0L) {
            return 0L;
        }

        return (
                millis + 999L
        ) / 1000L;
    }

    UUID getOpponent(
            UUID player
    );

    String getBackendServer(
            UUID player
    );

    CombatPunishmentService punishments();

    boolean isEnabled();

    final class Provider {

        private static VelocityCombatAPI instance;

        private Provider() {
        }

        public static VelocityCombatAPI get() {

            if (instance == null) {

                throw new IllegalStateException(
                        "VelocityCombatAPI is not initialized."
                );
            }

            return instance;
        }

        public static void register(
                VelocityCombatAPI api
        ) {

            if (api == null) {

                throw new IllegalArgumentException(
                        "API cannot be null."
                );
            }

            if (instance != null) {

                throw new IllegalStateException(
                        "VelocityCombatAPI is already registered."
                );
            }

            instance = api;
        }

        public static void unregister(
                VelocityCombatAPI api
        ) {

            if (instance == api) {
                instance = null;
            }
        }
    }
}