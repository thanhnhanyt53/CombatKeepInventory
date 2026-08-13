package com.votri.combatkeepinv;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class CombatListener implements Listener {

    private final CombatKeepInventory plugin;
    private final CombatManager combatManager;
    private final WorldGuardHook worldGuardHook;
    private final PvPManagerHook pvpManagerHook;

    public CombatListener(
            CombatKeepInventory plugin,
            CombatManager combatManager,
            WorldGuardHook worldGuardHook,
            PvPManagerHook pvpManagerHook
    ) {
        this.plugin = plugin;
        this.combatManager = combatManager;
        this.worldGuardHook = worldGuardHook;
        this.pvpManagerHook = pvpManagerHook;
    }

    private Player getPlayerAttacker(Entity damager) {

        if (damager instanceof Player player) {
            return player;
        }

        if (damager instanceof Projectile projectile) {

            if (projectile.getShooter()
                    instanceof Player player) {

                return player;
            }
        }

        return null;
    }

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onPlayerDamage(
            EntityDamageByEntityEvent event
    ) {

        if (!(event.getEntity()
                instanceof Player victim)) {

            return;
        }

        Player attacker =
                getPlayerAttacker(
                        event.getDamager()
                );

        if (attacker == null) {
            return;
        }

        if (attacker.getUniqueId()
                .equals(victim.getUniqueId())) {

            return;
        }

        /*
         * WorldGuard integration.
         */
        if (plugin.getConfig()
                .getBoolean(
                        "worldguard.enabled",
                        true
                )
                && worldGuardHook.isAvailable()) {

            if (!worldGuardHook
                    .isPvPAllowed(
                            attacker,
                            victim
                    )) {

                debugCombat(
                        "PvP ignored by WorldGuard: "
                                + attacker.getName()
                                + " -> "
                                + victim.getName()
                );

                return;
            }
        }

        /*
         * Tag CombatKeepInventory.
         */
        combatManager.tag(
                attacker,
                victim
        );

        debugCombat(
                "Combat tagged: "
                        + attacker.getName()
                        + " <-> "
                        + victim.getName()
        );
    }

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onPlayerDeath(
            PlayerDeathEvent event
    ) {

        Player victim =
                event.getEntity();

        /*
         * ----------------------------------------------------
         * 1. Direct Player killer
         * ----------------------------------------------------
         */
        boolean playerKilled =
                victim.getKiller() != null;

        /*
         * ----------------------------------------------------
         * 2. Own CombatKeepInventory combat
         * ----------------------------------------------------
         */
        boolean ownCombat =
                combatManager.isInCombat(victim);

        /*
         * ----------------------------------------------------
         * 3. PvPManager combat
         * ----------------------------------------------------
         */
        boolean pvpManagerCombat = false;

        if (plugin.getConfig()
                .getBoolean(
                        "pvpmanager.enabled",
                        true
                )
                && plugin.getConfig()
                .getBoolean(
                        "pvpmanager.use-combat-state",
                        true
                )
                && pvpManagerHook.isAvailable()) {

            pvpManagerCombat =
                    pvpManagerHook
                            .isInCombat(victim);
        }

        /*
         * ----------------------------------------------------
         * 4. PvPManager last PvP death
         * ----------------------------------------------------
         */
        boolean pvpManagerLastDeathPvP =
                false;

        if (plugin.getConfig()
                .getBoolean(
                        "pvpmanager.enabled",
                        true
                )
                && plugin.getConfig()
                .getBoolean(
                        "pvpmanager.trust-last-pvp-death",
                        true
                )
                && pvpManagerHook.isAvailable()) {

            pvpManagerLastDeathPvP =
                    pvpManagerHook
                            .wasLastDeathPvP(victim);
        }

        /*
         * ----------------------------------------------------
         * 5. Determine final combat state.
         * ----------------------------------------------------
         */

        boolean inheritPvPManager =
                plugin.getConfig()
                        .getBoolean(
                                "pvpmanager.inherit-combat-state",
                                true
                        );

        boolean inCombat =
                ownCombat
                        || (
                        inheritPvPManager
                                && pvpManagerCombat
                );

        /*
         * ----------------------------------------------------
         * 6. Final DROP decision.
         * ----------------------------------------------------
         */

        boolean shouldDrop = false;

        /*
         * Player trực tiếp giết.
         */
        if (playerKilled
                && plugin.getConfig()
                .getBoolean(
                        "death.player-kill-drops",
                        true
                )) {

            shouldDrop = true;
        }

        /*
         * Đang combat.
         */
        if (inCombat
                && plugin.getConfig()
                .getBoolean(
                        "death.combat-death-drops",
                        true
                )) {

            shouldDrop = true;
        }

        /*
         * PvPManager xác nhận death PvP.
         */
        if (pvpManagerLastDeathPvP
                && plugin.getConfig()
                .getBoolean(
                        "pvpmanager.trust-last-pvp-death",
                        true
                )) {

            shouldDrop = true;
        }

        /*
         * ----------------------------------------------------
         * 7. WorldGuard death restriction.
         * ----------------------------------------------------
         */

        if (plugin.getConfig()
                .getBoolean(
                        "worldguard.ignore-death-outside-regions",
                        false
                )
                && worldGuardHook.isAvailable()) {

            if (!worldGuardHook.isAllowed(victim)) {

                /*
                 * Không nằm trong region được phép:
                 * plugin không can thiệp death behavior.
                 */
                combatManager.remove(
                        victim.getUniqueId()
                );

                debugDeath(
                        "Ignored death outside allowed WorldGuard region: "
                                + victim.getName()
                );

                return;
            }
        }

        /*
         * ----------------------------------------------------
         * 8. Apply final result.
         * ----------------------------------------------------
         */

        if (shouldDrop) {

            event.setKeepInventory(false);
            event.setKeepLevel(false);

            debugDeath(
                    "DROP | player="
                            + victim.getName()
                            + " | directKiller="
                            + playerKilled
                            + " | ownCombat="
                            + ownCombat
                            + " | pvpManagerCombat="
                            + pvpManagerCombat
                            + " | pvpManagerLastDeathPvP="
                            + pvpManagerLastDeathPvP
            );

        } else {

            boolean keepInventory =
                    plugin.getConfig()
                            .getBoolean(
                                    "death.non-combat-death-keeps-inventory",
                                    true
                            );

            if (keepInventory) {

                event.setKeepInventory(true);

                if (plugin.getConfig()
                        .getBoolean(
                                "death.keep-experience",
                                true
                        )) {

                    event.setKeepLevel(true);
                    event.setDroppedExp(0);
                }

                event.getDrops().clear();

                debugDeath(
                        "KEEP | player="
                                + victim.getName()
                                + " | no PvP/combat state"
                );

            } else {

                event.setKeepInventory(false);
                event.setKeepLevel(false);
            }
        }

        combatManager.remove(
                victim.getUniqueId()
        );
    }

    @EventHandler
    public void onPlayerQuit(
            PlayerQuitEvent event
    ) {

        combatManager.remove(
                event.getPlayer()
                        .getUniqueId()
        );
    }

    private void debugCombat(String message) {

        if (plugin.getConfig()
                .getBoolean(
                        "debug.combat",
                        false
                )) {

            plugin.getLogger().info(
                    "[Combat] " + message
            );
        }
    }

    private void debugDeath(String message) {

        if (plugin.getConfig()
                .getBoolean(
                        "debug.death",
                        false
                )) {

            plugin.getLogger().info(
                    "[Death] " + message
            );
        }
    }
    }
