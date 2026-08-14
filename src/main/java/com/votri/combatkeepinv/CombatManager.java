package com.votri.combatkeepinv;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatManager {

    private final Map<UUID, Long> combatMap =
            new ConcurrentHashMap<>();

    private volatile long combatDurationMs;

    public CombatManager(long combatDurationMs) {
        this.combatDurationMs =
                Math.max(1000L, combatDurationMs);
    }

    public void setCombatDurationMs(long durationMs) {
        this.combatDurationMs =
                Math.max(1000L, durationMs);
    }

    public long getCombatDurationMs() {
        return combatDurationMs;
    }

    public void tag(Player player) {

        if (player == null) {
            return;
        }

        combatMap.put(
                player.getUniqueId(),
                System.currentTimeMillis()
        );
    }

    public void tag(UUID uuid) {

        if (uuid == null) {
            return;
        }

        combatMap.put(
                uuid,
                System.currentTimeMillis()
        );
    }

    public boolean isInCombat(Player player) {

        if (player == null) {
            return false;
        }

        return isInCombat(
                player.getUniqueId()
        );
    }

    public boolean isInCombat(UUID uuid) {

        Long lastCombat =
                combatMap.get(uuid);

        if (lastCombat == null) {
            return false;
        }

        long elapsed =
                System.currentTimeMillis()
                        - lastCombat;

        if (elapsed >= combatDurationMs) {
            combatMap.remove(uuid);
            return false;
        }

        return true;
    }

    public long getRemainingMs(Player player) {

        if (player == null) {
            return 0L;
        }

        return getRemainingMs(
                player.getUniqueId()
        );
    }

    public long getRemainingMs(UUID uuid) {

        Long lastCombat =
                combatMap.get(uuid);

        if (lastCombat == null) {
            return 0L;
        }

        long elapsed =
                System.currentTimeMillis()
                        - lastCombat;

        long remaining =
                combatDurationMs - elapsed;

        if (remaining <= 0L) {
            combatMap.remove(uuid);
            return 0L;
        }

        return remaining;
    }

    public boolean hasRemainingCombatTime(
            Player player,
            long minimumMilliseconds
    ) {

        return getRemainingMs(player)
                >= minimumMilliseconds;
    }

    public void remove(Player player) {

        if (player != null) {
            combatMap.remove(
                    player.getUniqueId()
            );
        }
    }

    public void remove(UUID uuid) {

        if (uuid != null) {
            combatMap.remove(uuid);
        }
    }

    public void clear() {
        combatMap.clear();
    }
}
