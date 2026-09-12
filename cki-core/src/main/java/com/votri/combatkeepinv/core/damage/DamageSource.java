package com.votri.combatkeepinv.core.damage;

import java.util.Optional;
import java.util.UUID;

public interface DamageSource {

    DamageCauseType getCauseType();

    Optional<UUID> getDirectAttackerId();

    Optional<UUID> getResponsiblePlayerId();

    Optional<UUID> getVictimId();

    boolean isPlayerCaused();

    boolean isIndirect();

    long getTimestamp();
}