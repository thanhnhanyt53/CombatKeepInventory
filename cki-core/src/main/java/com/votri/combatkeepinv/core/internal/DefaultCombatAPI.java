package com.votri.combatkeepinv.core.internal;

import com.votri.combatkeepinv.core.api.CombatAPI;
import com.votri.combatkeepinv.core.combat.CombatReason;
import com.votri.combatkeepinv.core.combat.CombatSession;
import com.votri.combatkeepinv.core.combat.CombatSessionManager;

import java.util.Optional;
import java.util.UUID;

public final class DefaultCombatAPI
        implements CombatAPI {

    private final CombatSessionManager manager;

    public DefaultCombatAPI(
            CombatSessionManager manager
    ) {
        this.manager = manager;
    }

    @Override
    public CombatSessionManager getSessionManager() {
        return manager;
    }

    @Override
    public Optional<CombatSession> getSession(
            UUID playerId
    ) {
        return manager.getSession(playerId);
    }

    @Override
    public boolean isInCombat(UUID playerId) {
        return manager.isInCombat(playerId);
    }

    @Override
    public CombatSession startCombat(
            UUID playerId
    ) {
        return manager.startCombat(playerId);
    }

    @Override
    public CombatSession startCombat(
            UUID playerId,
            UUID opponentId
    ) {
        return manager.startCombat(
                playerId,
                opponentId
        );
    }

    @Override
    public CombatSession refreshCombat(
            UUID playerId
    ) {
        return manager.refreshCombat(playerId);
    }

    @Override
    public CombatSession refreshCombat(
            UUID playerId,
            UUID opponentId
    ) {
        return manager.refreshCombat(
                playerId,
                opponentId
        );
    }

    @Override
    public boolean endCombat(
            UUID playerId,
            CombatReason reason
    ) {
        return manager.endCombat(
                playerId,
                reason
        );
    }
}