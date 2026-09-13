package com.votri.combatkeepinv.bukkit.listener;

import com.votri.combatkeepinv.bukkit.CombatKeepInventory;
import com.votri.combatkeepinv.bukkit.bridge.BukkitDamageSource;
import com.votri.combatkeepinv.bukkit.combat.CombatManager;
import com.votri.combatkeepinv.bukkit.hook.WorldGuardHook;
import com.votri.combatkeepinv.core.api.CombatResult;
import com.votri.combatkeepinv.core.api.CombatService;
import com.votri.combatkeepinv.core.damage.DamageSource;
import com.votri.combatkeepinv.core.death.DeathDecision;

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
        DamageSource source;

        if (event.getDamager()
                instanceof Projectile projectile) {

            source =
                    BukkitDamageSource.playerProjectile(
                            attacker.getUniqueId(),
                            projectile.getUniqueId(),
                            victim.getUniqueId()
                    );

        } else {

            source =
                    BukkitDamageSource.playerAttack(
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

        if (victim.hasPermission(
                BYPASS_PERMISSION
        )) {
            handleKeepInventory(event);

            combatManager.remove(uuid);

            debugDeath(
                    victim,
                    null,
                    "BYPASS"
            );

            return;
        }

        /*
         * ==========================================================
         * 1. ACTIVE COMBAT HAS ABSOLUTE PRIORITY
         * ==========================================================
         */
        if (combatService.isInCombat(uuid)) {

            /*
             * Build the final Bukkit damage source if available.
             */
            DamageSource source =
                    createDeathDamageSource(victim);

            /*
             * Record final damage in core.
             */
            plugin.getCombatService()
                    .getDamageAttributionService()
                    .recordDamage(source);

            /*
             * Core context is created, but the active CombatTag
             * rule remains authoritative here.
             */
            var context =
                    plugin.getCombatService()
                            .getDeathService()
                            .createContext(
                                    uuid,
                                    source
                            );

            var decision =
                    plugin.getCombatService()
                            .getDeathService()
                            .evaluate(context);

            /*
             * Active CombatTag MUST drop inventory.
             */
            applyCoreDecision(
                    event,
                    decision,
                    true
            );

            debugDeath(
                    victim,
                    null,
                    "ACTIVE_COMBAT_TAG"
            );

            combatManager.remove(uuid);

            return;
        }

        /*
         * ==========================================================
         * 2. NO ACTIVE COMBAT
         * ==========================================================
         */

        DamageSource source =
                createDeathDamageSource(victim);

        var coreContext =
                plugin.getCombatService()
                        .getDeathService()
                        .createContext(
                                uuid,
                                source
                        );

        var decision =
                plugin.getCombatService()
                        .getDeathService()
                        .evaluate(
                                coreContext
                        );

        applyCoreDecision(
                event,
                decision,
                false
        );

        debugDeath(
                victim,
                null,
                "CORE_DEATH_POLICY"
        );

        combatManager.remove(uuid);
    }

    /*
     * ==========================================================
     * DEATH DAMAGE SOURCE & DECISION HELPERS
     * ==========================================================
     */

    private DamageSource createDeathDamageSource(
            Player victim
    ) {
        var damage =
                victim.getLastDamageCause();

        if (damage instanceof EntityDamageByEntityEvent byEntity) {

            Entity damager =
                    byEntity.getDamager();

            if (damager instanceof Player attacker) {

                return BukkitDamageSource.playerAttack(
                                attacker.getUniqueId(),
                                victim.getUniqueId()
                        );
            }

            if (damager instanceof Projectile projectile) {

                if (projectile.getShooter()
                        instanceof Player attacker) {

                    return BukkitDamageSource.playerProjectile(
                                    attacker.getUniqueId(),
                                    projectile.getUniqueId(),
                                    victim.getUniqueId()
                            );
                }
            }
        }

        return BukkitDamageSource.environment(
                victim.getUniqueId(),
                damage
        );
    }

    private void applyCoreDecision(
            PlayerDeathEvent event,
            DeathDecision decision,
            boolean forceDrop
    ) {
        if (forceDrop) {
            handleDropInventory(
                    event,
                    decision != null
                            && decision.shouldKeepExperience()
            );
            return;
        }

        if (decision == null) {
            handleKeepInventory(event);
            return;
        }

        if (decision.shouldKeepInventory()) {

            handleKeepInventory(
                    event,
                    decision.shouldKeepExperience()
            );

        } else {

            handleDropInventory(
                    event,
                    decision.shouldKeepExperience()
            );
        }
    }

    /*
     * ==========================================================
     * INVENTORY DROP & KEEP
     * ==========================================================
     */

    private void handleDropInventory(
            PlayerDeathEvent event,
            boolean keepExperience
    ) {
        Player player = event.getEntity();

        event.getDrops().clear();

        addDrops(event, player.getInventory().getStorageContents());
        addDrops(event, player.getInventory().getArmorContents());
        addDrops(event, player.getInventory().getExtraContents());

        event.setKeepInventory(false);

        if (keepExperience) {
            event.setKeepLevel(true);
        } else {
            event.setKeepLevel(false);
            event.setDroppedExp(0);
        }
    }

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
            if (item == null || item.getType().isAir()) {
                continue;
            }

            event.getDrops().add(item.clone());
        }
    }

    private Player resolveAttackingPlayer(
            Entity damager
    ) {
        if (damager instanceof Player player) {
            return player;
        }

        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
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
            Object context,
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
