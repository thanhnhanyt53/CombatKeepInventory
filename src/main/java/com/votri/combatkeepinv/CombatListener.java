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
    private final InventoryJournalManager journalManager;

    public CombatListener(
            CombatKeepInventory plugin,
            CombatManager combatManager,
            WorldGuardHook worldGuard,
            PvPManagerHook pvpManager,
            InventoryJournalManager journalManager
    ) {
        this.plugin = plugin;
        this.combatManager = combatManager;
        this.worldGuard = worldGuard;
        this.pvpManager = pvpManager;
        this.journalManager = journalManager;
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

        if (plugin.isWorldDisabled(
                victim.getWorld()
        )) {
            return;
        }

        /*
         * Bypass player is completely ignored by CKI.
         * PvP itself is NOT cancelled.
         */
        if (victim.hasPermission(
                BYPASS_PERMISSION
        )) {
            return;
        }

        Entity damager =
                event.getDamager();

        Player attacker =
                getAttackingPlayer(
                        damager
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

            /*
             * Attacker bypass:
             * do not create CKI combat state.
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
             * Normal PvP combat tag.
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
         * Only tag if global PvP/CKI state allows it.
         */
        if (!plugin.isPvPEnabled()) {
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
         * CANCEL ANY PENDING JOURNAL
         * ========================================================
         *
         * A real death is authoritative.
         *
         * If a stale journal exists, it must not be restored later.
         */
        journalManager.cancelForDeath(
                uuid
        );

        /*
         * ========================================================
         * COMPLETE BYPASS
         * ========================================================
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

                handleDropInventory(
                        event
                );

            } else {

                handleKeepInventory(
                        event
                );
            }

            combatManager.remove(
                    uuid
            );

            return;
        }

        /*
         * ========================================================
         * COMBAT STATE
         * ========================================================
         */

        boolean ownCombat =
                combatManager.isInCombat(
                        uuid
                );

        boolean usePvPManager =
                plugin.getConfig().getBoolean(
                        "pvpmanager.use-combat-state",
                        true
                );

        boolean pvpManagerCombat =
                usePvPManager
                        && pvpManager.isInCombat(
                        victim
                );

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

                handleDropInventory(
                        event
                );

            } else {

                handleKeepInventory(
                        event
                );
            }

            combatManager.remove(
                    uuid
            );

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

            handleKeepInventory(
                    event
            );

        } else {

            handleDropInventory(
                    event
            );
        }

        combatManager.remove(
                uuid
        );
    }

    /*
     * ============================================================
     * FORCE INVENTORY DROP
     * ============================================================
     */

    private void handleDropInventory(
            PlayerDeathEvent event
    ) {

        Player player =
                event.getEntity();

        /*
         * Clear server-generated drops.
         *
         * This is important when gamerule keepInventory=true.
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
     * ============================================================
     * ADD INVENTORY CONTENTS TO DROPS
     * ============================================================
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

        event.setKeepInventory(true);

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