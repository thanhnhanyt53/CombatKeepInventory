package com.votri.combatkeepinv;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class InventoryJournal {

    public enum State {
        SNAPSHOTTED,
        PROCESSING,
        PROCESSED,
        JOIN_PENDING
    }

    private final UUID uuid;

    private final ItemStack[] storage;
    private final ItemStack[] armor;
    private final ItemStack[] extra;

    private final Location location;

    private final long createdAt;

    private State state;

    public InventoryJournal(
            UUID uuid,
            ItemStack[] storage,
            ItemStack[] armor,
            ItemStack[] extra,
            Location location
    ) {

        this(
                uuid,
                storage,
                armor,
                extra,
                location,
                System.currentTimeMillis()
        );
    }

    public InventoryJournal(
            UUID uuid,
            ItemStack[] storage,
            ItemStack[] armor,
            ItemStack[] extra,
            Location location,
            long createdAt
    ) {

        this.uuid = uuid;

        this.storage =
                cloneItems(
                        storage
                );

        this.armor =
                cloneItems(
                        armor
                );

        this.extra =
                cloneItems(
                        extra
                );

        this.location =
                location == null
                        ? null
                        : location.clone();

        this.createdAt =
                createdAt > 0L
                        ? createdAt
                        : System.currentTimeMillis();

        this.state =
                State.SNAPSHOTTED;
    }

    public UUID getUuid() {
        return uuid;
    }

    public ItemStack[] getStorage() {
        return cloneItems(storage);
    }

    public ItemStack[] getArmor() {
        return cloneItems(armor);
    }

    public ItemStack[] getExtra() {
        return cloneItems(extra);
    }

    public Location getLocation() {

        return location == null
                ? null
                : location.clone();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public State getState() {
        return state;
    }

    public void setState(
            State state
    ) {

        if (state == null) {
            return;
        }

        this.state = state;
    }

    private static ItemStack[] cloneItems(
            ItemStack[] source
    ) {

        if (source == null) {
            return new ItemStack[0];
        }

        ItemStack[] result =
                new ItemStack[
                        source.length
                ];

        for (
                int i = 0;
                i < source.length;
                i++
        ) {

            ItemStack item =
                    source[i];

            result[i] =
                    item == null
                            ? null
                            : item.clone();
        }

        return result;
    }
}