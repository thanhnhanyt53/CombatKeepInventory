package com.votri.combatkeepinv;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatManager {

    private final JavaPlugin plugin;
    private final long combatDurationMillis;

    /*
     * UUID -> thời điểm combat cuối cùng.
     *
     * ConcurrentHashMap được dùng để tránh các vấn đề nếu trạng thái
     * được truy cập từ nhiều context khác nhau.
     */
    private final Map<UUID, Long> combatStates =
            new ConcurrentHashMap<>();

    public CombatManager(JavaPlugin plugin, long combatDurationMillis) {
        this.plugin = plugin;
        this.combatDurationMillis = combatDurationMillis;
    }

    /**
     * Đưa player vào combat hoặc refresh combat timer.
     */
    public void tag(Player player) {
        if (player == null) {
            return;
        }

        combatStates.put(
                player.getUniqueId(),
                System.currentTimeMillis()
        );
    }

    /**
     * Tag cả hai player trong một cuộc PvP.
     */
    public void tag(Player attacker, Player victim) {
        tag(attacker);
        tag(victim);
    }

    /**
     * Kiểm tra player còn combat hay không.
     */
    public boolean isInCombat(Player player) {
        if (player == null) {
            return false;
        }

        return isInCombat(player.getUniqueId());
    }

    /**
     * Kiểm tra UUID còn combat hay không.
     */
    public boolean isInCombat(UUID uuid) {
        Long lastCombat = combatStates.get(uuid);

        if (lastCombat == null) {
            return false;
        }

        long elapsed = System.currentTimeMillis() - lastCombat;

        if (elapsed <= combatDurationMillis) {
            return true;
        }

        // Timer đã hết -> cleanup ngay.
        combatStates.remove(uuid, lastCombat);

        return false;
    }

    /**
     * Lấy thời gian còn lại của combat.
     */
    public long getRemainingMillis(UUID uuid) {
        Long lastCombat = combatStates.get(uuid);

        if (lastCombat == null) {
            return 0L;
        }

        long remaining =
                combatDurationMillis
                        - (System.currentTimeMillis() - lastCombat);

        if (remaining <= 0L) {
            combatStates.remove(uuid, lastCombat);
            return 0L;
        }

        return remaining;
    }

    /**
     * Xóa combat state của player.
     */
    public void remove(UUID uuid) {
        if (uuid != null) {
            combatStates.remove(uuid);
        }
    }

    /**
     * Cleanup toàn bộ combat state.
     */
    public void clear() {
        combatStates.clear();
    }

    public long getCombatDurationMillis() {
        return combatDurationMillis;
    }

    public int getCombatPlayerCount() {
        return combatStates.size();
    }
}
