package com.votri.combatkeepinv.bukkit.debug;

import com.votri.combatkeepinv.bukkit.CombatKeepInventory;
import com.votri.combatkeepinv.core.death.DeathDecision;
import com.votri.combatkeepinv.core.death.DeathReason;
import com.votri.combatkeepinv.core.inventory.InventoryAction;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks admins who have opted in to receive live
 * CombatKeepInventory debug output in-game (chat), so that
 * diagnosing combat/damage/death behaviour no longer requires
 * console or log-file access.
 *
 * <p>This does not replace the existing {@code debug.combat},
 * {@code debug.damage} and {@code debug.death} config flags,
 * which still control whether the same lines are also written
 * to the server console/log. Toggling live watch mode via
 * {@code /cki debug} works independently of those flags.</p>
 */
public final class DebugManager {

    private static final String PREFIX =
            "&8[&bCKI-Debug&8] &7";

    private final CombatKeepInventory plugin;

    /** How many players' last death decisions are remembered. */
    static final int MAX_DEATH_RECORDS = 256;

    /**
     * What the plugin decided the last time a player died, so
     * {@code /cki debug <player>} can explain it afterwards
     * without anyone having to catch the live feed.
     */
    public record DeathRecord(
            long timestamp,
            DeathReason reason,
            String ruleId,
            int priority,
            InventoryAction action,
            boolean keepExperience,
            boolean keepLevels
    ) {

        /** True when a priority actually took part in the decision. */
        public boolean hasPriority() {
            return priority != Integer.MIN_VALUE;
        }
    }

    private final Set<UUID> watchers =
            ConcurrentHashMap.newKeySet();

    private final Map<UUID, DeathRecord> lastDeaths =
            Collections.synchronizedMap(
                    new LinkedHashMap<UUID, DeathRecord>(
                            16,
                            0.75f,
                            false
                    ) {
                        @Override
                        protected boolean removeEldestEntry(
                                Map.Entry<UUID, DeathRecord> eldest
                        ) {
                            return size() > MAX_DEATH_RECORDS;
                        }
                    }
            );

    public DebugManager(
            CombatKeepInventory plugin
    ) {
        this.plugin = plugin;
    }

    /**
     * Toggles live debug watch mode for the given admin.
     *
     * @return true if watch mode is now ON for this admin
     */
    public boolean toggle(
            Player admin
    ) {

        UUID id =
                admin.getUniqueId();

        if (!watchers.add(id)) {

            watchers.remove(id);

            return false;
        }

        return true;
    }

    public boolean isWatching(
            UUID playerId
    ) {
        return watchers.contains(playerId);
    }

    /**
     * Returns whether at least one admin currently has live
     * debug watch mode enabled. Callers can use this to decide
     * whether it is worth building a debug message at all when
     * the related config debug flag is disabled.
     */
    public boolean hasWatchers() {
        return !watchers.isEmpty();
    }

    /**
     * Sends a debug line to every currently online watcher.
     * Safe to call unconditionally; it is a no-op when there
     * are no watchers online.
     */
    public void broadcast(
            String message
    ) {

        if (watchers.isEmpty()
                || message == null) {

            return;
        }

        String formatted =
                plugin.color(
                        PREFIX + message
                );

        for (UUID id : watchers) {

            Player player =
                    Bukkit.getPlayer(id);

            if (player != null
                    && player.isOnline()) {

                player.sendMessage(
                        formatted
                );
            }
        }
    }

    /**
     * Clears every watcher. Used on plugin disable so a reload
     * or shutdown does not leave stale state behind.
     */
    /**
     * Remembers the decision made for a player's death.
     */
    public void recordDeath(
            UUID playerId,
            DeathDecision decision
    ) {

        if (playerId == null
                || decision == null) {
            return;
        }

        lastDeaths.put(
                playerId,
                new DeathRecord(
                        System.currentTimeMillis(),
                        decision.getReason(),
                        decision.getRuleId(),
                        decision.getPriority(),
                        decision.getInventoryPolicy().getAction(),
                        decision.getInventoryPolicy().keepExperience(),
                        decision.getInventoryPolicy().keepLevels()
                )
        );
    }

    public Optional<DeathRecord> getLastDeath(
            UUID playerId
    ) {
        return Optional.ofNullable(
                lastDeaths.get(playerId)
        );
    }

    public void clear() {
        watchers.clear();
        lastDeaths.clear();
    }
}
