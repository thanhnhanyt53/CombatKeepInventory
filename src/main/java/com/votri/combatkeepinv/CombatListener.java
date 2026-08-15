package com.votri.combatkeepinv;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import java.util.UUID;

public final class CombatListener implements Listener {

    private static final String BYPASS_PERMISSION =
            "combatkeepinventory.bypass";

    private final CombatKeepInventory plugin;
    private final CombatManager combatManager;
    private final WorldGuardHook worldGuard;
    private final PvPManagerHook pvpManager;

    public CombatListener(
            CombatKeepInventory plugin,
            CombatManager combatManager,
            WorldGuardHook worldGuard,
            PvPManagerHook pvpManager
    ) {
        this.plugin = plugin;
        this.combatManager = combatManager;
        this.worldGuard = worldGuard;
        this.pvpManager = pvpManager;
    }

    /*
     * ============================================================
     * ENTITY DAMAGE
     * ============================================================
     *
     * player-vs-player-only:
     *
     * true:
     *   Only Player -> Player damage creates a combat tag.
     *
     * false:
     *   Player can also become combat-tagged from
     *   non-player entity damage.
     *
     * PvP toggle only controls Player -> Player damage.
     */

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onEntityDamageByEntity(
            EntityDamageByEntityEvent event
    ) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Entity damager = event.getDamager();

        Player attacker = getAttackingPlayer(damager);

        /*
         * ========================================================
         * WORLD CHECK
         * ========================================================
         */

        if (plugin.isWorldDisabled(victim.getWorld())) {
            return;
        }

        /*
         * ========================================================
         * CONFIG
         * ========================================================
         */

        boolean playerVsPlayerOnly =
                plugin.getConfig().getBoolean(
                        "combat.player-vs-player-only",
                        true
                );

        /*
         * ========================================================
         * PLAYER -> PLAYER
         * ========================================================
         */

        if (attacker != null) {

            if (attacker.equals(victim)) {
                return;
            }

            if (plugin.isWorldDisabled(attacker.getWorld())) {
                return;
            }

            /*
             * ----------------------------------------------------
             * GLOBAL PVP TOGGLE
             * ----------------------------------------------------
             *
             * PvP OFF:
             *
             * block=true:
             *     cancel PvP damage.
             *
             * block=false:
             *     allow PvP damage, but do not create CKI tag.
             */
            if (!plugin.isPvPEnabled()) {

                if (plugin.getConfig().getBoolean(
                        "pvp.block-pvp-when-disabled",
                        true
                )) {
                    event.setCancelled(true);
                }

                return;
            }

            /*
             * ----------------------------------------------------
             * WORLDGUARD
             * ----------------------------------------------------
             */

            if (!worldGuard.canPvP(attacker, victim)) {

                if (plugin.getConfig().getBoolean(
                        "worldguard.block-pvp-when-denied",
                        false
                )) {
                    event.setCancelled(true);
                }

                return;
            }

            /*
             * ----------------------------------------------------
             * BYPASS
             * ----------------------------------------------------
             *
             * Bypass players do not receive or create
             * CombatKeepInventory combat tags.
             *
             * PvP itself is still allowed.
             */

            if (attacker.hasPermission(BYPASS_PERMISSION)) {
                return;
            }

            if (victim.hasPermission(BYPASS_PERMISSION)) {
                return;
            }

            /*
             * ----------------------------------------------------
             * PLAYER VS PLAYER COMBAT
             * ----------------------------------------------------
             *
             * Since this is definitely Player -> Player,
             * it is always allowed to create a tag.
             */

            combatManager.tag(
                    attacker.getUniqueId(),
                    victim.getUniqueId()
            );

            debugCombat(
                    attacker,
                    victim,
                    "PLAYER_VS_PLAYER"
            );

            return;
        }

        /*
         * ========================================================
         * NON-PLAYER ENTITY -> PLAYER
         * ========================================================
         *
         * Only enabled when:
         *
         * combat.player-vs-player-only = false
         */

        if (playerVsPlayerOnly) {
            return;
        }

        /*
         * A bypass player is not affected by CKI.
         */

        if (victim.hasPermission(BYPASS_PERMISSION)) {
            return;
        }

        /*
         * Non-player damage does not have another Player UUID
         * to tag as attacker.
         *
         * Therefore only the victim is placed into combat.
         */

        combatManager.tag(
                victim.getUniqueId()
        );

        if (plugin.getConfig().getBoolean(
                "debug.combat",
                false
        )) {
            plugin.getLogger().info(
                    "Combat tagged: "
                            + victim.getName()
                            + " <- "
                            + damager.getType()
            );
        }
    }

    /*
     * ============================================================
     * PLAYER DEATH
     * ============================================================
     */

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onPlayerDeath(
            PlayerDeathEvent event
    ) {
        Player victim = event.getEntity();

        /*
         * Disabled world:
         * CKI does nothing.
         */

        if (plugin.isWorldDisabled(victim.getWorld())) {
            return;
        }

        UUID uuid = victim.getUniqueId();

        /*
         * ========================================================
         * BYPASS
         * ========================================================
         *
         * Bypass players keep their inventory.
         *
         * Do not process PvP/combat death logic.
         */

        if (victim.hasPermission(BYPASS_PERMISSION)) {

            handleKeepInventory(event);

            combatManager.remove(uuid);

            return;
        }

        /*
         * ========================================================
         * DIRECT PLAYER KILL
         * ========================================================
         */

        boolean directPlayerKill =
                victim.getKiller() != null;

        if (directPlayerKill) {

            boolean shouldDrop =
                    plugin.getConfig().getBoolean(
                            "death.player-kill-drops",
                            true
                    );

            if (shouldDrop) {
                handleDropInventory(event);
            } else {
                handleKeepInventory(event);
            }

            combatManager.remove(uuid);

            return;
        }

        /*
         * ========================================================
         * COMBAT STATE
         * ========================================================
         */

        boolean ownCombat =
                combatManager.isInCombat(uuid);

        boolean usePvPManager =
                plugin.getConfig().getBoolean(
                        "pvpmanager.use-combat-state",
                        true
                );

        boolean pvpManagerCombat =
                usePvPManager
                        && pvpManager.isInCombat(victim);

        boolean inCombat =
                ownCombat || pvpManagerCombat;

        /*
         * ========================================================
         * COMBAT DEATH
         * ========================================================
         */

        if (inCombat) {

            boolean shouldDrop =
                    plugin.getConfig().getBoolean(
                            "death.combat-death-drops",
                            false
                    );

            if (shouldDrop) {
                handleDropInventory(event);
            } else {
                handleKeepInventory(event);
            }

            combatManager.remove(uuid);

            return;
        }

        /*
         * ========================================================
         * NORMAL NON-COMBAT DEATH
         * ========================================================
         */

        boolean shouldKeep =
                plugin.getConfig().getBoolean(
                        "death.non-combat-death-keeps-inventory",
                        true
                );

        if (shouldKeep) {
            handleKeepInventory(event);
        } else {
            handleDropInventory(event);
        }

        combatManager.remove(uuid);
    }

    /*
     * ============================================================
     * FORCE INVENTORY DROP
     * ============================================================
     *
     * IMPORTANT:
     *
     * This method explicitly rebuilds the death drops.
     *
     * This fixes:
     *
     * gamerule keepInventory=true
     * +
     * player-kill-drops=true
     *
     * where simply calling:
     *
     * event.setKeepInventory(false)
     *
     * can leave the death event with no inventory drops.
     */

    private void handleDropInventory(
            PlayerDeathEvent event
    ) {
        Player player = event.getEntity();

        /*
         * Remove any existing drops first.
         *
         * This prevents duplicates if Paper already populated
         * the event drops.
         */

        event.getDrops().clear();

        /*
         * Normal storage inventory.
         */

        addDrops(
                event,
                player.getInventory().getStorageContents()
        );

        /*
         * Armor.
         */

        addDrops(
                event,
                player.getInventory().getArmorContents()
        );

        /*
         * Offhand / extra inventory slots.
         */

        addDrops(
                event,
                player.getInventory().getExtraContents()
        );

        /*
         * Inventory must NOT be retained.
         */

        event.setKeepInventory(false);

        /*
         * Experience.
         */

        boolean keepExperience =
                plugin.getConfig().getBoolean(
                        "death.keep-experience",
                        true
                );

        if (keepExperience) {
            event.setKeepLevel(true);
        } else {
            event.setKeepLevel(false);
            event.setDroppedExp(0);
        }
    }

    /*
     * Adds cloned ItemStacks to the death drops.
     *
     * Cloning is important because the ItemStack objects belong
     * to the player's inventory.
     */

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

    /*
     * ============================================================
     * FORCE INVENTORY KEEP
     * ============================================================
     */

    private void handleKeepInventory(
            PlayerDeathEvent event
    ) {
        /*
         * Inventory remains with the player.
         */

        event.setKeepInventory(true);

        /*
         * Prevent duplicate ground drops.
         */

        event.getDrops().clear();

        /*
         * Experience.
         */

        boolean keepExperience =
                plugin.getConfig().getBoolean(
                        "death.keep-experience",
                        true
                );

        if (keepExperience) {
            event.setKeepLevel(true);
            event.setDroppedExp(0);
        } else {
            event.setKeepLevel(false);
            event.setDroppedExp(0);
        }
    }

    /*
     * ============================================================
     * ATTACKER RESOLUTION
     * ============================================================
     */

    private Player getAttackingPlayer(
            Entity damager
    ) {
        /*
         * Direct melee.
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
     * ============================================================
     * DEBUG
     * ============================================================
     */

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
                        + " [" + reason + "]"
        );
    }
}
