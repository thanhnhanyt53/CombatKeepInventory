package com.votri.combatkeepinv.core.platform;

/**
 * Describes capabilities provided by a CKI platform implementation.
 */
public enum PlatformCapability {

    /**
     * Combat state and combat tracking.
     */
    COMBAT,

    /**
     * Player death handling.
     */
    DEATH,

    /**
     * Player inventory handling.
     */
    INVENTORY,

    /**
     * PvP damage/death attribution.
     */
    DAMAGE_ATTRIBUTION,

    /**
     * Proxy-level player session tracking.
     */
    PROXY_SESSION,

    /**
     * Detecting and handling server switches.
     */
    SERVER_SWITCH,

    /**
     * Detecting when a player leaves the entire server cluster.
     */
    CLUSTER_EXIT,

    /**
     * Communication between CKI platform modules.
     */
    PLUGIN_MESSAGING
}