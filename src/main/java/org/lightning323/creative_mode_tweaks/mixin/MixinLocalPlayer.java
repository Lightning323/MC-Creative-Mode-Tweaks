package org.lightning323.creative_mode_tweaks.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.world.item.ItemStack;
import org.lightning323.creative_mode_tweaks.Config;
import org.lightning323.creative_mode_tweaks.network.packets.DeleteItemPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public class MixinLocalPlayer {

    @Unique
    private boolean isImportant(ItemStack stack) {
        // 1. Protection against Modded Data (Frequencies, Links, Settings)
        // Most mods use CUSTOM_DATA to store their custom logic/links.
        if (stack.has(DataComponents.CUSTOM_DATA)) return true;

        // 2. Protection against "Container" targetItems
        // If it's a backpack, a crate, or a bundle, it has the BUNDLE_CONTENTS component.
        if (stack.has(DataComponents.BUNDLE_CONTENTS) || stack.has(DataComponents.CONTAINER)) return true;

        // 3. Protection against "Linked" targetItems (Compasses, Lodestones, Maps)
        // Redstone controllers often mimic the Map or Compass logic.
        if (stack.has(DataComponents.LODESTONE_TRACKER) || stack.has(DataComponents.MAP_ID)) return true;

        // 4. Protection against Tools/Gear with Progress
        if (stack.isEnchanted() || stack.has(DataComponents.CUSTOM_NAME)) return true;

        // 5. Check the Item Class/Tag
        // Many redstone mods tag their targetItems. We can check if the item is not "Simple"
        if (stack.getItem().isComplex()) return true;

        return false;
    }

    @Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true, remap = true)
    public void onDeleteItemOnDrop(boolean dropStack, CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        ItemStack heldItem = player.getInventory().getSelected();
        //Dont delete the item if it has custom data
        if (player.isCreative() && Config.enhanceCreativeHotbar && !isImportant(heldItem)) {
            cir.cancel();
            player.connection.send(new DeleteItemPayload(player.getInventory().selected));
            if (!heldItem.isEmpty()) {
                player.getInventory().setItem(player.getInventory().selected, ItemStack.EMPTY);
                cir.setReturnValue(true);
            }
            cir.setReturnValue(false);
        }
    }
}