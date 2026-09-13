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
import org.bukkit.event.EventHandler;
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
 * CombatService owns combat/death policy.</p>
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

    @EventHandler
    public void onEntityDamageByEntity(
            EntityDamageByEntityEvent event
    ) {
        if (event.isCancelled()) {
            return;
        }

        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        debugDamageReceived(
                event,
                victim
        );

        Player attacker =
                resolveAttackingPlayer(
                        event.getDamager()
                );

        if (attacker == null) {
            debugDamageIgnored(
                    "damager is not player-owned."
            );
            return;
        }

        if (attacker.getUniqueId().equals(
                victim.getUniqueId()
        )) {
            debugDamageIgnored(
                    "attacker and victim are the same player."
            );
            return;
        }

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
         * WorldGuard remains authoritative for the Bukkit
         * entry filter.
         */
        if (worldGuard != null
                && !worldGuard.canPvP(
                attacker,
                victim
        )) {
            debugDamageIgnored(
                    "WorldGuard does not allow PvP."
            );
            return;
        }

        /*
         * Build the new core DamageSource.
         */
        com.votri.combatkeepinv.core.damage.DamageSource source;

        if (event.getDamager()
                instanceof Projectile projectile) {

            source =
                    com.votri.combatkeepinv.bukkit.bridge
                            .BukkitDamageSource.playerProjectile(
                                    attacker.getUniqueId(),
                                    projectile.getUniqueId(),
                                    victim.getUniqueId()
                            );

        } else {

            source =
                    com.votri.combatkeepinv.bukkit.bridge
                            .BukkitDamageSource.playerAttack(
                                    attacker.getUniqueId(),
                                    victim.getUniqueId()
                            );
        }

        /*
         * Record damage before changing combat state.
         */
        plugin.getCombatService()
                .getDamageAttributionService()
                .recordDamage(source);

        CombatResult result =
                combatService.startCombat(
                        attacker.getUniqueId(),
                        victim.getUniqueId()
                );

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

    @EventHandler
    public void onPlayerDeath(
            PlayerDeathEvent event
    ) {

        Player victim =
                event.getEntity();

        UUID uuid =
                victim.getUniqueId();

        if (plugin.isWorldDisabled(
                victim.getWorld()
        )) {

            return;
        }

        /*
         * Bypass player always keeps inventory.
         *
         * This is not a combat death, so use
         * death.keep-experience.
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
         */

        boolean inCombat =
                combatService.isInCombat(
                        uuid
                );

        if (inCombat) {

            /*
             * Do not resolve DeathContext.
             *
             * Active CombatTag has absolute priority.
             */
            DeathResult result =
                    combatService.evaluateDeath(
                            uuid,
                            null
                    );

            /*
             * Defensive guarantee:
             * active CombatTag must always drop inventory.
             */
            if (result == null
                    || !result.shouldDropInventory()) {

                handleDropInventory(
                        event,
                        plugin.shouldKeepCombatDeathExperience()
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

            combatManager.remove(
                    uuid
            );

            return;
        }

        /*
         * ======================================================
         * STEP 2 — NO ACTIVE COMBAT TAG
         * ======================================================
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

        combatManager.remove(
                uuid
        );
    }

    /*
     * ==========================================================
     * DEATH CONTEXT
     * ==========================================================
     */

    private DeathContext resolveDeathContext(
            Player player
    ) {

        if (player.getKiller() != null) {
            return DeathContext.PLAYER;
        }

        if (player.getLastDamageCause()
                instanceof EntityDamageByEntityEvent damage) {

            Entity damager =
                    damage.getDamager();

            if (damager instanceof Player) {
                return DeathContext.PLAYER;
            }

            if (damager instanceof Projectile projectile) {

                ProjectileSource source =
                        projectile.getShooter();

                if (source instanceof Player) {
                    return DeathContext.PROJECTILE;
                }

                return DeathContext.MOB;
            }

            return DeathContext.MOB;
        }

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

    private void handleDropInventory(
            PlayerDeathEvent event,
            boolean keepExperience
    ) {

        Player player =
                event.getEntity();

        /*
         * Keep Bukkit/Minecraft's normal death-drop pipeline.
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

        event.setKeepInventory(
                false
        );

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
                plugin.shouldKeepDeathExperience()
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

    private void debugDamageReceived(
            EntityDamageByEntityEvent event,
            Player victim
    ) {

        if (!plugin.isDebugDamage()) {
            return;
        }

        plugin.getLogger().info(
                "[CombatDamage] received: "
                        + "damager="
                        + event.getDamager().getType()
                        + " -> victim=PLAYER("
                        + victim.getName()
                        + ")"
        );
    }

    private void debugDamageIgnored(
            String reason
    ) {

        if (!plugin.isDebugDamage()) {
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

        if (!plugin.isDebugCombat()) {
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

        if (!plugin.isDebugDeath()) {
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
