package com.votri.combatkeepinv.core.internal;

import com.votri.combatkeepinv.core.combat.CombatSession;
import com.votri.combatkeepinv.core.damage.DamageSource;
import com.votri.combatkeepinv.core.death.DeathContext;
import com.votri.combatkeepinv.core.death.DeathReason;
import com.votri.combatkeepinv.core.platform.PlatformInfo;

import java.util.Optional;
import java.util.UUID;

public final class DefaultDeathContext
        implements DeathContext {

    private final UUID playerId;
    private final long timestamp;
    private final DamageSource damageSource;
    private final DeathReason deathReason;
    private final UUID killerId;
    private final Optional<CombatSession> combatSession;
    private final boolean playerCaused;
    private final boolean directPvP;
    private final boolean indirectPvP;
    private final PlatformInfo platform;

    public DefaultDeathContext(
            UUID playerId,
            long timestamp,
            DamageSource damageSource,
            DeathReason deathReason,
            UUID killerId,
            Optional<CombatSession> combatSession,
            boolean playerCaused,
            boolean directPvP,
            boolean indirectPvP,
            PlatformInfo platform
    ) {
        this.playerId = playerId;
        this.timestamp = timestamp;
        this.damageSource = damageSource;
        this.deathReason = deathReason;
        this.killerId = killerId;
        this.combatSession = combatSession;
        this.playerCaused = playerCaused;
        this.directPvP = directPvP;
        this.indirectPvP = indirectPvP;
        this.platform = platform;
    }

    @Override
    public UUID getPlayerId() {
        return playerId;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public DamageSource getDamageSource() {
        return damageSource;
    }

    @Override
    public DeathReason getDeathReason() {
        return deathReason;
    }

    @Override
    public Optional<UUID> getKillerId() {
        return Optional.ofNullable(killerId);
    }

    @Override
    public Optional<CombatSession> getCombatSession() {
        return combatSession;
    }

    @Override
    public boolean wasInCombat() {
        return combatSession
                .map(CombatSession::isActive)
                .orElse(false);
    }

    @Override
    public boolean wasPlayerCaused() {
        return playerCaused;
    }

    @Override
    public boolean isDirectPvP() {
        return directPvP;
    }

    @Override
    public boolean isIndirectPvP() {
        return indirectPvP;
    }

    @Override
    public PlatformInfo getPlatform() {
        return platform;
    }
}