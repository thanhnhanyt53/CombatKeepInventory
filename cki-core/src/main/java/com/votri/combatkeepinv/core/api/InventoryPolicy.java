package com.votri.combatkeepinv.core.api;

public enum InventoryPolicy {

    KEEP,

    DROP,

    /**
     * Keep only the inventory components selected by the resolved policy.
     */
    PARTIAL,

    /**
     * Do not override the server's vanilla keepInventory behavior.
     */
    DEFAULT
}
