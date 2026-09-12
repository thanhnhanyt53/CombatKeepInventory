package com.votri.combatkeepinv.core.death;

import com.votri.combatkeepinv.core.combat.CombatSession;
import com.votri.combatkeepinv.core.damage.DamageSource;
import com.votri.combatkeepinv.core.platform.PlatformInfo;

import java.util.Optional;
import java.util.UUID;

public interface DeathContext {

    UUID getPlayerId();

    long getTimestamp();

    DamageSource getDamageSource();

    DeathReason getDeathReason();

    Optional<UUID> getKillerId();

    Optional<CombatSession> getCombatSession();

    boolean wasInCombat();

    boolean wasPlayerCaused();

    boolean isDirectPvP();

    boolean isIndirectPvP();

    PlatformInfo getPlatform();
}