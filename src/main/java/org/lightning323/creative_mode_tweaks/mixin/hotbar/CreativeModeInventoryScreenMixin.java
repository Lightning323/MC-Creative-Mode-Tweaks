//package org.lightning323.creative_mode_tweaks.mixin.hotbar;
//
//import net.minecraft.client.Minecraft;
//import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
//import net.minecraft.world.inventory.Slot;
//import org.spongepowered.asm.mixin.Mixin;
//import org.spongepowered.asm.mixin.injection.At;
//import org.spongepowered.asm.mixin.injection.Redirect;
//
//@Mixin(CreativeModeInventoryScreen.class)
//public abstract class CreativeModeInventoryScreenMixin {
////    Slots 0–4: The Crafting Grid + Result (5 slots).
////    Slots 5–8: The Armor Slots (4 slots).
////    Slots 9–35: The Main Inventory ($3 \times 9 = 27$ slots).
////    Slots 36–44: The Hotbar (9 slots).
////    Slot 45: The Offhand slot.
//
//
//    @Redirect(
//            method = "selectTab",
//            at = @At(
//                    value = "NEW",
//                    target = "(Lnet/minecraft/world/inventory/Slot;III)Lnet/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen$SlotWrapper;"
//            )
//    )
//    private CreativeModeInventoryScreen.SlotWrapper bypassSlotWrapper(Slot originalTarget, int index, int x, int y) {
//        var player = Minecraft.getInstance().player;
//
//        // index is 'k' from the loop (0-45)
//        // 36-44 is the Hotbar area at the bottom of the screen
//        if (player != null && player.isCreative() && index >= 36 && index <= 44) {
//
//            // originalTarget is the player's hotbar (0-8)
//            // We want to pull from the 2nd row of the inventory (9-17)
//            int secondRowIndex = originalTarget.getContainerSlot() + 9;
//
//            if (secondRowIndex < player.inventoryMenu.slots.size()) {
//                Slot rowTwoSlot = player.inventoryMenu.slots.get(secondRowIndex);
//
//                // Return a STANDARD Slot instead of a SlotWrapper
//                // This circumvents the inner class entirely
//                return new CreativeModeInventoryScreen.SlotWrapper(rowTwoSlot.container, rowTwoSlot.getContainerSlot(), x, y);
//            }
//        }
//
//        // For all other slots (armor, inventory rows), return a standard slot too
//        return new CreativeModeInventoryScreen.SlotWrapper(originalTarget.container, originalTarget.getContainerSlot(), x, y);
//    }
//}