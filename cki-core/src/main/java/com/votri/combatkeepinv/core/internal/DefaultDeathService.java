package com.votri.combatkeepinv.core.internal;

import com.votri.combatkeepinv.core.combat.CombatSession;
import com.votri.combatkeepinv.core.combat.CombatSessionManager;
import com.votri.combatkeepinv.core.combat.CombatState;
import com.votri.combatkeepinv.core.damage.DamageAttributionResult;
import com.votri.combatkeepinv.core.damage.DamageAttributionService;
import com.votri.combatkeepinv.core.damage.DamageSource;
import com.votri.combatkeepinv.core.death.DeathContext;
import com.votri.combatkeepinv.core.death.DeathDecision;
import com.votri.combatkeepinv.core.death.DeathOutcome;
import com.votri.combatkeepinv.core.death.DeathReason;
import com.votri.combatkeepinv.core.death.DeathService;
import com.votri.combatkeepinv.core.inventory.InventoryAction;
import com.votri.combatkeepinv.core.inventory.InventoryPolicy;
import com.votri.combatkeepinv.core.platform.PlatformInfo;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class DefaultDeathService
        implements DeathService {

    private final CombatSessionManager combatSessionManager;
    private final DamageAttributionService damageAttributionService;
    private final PlatformInfo platform;
    private final InventoryPolicy defaultPolicy;
    private final InventoryPolicy combatDeathPolicy;
    private final Map<DeathReason, InventoryPolicy> deathPolicies;
    private final Map<String, Integer> policyPriorities;

    public DefaultDeathService(
            CombatSessionManager combatSessionManager,
            DamageAttributionService damageAttributionService,
            PlatformInfo platform,
            InventoryPolicy defaultPolicy
    ) {
        this(
                combatSessionManager,
                damageAttributionService,
                platform,
                defaultPolicy,
                new DefaultInventoryPolicy(
                        InventoryAction.DROP,
                        false, false, false, false,
                        defaultPolicy.keepExperience(),
                        defaultPolicy.keepLevels()
                ),
                defaultDeathPolicies(defaultPolicy)
        );
    }

    public DefaultDeathService(
            CombatSessionManager combatSessionManager,
            DamageAttributionService damageAttributionService,
            PlatformInfo platform,
            InventoryPolicy defaultPolicy,
            InventoryPolicy combatDeathPolicy,
            Map<DeathReason, InventoryPolicy> deathPolicies
    ) {
        this(combatSessionManager, damageAttributionService, platform,
                defaultPolicy, combatDeathPolicy, deathPolicies, Map.of());
    }

    /** Higher number wins; ties retain the legacy combat-first behavior. */
    public DefaultDeathService(
            CombatSessionManager combatSessionManager,
            DamageAttributionService damageAttributionService,
            PlatformInfo platform,
            InventoryPolicy defaultPolicy,
            InventoryPolicy combatDeathPolicy,
            Map<DeathReason, InventoryPolicy> deathPolicies,
            Map<String, Integer> policyPriorities
    ) {
        this.policyPriorities = Map.copyOf(policyPriorities);
        this.combatSessionManager = combatSessionManager;
        this.damageAttributionService = damageAttributionService;
        this.platform = platform;
        this.defaultPolicy = defaultPolicy;
        this.combatDeathPolicy = combatDeathPolicy;
        this.deathPolicies = Map.copyOf(deathPolicies);
    }

    @Override
    public DeathContext createContext(
            UUID playerId,
            DamageSource damageSource
    ) {
        DamageAttributionResult attribution =
                damageAttributionService.resolve(
                        playerId,
                        damageSource
                );

        Optional<CombatSession> combat =
                combatSessionManager.getSession(playerId)
                        .filter(CombatSession::isActive);

        return new DefaultDeathContext(
                playerId,
                System.currentTimeMillis(),
                damageSource,
                resolveDeathReason(
                        damageSource,
                        attribution
                ),
                attribution.getKillerId().orElse(null),
                combat,
                attribution.isPlayerCaused(),
                attribution.isDirectPvP(),
                attribution.isIndirectPvP(),
                platform
        );
    }

    @Override
    public DeathDecision evaluate(
            DeathContext context
    ) {
        InventoryPolicy policy;
        String ruleId;

        DeathReason reason = context.getDeathReason();
        boolean playerCaused = context.wasPlayerCaused()
                || reason == DeathReason.PLAYER
                || reason == DeathReason.PLAYER_PROJECTILE
                || reason == DeathReason.PLAYER_EXPLOSION;
        boolean pve = reason == DeathReason.ENTITY
                || reason == DeathReason.ENVIRONMENT
                || reason == DeathReason.VOID
                || reason == DeathReason.FIRE
                || reason == DeathReason.LAVA;

        // Consider all matching policies, not merely the first matching if-block.
        // Defaults reproduce the original behavior: combat > player > PvE > unknown.
        int highest = Integer.MIN_VALUE;
        policy = defaultPolicy;
        ruleId = "default";

        if (context.wasInCombat()) {
            highest = priority("combat-death", 400);
            policy = combatDeathPolicy;
            ruleId = "combat-death";
        }
        if (playerCaused && priority("player-kill", 300) > highest) {
            highest = priority("player-kill", 300);
            /*
             * Look up the policy for the ACTUAL reason (PLAYER,
             * PLAYER_PROJECTILE or PLAYER_EXPLOSION), not a
             * hardcoded PLAYER key. createDeathPolicies() maps
             * all three to the same "player-kill" policy object
             * by default, which is why using DeathReason.PLAYER
             * unconditionally looked correct -- but it silently
             * ignores a reason-specific override (e.g. a
             * different policy configured for PLAYER_EXPLOSION
             * alone) the moment an admin sets one up.
             */
            policy = deathPolicies.getOrDefault(reason, defaultPolicy);
            ruleId = "player-kill";
        }
        if (pve && priority("pve-death", 200) > highest) {
            highest = priority("pve-death", 200);
            policy = deathPolicies.getOrDefault(reason, defaultPolicy);
            ruleId = "pve-death";
        }
        if (reason == DeathReason.UNKNOWN && priority("unknown", 100) > highest) {
            highest = priority("unknown", 100);
            policy = deathPolicies.getOrDefault(DeathReason.UNKNOWN, defaultPolicy);
            ruleId = "unknown";
        }
        if (highest == Integer.MIN_VALUE) {
            policy = deathPolicies.getOrDefault(reason, defaultPolicy);
            ruleId = deathPolicies.containsKey(reason)
                    ? "death." + reason.name().toLowerCase(java.util.Locale.ROOT)
                    : "default";
        }

        boolean keepInventory = policy.getAction() == InventoryAction.KEEP;

        return new DefaultDeathDecision(
                keepInventory,
                policy,
                context.getDeathReason(),
                ruleId
        );
    }

    private int priority(String rule, int fallback) {
        return policyPriorities.getOrDefault(rule, fallback);
    }

    @Override
    public DeathOutcome process(
            DeathContext context
    ) {
        DeathDecision decision =
                evaluate(context);

        return switch (decision.getInventoryPolicy().getAction()) {
            case KEEP -> DeathOutcome.INVENTORY_KEPT;
            case PARTIAL -> DeathOutcome.INVENTORY_PARTIALLY_KEPT;
            case DROP -> DeathOutcome.INVENTORY_DROPPED;
            case VANILLA -> DeathOutcome.NO_ACTION;
        };
    }

    private static Map<DeathReason, InventoryPolicy> defaultDeathPolicies(
            InventoryPolicy defaultPolicy
    ) {
        InventoryPolicy playerKill = new DefaultInventoryPolicy(
                InventoryAction.DROP,
                false, false, false, false,
                defaultPolicy.keepExperience(),
                defaultPolicy.keepLevels()
        );

        InventoryPolicy vanilla = new DefaultInventoryPolicy(
                InventoryAction.VANILLA,
                defaultPolicy.keepMainInventory(),
                defaultPolicy.keepArmor(),
                defaultPolicy.keepOffhand(),
                defaultPolicy.keepHotbar(),
                defaultPolicy.keepExperience(),
                defaultPolicy.keepLevels()
        );

        return Map.of(
                DeathReason.PLAYER, playerKill,
                DeathReason.PLAYER_PROJECTILE, playerKill,
                DeathReason.PLAYER_EXPLOSION, playerKill,
                DeathReason.ENTITY, defaultPolicy,
                DeathReason.ENVIRONMENT, defaultPolicy,
                DeathReason.VOID, defaultPolicy,
                DeathReason.FIRE, defaultPolicy,
                DeathReason.LAVA, defaultPolicy,
                DeathReason.UNKNOWN, vanilla
        );
    }

    private DeathReason resolveDeathReason(
            DamageSource source,
            DamageAttributionResult attribution
    ) {
        if (attribution.isDirectPvP()) {
            return DeathReason.PLAYER;
        }

        if (attribution.isIndirectPvP()) {
            return switch (source.getCauseType()) {
                case PROJECTILE ->
                        DeathReason.PLAYER_PROJECTILE;

                case TNT, EXPLOSION ->
                        DeathReason.PLAYER_EXPLOSION;

                default ->
                        DeathReason.PLAYER;
            };
        }

        return switch (source.getCauseType()) {
            case FIRE, FIRE_TICK ->
                    DeathReason.FIRE;

            case LAVA ->
                    DeathReason.LAVA;

            case VOID ->
                    DeathReason.VOID;

            case ENTITY ->
                    DeathReason.ENTITY;

            default ->
                    DeathReason.ENVIRONMENT;
        };
    }
}