package com.votri.combatkeepinv;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class CombatListener implements Listener {
    private static final String BYPASS = "combatkeepinventory.bypass";

    private final CombatKeepInventory plugin;
    private final CombatManager combatManager;
    private final WorldGuardHook worldGuard;
    private final PvPManagerHook pvpManager;

    public CombatListener(CombatKeepInventory plugin, CombatManager combatManager,
                           WorldGuardHook worldGuard, PvPManagerHook pvpManager) {
        this.plugin = plugin;
        this.combatManager = combatManager;
        this.worldGuard = worldGuard;
        this.pvpManager = pvpManager;
    }

    private Player getPlayerAttacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = getPlayerAttacker(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) return;

        if (hasBypass(attacker) || hasBypass(victim)) return;

        if (plugin.getConfig().getBoolean("worldguard.enabled", true)
                && !worldGuard.isPvPAllowed(attacker, victim)) {
            debug("PvP ignored by WorldGuard: " + attacker.getName() + " -> " + victim.getName());
            return;
        }

        if (plugin.getConfig().getBoolean("combat.player-vs-player-only", true)) {
            combatManager.tag(attacker, victim);
        }
        debug("Combat tagged: " + attacker.getName() + " <-> " + victim.getName());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();

        // Bypass means this plugin does not alter the death event at all.
        if (hasBypass(victim)) {
            combatManager.remove(victim.getUniqueId());
            return;
        }

        // If configured, WorldGuard can make this death completely untouched.
        if (plugin.getConfig().getBoolean("worldguard.ignore-death-outside-regions", false)
                && !worldGuard.isAllowed(victim)) {
            combatManager.remove(victim.getUniqueId());
            return;
        }

        boolean directPlayerKill = victim.getKiller() != null;
        boolean ownCombat = combatManager.isInCombat(victim);

        boolean pvpManagerCombat = false;
        boolean pvpManagerLastPvP = false;

        if (plugin.getConfig().getBoolean("pvpmanager.enabled", true)
                && pvpManager.isAvailable()) {
            if (plugin.getConfig().getBoolean("pvpmanager.use-combat-state", true)) {
                pvpManagerCombat = pvpManager.isInCombat(victim);
            }
            if (plugin.getConfig().getBoolean("pvpmanager.trust-last-pvp-death", true)) {
                pvpManagerLastPvP = pvpManager.wasLastDeathPvP(victim);
            }
        }

        boolean inCombat = ownCombat
                || (plugin.getConfig().getBoolean("pvpmanager.inherit-combat-state", true)
                    && pvpManagerCombat);

        boolean shouldDrop = false;

        if (directPlayerKill
                && plugin.getConfig().getBoolean("death.player-kill-drops", true)) {
            shouldDrop = true;
        }

        if (inCombat
                && plugin.getConfig().getBoolean("death.combat-death-drops", true)) {
            shouldDrop = true;
        }

        if (pvpManagerLastPvP
                && plugin.getConfig().getBoolean("pvpmanager.trust-last-pvp-death", true)) {
            shouldDrop = true;
        }

        if (shouldDrop) {
            /*
             * Critical fix:
             * false overrides server keep-inventory=true.
             * Do NOT clear event.getDrops() here.
             */
            event.setKeepInventory(false);
            event.setKeepLevel(false);
            debugDeath("DROP " + victim.getName()
                    + " direct=" + directPlayerKill
                    + " combat=" + inCombat
                    + " pvpmanager=" + pvpManagerLastPvP);
        } else if (plugin.getConfig().getBoolean(
                "death.non-combat-death-keeps-inventory", true)) {

            event.setKeepInventory(true);
            event.getDrops().clear();

            if (plugin.getConfig().getBoolean("death.keep-experience", true)) {
                event.setKeepLevel(true);
                event.setDroppedExp(0);
            }

            debugDeath("KEEP " + victim.getName());
        } else {
            event.setKeepInventory(false);
            event.setKeepLevel(false);
        }

        combatManager.remove(victim.getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        combatManager.remove(event.getPlayer().getUniqueId());
    }

    private boolean hasBypass(Player player) {
        return player.hasPermission(BYPASS);
    }

    private void debug(String message) {
        if (plugin.getConfig().getBoolean("debug.combat", false)) {
            plugin.getLogger().info("[Combat] " + message);
        }
    }

    private void debugDeath(String message) {
        if (plugin.getConfig().getBoolean("debug.death", false)) {
            plugin.getLogger().info("[Death] " + message);
        }
    }
}
