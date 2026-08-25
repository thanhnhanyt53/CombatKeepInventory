package com.votri.combatkeepinv.velocity.bridge;

/**
 * Protocol shared by Bukkit CombatStateBridge and
 * Velocity CombatStateListener.
 */
public final class CombatStateProtocol {

    public static final String CHANNEL =
            "votri:combat";

    public static final int VERSION = 1;

    public static final byte START = 1;
    public static final byte REFRESH = 2;
    public static final byte END = 3;
    public static final byte FORCE_END = 4;

    private CombatStateProtocol() {
    }
}