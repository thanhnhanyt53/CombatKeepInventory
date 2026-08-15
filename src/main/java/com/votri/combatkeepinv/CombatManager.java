package com.votri.combatkeepinv;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatManager {

    private final Map<UUID, Long> combatUntil =
            new ConcurrentHashMap<>();

    private volatile long durationMillis;

    public CombatManager(
            long durationMillis
    ) {
        setDurationMillis(
                durationMillis
        );
    }

    public void setDurationMillis(
            long durationMillis
    ) {
        this.durationMillis =
                Math.max(
                        1000L,
                        durationMillis
                );
    }

    public void tag(UUID uuid) {

        if (uuid == null) {
            return;
        }

        combatUntil.put(
                uuid,
                System.currentTimeMillis()
                        + durationMillis
        );
    }

    public void tag(
            UUID first,
            UUID second
    ) {
        tag(first);
        tag(second);
    }

    public boolean isInCombat(
            UUID uuid
    ) {

        if (uuid == null) {
            return false;
        }

        Long until =
                combatUntil.get(uuid);

        if (until == null) {
            return false;
        }

        if (until <=
                System.currentTimeMillis()) {

            combatUntil.remove(
                    uuid,
                    until
            );

            return false;
        }

        return true;
    }

    public long getRemainingMillis(
            UUID uuid
    ) {

        if (uuid == null) {
            return 0L;
        }

        Long until =
                combatUntil.get(uuid);

        if (until == null) {
            return 0L;
        }

        long remaining =
                until -
                        System.currentTimeMillis();

        if (remaining <= 0L) {

            combatUntil.remove(
                    uuid,
                    until
            );

            return 0L;
        }

        return remaining;
    }

    public long getRemainingSeconds(
            UUID uuid
    ) {

        long millis =
                getRemainingMillis(uuid);

        if (millis <= 0L) {
            return 0L;
        }

        return (
                millis + 999L
        ) / 1000L;
    }

    public void remove(UUID uuid) {

        if (uuid != null) {
            combatUntil.remove(uuid);
        }
    }

    public void clear() {
        combatUntil.clear();
    }

    public int size() {
        cleanupExpired();
        return combatUntil.size();
    }

    public void cleanupExpired() {

        long now =
                System.currentTimeMillis();

        combatUntil.entrySet()
                .removeIf(
                        entry ->
                                entry.getValue()
                                        <= now
                );
    }
}