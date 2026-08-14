package com.votri.combatkeepinv;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatManager {
    private final long durationMillis;
    private final Map<UUID, Long> lastCombat = new ConcurrentHashMap<>();

    public CombatManager(long durationMillis) {
        this.durationMillis = Math.max(1L, durationMillis);
    }

    public void tag(Player player) {
        if (player != null) lastCombat.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void tag(Player attacker, Player victim) {
        tag(attacker);
        tag(victim);
    }

    public boolean isInCombat(Player player) {
        return player != null && isInCombat(player.getUniqueId());
    }

    public boolean isInCombat(UUID uuid) {
        if (uuid == null) return false;
        Long timestamp = lastCombat.get(uuid);
        if (timestamp == null) return false;
        if (System.currentTimeMillis() - timestamp <= durationMillis) return true;
        lastCombat.remove(uuid, timestamp);
        return false;
    }

    public long getRemainingMillis(UUID uuid) {
        Long timestamp = lastCombat.get(uuid);
        if (timestamp == null) return 0L;
        long remaining = durationMillis - (System.currentTimeMillis() - timestamp);
        if (remaining <= 0L) {
            lastCombat.remove(uuid, timestamp);
            return 0L;
        }
        return remaining;
    }

    public void remove(UUID uuid) {
        if (uuid != null) lastCombat.remove(uuid);
    }

    public void clear() {
        lastCombat.clear();
    }

    public long getCombatDurationMillis() { return durationMillis; }
}
