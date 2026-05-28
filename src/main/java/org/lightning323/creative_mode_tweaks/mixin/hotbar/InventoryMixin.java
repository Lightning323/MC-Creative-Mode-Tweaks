package org.lightning323.creative_mode_tweaks.mixin.hotbar;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.lightning323.creative_mode_tweaks.hotbar.HotbarUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static org.lightning323.creative_mode_tweaks.hotbar.HotbarUtil.enableEnhancedHotbar;

@Mixin(Inventory.class)
public abstract class InventoryMixin {
    @Shadow
    public int selected;
    @Shadow
    @Final
    public Player player;
    @Shadow
    @Final
    public NonNullList<ItemStack> items;

    /**
     * Replaces the constant 9 with INVENTORY_SIZE only if in creative.
     */
//    @ModifyConstant(method = "isHotbarSlot", constant = @Constant(intValue = 9))
//    private static int isHotbarSlotMixin(int constant) {
//        return Inventory.INVENTORY_SIZE;
//    }

    // Redirect the isHotbarSlot check inside getSelected()
    @Redirect(
            method = "getSelected()Lnet/minecraft/world/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;isHotbarSlot(I)Z")
    )
    private boolean redirectIsHotbarSlotInGetSelected(int index) {
        int maxHotbar = HotbarUtil.getHotbarSize(player);
        return index >= 0 && index < maxHotbar;
    }

    // Redirect the isHotbarSlot check inside setPickedItem()
    @Redirect(
            method = "setPickedItem(Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;isHotbarSlot(I)Z")
    )
    private boolean redirectIsHotbarSlotInSetPickedItem(int index) {
        int maxHotbar = HotbarUtil.getHotbarSize(player);
        return index >= 0 && index < maxHotbar;
    }

    /**
     * Injects at the head of swapPaint to implement custom scroll logic.
     * We cancel the original method to prevent it from resetting our 'selected' value.
     */
    @Inject(method = "swapPaint", at = @At("HEAD"), cancellable = true)
    public void onSwapPaint(double pDirection, CallbackInfo ci) {
        if (enableEnhancedHotbar(player)) {
            int i = (int) Math.signum(pDirection);
            int max = Inventory.INVENTORY_SIZE;

            this.selected -= i;
            while (this.selected < 0) this.selected += max;
            while (this.selected >= max) this.selected -= max;

            ci.cancel(); // Stop the original method from running
        }
    }

    /**
     * Changes the selection size constant.
     */
    @Inject(method = "getSelectionSize", at = @At("HEAD"), cancellable = true)
    private static void onGetSelectionSize(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(HotbarUtil.INVENTORY_SIZE);
    }


    /**
     * Redirects the logic for finding a suitable slot.
     */
    @Inject(method = "getSuitableHotbarSlot", at = @At("HEAD"), cancellable = true)
    public void onGetSuitableHotbarSlot(CallbackInfoReturnable<Integer> cir) {
        if (enableEnhancedHotbar(player)) {
            int max = Inventory.INVENTORY_SIZE;

            // Check for empty slots
            for (int i = 0; i < max; ++i) {
                int j = (this.selected + i) % max;
                if (this.items.get(j).isEmpty()) {
                    cir.setReturnValue(j);
                    return;
                }
            }

            // Check for replaceable slots
            for (int k = 0; k < max; ++k) {
                int l = (this.selected + k) % max;
                if (!this.items.get(l).isNotReplaceableByPickAction(this.player, l)) {
                    cir.setReturnValue(l);
                    return;
                }
            }

            cir.setReturnValue(this.selected);
        }
    }
}
