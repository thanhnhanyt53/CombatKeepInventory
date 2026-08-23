package com.votri.combatkeepinv.bukkit.listener;

import com.votri.combatkeepinv.bukkit.CombatKeepInventory;
import com.votri.combatkeepinv.bukkit.hook.WorldGuardHook;
import com.votri.combatkeepinv.core.api.CombatResult;
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
 * Bukkit event adapter for CombatKeepInventory.
 *
 * <p>This class is responsible only for translating Bukkit
 * events into CombatService operations.</p>
 */
public final class CombatListener implements Listener {

    private static final String BYPASS_PERMISSION =
            "combatkeepinventory.bypass";

    private final CombatKeepInventory plugin;
    private final CombatService combatService;
    private final WorldGuardHook worldGuard;

    public CombatListener(
            CombatKeepInventory plugin,
            CombatService combatService,
            WorldGuardHook worldGuard
    ) {
        this.plugin = plugin;
        this.combatService = combatService;
        this.worldGuard = worldGuard;
    }

    /**
     * Handles every entity damage event.
     *
     * <p>Only actual player-vs-player damage creates or
     * refreshes Combat Tags.</p>
     */
    public void onEntityDamageByEntity(
            EntityDamageByEntityEvent event
    ) {

        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        if (plugin.isWorldDisabled(
                victim.getWorld()
        )) {
            return;
        }

        if (victim.hasPermission(
                BYPASS_PERMISSION
        )) {
            return;
        }

        Player attacker =
                getAttackingPlayer(
                        event.getDamager()
                );

        /*
         * ==========================================================
         * PLAYER VS PLAYER
         * ==========================================================
         */
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
         * WorldGuard decides whether this damage is valid PvP.
         *
         * CKI does NOT own the server PvP toggle.
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

        /*
         * Start/refresh the Combat Tag.
         */
        CombatResult result =
                combatService.startCombat(
                        attacker.getUniqueId(),
                        victim.getUniqueId()
                );

        if (result == CombatResult.SUCCESS
                || result == CombatResult.ALREADY_IN_COMBAT) {

            debugCombat(
                    attacker,
                    victim
            );
        }
    }

    /**
     * Evaluates the death using the following strict priority:
     *
     * <pre>
     * 1. Active Combat Tag -> DROP
     * 2. No Combat Tag + final PvP damage -> DROP
     * 3. No Combat Tag + PvE/environment -> KEEP
     * </pre>
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

        if (victim.hasPermission(
                BYPASS_PERMISSION
        )) {

            keepInventory(event);

            combatService.endCombat(uuid);

            return;
        }

        /*
         * Resolve the actual final damage source.
         */
        DeathContext context =
                resolveDeathContext(
                        victim
                );

        /*
         * CombatService checks the Combat Tag FIRST.
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

        /*
         * Only remove the tag AFTER the death decision
         * has been evaluated.
         */
        combatService.endCombat(uuid);

        debugDeath(
                victim,
                context,
                result
        );
    }

    /**
     * Resolves the final damage context.
     */
    private DeathContext resolveDeathContext(
            Player player
    ) {

        /*
         * Bukkit killer is the strongest direct indication
         * of a player kill.
         */
        if (player.getKiller() != null) {
            return DeathContext.PLAYER;
        }

        if (player.getLastDamageCause() == null) {
            return DeathContext.UNKNOWN;
        }

        if (!(player.getLastDamageCause()
                instanceof EntityDamageByEntityEvent damage)) {

            return DeathContext.ENVIRONMENT;
        }

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
         * Other entity damage is PvE unless an active
         * Combat Tag already exists.
         *
         * The active Combat Tag is evaluated separately
         * by BukkitCombatService.
         */
        return DeathContext.MOB;
    }

    /**
     * Applies the platform-neutral death policy to Bukkit.
     */
    private void applyDeathResult(
            PlayerDeathEvent event,
            DeathResult result
    ) {

        if (result == null) {
            keepInventory(event);
            return;
        }

        if (result.shouldDropInventory()) {

            dropInventory(
                    event,
                    result.shouldKeepExperience()
            );

            return;
        }

        if (result.shouldKeepInventory()) {

            keepInventory(
                    event,
                    result.shouldKeepExperience()
            );

            return;
        }

        /*
         * Fail-safe:
         * unknown/default policy = KEEP.
         */
        keepInventory(
                event,
                result.shouldKeepExperience()
        );
    }

    /**
     * Forces the player's inventory to drop.
     */
    private void dropInventory(
            PlayerDeathEvent event,
            boolean keepExperience
    ) {

        Player player =
                event.getEntity();

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
     * Keeps inventory using config experience policy.
     */
    private void keepInventory(
            PlayerDeathEvent event
    ) {

        keepInventory(
                event,
                plugin.getConfig().getBoolean(
                        "death.keep-experience",
                        true
                )
        );
    }

    /**
     * Keeps inventory.
     */
    private void keepInventory(
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

    private void addDrops(
            PlayerDeathEvent event,
            ItemStack[] items
    ) {

        if (items == null) {
            return;
        }

        for (ItemStack item : items) {

            if (item == null) {
                continue;
            }

            if (item.getType().isAir()) {
                continue;
            }

            event.getDrops().add(
                    item.clone()
            );
        }
    }

    /**
     * Resolves a player from a direct player attack
     * or player-owned projectile.
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
                "Combat Tag refreshed: "
                        + attacker.getName()
                        + " <-> "
                        + victim.getName()
                        + " | remaining="
                        + plugin.getCombatManager()
                                .getRemainingSeconds(
                                        victim.getUniqueId()
                                )
                        + "s"
        );
    }

    private void debugDeath(
            Player player,
            DeathContext context,
            DeathResult result
    ) {

        if (!plugin.getConfig().getBoolean(
                "debug.combat",
                false
        )) {
            return;
        }

        plugin.getLogger().info(
                "Death evaluated: "
                        + player.getName()
                        + " | context="
                        + context
                        + " | combat-tag="
                        + result.wasCombatDeath()
                        + " | policy="
                        + result.getInventoryPolicy()
        );
    }
}