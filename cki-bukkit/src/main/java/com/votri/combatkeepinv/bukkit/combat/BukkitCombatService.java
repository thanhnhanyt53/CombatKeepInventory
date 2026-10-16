package com.votri.combatkeepinv.bukkit.combat;

import com.votri.combatkeepinv.bukkit.CombatKeepInventory;
import com.votri.combatkeepinv.bukkit.bridge.BukkitDamageSource;

import com.votri.combatkeepinv.core.api.CombatResult;
import com.votri.combatkeepinv.core.api.CombatService;
import com.votri.combatkeepinv.core.api.CombatState;
import com.votri.combatkeepinv.core.api.CombatTag;
import com.votri.combatkeepinv.core.api.DeathContext;
import com.votri.combatkeepinv.core.api.DeathResult;
import com.votri.combatkeepinv.core.api.InventoryPolicy; // Sử dụng InventoryPolicy từ gói api core
import com.votri.combatkeepinv.core.combat.CombatSessionManager;

import com.votri.combatkeepinv.core.damage.DamageAttributionService;

import com.votri.combatkeepinv.core.death.DeathDecision;
import com.votri.combatkeepinv.core.death.DeathReason;
import com.votri.combatkeepinv.core.death.DeathService;

import com.votri.combatkeepinv.core.internal.DefaultDeathService;
import com.votri.combatkeepinv.core.internal.DefaultInventoryPolicy;

import com.votri.combatkeepinv.core.platform.PlatformInfo;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Bukkit compatibility service backed by the new core services.
 */
public final class BukkitCombatService
        implements CombatService {

    private final CombatKeepInventory plugin;
    private final CombatManager combatManager;

    private final CombatSessionManager sessionManager;
    private final DamageAttributionService damageService;
    private final DeathService deathService;
    private final com.votri.combatkeepinv.core.inventory.InventoryPolicy defaultInventoryPolicy;
    private final PlatformInfo platformInfo;

    public BukkitCombatService(
            CombatKeepInventory plugin,
            CombatManager combatManager
    ) {
        this.plugin =
                Objects.requireNonNull(
                        plugin,
                        "plugin"
                );

        this.combatManager =
                Objects.requireNonNull(
                        combatManager,
                        "combatManager"
                );

        this.sessionManager =
                combatManager.getSessionManager();

        this.platformInfo =
                plugin.getPlatform();

        this.defaultInventoryPolicy =
                createDefaultInventoryPolicy();

        this.damageService =
                new com.votri.combatkeepinv.core.internal
                        .DefaultDamageAttributionService();

        this.deathService =
                new DefaultDeathService(
                        sessionManager,
                        damageService,
                        platformInfo,
                        defaultInventoryPolicy,
                        createPolicy(
                                "inventory.policies.combat-death",
                                com.votri.combatkeepinv.core.inventory.InventoryAction.DROP,
                                false
                        ),
                        createDeathPolicies(),
                        createPolicyPriorities()
                );
    }

    @Override
    public CombatResult startCombat(
            UUID attacker,
            UUID victim
    ) {
        if (!isValidPair(
                attacker,
                victim
        )) {
            return CombatResult.INVALID_ARGUMENT;
        }

        if (!isEnabled()) {
            return CombatResult.DISABLED;
        }

        boolean already =
                combatManager.isInCombat(attacker)
                        || combatManager.isInCombat(victim);

        combatManager.tag(
                attacker,
                victim
        );

        return already
                ? CombatResult.ALREADY_IN_COMBAT
                : CombatResult.SUCCESS;
    }

    @Override
    public CombatResult refreshCombat(
            UUID attacker,
            UUID victim
    ) {
        if (!isValidPair(
                attacker,
                victim
        )) {
            return CombatResult.INVALID_ARGUMENT;
        }

        if (!isEnabled()) {
            return CombatResult.DISABLED;
        }

        combatManager.refresh(
                attacker,
                victim
        );

        return CombatResult.SUCCESS;
    }

    @Override
    public CombatResult endCombat(
            UUID player
    ) {
        if (player == null) {
            return CombatResult.INVALID_ARGUMENT;
        }

        if (!combatManager.isInCombat(player)) {
            return CombatResult.NOT_IN_COMBAT;
        }

        return combatManager.remove(player)
                ? CombatResult.SUCCESS
                : CombatResult.NOT_IN_COMBAT;
    }

    @Override
    public CombatResult forceEndCombat(
            UUID player
    ) {
        if (player == null) {
            return CombatResult.INVALID_ARGUMENT;
        }

        if (!combatManager.isInCombat(player)) {
            return CombatResult.NOT_IN_COMBAT;
        }

        return combatManager.forceRemove(player)
                ? CombatResult.SUCCESS
                : CombatResult.NOT_IN_COMBAT;
    }

    @Override
    public boolean isInCombat(
            UUID player
    ) {
        return player != null
                && combatManager.isInCombat(player);
    }

    @Override
    public CombatState getCombatState(
            UUID player
    ) {
        return isInCombat(player)
                ? CombatState.IN_COMBAT
                : CombatState.SAFE;
    }

    @Override
    public CombatTag getCombatTag(
            UUID player
    ) {
        return combatManager.getCombatTag(player);
    }

    @Override
    public long getRemainingCombatMillis(
            UUID player
    ) {
        return combatManager.getRemainingMillis(player);
    }

    public CombatSessionManager getSessionManager() {
        return sessionManager;
    }

    @Override
    public DamageAttributionService getDamageAttributionService() {
        return damageService;
    }

    @Override
    public DeathService getDeathService() {
        return deathService;
    }

    public com.votri.combatkeepinv.core.inventory.InventoryPolicy getDefaultInventoryPolicy() {
        return defaultInventoryPolicy;
    }

    public DeathDecision evaluateCoreDeath(
            UUID playerId,
            com.votri.combatkeepinv.core.damage.DamageSource source
    ) {
        if (playerId == null) {
            return null;
        }

        com.votri.combatkeepinv.core.death.DeathContext context =
                deathService.createContext(
                        playerId,
                        source
                );

        return deathService.evaluate(context);
    }

    @Override
    public DeathResult evaluateDeath(
            UUID player,
            DeathContext context
    ) {
        if (player == null) {
            return new DeathResult(
                    InventoryPolicy.DEFAULT,
                    true,
                    false
            );
        }

        com.votri.combatkeepinv.core.damage.DamageSource source =
                damageService.getLastDamage(player)
                        .orElseGet(() -> BukkitDamageSource.fromLegacyContext(
                                player,
                                context
                        ));

        com.votri.combatkeepinv.core.death.DeathContext coreContext =
                deathService.createContext(
                        player,
                        source
                );

        DeathDecision decision =
                deathService.evaluate(
                        coreContext
                );

        InventoryPolicy resolvedPolicy = toApiInventoryPolicy(
                decision.getInventoryPolicy().getAction()
        );

        return new DeathResult(
                resolvedPolicy,
                decision.shouldKeepExperience(),
                coreContext.wasInCombat()
        );
    }

    @Override
    public boolean isEnabled() {
        return plugin.isEnabled()
                && plugin.getConfig()
                        .getBoolean(
                                "combat.enabled",
                                true
                        );
    }

    private com.votri.combatkeepinv.core.inventory.InventoryPolicy createDefaultInventoryPolicy() {
        boolean keepExperience =
                plugin.getConfig().contains("inventory.policies.pve-death.keep-experience")
                        ? plugin.getConfig().getBoolean(
                                "inventory.policies.pve-death.keep-experience",
                                true
                        )
                        : plugin.getConfig().getBoolean(
                                "death.keep-experience",
                                true
                        );

        boolean keepMainInventory =
                plugin.getConfig()
                        .getBoolean(
                                "inventory.keep-main",
                                true
                        );

        boolean keepArmor =
                plugin.getConfig()
                        .getBoolean(
                                "inventory.keep-armor",
                                true
                        );

        boolean keepOffhand =
                plugin.getConfig()
                        .getBoolean(
                                "inventory.keep-offhand",
                                true
                        );

        boolean keepHotbar =
                plugin.getConfig()
                        .getBoolean(
                                "inventory.keep-hotbar",
                                true
                        );

        boolean keepLevels =
                plugin.getConfig()
                        .getBoolean(
                                "inventory.keep-levels",
                                keepExperience
                        );

        return new DefaultInventoryPolicy(
                com.votri.combatkeepinv.core.inventory.InventoryAction.KEEP,
                keepMainInventory,
                keepArmor,
                keepOffhand,
                keepHotbar,
                keepExperience,
                keepLevels
        );
    }


    private Map<String, Integer> createPolicyPriorities() {
        Map<String, Integer> priorities = new java.util.HashMap<>();
        priorities.put("combat-death", plugin.getConfig().getInt("inventory.policies.combat-death.priority", 400));
        priorities.put("player-kill", plugin.getConfig().getInt("inventory.policies.player-kill.priority", 300));
        priorities.put("pve-death", plugin.getConfig().getInt("inventory.policies.pve-death.priority", 200));
        priorities.put("unknown", plugin.getConfig().getInt("inventory.policies.unknown.priority", 100));
        return priorities;
    }

    private Map<DeathReason, com.votri.combatkeepinv.core.inventory.InventoryPolicy> createDeathPolicies() {
        Map<DeathReason, com.votri.combatkeepinv.core.inventory.InventoryPolicy> policies =
                new EnumMap<>(DeathReason.class);

        policies.put(DeathReason.PLAYER, createPolicy(
                "inventory.policies.player-kill",
                com.votri.combatkeepinv.core.inventory.InventoryAction.DROP, false));
        policies.put(DeathReason.PLAYER_PROJECTILE, createPolicy(
                "inventory.policies.player-kill",
                com.votri.combatkeepinv.core.inventory.InventoryAction.DROP, false));
        policies.put(DeathReason.PLAYER_EXPLOSION, createPolicy(
                "inventory.policies.player-kill",
                com.votri.combatkeepinv.core.inventory.InventoryAction.DROP, false));

        for (DeathReason reason : new DeathReason[]{
                DeathReason.ENTITY, DeathReason.ENVIRONMENT,
                DeathReason.VOID, DeathReason.FIRE, DeathReason.LAVA}) {
            policies.put(reason, createPolicy(
                    "inventory.policies.pve-death",
                    com.votri.combatkeepinv.core.inventory.InventoryAction.KEEP, true));
        }

        policies.put(DeathReason.UNKNOWN, createPolicy(
                "inventory.policies.unknown",
                com.votri.combatkeepinv.core.inventory.InventoryAction.VANILLA, true));

        return policies;
    }

    private com.votri.combatkeepinv.core.inventory.InventoryPolicy createPolicy(
            String path,
            com.votri.combatkeepinv.core.inventory.InventoryAction defaultAction,
            boolean defaultKeep
    ) {
        String actionName = plugin.getConfig().getString(path + ".action", defaultAction.name());
        com.votri.combatkeepinv.core.inventory.InventoryAction action = defaultAction;
        if (actionName != null) {
            try {
                action = com.votri.combatkeepinv.core.inventory.InventoryAction.valueOf(
                        actionName.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // Keep the safe default.
            }
        }

        boolean keepMain = plugin.getConfig().getBoolean(path + ".keep-main", defaultKeep);
        boolean keepArmor = plugin.getConfig().getBoolean(path + ".keep-armor", defaultKeep);
        boolean keepOffhand = plugin.getConfig().getBoolean(path + ".keep-offhand", defaultKeep);
        boolean keepHotbar = plugin.getConfig().getBoolean(path + ".keep-hotbar", defaultKeep);
        boolean keepExperience = plugin.getConfig().getBoolean(path + ".keep-experience", defaultKeep);
        boolean keepLevels = plugin.getConfig().getBoolean(path + ".keep-levels", keepExperience);

        return new DefaultInventoryPolicy(
                action, keepMain, keepArmor, keepOffhand, keepHotbar,
                keepExperience, keepLevels);
    }

    private InventoryPolicy toApiInventoryPolicy(
            com.votri.combatkeepinv.core.inventory.InventoryAction action
    ) {
        return switch (action) {
            case KEEP -> InventoryPolicy.KEEP;
            case DROP -> InventoryPolicy.DROP;
            case PARTIAL -> InventoryPolicy.PARTIAL;
            case VANILLA -> InventoryPolicy.DEFAULT;
        };
    }

    private boolean isValidPair(
            UUID attacker,
            UUID victim
    ) {
        return attacker != null
                && victim != null
                && !attacker.equals(victim);
    }
}
