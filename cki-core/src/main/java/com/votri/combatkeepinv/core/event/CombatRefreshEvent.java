package com.votri.combatkeepinv.core.event;

import com.votri.combatkeepinv.core.api.CombatPlayer;
import com.votri.combatkeepinv.core.api.CombatTag;

import java.util.Objects;

/**
 * Fired when an existing CombatTag is refreshed by a valid
 * player-originated combat interaction.
 *
 * <p>This event is intended for integrations such as
 * scoreboards, action bars, boss bars, combat timers,
 * anti-logout systems and combat-related UI.</p>
 */
public final class CombatRefreshEvent {

    private final CombatPlayer attacker;
    private final CombatPlayer victim;

    private final CombatTag attackerTag;
    private final CombatTag victimTag;

    private final long remainingCombatMillis;

    public CombatRefreshEvent(
            CombatPlayer attacker,
            CombatPlayer victim,
            CombatTag attackerTag,
            CombatTag victimTag
    ) {

        this.attacker =
                Objects.requireNonNull(
                        attacker,
                        "attacker"
                );

        this.victim =
                Objects.requireNonNull(
                        victim,
                        "victim"
                );

        this.attackerTag =
                Objects.requireNonNull(
                        attackerTag,
                        "attackerTag"
                );

        this.victimTag =
                Objects.requireNonNull(
                        victimTag,
                        "victimTag"
                );

        this.remainingCombatMillis =
                victimTag.getRemainingMillis();
    }

    /**
     * Returns the player who caused the refresh.
     *
     * @return attacker
     */
    public CombatPlayer getAttacker() {
        return attacker;
    }

    /**
     * Returns the player who received the damage.
     *
     * @return victim
     */
    public CombatPlayer getVictim() {
        return victim;
    }

    /**
     * Returns the refreshed tag of the attacker.
     *
     * @return attacker CombatTag
     */
    public CombatTag getAttackerTag() {
        return attackerTag;
    }

    /**
     * Returns the refreshed tag of the victim.
     *
     * @return victim CombatTag
     */
    public CombatTag getVictimTag() {
        return victimTag;
    }

    /**
     * Returns the remaining combat duration of the victim
     * at the moment the event was created.
     *
     * @return remaining milliseconds
     */
    public long getRemainingCombatMillis() {
        return remainingCombatMillis;
    }

    /**
     * Returns the remaining combat duration in whole seconds.
     *
     * @return remaining seconds
     */
    public long getRemainingCombatSeconds() {

        if (remainingCombatMillis <= 0L) {
            return 0L;
        }

        return (remainingCombatMillis + 999L) / 1000L;
    }
}