package com.votri.combatkeepinv.core.damage;

import java.util.Optional;
import java.util.UUID;

public interface DamageAttributionResult {

    UUID getVictimId();

    DamageSource getDamageSource();

    Optional<UUID> getKillerId();

    boolean isPlayerCaused();

    boolean isDirectPvP();

    boolean isIndirectPvP();
}