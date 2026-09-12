package com.votri.combatkeepinv.core.api;

import com.votri.combatkeepinv.core.inventory.InventoryAction;
import com.votri.combatkeepinv.core.inventory.InventoryDecision;
import com.votri.combatkeepinv.core.inventory.InventoryPolicy;

public interface InventoryAPI {

    InventoryPolicy getDefaultPolicy();

    InventoryDecision createDecision(
            InventoryAction action,
            InventoryPolicy policy
    );
}