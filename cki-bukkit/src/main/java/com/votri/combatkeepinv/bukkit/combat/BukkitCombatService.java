package com.votri.combatkeepinv.bukkit.combat;

import com.votri.combatkeepinv.bukkit.CombatKeepInventory;
import com.votri.combatkeepinv.bukkit.bridge.BukkitDamageSource;

import com.votri.combatkeepinv.core.api.CombatResult;
import com.votri.combatkeepinv.core.api.CombatService;
import com.votri.combatkeepinv.core.api.CombatState;
import com.votri.combatkeepinv.core.api.CombatTag;
import com.votri.combatkeepinv.core.api.DeathContext;
import com.votri.combatkeepinv.core.api.DeathResult;
import com.votri.combatkeepinv.core.inventory.InventoryPolicy;

import com.votri.combatkeepinv.core.combat.CombatReason;
import com.votri.combatkeepinv.core.combat.CombatSessionManager;

import com.votri.combatkeepinv.core.damage.DamageAttributionService;

import com.votri.combatkeepinv.core.death.DeathDecision;
import com.votri.combatkeepinv.core.death.DeathService;

import com.votri.combatkeepinv.core.internal.DefaultDeathService;
import com.votri.combatkeepinv.core.internal.DefaultInventoryPolicy;

import com.votri.combatkeepinv.core.platform.PlatformInfo;

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
    private final InventoryPolicy defaultInventoryPolicy;
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
                        defaultInventoryPolicy
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

    /**
     * Exposes the new core session manager to Bukkit integrations.
     */
    public CombatSessionManager getSessionManager() {
        return sessionManager;
    }

    /**
     * Exposes the new core damage attribution service.
     */
    public DamageAttributionService getDamageAttributionService() {
        return damageService;
    }

    /**
     * Exposes the new core death service.
     */
    public DeathService getDeathService() {
        return deathService;
    }

    /**
     * Exposes the current default inventory policy.
     */
    public InventoryPolicy getDefaultInventoryPolicy() {
        return defaultInventoryPolicy;
    }

    /**
     * Evaluates a precise core death context.
     */
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

    /**
     * Legacy Bukkit API compatibility.
     *
     * <p>The old DeathContext enum is translated into
     * the new core DamageSource contract.</p>
     */
    @Override
    public DeathResult evaluateDeath(
            UUID player,
            DeathContext context
    ) {
        if (player == null) {
            return new DeathResult(
                    InventoryPolicy.KEEP,
                    true,
                    false
            );
        }

        /*
         * Active combat remains an absolute Bukkit-side rule.
         * The new core service receives the environment/PvP source,
         * while the active-tag priority is preserved here.
         */
        if (combatManager.isInCombat(player)) {

            boolean keepExperience =
                    plugin.getConfig()
                            .getBoolean(
                                    "death.keep-experience",
                                    true
                            );

            return new DeathResult(
                    InventoryPolicy.DROP,
                    keepExperience,
                    true
            );
        }

        com.votri.combatkeepinv.core.damage.DamageSource source =
                BukkitDamageSource.fromLegacyContext(
                        player,
                        context
                );

        com.votri.combatkeepinv.core.death.DeathContext coreContext =
                deathService.createContext(
                        player,
                        source
                );

        DeathDecision decision =
                deathService.evaluate(
                        coreContext
                );

        return new DeathResult(
                decision.shouldKeepInventory()
                        ? InventoryPolicy.KEEP
                        : InventoryPolicy.DROP,
                decision.shouldKeepExperience(),
                decision.shouldKeepInventory() == false
                        && coreContext.wasPlayerCaused()
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

    private InventoryPolicy createDefaultInventoryPolicy() {
        boolean keepExperience =
                plugin.getConfig()
                        .getBoolean(
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

    private boolean isValidPair(
            UUID attacker,
            UUID victim
    ) {
        return attacker != null
                && victim != null
                && !attacker.equals(victim);
    }
}
