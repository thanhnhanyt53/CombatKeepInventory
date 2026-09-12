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

import java.util.Optional;
import java.util.UUID;

public final class DefaultDeathService
        implements DeathService {

    private final CombatSessionManager combatSessionManager;
    private final DamageAttributionService damageAttributionService;
    private final PlatformInfo platform;
    private final InventoryPolicy defaultPolicy;

    public DefaultDeathService(
            CombatSessionManager combatSessionManager,
            DamageAttributionService damageAttributionService,
            PlatformInfo platform,
            InventoryPolicy defaultPolicy
    ) {
        this.combatSessionManager = combatSessionManager;
        this.damageAttributionService =
                damageAttributionService;
        this.platform = platform;
        this.defaultPolicy = defaultPolicy;
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
                        .filter(session ->
                                session.getState()
                                        == CombatState.ACTIVE);

        return new DefaultDeathContext(
                playerId,
                System.currentTimeMillis(),
                damageSource,
                resolveDeathReason(
                        damageSource,
                        attribution
                ),
                attribution.getKillerId(),
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
        boolean keepInventory =
                context.wasInCombat()
                        && !context.wasPlayerCaused();

        if (context.wasPlayerCaused()) {
            keepInventory = false;
        }

        InventoryAction action =
                keepInventory
                        ? InventoryAction.KEEP
                        : InventoryAction.DROP;

        InventoryPolicy policy =
                keepInventory
                        ? defaultPolicy
                        : new DefaultInventoryPolicy(
                                InventoryAction.DROP,
                                false,
                                false,
                                false,
                                false,
                                false,
                                false
                        );

        return new DefaultDeathDecision(
                keepInventory,
                policy,
                context.getDeathReason(),
                context.wasPlayerCaused()
                        ? "pvp-combat"
                        : "default"
        );
    }

    @Override
    public DeathOutcome process(
            DeathContext context
    ) {
        DeathDecision decision =
                evaluate(context);

        if (decision.shouldKeepInventory()) {
            return decision.getInventoryPolicy()
                    .getAction()
                    == InventoryAction.KEEP
                    ? DeathOutcome.INVENTORY_KEPT
                    : DeathOutcome.INVENTORY_PARTIALLY_KEPT;
        }

        return DeathOutcome.INVENTORY_DROPPED;
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