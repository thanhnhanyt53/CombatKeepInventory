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
 * Converts Bukkit combat/death events into CKI combat state changes.
 *
 * <p>CombatListener is responsible for translating Bukkit events
 * into the public CombatService API.</p>
 *
 * <p>CombatManager owns the actual runtime CombatTag state.</p>
 *
 * <p>CombatService owns the combat/death policy.</p>
 *
 * <p>Important rules:</p>
 *
 * <ul>
 *     <li>Only player-originated damage creates/refreshes CombatTag.</li>
 *     <li>Player-owned projectiles count as player damage.</li>
 *     <li>Mob/environment damage never creates CombatTag.</li>
 *     <li>CombatTag is checked before DeathContext.</li>
 *     <li>Active CombatTag always has priority over death cause.</li>
 * </ul>
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

    /*
     * ==========================================================
     * COMBAT DAMAGE
     * ==========================================================
     */

    /**
     * Handles entity damage.
     *
     * <p>
     * CombatTag is created only when:
     *
     * <pre>
     * Player -> Player
     * </pre>
     *
     * or:
     *
     * <pre>
     * Player -> Projectile -> Player
     * </pre>
     *
     * is detected.
     * </p>
     */
    public void onEntityDamageByEntity(
            EntityDamageByEntityEvent event
    ) {

        /*
         * Cancelled damage is not a valid hit.
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
         * Debug the raw event before filtering.
         */
        debugDamageReceived(
                event,
                victim
        );

        /*
         * Resolve the actual player responsible
         * for the damage.
         */
        Player attacker =
                resolveAttackingPlayer(
                        event.getDamager()
                );

        /*
         * Mob / environmental damage.
         *
         * Examples:
         *
         * Iron Golem
         * Zombie
         * Skeleton
         * Creeper
         * TNT
         * End Crystal
         * etc.
         *
         * These must NEVER create CombatTag.
         */
        if (attacker == null) {

            debugDamageIgnored(
                    "damager is not player-owned."
            );

            return;
        }

        /*
         * Prevent self-damage.
         */
        if (attacker.getUniqueId().equals(
                victim.getUniqueId()
        )) {

            debugDamageIgnored(
                    "attacker and victim are the same player."
            );

            return;
        }

        /*
         * Bypass permission.
         *
         * If either participant has bypass permission,
         * this hit does not create or refresh CombatTag.
         */
        if (attacker.hasPermission(
                BYPASS_PERMISSION
        ) || victim.hasPermission(
                BYPASS_PERMISSION
        )) {

            debugDamageIgnored(
                    "bypass permission."
            );

            return;
        }

        /*
         * CKI disabled-world check.
         */
        if (plugin.isWorldDisabled(
                attacker.getWorld()
        ) || plugin.isWorldDisabled(
                victim.getWorld()
        )) {

            debugDamageIgnored(
                    "CKI is disabled in this world."
            );

            return;
        }

        /*
         * ======================================================
         * COMBAT SERVICE
         * ======================================================
         *
         * CombatListener deliberately does NOT call:
         *
         *     combatManager.tag(...)
         *
         *     combatManager.start(...)
         *
         *     combatManager.refresh(...)
         *
         * The service owns that decision.
         *
         * startCombat() means:
         *
         *     no active tag -> create tag
         *     active tag    -> refresh tag
         */
        CombatResult result =
                combatService.startCombat(
                        attacker.getUniqueId(),
                        victim.getUniqueId()
                );

        /*
         * Debug the final result.
         */
        debugCombatResult(
                attacker,
                victim,
                result
        );
    }

    /*
     * ==========================================================
     * PLAYER DEATH
     * ==========================================================
     */

    /**
     * Handles player death.
     *
     * <p>
     * The decision order is strictly:
     *
     * <pre>
     * 1. Check active CombatTag.
     * 2. If active -> DROP.
     * 3. If inactive -> resolve DeathContext.
     * 4. Apply PvP/PvE policy.
     * </pre>
     *
     * <p>
     * DeathContext must never override an active CombatTag.
     * </p>
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
         * STEP 1 — CHECK COMBAT TAG FIRST
         * ======================================================
         *
         * This check MUST happen before looking at:
         *
         *     getKiller()
         *
         *     getLastDamageCause()
         *
         *     DeathContext
         *
         * If the player is tagged, the cause of death is irrelevant.
         */
        boolean inCombat =
                combatService.isInCombat(
                        uuid
                );

        /*
         * ======================================================
         * ACTIVE COMBAT TAG
         * ======================================================
         */

        if (inCombat) {

            /*
             * Do NOT resolve DeathContext.
             *
             * CombatTag has absolute priority.
             */
            DeathResult result =
                    combatService.evaluateDeath(
                            uuid,
                            null
                    );

            /*
             * Defensive guarantee.
             *
             * Active CombatTag must always drop inventory.
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
             * Player is dead.
             * Remove the active CombatTag.
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
         * Only now is the death cause relevant.
         */
        DeathContext context =
                resolveDeathContext(
                        victim
                );

        /*
         * ======================================================
         * STEP 3 — DEATH POLICY
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
         * Defensive cleanup.
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
     * Resolves the final death context.
     *
     * <p>
     * This method is ONLY called after CombatTag has already
     * been checked and found inactive.
     * </p>
     */
    private DeathContext resolveDeathContext(
            Player player
    ) {

        /*
         * Bukkit's direct killer information.
         */
        if (player.getKiller() != null) {

            return DeathContext.PLAYER;
        }

        /*
         * Inspect final damage cause.
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
             * Projectile.
             */
            if (damager instanceof Projectile projectile) {

                ProjectileSource source =
                        projectile.getShooter();

                /*
                 * Player-owned projectile.
                 */
                if (source instanceof Player) {

                    return DeathContext.PROJECTILE;
                }

                /*
                 * Non-player projectile.
                 */
                return DeathContext.MOB;
            }

            /*
             * Other entity.
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
     * Converts a DeathResult into Bukkit death behavior.
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
     * Forces the inventory to drop.
     *
     * <p>
     * Bukkit/Minecraft will subsequently handle the actual
     * item-entity spawning according to the normal death-drop
     * pipeline.
     * </p>
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
         * Disable keep-inventory.
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
     * Resolves the actual Player responsible for damage.
     *
     * <p>
     * Supported:
     *
     * <pre>
     * Player
     * Player-owned Projectile
     * </pre>
     *
     * <p>
     * Unsupported entities return null.
     * </p>
     */
    private Player resolveAttackingPlayer(
            Entity damager
    ) {

        /*
         * Direct player.
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

    private void debugDamageReceived(
            EntityDamageByEntityEvent event,
            Player victim
    ) {

        if (!plugin.getConfig().getBoolean(
                "debug.combat",
                false
        )) {
            return;
        }

        plugin.getLogger().info(
                "[CombatDamage] received: "
                        + "damager="
                        + event.getDamager()
                                .getType()
                        + " -> victim=PLAYER("
                        + victim.getName()
                        + ")"
        );
    }

    private void debugDamageIgnored(
            String reason
    ) {

        if (!plugin.getConfig().getBoolean(
                "debug.combat",
                false
        )) {
            return;
        }

        plugin.getLogger().info(
                "[CombatDamage] ignored: "
                        + reason
        );
    }

    private void debugCombatResult(
            Player attacker,
            Player victim,
            CombatResult result
    ) {

        if (!plugin.getConfig().getBoolean(
                "debug.combat",
                false
        )) {
            return;
        }

        boolean attackerTagged =
                combatService.isInCombat(
                        attacker.getUniqueId()
                );

        boolean victimTagged =
                combatService.isInCombat(
                        victim.getUniqueId()
                );

        long attackerRemaining =
                combatService.getRemainingCombatMillis(
                        attacker.getUniqueId()
                );

        long victimRemaining =
                combatService.getRemainingCombatMillis(
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
                        + formatSeconds(
                                attackerRemaining
                        )
                        + "s"
                        + " | victimRemaining="
                        + formatSeconds(
                                victimRemaining
                        )
                        + "s"
        );
    }

    private long formatSeconds(
            long millis
    ) {

        if (millis <= 0L) {
            return 0L;
        }

        return (
                millis + 999L
        ) / 1000L;
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
                        + (
                        context == null
                                ? "NOT_EVALUATED"
                                : context
                )
        );
    }
}