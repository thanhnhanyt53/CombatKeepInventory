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

        /*
         * If CKI is disabled in this world, do absolutely nothing.
         */
        if (plugin.isWorldDisabled(victim.getWorld())) {
            return;
        }

        /*
         * ========================================================
         * COMPLETE BYPASS
         * ========================================================
         *
         * A bypass player must never:
         *
         * - receive a CKI combat tag
         * - create a CKI combat tag
         * - be affected by CKI PvP logic
         *
         * PvP itself is NOT cancelled here.
         */
        if (victim.hasPermission(BYPASS_PERMISSION)) {
            return;
        }

        Entity damager = event.getDamager();

        Player attacker = getAttackingPlayer(damager);

        /*
         * ========================================================
         * PLAYER -> PLAYER
         * ========================================================
         */
        if (attacker != null) {

            /*
             * Self damage is not combat.
             */
            if (attacker.equals(victim)) {
                return;
            }

            /*
             * If attacker has bypass, CKI does not process
             * this combat interaction.
             *
             * Damage itself is still allowed.
             */
            if (attacker.hasPermission(BYPASS_PERMISSION)) {
                return;
            }

            /*
             * Both players must be in an enabled world.
             */
            if (plugin.isWorldDisabled(attacker.getWorld())) {
                return;
            }

            /*
             * ====================================================
             * GLOBAL PVP SWITCH
             * ====================================================
             *
             * PvP OFF + block=true:
             *     cancel PvP damage.
             *
             * PvP OFF + block=false:
             *     allow damage but do not create CKI tag.
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
             * ====================================================
             * WORLDGUARD
             * ====================================================
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
             * ====================================================
             * PLAYER VS PLAYER TAG
             * ====================================================
             *
             * This is always a valid CKI combat interaction.
             *
             * player-vs-player-only affects NON-PLAYER damage.
             * It does not disable normal PvP tagging.
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
         * This section is active ONLY when:
         *
         * combat.player-vs-player-only: false
         *
         * Examples:
         *
         * - Zombie
         * - Skeleton
         * - Creeper
         * - Spider
         * - Enderman
         * - Lava/projectile entities represented by damage events
         * - Other EntityDamageByEntityEvent sources
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
         * Victim bypass was already checked above.
         *
         * Therefore this player can safely receive a CKI tag.
         */
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
        Player victim = event.getEntity();

        /*
         * CKI is completely disabled in this world.
         */
        if (plugin.isWorldDisabled(victim.getWorld())) {
            return;
        }

        UUID uuid = victim.getUniqueId();

        /*
         * ========================================================
         * COMPLETE BYPASS
         * ========================================================
         *
         * Bypass means CKI must NOT make the player drop items.
         *
         * We explicitly force keepInventory=true instead of simply
         * returning, because the server gamerule may be false.
         *
         * This makes bypass deterministic.
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
         *
         * getKiller() is the Bukkit-supported way of determining
         * whether the death was directly caused by a player.
         *
         * This path takes priority over combat state.
         */
        Player killer = victim.getKiller();

        if (killer != null) {

            /*
             * If the killer has bypass, the victim is still NOT
             * bypassed. The victim follows normal death rules.
             *
             * Bypass belongs to the player being protected, not
             * the attacker.
             */

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
         *
         * Example:
         *
         * player-vs-player-only=false
         * player gets killed by zombie while tagged
         *
         * The combat-death-drops option decides the result.
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
     * This method deliberately rebuilds the drops from the
     * player's inventory.
     *
     * This is important when:
     *
     * gamerule keepInventory=true
     *
     * because relying only on:
     *
     * event.setKeepInventory(false)
     *
     * can leave the event with an empty drop list depending on
     * the server's death-event state.
     */
    private void handleDropInventory(
            PlayerDeathEvent event
    ) {
        Player player = event.getEntity();

        /*
         * Remove whatever drops the server/plugin has already
         * placed in the event.
         */
        event.getDrops().clear();

        /*
         * Storage inventory.
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
         * Offhand / extra contents.
         */
        addDrops(
                event,
                player.getInventory().getExtraContents()
        );

        /*
         * Explicitly disable inventory retention.
         */
        event.setKeepInventory(false);

        /*
         * Experience handling.
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
     * ============================================================
     * ADD INVENTORY CONTENTS TO DEATH DROPS
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

            /*
             * Clone the ItemStack so the death event does not
             * reference the player's live inventory object.
             */
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
         * Explicitly retain the inventory.
         */
        event.setKeepInventory(true);

        /*
         * Remove normal death drops so the player does not get
         * duplicated items.
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
     * RESOLVE PLAYER ATTACKER
     * ============================================================
     */
    private Player getAttackingPlayer(
            Entity damager
    ) {
        /*
         * Direct melee attack.
         */
        if (damager instanceof Player player) {
            return player;
        }

        /*
         * Projectile fired by a player.
         *
         * Examples:
         * - Arrow
         * - Spectral arrow
         * - Trident
         * - Fireball-like projectile when shooter is Player
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
     * DEBUG - PLAYER VS PLAYER
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

    /*
     * ============================================================
     * DEBUG - NON PLAYER
     * ============================================================
     */
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