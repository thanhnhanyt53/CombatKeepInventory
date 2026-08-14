package com.votri.combatkeepinv;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class CombatListener
        implements Listener {

    private final CombatKeepInventory plugin;
    private final CombatManager combatManager;
    private final WorldGuardHook worldGuard;
    private final PvPManagerHook pvpManager;

    public CombatListener(
            CombatKeepInventory plugin,
            CombatManager combatManager,
            WorldGuardHook worldGuard,
            PvPManagerHook pvpManager
    ) {
        this.plugin = plugin;
        this.combatManager = combatManager;
        this.worldGuard = worldGuard;
        this.pvpManager = pvpManager;
    }

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onEntityDamageByEntity(
            EntityDamageByEntityEvent event
    ) {

        if (!(event.getEntity()
                instanceof Player victim)) {
            return;
        }

        if (plugin.isWorldDisabled(
                victim.getWorld()
        )) {
            return;
        }

        Player attacker =
                getAttackingPlayer(
                        event.getDamager()
                );

        if (attacker == null) {
            return;
        }

        if (attacker.equals(victim)) {
            return;
        }

        if (plugin.isWorldDisabled(
                attacker.getWorld()
        )) {
            return;
        }

        /*
         * PvP disabled.
         */
        if (!plugin.isPvPEnabled()) {

            if (plugin.getConfig().getBoolean(
                    "pvp.block-pvp-when-disabled",
                    true
            )) {
                event.setCancelled(true);
            }

            return;
        }

        /*
         * WorldGuard integration.
         */
        if (!worldGuard.canPvP(
                attacker,
                victim
        )) {
            return;
        }

        /*
         * Start combat.
         */
        combatManager.tag(attacker);
        combatManager.tag(victim);

        if (plugin.getConfig().getBoolean(
                "debug.combat",
                false
        )) {

            plugin.getLogger().info(
                    "Combat tagged: "
                            + attacker.getName()
                            + " <-> "
                            + victim.getName()
            );
        }
    }

    private Player getAttackingPlayer(
            org.bukkit.entity.Entity damager
    ) {

        if (damager instanceof Player player) {
            return player;
        }

        if (damager instanceof Projectile projectile) {

            ProjectileSource source =
                    projectile.getShooter();

            if (source instanceof Player player) {
                return player;
            }
        }

        return null;
    }

    @EventHandler(
            priority = EventPriority.HIGHEST
    )
    public void onPlayerDeath(
            PlayerDeathEvent event
    ) {

        Player victim =
                event.getEntity();

        if (plugin.isWorldDisabled(
                victim.getWorld()
        )) {
            return;
        }

        /*
         * Bypass.
         */
        if (victim.hasPermission(
                "combatkeepinventory.bypass"
        )) {

            combatManager.remove(victim);
            return;
        }

        boolean playerKilled =
                victim.getKiller() != null;

        boolean ownCombat =
                combatManager.isInCombat(victim);

        boolean externalCombat =
                pvpManager.isInCombat(victim);

        boolean inCombat =
                ownCombat || externalCombat;

        /*
         * Direct player kill.
         */
        if (playerKilled
                && plugin.getConfig().getBoolean(
                "death.player-kill-drops",
                true
        )) {

            forceDrop(event);

            combatManager.remove(victim);
            return;
        }

        /*
         * Death while combat tagged.
         */
        if (inCombat
                && plugin.getConfig().getBoolean(
                "death.combat-death-drops",
                false
        )) {

            forceDrop(event);

            combatManager.remove(victim);
            return;
        }

        /*
         * Non-combat PVE/environment death.
         */
        if (plugin.getConfig().getBoolean(
                "death.non-combat-death-keeps-inventory",
                true
        )) {

            keepInventory(event);
        }

        combatManager.remove(victim);
    }

    private void forceDrop(
            PlayerDeathEvent event
    ) {

        event.setKeepInventory(false);

        event.setKeepLevel(false);

        /*
         * IMPORTANT:
         *
         * Do NOT clear drops here.
         *
         * This fixes the old bug where:
         *
         * keepInventory=true
         * +
         * player kill
         *
         * caused the player to respawn without
         * inventory and without dropped items.
         */
    }

    private void keepInventory(
            PlayerDeathEvent event
    ) {

        event.setKeepInventory(true);

        event.getDrops().clear();

        if (plugin.getConfig().getBoolean(
                "death.keep-experience",
                true
        )) {

            event.setKeepLevel(true);
            event.setDroppedExp(0);
        }
    }
}
