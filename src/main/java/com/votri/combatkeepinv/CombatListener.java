package com.votri.combatkeepinv;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.UUID;

public final class CombatListener implements Listener {

    private static final String BYPASS_PERMISSION =
            "combatkeepinventory.bypass";

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
    public void onPvPDamage(
            EntityDamageByEntityEvent event
    ) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player attacker = findPlayerAttacker(
                event.getDamager()
        );

        if (attacker == null) {
            return;
        }

        if (attacker.equals(victim)) {
            return;
        }

        if (plugin.isWorldDisabled(
                victim.getWorld()
        )) {
            return;
        }

        if (plugin.isWorldDisabled(
                attacker.getWorld()
        )) {
            return;
        }

        if (attacker.hasPermission(
                BYPASS_PERMISSION
        )) {
            return;
        }

        if (victim.hasPermission(
                BYPASS_PERMISSION
        )) {
            return;
        }

        if (!plugin.isPvPEnabled()) {
            if (plugin.getConfig().getBoolean(
                    "pvp.block-pvp-when-disabled",
                    true
            )) {
                event.setCancelled(true);
            }

            return;
        }

        if (!worldGuard.canPvP(
                attacker,
                victim
        )) {
            if (plugin.getConfig().getBoolean(
                    "worldguard.block-pvp-when-denied",
                    false
            )) {
                event.setCancelled(true);
            }

            return;
        }

        boolean playerVsPlayerOnly =
                plugin.getConfig().getBoolean(
                        "combat.player-vs-player-only",
                        true
                );

        if (!playerVsPlayerOnly) {
            /*
             * This plugin intentionally only starts its own combat
             * tag from Player vs Player damage. The option exists
             * for compatibility with older configurations.
             */
        }

        combatManager.tag(
                attacker.getUniqueId(),
                victim.getUniqueId()
        );
    }

    @EventHandler(
            priority = EventPriority.HIGHEST
    )
    public void onPlayerDeath(
            PlayerDeathEvent event
    ) {
        Player victim = event.getEntity();

        if (plugin.isWorldDisabled(
                victim.getWorld()
        )) {
            return;
        }

        UUID uuid = victim.getUniqueId();

        if (victim.hasPermission(
                BYPASS_PERMISSION
        )) {
            combatManager.remove(uuid);
            return;
        }

        boolean directPlayerKill =
                victim.getKiller() != null;

        boolean ownCombat =
                combatManager.isInCombat(uuid);

        boolean pvpManagerCombat =
                pvpManager.isInCombat(victim);

        boolean externalCombatAllowed =
                plugin.getConfig().getBoolean(
                        "pvpmanager.use-combat-state",
                        true
                );

        boolean inCombat =
                ownCombat ||
                        (externalCombatAllowed &&
                                pvpManagerCombat);

        /*
         * Direct player kill ALWAYS drops inventory.
         *
         * This explicitly overrides the server gamerule
         * keepInventory because event.setKeepInventory(false)
         * is applied here and the drops are preserved.
         */
        if (directPlayerKill) {
            handlePlayerKill(event);
            combatManager.remove(uuid);
            return;
        }

        /*
         * Death caused by mob/environment while still combat tagged.
         */
        if (inCombat) {
            boolean drop =
                    plugin.getConfig().getBoolean(
                            "death.combat-death-drops",
                            false
                    );

            if (drop) {
                handleDrop(event);
            } else {
                handleKeep(event);
            }

            combatManager.remove(uuid);
            return;
        }

        /*
         * Non-combat PvE/environment death.
         */
        boolean keep =
                plugin.getConfig().getBoolean(
                        "death.non-combat-death-keeps-inventory",
                        true
                );

        if (keep) {
            handleKeep(event);
        } else {
            handleDrop(event);
        }

        combatManager.remove(uuid);
    }

    private void handlePlayerKill(
            PlayerDeathEvent event
    ) {
        event.setKeepInventory(false);
        event.setKeepLevel(false);

        /*
         * IMPORTANT:
         *
         * Do NOT clear event.getDrops().
         *
         * This is what makes Player Kill drop the inventory even
         * when gamerule keepInventory=true.
         */
        if (!plugin.getConfig().getBoolean(
                "death.keep-experience",
                true
        )) {
            event.setDroppedExp(
                    event.getDroppedExp()
            );
        }
    }

    private void handleDrop(
            PlayerDeathEvent event
    ) {
        event.setKeepInventory(false);

        if (!plugin.getConfig().getBoolean(
                "death.keep-experience",
                true
        )) {
            event.setKeepLevel(false);
        }
    }

    private void handleKeep(
            PlayerDeathEvent event
    ) {
        event.setKeepInventory(true);

        /*
         * Because keepInventory=true is explicitly requested,
         * remove normal drops to prevent duplicate inventory.
         */
        event.getDrops().clear();

        boolean keepExperience =
                plugin.getConfig().getBoolean(
                        "death.keep-experience",
                        true
                );

        if (keepExperience) {
            event.setKeepLevel(true);
        } else {
            event.setKeepLevel(false);
            event.setDroppedExp(0);
        }
    }

    private Player findPlayerAttacker(
            org.bukkit.entity.Entity damager
    ) {
        if (damager instanceof Player player) {
            return player;
        }

        if (damager instanceof Projectile projectile) {
            Object shooter =
                    projectile.getShooter();

            if (shooter instanceof Player player) {
                return player;
            }
        }

        return null;
    }
}