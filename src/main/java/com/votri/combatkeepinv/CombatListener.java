package com.votri.combatkeepinv;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

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
     * Handles Player -> Player damage.
     *
     * PvP toggle has priority over bypass:
     * - PvP OFF + block=true  -> cancel damage.
     * - PvP OFF + block=false -> allow damage, no combat tag.
     * - PvP ON                  -> normal processing.
     *
     * Bypass only prevents CombatKeepInventory combat tagging;
     * it does NOT bypass the global PvP switch.
     */
    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onEntityDamageByEntity(
            EntityDamageByEntityEvent event
    ) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player attacker = getAttackingPlayer(
                event.getDamager()
        );

        if (attacker == null) {
            return;
        }

        if (attacker.equals(victim)) {
            return;
        }

        /*
         * Disabled worlds:
         * CombatKeepInventory does not interfere here.
         */
        if (plugin.isWorldDisabled(victim.getWorld())) {
            return;
        }

        if (plugin.isWorldDisabled(attacker.getWorld())) {
            return;
        }

        /*
         * ------------------------------------------------------
         * GLOBAL PVP TOGGLE
         * ------------------------------------------------------
         */
        if (!plugin.isPvPEnabled()) {

            /*
             * PvP OFF + blocking enabled:
             * prevent the actual PvP damage.
             */
            if (plugin.getConfig().getBoolean(
                    "pvp.block-pvp-when-disabled",
                    true
            )) {
                event.setCancelled(true);
            }

            /*
             * In both modes we do NOT create a CKI combat tag.
             */
            return;
        }

        /*
         * ------------------------------------------------------
         * WORLDGUARD
         * ------------------------------------------------------
         */
        if (!worldGuard.canPvP(
                attacker,
                victim
        )) {

            /*
             * WorldGuard integration can optionally cancel
             * PvP damage itself.
             */
            if (plugin.getConfig().getBoolean(
                    "worldguard.block-pvp-when-denied",
                    false
            )) {
                event.setCancelled(true);
            }

            return;
        }

        /*
         * ------------------------------------------------------
         * BYPASS
         * ------------------------------------------------------
         *
         * PvP itself is still allowed.
         * The bypass player simply does not participate
         * in CombatKeepInventory combat tagging.
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
         * ------------------------------------------------------
         * PLAYER VS PLAYER COMBAT TAG
         * ------------------------------------------------------
         *
         * This event is already Player -> Player, therefore
         * player-vs-player-only remains true/compatible.
         *
         * The config option is retained for compatibility
         * with older configurations.
         */
        combatManager.tag(
                attacker.getUniqueId(),
                victim.getUniqueId()
        );

        /*
         * Debug logging.
         */
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

    /**
     * Handles all player deaths.
     */
    @EventHandler(
            priority = EventPriority.HIGHEST
    )
    public void onPlayerDeath(
            PlayerDeathEvent event
    ) {
        Player victim = event.getEntity();

        /*
         * Disabled world:
         * CombatKeepInventory does not interfere.
         */
        if (plugin.isWorldDisabled(
                victim.getWorld()
        )) {
            return;
        }

        UUID uuid = victim.getUniqueId();

        /*
         * ------------------------------------------------------
         * BYPASS
         * ------------------------------------------------------
         *
         * A bypass player must ALWAYS keep inventory,
         * regardless of:
         *
         * - keepInventory gamerule
         * - PvP
         * - combat tag
         * - death cause
         */
        if (victim.hasPermission(
                BYPASS_PERMISSION
        )) {

            handleKeepInventory(event);

            combatManager.remove(uuid);

            return;
        }

        /*
         * ------------------------------------------------------
         * DIRECT PLAYER KILL
         * ------------------------------------------------------
         *
         * This is intentionally independent from the current
         * PvP toggle state.
         *
         * If a valid PlayerDeathEvent contains a player killer,
         * the death follows player-kill-drops.
         */
        boolean directPlayerKill =
                victim.getKiller() != null;

        /*
         * ------------------------------------------------------
         * COMBAT STATE
         * ------------------------------------------------------
         */
        boolean ownCombat =
                combatManager.isInCombat(uuid);

        boolean externalCombat =
                plugin.getConfig().getBoolean(
                        "pvpmanager.use-combat-state",
                        true
                )
                        && pvpManager.isInCombat(victim);

        boolean inCombat =
                ownCombat || externalCombat;

        /*
         * ------------------------------------------------------
         * DIRECT PLAYER KILL
         * ------------------------------------------------------
         */
        if (directPlayerKill) {

            boolean shouldDrop =
                    plugin.getConfig().getBoolean(
                            "death.player-kill-drops",
                            true
                    );

            if (shouldDrop) {
                handleDropInventory(event);
            } else {
                handleKeepInventory(event);
            }

            combatManager.remove(uuid);

            return;
        }

        /*
         * ------------------------------------------------------
         * NON-PLAYER DEATH WHILE COMBAT TAGGED
         * ------------------------------------------------------
         */
        if (inCombat) {

            boolean shouldDrop =
                    plugin.getConfig().getBoolean(
                            "death.combat-death-drops",
                            false
                    );

            if (shouldDrop) {
                handleDropInventory(event);
            } else {
                handleKeepInventory(event);
            }

            combatManager.remove(uuid);

            return;
        }

        /*
         * ------------------------------------------------------
         * NON-COMBAT PVE / ENVIRONMENT DEATH
         * ------------------------------------------------------
         */
        boolean shouldKeep =
                plugin.getConfig().getBoolean(
                        "death.non-combat-death-keeps-inventory",
                        true
                );

        if (shouldKeep) {
            handleKeepInventory(event);
        } else {
            handleDropInventory(event);
        }

        combatManager.remove(uuid);
    }

    /**
     * Forces inventory to DROP.
     *
     * IMPORTANT:
     * Do NOT clear event.getDrops().
     *
     * This is required to fix:
     *
     * keepInventory=true
     * +
     * Player Kill
     * =
     * item must still drop.
     */
    private void handleDropInventory(
            PlayerDeathEvent event
    ) {
        event.setKeepInventory(false);

        /*
         * Experience behavior.
         */
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

        /*
         * NEVER clear event.getDrops() here.
         */
    }

    /**
     * Forces inventory to be kept.
     */
    private void handleKeepInventory(
            PlayerDeathEvent event
    ) {
        event.setKeepInventory(true);

        /*
         * Prevent duplicate inventory:
         * the inventory is kept by the server, so normal drops
         * must be removed.
         */
        event.getDrops().clear();

        boolean keepExperience =
                plugin.getConfig().getBoolean(
                        "death.keep-experience",
                        true
                );

        if (keepExperience) {
            event.setKeepLevel(true);
            event.setDroppedExp(0);
        } else {
            event.setKeepLevel(false);
            event.setDroppedExp(0);
        }
    }

    /**
     * Resolves the Player responsible for the damage.
     *
     * Supports:
     * - Direct Player attacks
     * - Player-fired projectiles
     */
    private Player getAttackingPlayer(
            Entity damager
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
}