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
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.UUID;

/**
 * Bukkit event adapter for CombatKeepInventory.
 *
 * <p>This class has two responsibilities:</p>
 *
 * <ul>
 *     <li>Convert valid player-originated damage into CombatTag state.</li>
 *     <li>Evaluate the player's CombatTag before deciding inventory policy
 *     on death.</li>
 * </ul>
 *
 * <p>Important:</p>
 *
 * <ul>
 *     <li>PvPManager is not used.</li>
 *     <li>WorldGuard is only used for world restrictions.</li>
 *     <li>Bypass permission does NOT prevent CombatTag creation.</li>
 *     <li>Vanilla/Bukkit handles actual item dropping.</li>
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
     * DAMAGE
     * ==========================================================
     */

    /**
     * Handles entity damage.
     *
     * <p>Only damage caused by a player, either directly or
     * through a player-owned projectile, creates CombatTag.</p>
     *
     * <p>Every valid player-originated hit refreshes the
     * CombatTag of both the attacker and victim.</p>
     */
    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onEntityDamageByEntity(
            EntityDamageByEntityEvent event
    ) {

        /*
         * ======================================================
         * VICTIM
         * ======================================================
         */

        if (!(event.getEntity() instanceof Player victim)) {

            return;
        }

        /*
         * ======================================================
         * DEBUG: DAMAGE RECEIVED
         * ======================================================
         */

        debugDamageReceived(
                event,
                victim
        );

        /*
         * ======================================================
         * RESOLVE PLAYER ATTACKER
         * ======================================================
         *
         * Supports:
         *
         * Player -> Player
         *
         * Player -> Projectile -> Player
         */

        Player attacker =
                resolveAttackingPlayer(
                        event.getDamager()
                );

        /*
         * Mob / environment / non-player-owned projectile.
         */

        if (attacker == null) {

            debugDamageIgnored(
                    "damager is not player-owned."
            );

            return;
        }

        /*
         * ======================================================
         * SELF DAMAGE
         * ======================================================
         */

        if (attacker.getUniqueId()
                .equals(victim.getUniqueId())) {

            debugDamageIgnored(
                    "attacker and victim are the same player."
            );

            return;
        }

        /*
         * ======================================================
         * WORLD CHECK
         * ======================================================
         */

        if (plugin.isWorldDisabled(
                attacker.getWorld()
        ) || plugin.isWorldDisabled(
                victim.getWorld()
        )) {

            debugDamageIgnored(
                    "damage occurred in a disabled world."
            );

            return;
        }

        /*
         * ======================================================
         * IMPORTANT:
         * BYPASS DOES NOT BELONG HERE.
         * ======================================================
         *
         * combatkeepinventory.bypass only affects the death
         * inventory policy.
         *
         * It must NOT prevent CombatTag from being created.
         *
         * Therefore there is intentionally NO:
         *
         * attacker.hasPermission(...)
         *
         * or:
         *
         * victim.hasPermission(...)
         *
         * check in this section.
         */

        /*
         * ======================================================
         * CREATE / REFRESH COMBAT TAG
         * ======================================================
         *
         * Both players receive the same combat duration.
         *
         * Any later valid hit refreshes the timer again.
         */

        combatManager.tag(
                attacker.getUniqueId(),
                victim.getUniqueId()
        );

        /*
         * ======================================================
         * VERIFY TAG
         * ======================================================
         */

        debugCombatTag(
                attacker,
                victim
        );
    }

    /*
     * ==========================================================
     * DEATH
     * ==========================================================
     */

    /**
     * Handles player death.
     *
     * <p>The decision order is deliberately:</p>
     *
     * <ol>
     *     <li>Check bypass.</li>
     *     <li>Check active CombatTag.</li>
     *     <li>If no CombatTag, resolve final death context.</li>
     *     <li>Apply the resulting inventory policy.</li>
     *     <li>Remove CombatTag.</li>
     * </ol>
     *
     * <p>Most importantly, CombatTag is checked BEFORE using
     * the final death cause to decide the inventory policy.</p>
     */
    @EventHandler(
            priority = EventPriority.HIGHEST
    )
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

            debugDeath(
                    victim,
                    null,
                    "WORLD_DISABLED"
            );

            return;
        }

        /*
         * ======================================================
         * BYPASS
         * ======================================================
         *
         * Bypass affects the death policy only.
         *
         * CombatTag itself may have existed normally.
         */

        if (victim.hasPermission(
                BYPASS_PERMISSION
        )) {

            handleKeepInventory(
                    event,
                    getKeepExperience()
            );

            combatManager.remove(uuid);

            debugDeath(
                    victim,
                    resolveDeathContext(victim),
                    "BYPASS"
            );

            return;
        }

        /*
         * ======================================================
         * STEP 1:
         * CHECK ACTIVE COMBAT TAG FIRST
         * ======================================================
         */

        boolean inCombat =
                combatManager.isInCombat(
                        uuid
                );

        if (inCombat) {

            /*
             * CombatTag has priority over the final damage source.
             *
             * Example:
             *
             * Player -> Player
             * Player receives CombatTag
             * Player is later killed by Iron Golem
             *
             * Result:
             *
             * DROP
             *
             * because the player was still in active combat.
             */

            DeathResult result =
                    combatService.evaluateDeath(
                            uuid,
                            DeathContext.MOB
                    );

            /*
             * Defensive behavior:
             *
             * Active CombatTag MUST result in inventory DROP.
             *
             * If the service returns KEEP unexpectedly,
             * force DROP here.
             */

            if (result == null
                    || !result.shouldDropInventory()) {

                handleDropInventory(
                        event,
                        getKeepExperience()
                );

            } else {

                applyDeathResult(
                        event,
                        result
                );
            }

            debugDeath(
                    victim,
                    resolveDeathContext(victim),
                    "ACTIVE_COMBAT_TAG"
            );

            /*
             * CombatTag is no longer needed after death.
             */

            combatManager.remove(uuid);

            return;
        }

        /*
         * ======================================================
         * STEP 2:
         * NO ACTIVE COMBAT TAG
         * ======================================================
         */

        DeathContext context =
                resolveDeathContext(
                        victim
                );

        /*
         * ======================================================
         * STEP 3:
         * FINAL DEATH POLICY
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
         * ======================================================
         * STEP 4:
         * CLEAR COMBAT TAG
         * ======================================================
         */

        combatManager.remove(uuid);
    }

    /*
     * ==========================================================
     * DEATH CONTEXT
     * ==========================================================
     */

    /**
     * Resolves the final damage source.
     *
     * <p>This method is only used when there is NO active
     * CombatTag. An active CombatTag already has priority.</p>
     */
    private DeathContext resolveDeathContext(
            Player player
    ) {

        /*
         * Bukkit's killer API is the most direct indication
         * of a player kill.
         */

        if (player.getKiller() != null) {

            return DeathContext.PLAYER;
        }

        /*
         * Inspect the final damage event.
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
             * Other entity.
             */

            return DeathContext.MOB;
        }

        /*
         * Fall, void, lava, fire, suffocation,
         * explosion, etc.
         */

        return DeathContext.ENVIRONMENT;
    }

    /*
     * ==========================================================
     * DEATH RESULT
     * ==========================================================
     */

    private void applyDeathResult(
            PlayerDeathEvent event,
            DeathResult result
    ) {

        if (result == null) {

            /*
             * Defensive fallback:
             * keep inventory rather than accidentally losing it.
             */

            handleKeepInventory(
                    event,
                    getKeepExperience()
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
     * VANILLA DROP
     * ==========================================================
     */

    /**
     * Allows Bukkit/Minecraft to perform the normal death drop.
     *
     * <p>CKI deliberately does NOT:</p>
     *
     * <ul>
     *     <li>copy inventory contents;</li>
     *     <li>clear the existing drop list;</li>
     *     <li>calculate coordinates;</li>
     *     <li>create Item entities;</li>
     *     <li>apply random offsets;</li>
     *     <li>apply custom velocity.</li>
     * </ul>
     *
     * <p>This keeps the actual item drop behavior under
     * Minecraft/Paper's normal death-drop pipeline.</p>
     */
    private void handleDropInventory(
            PlayerDeathEvent event,
            boolean keepExperience
    ) {

        /*
         * Do NOT call event.getDrops().clear() here.
         *
         * Bukkit/Paper has already prepared the death drops.
         */

        event.setKeepInventory(false);

        if (keepExperience) {

            event.setKeepLevel(true);

        } else {

            event.setKeepLevel(false);
            event.setDroppedExp(0);
        }
    }

    /*
     * ==========================================================
     * KEEP INVENTORY
     * ==========================================================
     */

    private void handleKeepInventory(
            PlayerDeathEvent event
    ) {

        handleKeepInventory(
                event,
                getKeepExperience()
        );
    }

    private void handleKeepInventory(
            PlayerDeathEvent event,
            boolean keepExperience
    ) {

        /*
         * Inventory is kept, therefore the death drop list
         * must not contain those inventory items.
         */

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

    /*
     * ==========================================================
     * CONFIG
     * ==========================================================
     */

    private boolean getKeepExperience() {

        return plugin.getConfig().getBoolean(
                "inventory.keep-experience",
                true
        );
    }

    /*
     * ==========================================================
     * PLAYER ATTACKER RESOLUTION
     * ==========================================================
     */

    /**
     * Resolves the actual Player responsible for damage.
     *
     * <p>Supported:</p>
     *
     * <ul>
     *     <li>Player</li>
     *     <li>Player-owned Projectile</li>
     * </ul>
     *
     * <p>Not supported:</p>
     *
     * <ul>
     *     <li>Iron Golem</li>
     *     <li>Zombie</li>
     *     <li>Skeleton</li>
     *     <li>Unowned projectile</li>
     *     <li>Other mobs</li>
     * </ul>
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

    /*
     * ==========================================================
     * DEBUG
     * ==========================================================
     */

    private boolean isCombatDebugEnabled() {

        return plugin.getConfig().getBoolean(
                "debug.combat",
                false
        );
    }

    private void debugDamageReceived(
            EntityDamageByEntityEvent event,
            Player victim
    ) {

        if (!isCombatDebugEnabled()) {

            return;
        }

        plugin.getLogger().info(
                "[CombatDamage] received: "
                        + "damager="
                        + event.getDamager()
                        .getType()
                        + " -> victim=PLAYER"
        );
    }

    private void debugDamageIgnored(
            String reason
    ) {

        if (!isCombatDebugEnabled()) {

            return;
        }

        plugin.getLogger().info(
                "[CombatDamage] ignored: "
                        + reason
        );
    }

    private void debugCombatTag(
            Player attacker,
            Player victim
    ) {

        if (!isCombatDebugEnabled()) {

            return;
        }

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

    private void debugDeath(
            Player player,
            DeathContext context,
            String state
    ) {

        if (!isCombatDebugEnabled()) {

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
                                ? "UNKNOWN"
                                : context
                )
        );
    }
}