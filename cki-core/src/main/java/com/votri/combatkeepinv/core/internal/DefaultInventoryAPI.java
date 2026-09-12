package com.votri.combatkeepinv.core.internal;

import com.votri.combatkeepinv.core.api.InventoryAPI;
import com.votri.combatkeepinv.core.inventory.InventoryAction;
import com.votri.combatkeepinv.core.inventory.InventoryDecision;
import com.votri.combatkeepinv.core.inventory.InventoryPolicy;

public final class DefaultInventoryAPI
        implements InventoryAPI {

    private final InventoryPolicy defaultPolicy;

    public DefaultInventoryAPI(
            InventoryPolicy defaultPolicy
    ) {
        this.defaultPolicy = defaultPolicy;
    }

    @Override
    public InventoryPolicy getDefaultPolicy() {
        return defaultPolicy;
    }

    @Override
    public InventoryDecision createDecision(
            InventoryAction action,
            InventoryPolicy policy
    ) {
        return new DefaultInventoryDecision(
                action,
                policy
        );
    }
}