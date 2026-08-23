package com.votri.combatkeepinv.bukkit.listener;

import com.votri.combatkeepinv.bukkit.CombatKeepInventory;
import com.votri.combatkeepinv.bukkit.combat.CombatManager;
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
 * Converts Bukkit combat/death events into CKI state changes.
 *
 * <p>CombatManager owns the active CombatTag state.</p>
 *
 * <p>CombatService owns the combat/death policy.</p>
 *
 * <p>
 * The most important rule is:
 *
 * <pre>
 * PlayerDeathEvent
 *       |
 *       +-- Active CombatTag --> DROP
 *       |
 *       +-- No CombatTag
 *              |
 *              +-- PvP death --> configured PvP policy
 *              |
 *              +-- PvE death --> configured PvE policy
 * </pre>
 *
 * <p>
 * DeathContext is therefore NEVER allowed to override an active
 * CombatTag.
 * </p>
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
        this.plugin = plugin;

        this.combatManager =
                plugin.getCombatManager();

        this.combatService =
                combatService;

        this.worldGuard =
                worldGuard;
    }

    /*
     * ==========================================================
     * COMBAT
     * ==========================================================
     */

    /**
     * Handles player-originated damage.
     *
     * <p>
     * Only Player -> Player damage creates a CombatTag.
     * Player-owned projectiles also count as PvP damage.
     * </p>
     */
    public void onEntityDamageByEntity(
            EntityDamageByEntityEvent event
    ) {

        /*
         * Cancelled damage is not considered a valid hit.
         */
        if (event.isCancelled()) {
            return;
        }

        /*
         * Victim must be a player.
         */
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        /*
         * Resolve the actual player responsible for the hit.
         *
         * Supported:
         *
         * Player -> Player
         * Player -> Projectile -> Player
         */
        Player attacker =
                resolveAttackingPlayer(
                        event.getDamager()
                );

        /*
         * Mob / environment damage does not create CombatTag.
         */
        if (attacker == null) {
            return;
        }

        /*
         * Self-damage must never create CombatTag.
         */
        if (attacker.getUniqueId().equals(
                victim.getUniqueId()
        )) {
            return;
        }

        /*
         * Bypass permission.
         */
        if (attacker.hasPermission(
                BYPASS_PERMISSION
        ) || victim.hasPermission(
                BYPASS_PERMISSION
        )) {
            return;
        }

        /*
         * CKI disabled world check.
         *
         * This is the only world restriction CKI itself applies.
         *
         * PvPManager and WorldGuard do NOT control the CombatTag.
         */
        if (plugin.isWorldDisabled(
                attacker.getWorld()
        ) || plugin.isWorldDisabled(
                victim.getWorld()
        )) {
            return;
        }

        /*
         * ======================================================
         * CREATE / REFRESH COMBAT TAG
         * ======================================================
         *
         * Do NOT check:
         *
         * - PvPManager
         * - WorldGuard PvP state
         * - /cki pvp
         * - DeathContext
         *
         * A valid Player -> Player damage event is sufficient.
         */
        CombatResult result =
                combatService.startCombat(
                        attacker.getUniqueId(),
                        victim.getUniqueId()
                );

        /*
         * Verify the result immediately.
         */
        if (plugin.getConfig().getBoolean(
                "debug.combat",
                false
        )) {

            boolean attackerTagged =
                    combatManager.isInCombat(
                            attacker.getUniqueId()
                    );

            boolean victimTagged =
                    combatManager.isInCombat(
                            victim.getUniqueId()
                    );

            plugin.getLogger().info(
                    "[CombatTag] "
                            + attacker.getName()
                            + " -> "
                            + victim.getName()
                            + " | result="
                            + result
                            + " | attacker="
                            + attackerTagged
                            + " | victim="
                            + victimTagged
                            + " | attackerRemaining="
                            + combatManager.getRemainingSeconds(
                                    attacker.getUniqueId()
                            )
                            + "s"
                            + " | victimRemaining="
                            + combatManager.getRemainingSeconds(
                                    victim.getUniqueId()
                            )
                            + "s"
            );
        }
    }

    /*
     * ==========================================================
     * DEATH
     * ==========================================================
     */

    /**
     * Handles player death.
     *
     * <p>
     * Decision order:
     *
     * <ol>
     *     <li>Check CombatTag FIRST.</li>
     *     <li>If active -> DROP regardless of death cause.</li>
     *     <li>If inactive -> resolve DeathContext.</li>
     *     <li>Apply PvP/PvE policy.</li>
     * </ol>
     */
    public void onPlayerDeath(
            PlayerDeathEvent event
    ) {

        Player victim =
                event.getEntity();

        UUID uuid =
                victim.getUniqueId();

        /*
         * ======================================================
         * WORLD CHECK
         * ======================================================
         */

        if (plugin.isWorldDisabled(
                victim.getWorld()
        )) {
            return;
        }

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

            combatManager.remove(
                    uuid
            );

            debugDeath(
                    victim,
                    null,
                    "BYPASS"
            );

            return;
        }

        /*
         * ======================================================
         * STEP 1 — COMBAT TAG FIRST
         * ======================================================
         *
         * This MUST happen before resolveDeathContext().
         *
         * If CombatTag is active, the cause of death does not
         * matter.
         */
        boolean inCombat =
                combatManager.isInCombat(
                        uuid
                );

        /*
         * ======================================================
         * ACTIVE COMBAT TAG
         * ======================================================
         *
         * Any death while tagged causes inventory DROP.
         *
         * Examples:
         *
         * Player
         * Projectile
         * Zombie
         * Skeleton
         * Creeper
         * Lava
         * Fall
         * Void
         * Fire
         * Explosion
         * etc.
         */
        if (inCombat) {

            /*
             * We deliberately DO NOT resolve DeathContext here.
             *
             * CombatTag has priority over death cause.
             */
            DeathResult result =
                    combatService.evaluateDeath(
                            uuid,
                            null
                    );

            /*
             * Defensive guarantee.
             *
             * Even if another implementation accidentally
             * returns KEEP, active CombatTag must still DROP.
             */
            if (result == null
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
                    null,
                    "ACTIVE_COMBAT_TAG"
            );

            /*
             * The player is dead.
             *
             * Remove the tag after evaluating the death.
             */
            combatManager.remove(
                    uuid
            );

            return;
        }

        /*
         * ======================================================
         * STEP 2 — NO ACTIVE COMBAT TAG
         * ======================================================
         *
         * Only now is DeathContext relevant.
         */
        DeathContext context =
                resolveDeathContext(
                        victim
                );

        /*
         * ======================================================
         * STEP 3 — APPLY FINAL DEATH POLICY
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

        /*
         * Ensure stale state is removed.
         */
        combatManager.remove(
                uuid
        );
    }

    /*
     * ==========================================================
     * DEATH CONTEXT
     * ==========================================================
     */

    /**
     * Resolves the final PvP/PvE death context.
     *
     * <p>
     * This method is called ONLY after CombatTag has already
     * been checked and found inactive.
     * </p>
     */
    private DeathContext resolveDeathContext(
            Player player
    ) {

        /*
         * Direct Bukkit killer.
         */
        if (player.getKiller() != null) {

            return DeathContext.PLAYER;
        }

        /*
         * Inspect final damage source.
         */
        if (player.getLastDamageCause()
                instanceof EntityDamageByEntityEvent damage) {

            Entity damager =
                    damage.getDamager();

            /*
             * Direct Player damage.
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
             * Any other entity.
             */
            return DeathContext.MOB;
        }

        /*
         * Fall, lava, void, fire, suffocation,
         * explosion, etc.
         */
        return DeathContext.ENVIRONMENT;
    }

    /*
     * ==========================================================
     * DEATH RESULT
     * ==========================================================
     */

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

    /*
     * ==========================================================
     * INVENTORY DROP
     * ==========================================================
     */

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
         * Clear Bukkit's automatically generated drops.
         */
        event.getDrops().clear();

        /*
         * Storage inventory.
         */
        addDrops(
                event,
                player.getInventory()
                        .getStorageContents()
        );

        /*
         * Armor.
         */
        addDrops(
                event,
                player.getInventory()
                        .getArmorContents()
        );

        /*
         * Offhand / extra contents.
         */
        addDrops(
                event,
                player.getInventory()
                        .getExtraContents()
        );

        /*
         * Explicitly disable keep inventory.
         */
        event.setKeepInventory(
                false
        );

        /*
         * Experience policy.
         */
        if (keepExperience) {

            event.setKeepLevel(
                    true
            );

        } else {

            event.setKeepLevel(
                    false
            );

            event.setDroppedExp(
                    0
            );
        }
    }

    /*
     * ==========================================================
     * INVENTORY KEEP
     * ==========================================================
     */

    private void handleKeepInventory(
            PlayerDeathEvent event
    ) {

        handleKeepInventory(
                event,
                plugin.getConfig().getBoolean(
                        "inventory.keep-experience",
                        true
                )
        );
    }

    private void handleKeepInventory(
            PlayerDeathEvent event,
            boolean keepExperience
    ) {

        event.setKeepInventory(
                true
        );

        event.getDrops().clear();

        if (keepExperience) {

            event.setKeepLevel(
                    true
            );

            event.setDroppedExp(
                    0
            );

        } else {

            event.setKeepLevel(
                    false
            );

            event.setDroppedExp(
                    0
            );
        }
    }

    /*
     * ==========================================================
     * DROP UTILITIES
     * ==========================================================
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

    /*
     * ==========================================================
     * ATTACKER RESOLUTION
     * ==========================================================
     */

    /**
     * Resolves a Player from an entity or a player-owned
     * projectile.
     */
    private Player resolveAttackingPlayer(
            Entity damager
    ) {

        /*
         * Direct Player.
         */
        if (damager instanceof Player player) {

            return player;
        }

        /*
         * Projectile.
         */
        if (damager instanceof Projectile projectile) {

            ProjectileSource source =
                    projectile.getShooter();

            if (source instanceof Player player) {

                return player;
            }
        }

        return null;
    }

    /*
     * ==========================================================
     * DEBUG
     * ==========================================================
     */

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
                        + (
                        context == null
                                ? "NOT_EVALUATED"
                                : context
                )
        );
    }
}