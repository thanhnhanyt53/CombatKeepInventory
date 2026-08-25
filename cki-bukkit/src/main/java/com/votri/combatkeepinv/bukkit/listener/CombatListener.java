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
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import java.util.UUID;

public final class CombatListener
        implements Listener {

    private static final String BYPASS_PERMISSION =
            "combatkeepinventory.bypass";

    private final CombatKeepInventory plugin;
    private final CombatManager combatManager;
    private final CombatService combatService;

    /*
     * Retained for compatibility with the existing constructor.
     *
     * WorldGuard is NOT used as a gate for CombatTag.
     */
    @SuppressWarnings("unused")
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
     * Handles damage dealt by entities.
     *
     * <p>
     * A CombatTag is created only when:
     *
     * <ul>
     *     <li>the victim is a Player,</li>
     *     <li>the attacker can be resolved to a Player,</li>
     *     <li>the attacker and victim are different players,</li>
     *     <li>the event was not cancelled,</li>
     *     <li>neither player bypasses CKI,</li>
     *     <li>the world is not disabled for CKI.</li>
     * </ul>
     *
     * <p>
     * Every valid hit resets the timers of BOTH players.
     * </p>
     */
    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onEntityDamageByEntity(
            EntityDamageByEntityEvent event
    ) {
        /*
         * Debug FIRST.
         *
         * This confirms that Bukkit actually reaches
         * the damage listener.
         */
        boolean debug =
                plugin.getConfig().getBoolean(
                        "debug.combat",
                        false
                );

        if (debug) {
            plugin.getLogger().info(
                    "[CombatDamage] received: damager="
                            + event.getDamager().getType()
                            + " -> victim="
                            + event.getEntity().getType()
            );
        }

        /*
         * The victim must be a real Player.
         */
        if (!(event.getEntity() instanceof Player victim)) {

            if (debug) {
                plugin.getLogger().info(
                        "[CombatDamage] ignored: victim is not a player."
                );
            }

            return;
        }

        /*
         * Resolve Player attacker.
         */
        Player attacker =
                resolveAttackingPlayer(
                        event.getDamager()
                );

        if (attacker == null) {

            if (debug) {
                plugin.getLogger().info(
                        "[CombatDamage] ignored: damager is not player-owned."
                );
            }

            return;
        }

        /*
         * Never tag self-damage.
         */
        if (attacker.getUniqueId().equals(
                victim.getUniqueId()
        )) {

            if (debug) {
                plugin.getLogger().info(
                        "[CombatDamage] ignored: self damage."
                );
            }

            return;
        }

        /*
         * CKI bypass.
         */
        if (attacker.hasPermission(
                BYPASS_PERMISSION
        ) || victim.hasPermission(
                BYPASS_PERMISSION
        )) {

            if (debug) {
                plugin.getLogger().info(
                        "[CombatDamage] ignored: bypass permission."
                );
            }

            return;
        }

        /*
         * CKI disabled world.
         */
        if (plugin.isWorldDisabled(
                attacker.getWorld()
        ) || plugin.isWorldDisabled(
                victim.getWorld()
        )) {

            if (debug) {
                plugin.getLogger().info(
                        "[CombatDamage] ignored: CKI disabled world."
                );
            }

            return;
        }

        /*
         * ======================================================
         * PLAYER -> PLAYER
         * ======================================================
         *
         * This is the ONLY point where CombatTag is created.
         */
        CombatResult result =
                combatService.startCombat(
                        attacker.getUniqueId(),
                        victim.getUniqueId()
                );

        /*
         * Verify the state immediately.
         */
        if (debug) {

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
                            + " | attackerTagged="
                            + attackerTagged
                            + " | victimTagged="
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

    /**
     * Handles death.
     *
     * <p>
     * IMPORTANT:
     *
     * <pre>
     * CombatTag check
     *        ↓
     * active -> DROP immediately
     *        ↓
     * inactive -> resolve DeathContext
     * </pre>
     */
    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onPlayerDeath(
            PlayerDeathEvent event
    ) {
        Player victim =
                event.getEntity();

        UUID uuid =
                victim.getUniqueId();

        /*
         * CKI disabled world.
         */
        if (plugin.isWorldDisabled(
                victim.getWorld()
        )) {
            return;
        }

        /*
         * Bypass.
         */
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
         * ======================================================
         * STEP 1 — CHECK COMBAT TAG FIRST
         * ======================================================
         */
        boolean inCombat =
                combatManager.isInCombat(uuid);

        if (inCombat) {

            /*
             * Do NOT resolve DeathContext.
             *
             * The cause of death is irrelevant while the tag
             * is active.
             */
            handleDropInventory(
                    event,
                    plugin.getConfig().getBoolean(
                            "death.keep-experience",
                            true
                    )
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
         * ======================================================
         * STEP 2 — TAG EXPIRED / ABSENT
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

        combatManager.remove(uuid);
    }

    /**
     * Resolves a Player from direct damage or a player-owned
     * projectile.
     */
    private Player resolveAttackingPlayer(
            Entity damager
    ) {
        /*
         * Direct player hit.
         */
        if (damager instanceof Player player) {
            return player;
        }

        /*
         * Player-owned projectile.
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

    /**
     * Resolves death cause.
     *
     * <p>This method is called ONLY after CombatTag has been
     * confirmed inactive.</p>
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
         * Entity damage.
         */
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

        /*
         * Lava, fall, void, fire, explosion, suffocation, etc.
         */
        return DeathContext.ENVIRONMENT;
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

    /**
     * Forces inventory to drop.
     */
    private void handleDropInventory(
            PlayerDeathEvent event,
            boolean keepExperience
    ) {
        Player player =
                event.getEntity();

        /*
         * Bukkit will normally populate this list.
         * We rebuild it explicitly to guarantee the policy.
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
            event.setDroppedExp(0);

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
                        "death.keep-experience",
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