package com.votri.combatkeepinv;

import org.bukkit.entity.Entity;
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

    /**
     * Starts combat when one player damages another player.
     */
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

        /*
         * A player with bypass permission does not
         * participate in CombatKeepInventory combat tagging.
         */
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

        /*
         * Global PvP state.
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
         * WorldGuard PvP state.
         */
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

        /*
         * The plugin intentionally starts its own combat
         * tag from Player vs Player damage.
         *
         * This option remains for compatibility with
         * older configurations.
         */
        if (!playerVsPlayerOnly) {
            // Compatibility option.
        }

        combatManager.tag(
                attacker.getUniqueId(),
                victim.getUniqueId()
        );
    }

    /**
     * Handles player death and determines whether
     * the inventory should be kept or dropped.
     */
    @EventHandler(
            priority = EventPriority.HIGHEST
    )
    public void onPlayerDeath(
            PlayerDeathEvent event
    ) {
        Player victim = event.getEntity();

        /*
         * Ignore worlds disabled in the configuration.
         */
        if (plugin.isWorldDisabled(
                victim.getWorld()
        )) {
            return;
        }

        UUID uuid = victim.getUniqueId();

        /*
         * BYPASS
         *
         * A player with combatkeepinventory.bypass
         * must always keep their inventory.
         *
         * IMPORTANT:
         * Do not simply return here.
         * We must explicitly set keepInventory=true.
         */
        if (victim.hasPermission(
                BYPASS_PERMISSION
        )) {
            handleKeep(event);
            combatManager.remove(uuid);
            return;
        }

        /*
         * Check whether the death was caused directly
         * by another player.
         */
        boolean directPlayerKill =
                victim.getKiller() != null;

        /*
         * Check CombatKeepInventory combat state.
         */
        boolean ownCombat =
                combatManager.isInCombat(uuid);

        /*
         * Check optional PvPManager combat state.
         */
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
         * Direct player kill.
         *
         * This takes priority over the normal combat
         * death configuration.
         */
        if (directPlayerKill) {
            handlePlayerKill(event);
            combatManager.remove(uuid);
            return;
        }

        /*
         * Death caused by mob/environment while
         * the player is still combat tagged.
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

    /**
     * Handles death directly caused by another player.
     *
     * Inventory is dropped normally.
     */
    private void handlePlayerKill(
            PlayerDeathEvent event
    ) {
        event.setKeepInventory(false);
        event.setKeepLevel(false);

        /*
         * Do NOT clear event.getDrops().
         *
         * This allows the player's inventory to drop.
         */
        if (!plugin.getConfig().getBoolean(
                "death.keep-experience",
                true
        )) {
            event.setDroppedExp(0);
        }
    }

    /**
     * Handles a death where inventory should be dropped.
     */
    private void handleDrop(
            PlayerDeathEvent event
    ) {
        event.setKeepInventory(false);

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

    /**
     * Handles a death where inventory should be kept.
     */
    private void handleKeep(
            PlayerDeathEvent event
    ) {
        event.setKeepInventory(true);

        /*
         * Prevent the same inventory from also
         * appearing as normal death drops.
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

    /**
     * Finds the player responsible for an attack.
     *
     * Supports:
     * - Direct player attacks
     * - Player-fired projectiles
     */
    private Player findPlayerAttacker(
            Entity damager
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
