package org.lightning323.creative_mode_tweaks.mixin.hotbar;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin({Minecraft.class})
public class MinecraftMixin {
   @ModifyConstant(
      method = {"pickBlock"},
      constant = {@Constant(
   intValue = 36
)}
   )
   public int fromInventorySlotToMenuSlot(int value, @Local Inventory inventory) {
      return inventory.selected >= 9 ? 0 : 36;
   }
}
