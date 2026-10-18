package com.votri.combatkeepinv.bukkit.version;

import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;

import java.util.Optional;

/**
 * Isolates the handful of Bukkit API touchpoints in this plugin
 * that are not guaranteed to exist/behave identically across the
 * whole 1.17 - 1.20.6 (and current) support range.
 *
 * <p>Every other API this plugin uses (PlayerDeathEvent,
 * EntityDamageEvent, EntityExplodeEvent, PlayerInventory array
 * accessors, Material, CommandExecutor/TabCompleter) has been
 * stable Bukkit API since well before 1.17, so it is called
 * directly and is NOT routed through an adapter -- adding a layer
 * of indirection for things that do not actually vary per version
 * would just be ceremony.</p>
 *
 * <p>Implementations must never throw for an unsupported
 * capability; they report it (so the caller can disable the
 * dependent feature and log why) instead of crashing combat
 * processing.</p>
 */
public interface VersionAdapter {

    /**
     * Human-readable label for logs (e.g. "1.18.x - 1.19.x").
     */
    String getLabel();

    /**
     * Whether this adapter was able to confirm, on this exact
     * running server, that {@link TNTPrimed#getSource()} is
     * callable. Used only for startup logging -- callers should
     * still go through {@link #getTntIgniter(TNTPrimed)} rather
     * than branching on this themselves, since that method
     * degrades safely either way.
     */
    boolean supportsTntSourceTracking();

    /**
     * Safely resolves the player who ignited a given primed TNT,
     * if the running server exposes that information and a
     * player actually lit it.
     *
     * @return the igniting player, or empty if unsupported on
     *         this server, untraceable, or not player-lit
     */
    Optional<Player> getTntIgniter(TNTPrimed tnt);
}
