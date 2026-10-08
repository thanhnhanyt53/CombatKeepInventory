package com.votri.combatkeepinv.bukkit.listener;

import com.votri.combatkeepinv.bukkit.CombatKeepInventory;
import com.votri.combatkeepinv.bukkit.bridge.BukkitDamageSource;
import com.votri.combatkeepinv.bukkit.hook.WorldGuardHook;
import com.votri.combatkeepinv.core.api.CombatResult;
import com.votri.combatkeepinv.core.damage.DamageSource;
import com.votri.combatkeepinv.core.death.DeathDecision;
import com.votri.combatkeepinv.core.inventory.InventoryPolicy;

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
                instanceof TNTPrimed tnt
                && tnt.getSource() instanceof Player) {

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
    }

    /**
     * Handles explosion damage that Bukkit exposes as a plain
     * {@link EntityDamageEvent} instead of an
     * {@link EntityDamageByEntityEvent}.
     *
     * <p>The TNT owner is resolved from the same short-lived
     * {@code recentExplosions} registry used by death attribution.
     * This keeps TNT combat tagging independent of the exact Bukkit
     * damage-event subtype emitted by the server implementation.</p>
     *
     * <p>{@link EntityDamageByEntityEvent} is a subclass of
     * {@link EntityDamageEvent}, so it is explicitly ignored here;
     * those events are handled by {@link #onEntityDamageByEntity}.</p>
     */
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.isCancelled()
                || event instanceof EntityDamageByEntityEvent) {
            return;
        }

        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        if (!isExplosionDamage(event)) {
            return;
        }

        UUID attackerId = findRecentExplosionOwner(victim);
        if (attackerId == null) {
            debugDamageIgnored(
                    "explosion has no recent player attribution."
            );
            return;
        }

        if (attackerId.equals(victim.getUniqueId())) {
            debugDamageIgnored(
                    "explosion was caused by the victim."
            );
            return;
        }

        Player attacker =
                plugin.getServer().getPlayer(attackerId);

        if (attacker == null) {
            debugDamageIgnored(
                    "explosion owner is not online."
            );
            return;
        }

        if (attacker.hasPermission(BYPASS_PERMISSION)
                || victim.hasPermission(BYPASS_PERMISSION)) {
            debugDamageIgnored(
                    "bypass permission."
            );
            return;
        }

        if (plugin.isWorldDisabled(attacker.getWorld())
                || plugin.isWorldDisabled(victim.getWorld())) {
            debugDamageIgnored(
                    "CKI is disabled in this world."
            );
            return;
        }

        if (worldGuard != null
                && !worldGuard.canPvP(attacker, victim)) {
            debugDamageIgnored(
                    "WorldGuard does not allow PvP."
            );
            return;
        }

        DamageSource source =
                BukkitDamageSource.playerExplosion(
                        attackerId,
                        victim.getUniqueId(),
                        mapExplosionCause(event)
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
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (!(event.getEntity() instanceof TNTPrimed tnt)) {
            return;
        }

        if (!(tnt.getSource() instanceof Player player)) {
            return;
        }

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

            if (damager instanceof TNTPrimed tnt
                    && tnt.getSource() instanceof Player attacker) {
                return BukkitDamageSource.playerExplosion(
                        attacker.getUniqueId(),
                        victim.getUniqueId(),
                        com.votri.combatkeepinv.core.damage.DamageCauseType.TNT
                );
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

        InventoryPolicy policy = decision.getInventoryPolicy();
        switch (policy.getAction()) {
            case KEEP -> handleKeepInventory(event, policy.keepExperience());
            case DROP -> handleDropInventory(event, policy.keepExperience());
            case PARTIAL -> handlePartialInventory(event, policy);
            case VANILLA -> {
                // Preserve the server's vanilla keepInventory behavior.
            }
        }
    }

    private boolean isExplosionDamage(EntityDamageEvent event) {
        return event != null
                && (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION);
    }

    private com.votri.combatkeepinv.core.damage.DamageCauseType mapExplosionCause(
            EntityDamageEvent damage
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
            boolean keepExperience
    ) {
        Player player = event.getEntity();

        event.getDrops().clear();

        addDrops(event, player.getInventory().getStorageContents());
        addDrops(event, player.getInventory().getArmorContents());
        addDrops(event, player.getInventory().getExtraContents());

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
                plugin.shouldKeepDeathExperience()
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

        if (policy.keepExperience()) {
            event.setKeepLevel(true);
            event.setDroppedExp(0);
        } else {
            event.setKeepLevel(false);
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

        if (damager instanceof TNTPrimed tnt
                && tnt.getSource() instanceof Player player) {
            return player;
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
        if (!plugin.isDebugDamage()) {
            return;
        }

        plugin.getLogger().info(
                "[CombatDamage] received: "
                        + "damager="
                        + event.getDamager().getType()
                        + " -> victim=PLAYER("
                        + victim.getName()
                        + ")"
        );
    }

    private void debugDamageIgnored(
            String reason
    ) {
        if (!plugin.isDebugDamage()) {
            return;
        }

        plugin.getLogger().info(
                "[CombatDamage] ignored: "
                        + reason
        );
    }

    private void debugCombatResult(
            Player attacker,
            Player victim,
            CombatResult result
    ) {
        if (!plugin.isDebugCombat()) {
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

        plugin.getLogger().info(
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
                        + "s"
        );
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
        if (!plugin.isDebugDeath()) {
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
