package com.votri.combatkeepinv.core.inventory;

import java.util.UUID;

public interface InventorySnapshot {

    UUID getPlayerId();

    int getMainInventorySize();

    int getArmorSize();

    boolean hasOffhand();

    boolean hasExperience();

    long getExperience();

    int getExperienceLevel();
}