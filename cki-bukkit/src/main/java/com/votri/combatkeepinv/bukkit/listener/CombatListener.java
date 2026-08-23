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

    public void onEntityDamageByEntity(
            EntityDamageByEntityEvent event
    ) {
        /*
         * Never create a combat tag from damage that another
         * plugin has already cancelled.
         */
        if (event.isCancelled()) {
            return;
        }

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
         * ======================================================
         * PLAYER DAMAGE
         * ======================================================
         */

        if (attacker != null) {

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
             * PvP disabled.
             */
            if (!combatService.isPvPEnabled()) {

                if (plugin.getConfig().getBoolean(
                        "pvp.block-pvp-when-disabled",
                        true
                )) {
                    event.setCancelled(true);
                }

                return;
            }

            /*
             * WorldGuard.
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
             * Every valid hit refreshes the CombatTag.
             */
            CombatResult result =
                    combatService.startCombat(
                            attacker.getUniqueId(),
                            victim.getUniqueId()
                    );

            if (result == CombatResult.SUCCESS) {

                debugCombat(
                        attacker,
                        victim,
                        "PLAYER_VS_PLAYER"
                );
            }

            return;
        }

        /*
         * Non-player damage does NOT create CombatTag.
         */
    }

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

            handleKeepInventory(event);

            combatService.endCombat(uuid);

            return;
        }

        /*
         * ======================================================
         * COMBAT TAG HAS PRIORITY
         * ======================================================
         *
         * This check MUST happen before death attribution.
         */
        boolean inCombat =
                combatService.isInCombat(uuid);

        if (inCombat) {

            DeathResult result =
                    combatService.evaluateDeath(
                            uuid,
                            DeathContext.ENVIRONMENT
                    );

            applyDeathResult(
                    event,
                    result
            );

            debugDeath(
                    victim,
                    DeathContext.ENVIRONMENT,
                    result
            );

            /*
             * Only remove the tag AFTER policy has been evaluated.
             */
            combatService.endCombat(uuid);

            return;
        }

        /*
         * ======================================================
         * NO ACTIVE COMBAT TAG
         * ======================================================
         */

        DeathContext context =
                resolveDeathContext(
                        victim
                );

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
                result
        );

        combatService.endCombat(uuid);
    }

    private DeathContext resolveDeathContext(
            Player player
    ) {
        /*
         * Direct Bukkit killer.
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
         * Other entity damage is PvE.
         */
        return DeathContext.MOB;
    }

    private void applyDeathResult(
            PlayerDeathEvent event,
            DeathResult result
    ) {
        if (result == null) {
            handleKeepInventory(event);
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

    private void handleDropInventory(
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

            if (item == null
                    || item.getType().isAir()) {
                continue;
            }

            event.getDrops().add(
                    item.clone()
            );
        }
    }

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
            Player victim,
            String reason
    ) {
        if (!plugin.getConfig().getBoolean(
                "debug.combat",
                false
        )) {
            return;
        }

        plugin.getLogger().info(
                "Combat tagged: "
                        + attacker.getName()
                        + " <-> "
                        + victim.getName()
                        + " ["
                        + reason
                        + "]"
        );

        plugin.getLogger().info(
                "Combat remaining: "
                        + combatService
                        .getRemainingCombatMillis(
                                victim.getUniqueId()
                        )
                        + " ms for "
                        + victim.getName()
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
                        + " context="
                        + context
                        + " policy="
                        + result.getInventoryPolicy()
                        + " combatDeath="
                        + result.wasCombatDeath()
                        + " combatRemainingAfter="
                        + combatService
                        .getRemainingCombatMillis(
                                player.getUniqueId()
                        )
                        + " ms"
        );
    }
}