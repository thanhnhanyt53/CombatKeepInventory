package com.votri.combatkeepinv.core.internal;

import com.votri.combatkeepinv.core.combat.CombatSession;
import com.votri.combatkeepinv.core.combat.CombatSessionManager;
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
    private final Map<String, Integer> extraMap;

    // Constructor 4 tham số
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
                defaultDeathPolicies(defaultPolicy),
                Map.of()
        );
    }

    // Constructor 6 tham số
    public DefaultDeathService(
            CombatSessionManager combatSessionManager,
            DamageAttributionService damageAttributionService,
            PlatformInfo platform,
            InventoryPolicy defaultPolicy,
            InventoryPolicy combatDeathPolicy,
            Map<DeathReason, InventoryPolicy> deathPolicies
    ) {
        this(
                combatSessionManager,
                damageAttributionService,
                platform,
                defaultPolicy,
                combatDeathPolicy,
                deathPolicies,
                Map.of()
        );
    }

    // Constructor 7 tham số (Master Constructor)
    public DefaultDeathService(
            CombatSessionManager combatSessionManager,
            DamageAttributionService damageAttributionService,
            PlatformInfo platform,
            InventoryPolicy defaultPolicy,
            InventoryPolicy combatDeathPolicy,
            Map<DeathReason, InventoryPolicy> deathPolicies,
            Map<String, Integer> extraMap
    ) {
        this.combatSessionManager = combatSessionManager;
        this.damageAttributionService = damageAttributionService;
        this.platform = platform;
        this.defaultPolicy = defaultPolicy;
        this.combatDeathPolicy = combatDeathPolicy;
        this.deathPolicies = Map.copyOf(deathPolicies);
        this.extraMap = extraMap != null ? Map.copyOf(extraMap) : Map.of();
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

        if (context.wasInCombat()) {
            policy = combatDeathPolicy;
            ruleId = "combat-death";
        } else {
            policy = deathPolicies.get(context.getDeathReason());
            if (policy == null && context.wasPlayerCaused()) {
                policy = deathPolicies.get(DeathReason.PLAYER);
            }
            if (policy == null) {
                policy = defaultPolicy;
                ruleId = "default";
            } else {
                ruleId = "death." + context.getDeathReason().name().toLowerCase();
            }
        }

        boolean keepInventory = policy.getAction() == InventoryAction.KEEP;

        return new DefaultDeathDecision(
                keepInventory,
                policy,
                context.getDeathReason(),
                ruleId
        );
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
