package com.votri.combatkeepinv.core.api;

import java.util.UUID;

public interface CombatPlayer {

    UUID getUniqueId();

    String getName();

    boolean hasPermission(String permission);

    boolean isOnline();
}