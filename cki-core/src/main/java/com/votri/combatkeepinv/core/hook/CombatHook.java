package com.votri.combatkeepinv.core.hook;

import com.votri.combatkeepinv.core.api.CombatPlayer;
import com.votri.combatkeepinv.core.api.DeathContext;

public interface CombatHook {

    default void onCombatStart(
            CombatPlayer attacker,
            CombatPlayer victim
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