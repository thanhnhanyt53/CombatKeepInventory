package com.votri.combatkeepinv.bukkit.bridge;

import com.votri.combatkeepinv.core.api.DeathContext;
import com.votri.combatkeepinv.core.damage.DamageCauseType;
import com.votri.combatkeepinv.core.damage.DamageSource;

import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Optional;
import java.util.UUID;

/**
 * Bukkit implementation of the core DamageSource contract.
 */
public final class BukkitDamageSource
        implements DamageSource {

    private final DamageCauseType causeType;
    private final UUID directAttackerId;
    private final UUID responsiblePlayerId;
    private final UUID victimId;
    private final boolean playerCaused;
    private final boolean indirect;
    private final long timestamp;

    public BukkitDamageSource(
            DamageCauseType causeType,
            UUID directAttackerId,
            UUID responsiblePlayerId,
            UUID victimId,
            boolean playerCaused,
            boolean indirect,
            long timestamp
    ) {
        this.causeType =
                causeType == null
                        ? DamageCauseType.UNKNOWN
                        : causeType;

        this.directAttackerId =
                directAttackerId;

        this.responsiblePlayerId =
                responsiblePlayerId;

        this.victimId =
                victimId;

        this.playerCaused =
                playerCaused;

        this.indirect =
                indirect;

        this.timestamp =
                timestamp;
    }

    public static BukkitDamageSource environment(
            UUID victimId,
            EntityDamageEvent event
    ) {
        return new BukkitDamageSource(
                mapCause(
                        event == null
                                ? null
                                : event.getCause()
                ),
                null,
                null,
                victimId,
                false,
                false,
                System.currentTimeMillis()
        );
    }

    public static BukkitDamageSource fromLegacyContext(
            UUID victimId,
            DeathContext context
    ) {
        if (context == null) {
            return environment(
                    victimId,
                    null
            );
        }

        return switch (context) {

            case PLAYER ->
                    new BukkitDamageSource(
                            DamageCauseType.PLAYER,
                            null,
                            null,
                            victimId,
                            true,
                            false,
                            System.currentTimeMillis()
                    );

            case PROJECTILE ->
                    new BukkitDamageSource(
                            DamageCauseType.PROJECTILE,
                            null,
                            null,
                            victimId,
                            true,
                            true,
                            System.currentTimeMillis()
                    );

            case MOB ->
                    new BukkitDamageSource(
                            DamageCauseType.ENTITY,
                            null,
                            null,
                            victimId,
                            false,
                            false,
                            System.currentTimeMillis()
                    );

            case VOID ->
                    new BukkitDamageSource(
                            DamageCauseType.VOID,
                            null,
                            null,
                            victimId,
                            false,
                            false,
                            System.currentTimeMillis()
                    );

            case ENVIRONMENT ->
                    new BukkitDamageSource(
                            DamageCauseType.UNKNOWN,
                            null,
                            null,
                            victimId,
                            false,
                            false,
                            System.currentTimeMillis()
                    );

            case UNKNOWN ->
                    new BukkitDamageSource(
                            DamageCauseType.UNKNOWN,
                            null,
                            null,
                            victimId,
                            false,
                            false,
                            System.currentTimeMillis()
                    );
        };
    }

    /**
     * Creates a precise player attack source.
     */
    public static BukkitDamageSource playerAttack(
            UUID attackerId,
            UUID victimId
    ) {
        return new BukkitDamageSource(
                DamageCauseType.PLAYER,
                attackerId,
                attackerId,
                victimId,
                true,
                false,
                System.currentTimeMillis()
        );
    }

    /**
     * Creates a precise player projectile source.
     */
    public static BukkitDamageSource playerProjectile(
            UUID attackerId,
            UUID projectileId,
            UUID victimId
    ) {
        return new BukkitDamageSource(
                DamageCauseType.PROJECTILE,
                projectileId,
                attackerId,
                victimId,
                true,
                true,
                System.currentTimeMillis()
        );
    }

    private static DamageCauseType mapCause(
            EntityDamageEvent.DamageCause cause
    ) {
        if (cause == null) {
            return DamageCauseType.UNKNOWN;
        }

        return switch (cause) {
            case ENTITY_ATTACK ->
                    DamageCauseType.PLAYER;

            case PROJECTILE ->
                    DamageCauseType.PROJECTILE;

            case ENTITY_EXPLOSION ->
                    DamageCauseType.EXPLOSION;

            case BLOCK_EXPLOSION ->
                    DamageCauseType.EXPLOSION;

            case FIRE ->
                    DamageCauseType.FIRE;

            case FIRE_TICK ->
                    DamageCauseType.FIRE_TICK;

            case FALL ->
                    DamageCauseType.FALL;

            case VOID ->
                    DamageCauseType.VOID;

            case LAVA ->
                    DamageCauseType.LAVA;

            case DROWNING ->
                    DamageCauseType.DROWNING;

            case MAGIC ->
                    DamageCauseType.MAGIC;

            case POISON ->
                    DamageCauseType.POISON;

            case WITHER ->
                    DamageCauseType.WITHER;

            case STARVATION ->
                    DamageCauseType.STARVATION;

            case SUFFOCATION ->
                    DamageCauseType.SUFFOCATION;

            case LIGHTNING ->
                    DamageCauseType.LIGHTNING;

            default ->
                    DamageCauseType.UNKNOWN;
        };
    }

    @Override
    public DamageCauseType getCauseType() {
        return causeType;
    }

    @Override
    public Optional<UUID> getDirectAttackerId() {
        return Optional.ofNullable(
                directAttackerId
        );
    }

    @Override
    public Optional<UUID> getResponsiblePlayerId() {
        return Optional.ofNullable(
                responsiblePlayerId
        );
    }

    @Override
    public Optional<UUID> getVictimId() {
        return Optional.ofNullable(
                victimId
        );
    }

    @Override
    public boolean isPlayerCaused() {
        return playerCaused;
    }

    @Override
    public boolean isIndirect() {
        return indirect;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }
}