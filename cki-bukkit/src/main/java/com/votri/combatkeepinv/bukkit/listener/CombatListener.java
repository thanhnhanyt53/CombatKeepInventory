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
 * <p>This class is intentionally thin:
 * Bukkit events are translated into core API operations,
 * while CombatService decides combat/death policy.</p>
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
     * Handles Bukkit damage events and creates combat tags
     * for valid player-versus-player damage.
     */
    public void onEntityDamageByEntity(
            EntityDamageByEntityEvent event
    ) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        if (plugin.isWorldDisabled(victim.getWorld())) {
            return;
        }

        if (victim.hasPermission(BYPASS_PERMISSION)) {
            return;
        }

        Entity damager = event.getDamager();

        Player attacker = getAttackingPlayer(damager);

        /*
         * =========================================================
         * PLAYER VS PLAYER
         * =========================================================
         */
        if (attacker != null) {

            if (attacker.equals(victim)) {
                return;
            }

            if (attacker.hasPermission(BYPASS_PERMISSION)) {
                return;
            }

            if (plugin.isWorldDisabled(attacker.getWorld())) {
                return;
            }

            /*
             * Global PvP switch.
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
             * WorldGuard protection.
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
         * =========================================================
         * NON-PLAYER DAMAGE
         * =========================================================
         *
         * This part exists only for the optional combat-tagging
         * feature. It does NOT make the eventual death a PvP
         * death.
         */
        boolean playerVsPlayerOnly =
                plugin.getConfig().getBoolean(
                        "combat.player-vs-player-only",
                        true
                );

        if (playerVsPlayerOnly) {
            return;
        }

        /*
         * There is no CombatService operation for creating a
         * non-player combat tag in the current core contract.
         *
         * Therefore this optional legacy behavior is deliberately
         * not mapped into the new PvP death policy.
         */
        debugNonPlayerCombat(
                victim,
                damager
        );
    }

    /**
     * Handles player deaths.
     *
     * <p>The Bukkit death cause is converted to DeathContext,
     * then CombatService decides KEEP/DROP.</p>
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
         * =========================================================
         * BYPASS
         * =========================================================
         */
        if (victim.hasPermission(
                BYPASS_PERMISSION
        )) {

            handleKeepInventory(event);

            combatService.endCombat(uuid);

            return;
        }

        /*
         * Determine the actual death context.
         *
         * Do NOT use combatService.isInCombat() here.
         *
         * Being combat-tagged does not mean that every subsequent
         * death is a PvP death.
         */
        DeathContext context =
                resolveDeathContext(victim);

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
         * A death always ends the local combat state.
         */
        combatService.endCombat(uuid);

        debugDeath(
                victim,
                context,
                result
        );
    }

    /**
     * Converts the Bukkit death cause into the core DeathContext.
     */
    private DeathContext resolveDeathContext(
            Player player
    ) {
        /*
         * Bukkit's killer is the strongest indication of a
         * direct player kill.
         */
        if (player.getKiller() != null) {
            return DeathContext.PLAYER;
        }

        if (player.getLastDamageCause() == null) {
            return DeathContext.UNKNOWN;
        }

        EntityDamageByEntityEvent damage =
                player.getLastDamageCause()
                        instanceof EntityDamageByEntityEvent
                        ? (EntityDamageByEntityEvent)
                        player.getLastDamageCause()
                        : null;

        if (damage == null) {
            return resolveNonEntityDeathContext();
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
         * Projectile.
         *
         * A player-owned projectile is a PvP death.
         * A mob-owned projectile is PvE.
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
         * Any other entity damage is treated as PvE.
         */
        return DeathContext.MOB;
    }

    /**
     * Resolves non-entity environmental death causes.
     */
    private DeathContext resolveNonEntityDeathContext() {

        /*
         * We intentionally use Bukkit's last damage event type
         * through the PlayerDeathEvent-compatible damage cause
         * when available.
         *
         * If there is no entity attacker, this is environmental
         * unless Bukkit gives us a more specific void indication.
         */
        return DeathContext.ENVIRONMENT;
    }

    /**
     * Applies the platform-neutral death decision to Bukkit.
     */
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

        if (result.shouldKeepInventory()) {

            handleKeepInventory(
                    event,
                    result.shouldKeepExperience()
            );

            return;
        }

        /*
         * DEFAULT is resolved conservatively to KEEP.
         *
         * This prevents accidental item loss if a future
         * implementation returns DEFAULT.
         */
        handleKeepInventory(
                event,
                result.shouldKeepExperience()
        );
    }

    /**
     * Forces inventory drops even when the server gamerule
     * keepInventory is enabled.
     */
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
     * Keeps inventory and optionally experience.
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
    }

    private void debugNonPlayerCombat(
            Player victim,
            Entity damager
    ) {
        if (!plugin.getConfig().getBoolean(
                "debug.combat",
                false
        )) {
            return;
        }

        plugin.getLogger().info(
                "Non-player damage observed: "
                        + victim.getName()
                        + " <- "
                        + damager.getType()
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
        );
    }
}