package com.votri.combatkeepinv;

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
     * DAMAGE / COMBAT TAG
     * ============================================================
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

        if (plugin.isWorldDisabled(victim.getWorld())) {
            return;
        }

        /*
         * Complete bypass for the victim.
         *
         * The victim is not combat-tagged and is not affected
         * by CKI PvP logic.
         */
        if (victim.hasPermission(
                BYPASS_PERMISSION
        )) {
            return;
        }

        Entity damager =
                event.getDamager();

        Player attacker =
                getAttackingPlayer(damager);

        /*
         * ========================================================
         * PLAYER -> PLAYER
         * ========================================================
         */

        if (attacker != null) {

            if (attacker.equals(victim)) {
                return;
            }

            /*
             * A bypass attacker is not processed by CKI.
             * PvP damage itself is not cancelled here.
             */
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
             * Global PvP switch.
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
             * WorldGuard integration.
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
             * Player vs Player combat tag.
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
         * NON-PLAYER -> PLAYER
         * ========================================================
         *
         * Enabled only when:
         *
         * combat.player-vs-player-only: false
         */

        boolean playerVsPlayerOnly =
                plugin.getConfig().getBoolean(
                        "combat.player-vs-player-only",
                        true
                );

        if (playerVsPlayerOnly) {
            return;
        }

        combatManager.tag(
                victim.getUniqueId()
        );

        debugNonPlayerCombat(
                victim,
                damager
        );
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
         * ========================================================
         * COMPLETE BYPASS
         * ========================================================
         *
         * Bypass players always keep their inventory.
         */

        if (victim.hasPermission(
                BYPASS_PERMISSION
        )) {

            handleKeepInventory(event);

            combatManager.remove(uuid);

            return;
        }

        /*
         * ========================================================
         * DIRECT PLAYER KILL
         * ========================================================
         */

        Player killer =
                victim.getKiller();

        if (killer != null) {

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
                ownCombat ||
                        pvpManagerCombat;

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
     * Explicitly rebuild the drops from the live inventory.
     *
     * This is important when the server gamerule is:
     *
     * keepInventory = true
     */

    private void handleDropInventory(
            PlayerDeathEvent event
    ) {
        Player player =
                event.getEntity();

        event.getDrops().clear();

        /*
         * Main inventory.
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
         * Explicitly disable inventory retention.
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
        event.setKeepInventory(true);

        /*
         * Prevent duplicate drops.
         */
        event.getDrops().clear();

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
     * RESOLVE PLAYER ATTACKER
     * ============================================================
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
                "Combat tagged: "
                        + victim.getName()
                        + " <- "
                        + damager.getType()
                        + " [NON_PLAYER_DAMAGE]"
        );
    }
}