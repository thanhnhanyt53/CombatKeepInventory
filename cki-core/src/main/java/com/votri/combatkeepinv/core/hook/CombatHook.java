package com.votri.combatkeepinv.core.hook;

import com.votri.combatkeepinv.core.api.CombatPlayer;
import com.votri.combatkeepinv.core.api.CombatTag;
import com.votri.combatkeepinv.core.api.DeathContext;

public interface CombatHook {

    default void onCombatStart(
            CombatPlayer attacker,
            CombatPlayer victim,
            CombatTag attackerTag,
            CombatTag victimTag
    ) {
    }

    default void onCombatRefresh(
            CombatPlayer attacker,
            CombatPlayer victim,
            CombatTag attackerTag,
            CombatTag victimTag
    ) {
    }

    default void onCombatEnd(
            CombatPlayer player
    ) {
    }

    default void onDeath(
            CombatPlayer player,
            DeathContext context
    ) {
    }
}