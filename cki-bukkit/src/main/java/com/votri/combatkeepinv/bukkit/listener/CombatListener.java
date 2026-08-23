package com.votri.combatkeepinv.bukkit.listener;

import com.votri.combatkeepinv.bukkit.CombatKeepInventory;
import com.votri.combatkeepinv.bukkit.combat.CombatManager;
import com.votri.combatkeepinv.bukkit.hook.WorldGuardHook;
import com.votri.combatkeepinv.core.api.CombatService;
import com.votri.combatkeepinv.core.api.DeathContext;
import com.votri.combatkeepinv.core.api.DeathResult;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import java.util.UUID;

/**
 * Converts Bukkit combat/death events into CKI state changes.
 *
 * <p>CombatManager owns CombatTag state.
 * CombatService owns the platform-independent death policy.</p>
 */
public final class CombatListener implements Listener {

    private static final String BYPASS_PERMISSION =
            "combatkeepinventory.bypass";

    private final CombatKeepInventory plugin;
    private final CombatManager combatManager;
    private final CombatService combatService;
    private final WorldGuardHook worldGuard;

    public CombatListener(
            CombatKeepInventory plugin,
            CombatService combatService,
            WorldGuardHook worldGuard
    ) {
        this.plugin =
                plugin;

        this.combatManager =
                plugin.getCombatManager();

        this.combatService =
                combatService;

        this.worldGuard =
                worldGuard;
    }

    /**
     * Handles entity damage.
     *
     * <p>Only actual player-originated damage creates a
     * CombatTag. Every valid hit refreshes both players.</p>
     */
    public void onEntityDamageByEntity(
            EntityDamageByEntityEvent event
    ) {

        /*
         * A cancelled damage event is not a successful PvP hit.
         */
        if (event.isCancelled()) {
            return;
        }

        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        if (victim.hasPermission(
                BYPASS_PERMISSION
        )) {
            return;
        }

        if (plugin.isWorldDisabled(
                victim.getWorld()
        )) {
            return;
        }

        Player attacker =
                resolveAttackingPlayer(
                        event.getDamager()
                );

        if (attacker == null) {
            return;
        }

        if (attacker.equals(victim)) {
            return;
        }

        if (attacker.hasPermission(
                BYPASS_PERMISSION
        )) {
            return;
        }

        if (plugin.isWorldDisabled(
                attacker.getWorld()
        )) {
            return;
        }

        /*
         * WorldGuard is the only optional PvP permission
         * check performed by CKI itself.
         */
        if (worldGuard != null
                && !worldGuard.canPvP(
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

        /*
         * ======================================================
         * COMBAT TAG
         * ======================================================
         *
         * IMPORTANT:
         *
         * Do NOT call combatService.isPvPEnabled().
         * CKI no longer owns a PvP on/off switch.
         *
         * The successful Bukkit damage event itself proves
         * that a player hit another player.
         */
        combatManager.tag(
                attacker.getUniqueId(),
                victim.getUniqueId()
        );

        debugCombat(
                attacker,
                victim
        );
    }

    /**
     * Handles player death.
     *
     * <p>The decision order is:</p>
     *
     * <ol>
     *     <li>Active CombatTag -> DROP</li>
     *     <li>No CombatTag + PvP final hit -> DROP</li>
     *     <li>No CombatTag + PvE/environment -> KEEP</li>
     * </ol>
     */
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

        UUID uuid =
                victim.getUniqueId();

        /*
         * ======================================================
         * BYPASS
         * ======================================================
         */

        if (victim.hasPermission(
                BYPASS_PERMISSION
        )) {

            handleKeepInventory(
                    event
            );

            combatManager.remove(uuid);

            return;
        }

        /*
         * Resolve the final damage source first.
         *
         * This is only used when CombatTag has already expired.
         */
        DeathContext context =
                resolveDeathContext(
                        victim
                );

        /*
         * ======================================================
         * ACTIVE COMBAT TAG
         * ======================================================
         *
         * CombatManager is checked directly so no other
         * abstraction can accidentally bypass the CombatTag rule.
         */
        boolean inCombat =
                combatManager.isInCombat(
                        uuid
                );

        if (inCombat) {

            /*
             * We still use CombatService to create the
             * standardized DeathResult, but the CombatTag
             * decision has already been made locally.
             */
            DeathResult result =
                    combatService.evaluateDeath(
                            uuid,
                            context
                    );

            /*
             * Defensive guarantee:
             *
             * Even if another implementation accidentally
             * returns KEEP for an active tag, CKI must DROP.
             */
            if (!result.wasCombatDeath()
                    || !result.shouldDropInventory()) {

                handleDropInventory(
                        event,
                        plugin.getConfig().getBoolean(
                                "inventory.keep-experience",
                                true
                        )
                );

            } else {

                applyDeathResult(
                        event,
                        result
                );
            }

            debugDeath(
                    victim,
                    context,
                    "ACTIVE_COMBAT_TAG"
            );

            combatManager.remove(uuid);

            return;
        }

        /*
         * ======================================================
         * COMBAT TAG EXPIRED
         * ======================================================
         */

        DeathResult result =
                combatService.evaluateDeath(
                        uuid,
                        context
                );

        applyDeathResult(
                event,
                result
        );

        debugDeath(
                victim,
                context,
                "NO_ACTIVE_COMBAT_TAG"
        );

        combatManager.remove(uuid);
    }

    /**
     * Resolves the final PvP/PvE death context.
     */
    private DeathContext resolveDeathContext(
            Player player
    ) {

        /*
         * Direct player killer.
         */
        if (player.getKiller() != null) {
            return DeathContext.PLAYER;
        }

        /*
         * Final damage event.
         */
        if (player.getLastDamageCause()
                instanceof EntityDamageByEntityEvent damage) {

            Entity damager =
                    damage.getDamager();

            /*
             * Direct player attack.
             */
            if (damager instanceof Player) {
                return DeathContext.PLAYER;
            }

            /*
             * Player-owned projectile.
             */
            if (damager instanceof Projectile projectile) {

                ProjectileSource source =
                        projectile.getShooter();

                if (source instanceof Player) {
                    return DeathContext.PROJECTILE;
                }

                return DeathContext.MOB;
            }

            /*
             * Other entity damage = PvE.
             */
            return DeathContext.MOB;
        }

        /*
         * Fall, lava, void, fire, suffocation, explosion,
         * etc.
         */
        return DeathContext.ENVIRONMENT;
    }

    /**
     * Converts DeathResult into Bukkit death behavior.
     */
    private void applyDeathResult(
            PlayerDeathEvent event,
            DeathResult result
    ) {

        if (result == null) {

            handleKeepInventory(
                    event
            );

            return;
        }

        if (result.shouldDropInventory()) {

            handleDropInventory(
                    event,
                    result.shouldKeepExperience()
            );

            return;
        }

        handleKeepInventory(
                event,
                result.shouldKeepExperience()
        );
    }

    /**
     * Forces the entire inventory to drop.
     */
    private void handleDropInventory(
            PlayerDeathEvent event,
            boolean keepExperience
    ) {

        Player player =
                event.getEntity();

        /*
         * Clear Bukkit's automatically generated list first.
         */
        event.getDrops().clear();

        addDrops(
                event,
                player.getInventory()
                        .getStorageContents()
        );

        addDrops(
                event,
                player.getInventory()
                        .getArmorContents()
        );

        addDrops(
                event,
                player.getInventory()
                        .getExtraContents()
        );

        event.setKeepInventory(false);

        if (keepExperience) {

            event.setKeepLevel(true);

        } else {

            event.setKeepLevel(false);
            event.setDroppedExp(0);
        }
    }

    /**
     * Keeps the player's inventory.
     */
    private void handleKeepInventory(
            PlayerDeathEvent event
    ) {

        handleKeepInventory(
                event,
                plugin.getConfig().getBoolean(
                        "death.keep-experience",
                        true
                )
        );
    }

    /**
     * Keeps inventory with configured XP behavior.
     */
    private void handleKeepInventory(
            PlayerDeathEvent event,
            boolean keepExperience
    ) {

        event.setKeepInventory(true);
        event.getDrops().clear();

        if (keepExperience) {

            event.setKeepLevel(true);
            event.setDroppedExp(0);

        } else {

            event.setKeepLevel(false);
            event.setDroppedExp(0);
        }
    }

    /**
     * Adds a copy of every non-air item to the death drop list.
     */
    private void addDrops(
            PlayerDeathEvent event,
            ItemStack[] items
    ) {

        if (items == null) {
            return;
        }

        for (ItemStack item : items) {

            if (item == null
                    || item.getType().isAir()) {
                continue;
            }

            event.getDrops().add(
                    item.clone()
            );
        }
    }

    /**
     * Resolves a Player attacker from an entity or
     * player-owned projectile.
     */
    private Player resolveAttackingPlayer(
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

    private void debugCombat(
            Player attacker,
            Player victim
    ) {

        if (!plugin.getConfig().getBoolean(
                "debug.combat",
                false
        )) {
            return;
        }

        plugin.getLogger().info(
                "[CombatTag] "
                        + attacker.getName()
                        + " <-> "
                        + victim.getName()
                        + " | duration="
                        + (
                        plugin.getCombatManager()
                                .getRemainingSeconds(
                                        victim.getUniqueId()
                                )
                        )
                        + "s"
        );
    }

    private void debugDeath(
            Player player,
            DeathContext context,
            String state
    ) {

        if (!plugin.getConfig().getBoolean(
                "debug.combat",
                false
        )) {
            return;
        }

        plugin.getLogger().info(
                "[CombatDeath] "
                        + player.getName()
                        + " | state="
                        + state
                        + " | context="
                        + context
        );
    }
}