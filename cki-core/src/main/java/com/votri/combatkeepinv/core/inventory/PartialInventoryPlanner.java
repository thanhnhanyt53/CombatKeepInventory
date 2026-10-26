package com.votri.combatkeepinv.core.inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Computes, without touching anything, which items a
 * {@link InventoryAction#PARTIAL} policy keeps and which it drops.
 *
 * <p>It is generic over the item type and never mutates its
 * inputs. The Bukkit layer feeds it {@code ItemStack} arrays and
 * then applies the returned plan in a single step, instead of
 * editing the live inventory item by item while building the drop
 * list. That ordering is what makes the inventory protection
 * possible: the plan is fully computed (and can be checked) before
 * anything is changed, so a failure can never leave an item both
 * in the inventory and in the drops, or in neither.</p>
 *
 * <p>Guarantees, for every non-empty input item:</p>
 * <ul>
 *   <li>it ends up in exactly one place: its original slot in the
 *       returned arrays, or in {@link Plan#drops()}</li>
 *   <li>a kept item never changes slot</li>
 *   <li>an item is dropped at most once</li>
 * </ul>
 *
 * <p>Slot layout follows Bukkit's {@code getStorageContents()}:
 * indexes 0-8 are the hotbar (governed by {@code keepHotbar}), the
 * rest is the main inventory (governed by
 * {@code keepMainInventory}).</p>
 */
public final class PartialInventoryPlanner {

    /** Number of hotbar slots at the start of the storage array. */
    public static final int HOTBAR_SIZE = 9;

    private PartialInventoryPlanner() {
    }

    /**
     * @param storage new storage contents (kept items stay in place)
     * @param armor   new armor contents
     * @param extra   new extra (offhand) contents
     * @param drops   items to drop, in storage, armor, extra order
     */
    public record Plan<T>(
            T[] storage,
            T[] armor,
            T[] extra,
            List<T> drops
    ) {

        /** Number of items the plan drops. */
        public int dropCount() {
            return drops.size();
        }
    }

    /**
     * @param isEmpty returns true for null / air / empty stacks,
     *                which are left exactly as they are
     * @param copy    copies an item for the drop list so the
     *                dropped object is not shared with the arrays
     */
    public static <T> Plan<T> plan(
            T[] storage,
            T[] armor,
            T[] extra,
            InventoryPolicy policy,
            Predicate<T> isEmpty,
            UnaryOperator<T> copy
    ) {

        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(isEmpty, "isEmpty");
        Objects.requireNonNull(copy, "copy");

        T[] newStorage =
                storage == null ? null : storage.clone();

        T[] newArmor =
                armor == null ? null : armor.clone();

        T[] newExtra =
                extra == null ? null : extra.clone();

        List<T> drops =
                new ArrayList<>();

        if (newStorage != null) {

            for (int index = 0;
                 index < newStorage.length;
                 index++) {

                T item =
                        newStorage[index];

                if (isEmpty.test(item)) {
                    continue;
                }

                boolean hotbar =
                        index < HOTBAR_SIZE;

                boolean keep =
                        hotbar
                                ? policy.keepHotbar()
                                : policy.keepMainInventory();

                if (!keep) {
                    drops.add(copy.apply(item));
                    newStorage[index] = null;
                }
            }
        }

        dropAll(
                newArmor,
                policy.keepArmor(),
                isEmpty,
                copy,
                drops
        );

        dropAll(
                newExtra,
                policy.keepOffhand(),
                isEmpty,
                copy,
                drops
        );

        return new Plan<>(
                newStorage,
                newArmor,
                newExtra,
                Collections.unmodifiableList(drops)
        );
    }

    private static <T> void dropAll(
            T[] slots,
            boolean keep,
            Predicate<T> isEmpty,
            UnaryOperator<T> copy,
            List<T> drops
    ) {

        if (slots == null
                || keep) {
            return;
        }

        for (int index = 0;
             index < slots.length;
             index++) {

            T item =
                    slots[index];

            if (isEmpty.test(item)) {
                continue;
            }

            drops.add(copy.apply(item));
            slots[index] = null;
        }
    }
}
