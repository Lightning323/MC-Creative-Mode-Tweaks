package org.lightning323.creative_mode_tweaks.utils;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponentMap;
import java.util.Objects;

public class InventoryHasher {

    /**
     * Computes a highly reliable 32-bit hash of the player's standard inventory slots (0-35).
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
            // This ensures that moving an item from slot 0 to slot 1 changes the total hash.
            result = 31 * result + (slotHash ^ slot);
        }

        return result;
    }
}