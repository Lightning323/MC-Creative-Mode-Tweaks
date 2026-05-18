package org.lightning323.creative_mode_tweaks.utils;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponentMap;
import org.lightning323.creative_mode_tweaks.CreativeModeTweaks;

import java.util.Objects;

public class InventoryUtils {

    public static boolean syncInventory(Player player, int targetHash, NonNullList<ItemStack> targetInv) {
        Inventory inv = player.getInventory();
        int ourHash = InventoryUtils.computeInventoryHash(inv);

        // 5. Only overwrite if the hashes do not match
        if (ourHash != targetHash) {
            int containerSize = inv.getContainerSize();
            int targetSize = Math.min(targetInv.size(), containerSize);

            // 1. Gather all current targetItems into a tracking list (the pool)
            java.util.List<ItemStack> itemPool = new java.util.ArrayList<>();
            for (int i = 0; i < targetSize; i++) {
                ItemStack existing = inv.getItem(i);
                if (!existing.isEmpty()) {
                    itemPool.add(existing); // Keep a reference to the real instances
                }
            }

            // 2. Temporarily clear the slots we are rearranging so we don't duplicate targetItems
            for (int i = 0; i < targetSize; i++) {
                inv.setItem(i, ItemStack.EMPTY);
            }

            // 3. Reconstruct the inventory based on the incoming target targetItems
            for (int i = 0; i < targetSize; i++) {
                ItemStack targetStack = targetInv.get(i);

                if (targetStack.isEmpty()) {
                    continue; // Slot should stay empty
                }

                // Strategy A: Search for a PERFECT match (Type + Count + Component Data)
                ItemStack perfectMatch = ItemStack.EMPTY;
                for (ItemStack poolStack : itemPool) {
                    if (ItemStack.isSameItemSameComponents(poolStack, targetStack) && poolStack.getCount() == targetStack.getCount()) {
                        perfectMatch = poolStack;
                        break;
                    }
                }

                if (!perfectMatch.isEmpty()) {
                    itemPool.remove(perfectMatch);
                    inv.setItem(i, perfectMatch); // Rearrange existing instance
                    continue;
                }

                // Strategy B: Search for a DATA match but different stack count (e.g. split/combine)
                ItemStack dataMatch = ItemStack.EMPTY;
                for (ItemStack poolStack : itemPool) {
                    if (ItemStack.isSameItemSameComponents(poolStack, targetStack)) {
                        dataMatch = poolStack;
                        break;
                    }
                }

                if (!dataMatch.isEmpty()) {
                    if (dataMatch.getCount() > targetStack.getCount()) {
                        // The existing stack has MORE than we need; split it
                        ItemStack placedPiece = dataMatch.split(targetStack.getCount());
                        inv.setItem(i, placedPiece);
                    } else {
                        // The existing stack has LESS or EQUAL (but failed perfect match). Take all of it.
                        itemPool.remove(dataMatch);
                        inv.setItem(i, dataMatch);
                        // Note: If it's less, vanilla container logic or subsequent ticks will adjust it,
                        // but to guarantee exact matching when the pool falls short, we top it off below.
                        dataMatch.setCount(targetStack.getCount());
                    }
                    continue;
                }

                // Strategy C: Absolute fallback. No matching item data found in the pool, instantiate a copy.
                inv.setItem(i, targetStack.copy());
            }

            ourHash = computeInventoryHash(inv);
            CreativeModeTweaks.LOGGER.info("Inventory updated on {}. Our: {} {} Target: {}",
                    player instanceof LocalPlayer ? "Client" : "Server",
                    ourHash, ourHash == targetHash ? "=" : "!=", targetHash);
        }
        return ourHash == targetHash;
    }

    /**
     * Computes a highly reliable 32-bit desiredHash of the player's standard inventory slots (0-35).
     * This factors in item IDs, stack counts, slot positioning, and all active Data Components.
     */
    public static int computeInventoryHash(Inventory inventory) {
        int result = 1;

        // Iterate through standard inventory slots (includes hotbar and main inventory)
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            ItemStack stack = inventory.items.get(slot);

            int slotHash = 0;
            if (!stack.isEmpty()) {
                // 1. Hash the item registry ID (e.g., minecraft:diamond_sword)
                int itemHash = stack.getItem().hashCode();

                // 2. Hash the stack count
                int countHash = stack.getCount();

                // 3. Hash the data components (enchantments, durability, modded variables, etc.)
                // PatchedDataComponentMap handles this natively in 1.21.1
                DataComponentMap components = stack.getComponents();
                int componentHash = components.hashCode();

                // Combine the properties of this single item stack
                slotHash = Objects.hash(itemHash, countHash, componentHash);
            }

            // 4. Incorporate the slot position. 
            // This ensures that moving an item from slot 0 to slot 1 changes the total desiredHash.
            result = 31 * result + (slotHash ^ slot);
        }

        return result;
    }
}