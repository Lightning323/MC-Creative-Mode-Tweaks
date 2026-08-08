package org.lightning323.creative_mode_tweaks.mixin;

import net.minecraft.client.Minecraft;
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
        if (stack.isEmpty()) {
            return false;
        }

        // Protect items that contain actual custom/modded data.
        var customData = stack.get(DataComponents.CUSTOM_DATA);

        if (customData != null) {
            var tag = customData.copyTag();

            // Create-linked items store their configuration/link data here.
            if (!tag.isEmpty()) {
                return true;
            }
        }

        // Vanilla items with persistent state.
        if (stack.has(DataComponents.BUNDLE_CONTENTS)
                || stack.has(DataComponents.CONTAINER)
                || stack.has(DataComponents.LODESTONE_TRACKER)
                || stack.has(DataComponents.MAP_ID)) {
            return true;
        }

        // Don't delete enchanted/named gear.
        if (stack.isEnchanted() || stack.has(DataComponents.CUSTOM_NAME)) {
            return true;
        }
        return false;
    }

    @Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true, remap = true)
    public void onDeleteItemOnDrop(boolean dropStack, CallbackInfoReturnable<Boolean> cir) {

        // Only use the enhanced deletion when Shift is held.
        if (!Minecraft.getInstance().options.keyShift.isDown()) {
            return;
        }
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