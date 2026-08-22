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

    /**
     * Returns whether this platform belongs to the Bukkit server family.
     *
     * @return true for Bukkit, Spigot, Paper, and Purpur
     */
    public boolean isBukkitFamily() {
        return switch (this) {
            case BUKKIT, SPIGOT, PAPER, PURPUR -> true;
            case VELOCITY, UNKNOWN -> false;
        };
    }

    /**
     * Returns whether this platform is a proxy platform.
     *
     * @return true for Velocity
     */
    public boolean isProxy() {
        return this == VELOCITY;
    }

    /**
     * Returns whether this platform represents a backend game server.
     *
     * @return true for Bukkit-family platforms
     */
    public boolean isServer() {
        return isBukkitFamily();
    }
}