package com.votri.combatkeepinv.core.api;

import com.votri.combatkeepinv.core.damage.DamageAttributionService;
import com.votri.combatkeepinv.core.death.DeathService;

import java.util.UUID;

public interface CombatService {

    CombatResult startCombat(UUID attacker, UUID victim);

    CombatResult refreshCombat(UUID attacker, UUID victim);

    CombatResult endCombat(UUID player);

    CombatResult forceEndCombat(UUID player);

    boolean isInCombat(UUID player);

    CombatState getCombatState(UUID player);

    CombatTag getCombatTag(UUID player);

    long getRemainingCombatMillis(UUID player);

    DeathResult evaluateDeath(UUID player, DeathContext context);

    boolean isEnabled();

    // Bổ sung các phương thức bị thiếu để CombatListener gọi được
    DamageAttributionService getDamageAttributionService();

    DeathService getDeathService();
}
