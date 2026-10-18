package com.votri.combatkeepinv.bukkit.listener;

import com.votri.combatkeepinv.bukkit.CombatKeepInventory;
import com.votri.combatkeepinv.bukkit.bridge.BukkitDamageSource;
import com.votri.combatkeepinv.bukkit.hook.WorldGuardHook;
import com.votri.combatkeepinv.core.api.CombatResult;
import com.votri.combatkeepinv.core.damage.DamageSource;
import com.votri.combatkeepinv.core.death.DeathDecision;
import com.votri.combatkeepinv.core.inventory.InventoryPolicy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.Location;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import java.util.UUID;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

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
    private final WorldGuardHook worldGuard;

    /** Recent player-attributed TNT explosions used to resolve BLOCK/ENTITY_EXPLOSION deaths. */
    private final List<ExplosionAttribution> recentExplosions = new ArrayList<>();

    public CombatListener(
            CombatKeepInventory plugin,
            WorldGuardHook worldGuard
    ) {

        this.plugin =
                plugin;

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
                instanceof TNTPrimed) {

            /*
             * attacker was already resolved above via
             * resolveAttackingPlayer(), which for a TNTPrimed
             * damager can only have succeeded through
             * VersionAdapter#getTntIgniter(...) -- no need to
             * probe the source a second time here.
             */
            source =
                    BukkitDamageSource.playerExplosion(
                            attacker.getUniqueId(),
                            victim.getUniqueId(),
                            com.votri.combatkeepinv.core.damage.DamageCauseType.TNT
                    );

        } else if (event.getDamager()
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
                plugin.getCombatService().startCombat(
                        attacker.getUniqueId(),
                        victim.getUniqueId()
                );

        debugCombatResult(
                attacker,
                victim,
                result
        );

        notifyCombatResult(
                attacker,
                victim,
                result
        );
    }

    /*
     * ==========================================================
     * NON-"BY ENTITY" EXPLOSION DAMAGE (COMBAT TAGGING)
     * ==========================================================
     *
     * Bukkit does not always deliver explosion damage as an
     * EntityDamageByEntityEvent with the TNTPrimed as damager;
     * it is frequently a plain EntityDamageEvent with cause
     * BLOCK_EXPLOSION / ENTITY_EXPLOSION and no damager at all.
     *
     * onEntityDamageByEntity() above already handles the case
     * where Bukkit *does* provide the TNTPrimed as damager.
     * This handler covers the remaining case using the same
     * recentExplosions attribution already used at death time
     * by createDeathDamageSource()/findRecentExplosionOwner(),
     * so that non-lethal TNT hits also start/refresh CombatTag
     * instead of only being attributed if they happen to kill.
     */
    @EventHandler
    public void onEntityDamage(
            EntityDamageEvent event
    ) {
        if (event.isCancelled()) {
            return;
        }

        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        EntityDamageEvent.DamageCause cause =
                event.getCause();

        if (cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                && cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            return;
        }

        UUID attackerId =
                findRecentExplosionOwner(victim);

        if (attackerId == null) {
            debugDamageIgnored(
                    "explosion damage with no recent "
                            + "player-lit TNT nearby."
            );
            return;
        }

        if (attackerId.equals(
                victim.getUniqueId()
        )) {
            debugDamageIgnored(
                    "attacker and victim are the same player."
            );
            return;
        }

        Player attacker =
                Bukkit.getPlayer(attackerId);

        if (attacker == null) {
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

        DamageSource source =
                BukkitDamageSource.playerExplosion(
                        attackerId,
                        victim.getUniqueId(),
                        cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                                ? com.votri.combatkeepinv.core.damage.DamageCauseType.TNT
                                : com.votri.combatkeepinv.core.damage.DamageCauseType.EXPLOSION
                );

        plugin.getCombatService()
                .getDamageAttributionService()
                .recordDamage(source);

        CombatResult result =
                plugin.getCombatService().startCombat(
                        attackerId,
                        victim.getUniqueId()
                );

        debugCombatResult(
                attacker,
                victim,
                result
        );

        notifyCombatResult(
                attacker,
                victim,
                result
        );
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (!(event.getEntity() instanceof TNTPrimed tnt)) {
            return;
        }

        Optional<Player> igniter =
                plugin.getVersionAdapter()
                        .getTntIgniter(tnt);

        if (igniter.isEmpty()) {
            return;
        }

        Player player = igniter.get();

        Location location = tnt.getLocation();
        if (location.getWorld() == null) {
            return;
        }

        long now = System.currentTimeMillis();
        cleanupExplosionAttributions(now);
        recentExplosions.add(new ExplosionAttribution(
                location.getWorld().getUID(),
                location.getX(),
                location.getY(),
                location.getZ(),
                player.getUniqueId(),
                now
        ));
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

            plugin.getCombatManager().remove(uuid);
            plugin.getCombatService().getDamageAttributionService().clearDamageHistory(uuid);

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
        if (plugin.getCombatService().isInCombat(uuid)) {

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

            applyCoreDecision(
                    event,
                    decision
            );

            debugDeath(
                    victim,
                    null,
                    "ACTIVE_COMBAT_TAG"
            );

            plugin.getCombatManager().remove(uuid);
            plugin.getCombatService().getDamageAttributionService().clearDamageHistory(uuid);

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
                decision
        );

        debugDeath(
                victim,
                null,
                "CORE_DEATH_POLICY"
        );

        plugin.getCombatManager().remove(uuid);
        plugin.getCombatService().getDamageAttributionService().clearDamageHistory(uuid);
    }

    /*
     * ==========================================================
     * DEATH DAMAGE SOURCE & DECISION HELPERS
     * ==========================================================
     */

    private DamageSource createDeathDamageSource(
            Player victim
    ) {
        var damage = victim.getLastDamageCause();

        if (damage instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();

            if (damager instanceof Player attacker) {
                return BukkitDamageSource.playerAttack(
                        attacker.getUniqueId(),
                        victim.getUniqueId()
                );
            }

            if (damager instanceof Projectile projectile
                    && projectile.getShooter() instanceof Player attacker) {
                return BukkitDamageSource.playerProjectile(
                        attacker.getUniqueId(),
                        projectile.getUniqueId(),
                        victim.getUniqueId()
                );
            }

            if (damager instanceof TNTPrimed tnt) {

                Optional<Player> igniter =
                        plugin.getVersionAdapter()
                                .getTntIgniter(tnt);

                if (igniter.isPresent()) {
                    return BukkitDamageSource.playerExplosion(
                            igniter.get().getUniqueId(),
                            victim.getUniqueId(),
                            com.votri.combatkeepinv.core.damage.DamageCauseType.TNT
                    );
                }
            }
        }

        com.votri.combatkeepinv.core.damage.DamageCauseType cause =
                mapExplosionCause(damage);

        if (cause == com.votri.combatkeepinv.core.damage.DamageCauseType.TNT
                || cause == com.votri.combatkeepinv.core.damage.DamageCauseType.EXPLOSION) {
            UUID attacker = findRecentExplosionOwner(victim);
            if (attacker != null) {
                return BukkitDamageSource.playerExplosion(
                        attacker,
                        victim.getUniqueId(),
                        cause
                );
            }
        }

        return BukkitDamageSource.environment(
                victim.getUniqueId(),
                damage
        );
    }

    private void applyCoreDecision(
            PlayerDeathEvent event,
            DeathDecision decision
    ) {
        if (decision == null) {
            return;
        }

        Player victim =
                event.getEntity();

        InventoryPolicy policy = decision.getInventoryPolicy();
        switch (policy.getAction()) {
            case KEEP -> {
                handleKeepInventory(
                        event,
                        policy.keepExperience(),
                        policy.keepLevels()
                );
                victim.sendMessage(
                        plugin.getPrefixedMessage(
                                "death.keep-inventory",
                                "&aYour inventory has been kept."
                        )
                );
            }
            case DROP -> {
                handleDropInventory(
                        event,
                        policy.keepExperience(),
                        policy.keepLevels()
                );
                victim.sendMessage(
                        plugin.getPrefixedMessage(
                                "death.drop-inventory",
                                "&cYour inventory has been dropped "
                                        + "because you died in combat."
                        )
                );
            }
            case PARTIAL -> {
                handlePartialInventory(event, policy);
                victim.sendMessage(
                        plugin.getPrefixedMessage(
                                "death.partial-inventory",
                                "&eSome of your inventory was kept; "
                                        + "the rest was dropped."
                        )
                );
            }
            case VANILLA -> {
                /*
                 * Preserve the server's vanilla keepInventory
                 * behavior. No message is sent on purpose -- CKI
                 * is deliberately staying out of the way here, so
                 * a CKI-branded message would be misleading.
                 */
            }
        }
    }

    private com.votri.combatkeepinv.core.damage.DamageCauseType mapExplosionCause(
            org.bukkit.event.entity.EntityDamageEvent damage
    ) {
        if (damage == null) {
            return com.votri.combatkeepinv.core.damage.DamageCauseType.UNKNOWN;
        }

        return switch (damage.getCause()) {
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION ->
                    com.votri.combatkeepinv.core.damage.DamageCauseType.EXPLOSION;
            default -> com.votri.combatkeepinv.core.damage.DamageCauseType.UNKNOWN;
        };
    }

    private UUID findRecentExplosionOwner(Player victim) {
        long now = System.currentTimeMillis();
        cleanupExplosionAttributions(now);

        Location location = victim.getLocation();
        UUID worldId = location.getWorld() == null
                ? null
                : location.getWorld().getUID();

        if (worldId == null) {
            return null;
        }

        UUID best = null;
        double bestDistance = Double.MAX_VALUE;

        for (ExplosionAttribution attribution : recentExplosions) {
            if (!worldId.equals(attribution.worldId)) {
                continue;
            }

            double distance =
                    location.distanceSquared(new Location(
                            location.getWorld(),
                            attribution.x,
                            attribution.y,
                            attribution.z
                    ));

            if (distance <= 64.0 && distance < bestDistance) {
                bestDistance = distance;
                best = attribution.playerId;
            }
        }

        return best;
    }

    private void cleanupExplosionAttributions(long now) {
        Iterator<ExplosionAttribution> iterator = recentExplosions.iterator();
        while (iterator.hasNext()) {
            ExplosionAttribution attribution = iterator.next();
            if (now - attribution.timestamp > 2000L) {
                iterator.remove();
            }
        }
    }

    private record ExplosionAttribution(
            UUID worldId,
            double x,
            double y,
            double z,
            UUID playerId,
            long timestamp
    ) {
    }

    private void handleDropInventory(
            PlayerDeathEvent event,
            boolean keepExperience,
            boolean keepLevels
    ) {
        Player player = event.getEntity();

        event.getDrops().clear();

        addDrops(event, player.getInventory().getStorageContents());
        addDrops(event, player.getInventory().getArmorContents());
        addDrops(event, player.getInventory().getExtraContents());

        event.setKeepInventory(false);

        /*
         * keepExperience controls whether XP orbs drop on the
         * ground; keepLevels separately controls whether the
         * player's level/XP progress survives respawn. Only the
         * setKeepLevel() source changes here (now keepLevels
         * instead of keepExperience) -- the pre-existing
         * setDroppedExp() behaviour per branch is left exactly
         * as it was to avoid guessing at unrelated semantics.
         */
        event.setKeepLevel(keepLevels);

        if (!keepExperience) {
            event.setDroppedExp(0);
        }
    }

    private void handleKeepInventory(
            PlayerDeathEvent event
    ) {
        handleKeepInventory(
                event,
                plugin.shouldKeepDeathExperience(),
                plugin.shouldKeepDeathLevels()
        );
    }

    private void handleKeepInventory(
            PlayerDeathEvent event,
            boolean keepExperience,
            boolean keepLevels
    ) {
        event.setKeepInventory(true);
        event.getDrops().clear();

        /*
         * Inventory is fully kept here, so no XP orbs are ever
         * spawned regardless of keepExperience -- matches the
         * pre-existing behaviour of this method. keepLevels is
         * now applied from the policy instead of being tied to
         * keepExperience.
         */
        event.setKeepLevel(keepLevels);
        event.setDroppedExp(0);
    }

    private void handlePartialInventory(
            PlayerDeathEvent event,
            InventoryPolicy policy
    ) {
        Player player = event.getEntity();
        event.getDrops().clear();
        event.setKeepInventory(true);

        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int index = 0; index < storage.length; index++) {
            ItemStack item = storage[index];
            if (item == null || item.getType().isAir()) {
                continue;
            }

            boolean isHotbar = index < 9;
            boolean keep = isHotbar
                    ? policy.keepHotbar()
                    : policy.keepMainInventory();

            if (!keep) {
                event.getDrops().add(item.clone());
                storage[index] = null;
            }
        }
        player.getInventory().setStorageContents(storage);

        if (!policy.keepArmor()) {
            ItemStack[] armor = player.getInventory().getArmorContents();
            addDrops(event, armor);
            player.getInventory().setArmorContents(new ItemStack[armor.length]);
        }

        if (!policy.keepOffhand()) {
            ItemStack[] extra = player.getInventory().getExtraContents();
            addDrops(event, extra);
            player.getInventory().setExtraContents(new ItemStack[extra.length]);
        }

        event.setKeepLevel(policy.keepLevels());

        if (policy.keepExperience()) {
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

        if (damager instanceof TNTPrimed tnt) {

            Optional<Player> igniter =
                    plugin.getVersionAdapter()
                            .getTntIgniter(tnt);

            if (igniter.isPresent()) {
                return igniter.get();
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
        if (!plugin.isDebugDamage()
                && !plugin.getDebugManager().hasWatchers()) {
            return;
        }

        String message =
                "[CombatDamage] received: "
                        + "damager="
                        + event.getDamager().getType()
                        + " -> victim=PLAYER("
                        + victim.getName()
                        + ")";

        if (plugin.isDebugDamage()) {
            plugin.getLogger().info(message);
        }

        plugin.getDebugManager().broadcast(message);
    }

    private void debugDamageIgnored(
            String reason
    ) {
        if (!plugin.isDebugDamage()
                && !plugin.getDebugManager().hasWatchers()) {
            return;
        }

        String message =
                "[CombatDamage] ignored: "
                        + reason;

        if (plugin.isDebugDamage()) {
            plugin.getLogger().info(message);
        }

        plugin.getDebugManager().broadcast(message);
    }

    /**
     * Notifies the two participants that they were just tagged.
     * Only fires on a brand-new tag (CombatResult.SUCCESS) --
     * a refresh of an already-active tag (ALREADY_IN_COMBAT)
     * intentionally stays silent so an ongoing fight with many
     * hits per second does not spam chat every single hit.
     */
    private void notifyCombatResult(
            Player attacker,
            Player victim,
            CombatResult result
    ) {
        if (result != CombatResult.SUCCESS) {
            return;
        }

        attacker.sendMessage(
                plugin.getPrefixedMessage(
                        "combat.attacker-tagged",
                        "&cYou are now in combat with &e%player%&c."
                ).replace(
                        "%player%",
                        victim.getName()
                )
        );

        victim.sendMessage(
                plugin.getPrefixedMessage(
                        "combat.victim-tagged",
                        "&cYou are now in combat with &e%player%&c."
                ).replace(
                        "%player%",
                        attacker.getName()
                )
        );
    }

    private void debugCombatResult(
            Player attacker,
            Player victim,
            CombatResult result
    ) {
        if (!plugin.isDebugCombat()
                && !plugin.getDebugManager().hasWatchers()) {
            return;
        }

        boolean attackerTagged =
                plugin.getCombatService().isInCombat(
                        attacker.getUniqueId()
                );

        boolean victimTagged =
                plugin.getCombatService().isInCombat(
                        victim.getUniqueId()
                );

        long attackerRemaining =
                plugin.getCombatService().getRemainingCombatMillis(
                        attacker.getUniqueId()
                );

        long victimRemaining =
                plugin.getCombatService().getRemainingCombatMillis(
                        victim.getUniqueId()
                );

        String message =
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
                        + "s";

        if (plugin.isDebugCombat()) {
            plugin.getLogger().info(message);
        }

        plugin.getDebugManager().broadcast(message);
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
        if (!plugin.isDebugDeath()
                && !plugin.getDebugManager().hasWatchers()) {
            return;
        }

        String message =
                "[CombatDeath] "
                        + player.getName()
                        + " | state="
                        + state
                        + " | context="
                        + (
                        context == null
                                ? "NOT_EVALUATED"
                                : context
                );

        if (plugin.isDebugDeath()) {
            plugin.getLogger().info(message);
        }

        plugin.getDebugManager().broadcast(message);
    }
}
