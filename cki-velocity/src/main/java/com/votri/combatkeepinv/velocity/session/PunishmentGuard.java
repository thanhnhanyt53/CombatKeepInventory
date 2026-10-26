package com.votri.combatkeepinv.velocity.session;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Decides whether a combat-logging punishment is justified.
 *
 * <p>Deliberately free of any Velocity API types so every rule
 * can be unit tested without a proxy. The caller supplies the
 * facts (is the tag active, how long is left) and this class
 * adds the state that makes false positives less likely:</p>
 *
 * <ul>
 *   <li>the proxy is shutting down (every player gets
 *       disconnected, none of them are combat-logging)</li>
 *   <li>the player was moved by a backend kick/fallback, not
 *       by their own choice, shortly before a server switch</li>
 *   <li>the disconnect was not a normal quit (duplicate login,
 *       cancelled by the proxy, ...)</li>
 *   <li>the tag has (almost) run out: backend and proxy clocks
 *       are not perfectly in sync and the backend only sweeps
 *       expired tags once per second, so a tag with only a
 *       moment left is treated as already over</li>
 *   <li>the player was already punished a moment ago (a switch
 *       immediately followed by a quit must not be punished
 *       twice for one fight)</li>
 * </ul>
 */
public final class PunishmentGuard {

    /** How long a "was kicked/redirected" marker stays valid. */
    static final long INVOLUNTARY_SWITCH_WINDOW_MILLIS = 15_000L;

    /** Entries older than this are dropped from the cooldown map. */
    private static final long COOLDOWN_RETENTION_MILLIS = 600_000L;

    public enum Kind {
        SERVER_SWITCH,
        CLUSTER_EXIT
    }

    public enum Verdict {
        ALLOW,
        PROXY_SHUTTING_DOWN,
        INVOLUNTARY_SWITCH,
        NON_PUNISHABLE_DISCONNECT,
        NOT_IN_COMBAT,
        TAG_EXPIRING,
        COOLDOWN
    }

    private final LongSupplier clock;

    private volatile boolean shuttingDown;

    private final Set<UUID> nonPunishableExits =
            ConcurrentHashMap.newKeySet();

    private final Map<UUID, Long> involuntarySwitches =
            new ConcurrentHashMap<>();

    private final Map<UUID, Long> lastPunished =
            new ConcurrentHashMap<>();

    public PunishmentGuard() {
        this(System::currentTimeMillis);
    }

    public PunishmentGuard(
            LongSupplier clock
    ) {
        this.clock = clock;
    }

    public void markShuttingDown() {
        shuttingDown = true;
    }

    public boolean isShuttingDown() {
        return shuttingDown;
    }

    /**
     * The next CLUSTER_EXIT for this player was not a normal
     * voluntary quit and must not be punished.
     */
    public void markNonPunishableExit(
            UUID playerId
    ) {
        if (playerId != null) {
            nonPunishableExits.add(playerId);
        }
    }

    /**
     * The player was kicked from a backend / redirected; a server
     * switch within the window is not something they chose.
     */
    public void markInvoluntarySwitch(
            UUID playerId
    ) {
        if (playerId != null) {
            involuntarySwitches.put(
                    playerId,
                    clock.getAsLong()
            );
        }
    }

    /**
     * @param inCombat        mirrored tag currently active
     * @param remainingMillis time left on the mirrored tag
     * @param graceMillis     a tag with this much or less left is
     *                        treated as expired (0 disables)
     * @param cooldownMillis  minimum gap between two punishments
     *                        of the same player (0 disables)
     */
    public Verdict check(
            Kind kind,
            UUID playerId,
            boolean inCombat,
            long remainingMillis,
            long graceMillis,
            long cooldownMillis
    ) {

        /*
         * Always consume the one-shot markers, even when another
         * rule would already have denied, so a stale marker can
         * never leak into a later, unrelated event.
         */
        boolean nonPunishableExit =
                kind == Kind.CLUSTER_EXIT
                        && playerId != null
                        && nonPunishableExits.remove(playerId);

        boolean involuntarySwitch = false;

        if (kind == Kind.SERVER_SWITCH
                && playerId != null) {

            Long markedAt =
                    involuntarySwitches.remove(playerId);

            involuntarySwitch =
                    markedAt != null
                            && clock.getAsLong() - markedAt
                            <= INVOLUNTARY_SWITCH_WINDOW_MILLIS;
        }

        if (shuttingDown) {
            return Verdict.PROXY_SHUTTING_DOWN;
        }

        if (involuntarySwitch) {
            return Verdict.INVOLUNTARY_SWITCH;
        }

        if (nonPunishableExit) {
            return Verdict.NON_PUNISHABLE_DISCONNECT;
        }

        if (playerId == null
                || !inCombat) {
            return Verdict.NOT_IN_COMBAT;
        }

        if (remainingMillis <= Math.max(0L, graceMillis)) {
            return Verdict.TAG_EXPIRING;
        }

        if (cooldownMillis > 0L) {

            Long last =
                    lastPunished.get(playerId);

            if (last != null
                    && clock.getAsLong() - last < cooldownMillis) {
                return Verdict.COOLDOWN;
            }
        }

        return Verdict.ALLOW;
    }

    /** Call after a punishment was actually executed. */
    public void recordPunished(
            UUID playerId
    ) {
        if (playerId == null) {
            return;
        }

        long now =
                clock.getAsLong();

        lastPunished.put(
                playerId,
                now
        );

        /*
         * Keep the map from growing for the lifetime of the proxy:
         * entries far older than any sensible cooldown carry no
         * information any more.
         */
        if (lastPunished.size() > 1024) {
            lastPunished.values().removeIf(
                    at -> now - at > COOLDOWN_RETENTION_MILLIS
            );
        }
    }

    /** Drops per-player state; call when a player is gone for good. */
    public void forget(
            UUID playerId
    ) {
        if (playerId != null) {
            nonPunishableExits.remove(playerId);
            involuntarySwitches.remove(playerId);
        }
    }
}
