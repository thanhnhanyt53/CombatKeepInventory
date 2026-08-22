package com.votri.combatkeepinv.core.platform;

/**
 * Identifies the server platform on which CombatKeepInventory is running.
 *
 * <p>The enum deliberately distinguishes Bukkit API based implementations
 * from their concrete server implementations.</p>
 */
public enum PlatformType {

    /**
     * Pure Bukkit implementation.
     */
    BUKKIT,

    /**
     * Spigot server.
     */
    SPIGOT,

    /**
     * Paper server.
     */
    PAPER,

    /**
     * Purpur server.
     */
    PURPUR,

    /**
     * Velocity proxy.
     */
    VELOCITY,

    /**
     * Unknown or unsupported platform.
     */
    UNKNOWN;

    public boolean isBukkitFamily() {
        return switch (this) {
            case BUKKIT, SPIGOT, PAPER, PURPUR -> true;
            case VELOCITY, UNKNOWN -> false;
        };
    }

    public boolean isProxy() {
        return this == VELOCITY;
    }

    public boolean isServer() {
        return isBukkitFamily();
    }
}