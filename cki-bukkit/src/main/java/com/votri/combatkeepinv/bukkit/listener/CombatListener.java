package com.votri.combatkeepinv.bukkit.listener;

import com.votri.combatkeepinv.bukkit.CombatKeepInventory;
import com.votri.combatkeepinv.bukkit.combat.CombatManager;
import com.votri.combatkeepinv.bukkit.hook.WorldGuardHook;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
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
    private final WorldGuardHook worldGuard;

    public CombatListener(
            CombatKeepInventory plugin,
            CombatManager combatManager,
            WorldGuardHook worldGuard
    ) {

        this.plugin = plugin;
        this.combatManager = combatManager;
        this.worldGuard = worldGuard;
    }

    public void onEntityDamageByEntity(
            EntityDamageByEntityEvent event
    ) {

        if (!(event.getEntity()
                instanceof Player victim)) {

            return;
        }

        if (plugin.isWorldDisabled(
                victim.getWorld()
        )) {

            return;
        }

        /*
         * Complete CKI bypass.
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
         * PLAYER VS PLAYER
         * ========================================================
         */

        if (attacker != null) {

            if (attacker.equals(victim)) {
                return;
            }

            /*
             * Bypass attacker does not create CKI combat.
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
             * Global PvP state.
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
             * Tag both players.
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
         * NON-PLAYER DAMAGE
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

        combatManager.tag(
                victim.getUniqueId()
        );

        debugNonPlayerCombat(
                victim,
                damager
        );
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
         * ========================================================
         * BYPASS
         * ========================================================
         */

        if (victim.hasPermission(
                BYPASS_PERMISSION
        )) {

            handleKeepInventory(
                    event
            );

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

                handleDropInventory(
                        event
                );

            } else {

                handleKeepInventory(
                        event
                );
            }

            combatManager.remove(uuid);

            return;
        }

        /*
         * ========================================================
         * COMBAT DEATH
         * ========================================================
         */

        boolean inCombat =
                combatManager.isInCombat(
                        uuid
                );

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

            combatManager.remove(uuid);

            return;
        }

        /*
         * ========================================================
         * NORMAL DEATH
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

        combatManager.remove(uuid);
    }

    private void handleDropInventory(
            PlayerDeathEvent event
    ) {

        Player player =
                event.getEntity();

        /*
         * Important when server gamerule:
         *
         * keepInventory = true
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
                "Combat tagged: "
                        + victim.getName()
                        + " <- "
                        + damager.getType()
                        + " [NON_PLAYER_DAMAGE]"
        );
    }
}