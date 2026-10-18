package com.votri.combatkeepinv.bukkit.version;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Shared, reflection-based implementation of the TNT-igniter
 * lookup used by every era adapter below.
 *
 * <p>{@code TNTPrimed#getSource()} has been present on every
 * server this plugin has actually been tested against, but
 * rather than hard-coding a "supported since version X" guess
 * this probes the real method on the real running server once at
 * startup and remembers the result. A server where the method is
 * missing or throws simply gets TNT-igniter tracking disabled --
 * every other feature (including the EntityDamageEvent fallback
 * attribution already used at death time) is unaffected.</p>
 */
abstract class AbstractVersionAdapter implements VersionAdapter {

    private final boolean tntSourceSupported;

    protected AbstractVersionAdapter(
            Logger logger
    ) {

        this.tntSourceSupported =
                probeTntSource(logger);
    }

    private static boolean probeTntSource(
            Logger logger
    ) {

        try {

            Method method =
                    TNTPrimed.class.getMethod(
                            "getSource"
                    );

            return Entity.class.isAssignableFrom(
                    method.getReturnType()
            );

        } catch (NoSuchMethodException
                 | SecurityException
                 | LinkageError exception) {

            logger.warning(
                    "TNTPrimed#getSource() is not available on "
                            + "this server ("
                            + exception.getClass().getSimpleName()
                            + "). Disabling player-TNT death/"
                            + "combat-tag attribution; every other "
                            + "feature is unaffected."
            );

            return false;
        }
    }

    @Override
    public final boolean supportsTntSourceTracking() {
        return tntSourceSupported;
    }

    @Override
    public final Optional<Player> getTntIgniter(
            TNTPrimed tnt
    ) {

        if (!tntSourceSupported
                || tnt == null) {

            return Optional.empty();
        }

        try {

            Entity source =
                    tnt.getSource();

            if (source instanceof Player player) {
                return Optional.of(player);
            }

            return Optional.empty();

        } catch (LinkageError
                 | RuntimeException exception) {

            /*
             * Defensive: a server could expose the method but
             * still misbehave on a specific build. Never let a
             * TNT attribution failure take down damage/death
             * processing.
             */
            return Optional.empty();
        }
    }
}
