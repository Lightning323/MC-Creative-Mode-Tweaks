package org.lightning323.creative_mode_tweaks.mixin.hotbar;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lightning323.creative_mode_tweaks.utils.HotbarUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin({Inventory.class})
public class InventoryMixin {
   @Shadow
   public int selected;
   @Shadow
   @Final
   public Player player;
   @Shadow
   @Final
   public NonNullList<ItemStack> items;

   @ModifyConstant(
      method = {"isHotbarSlot"},
      constant = {@Constant(
   intValue = 9
)}
   )
   private static int hotBaaaarAllSelectable(int constant) {
      return 36;
   }

   @Overwrite
   public void swapPaint(double pDirection) {
      int i = (int)Math.signum(pDirection);
      int max = 9;
      if (this.player.level().isClientSide) {
         max = this.hotBaaaarGetHotbarAmount() * 9;
         i *= this.hotBaaaarGetScrollStep();
      }

      for(this.selected -= i; this.selected < 0; this.selected += max) {
      }

      while(this.selected >= max) {
         this.selected -= max;
      }

   }

   @Unique
   @OnlyIn(Dist.CLIENT)
   private int hotBaaaarGetHotbarAmount() {
      return HotbarUtil.getBarCount(Minecraft.getInstance().getWindow().getGuiScaledWidth());
   }


   @Unique
   @OnlyIn(Dist.CLIENT)
   private int hotBaaaarGetScrollStep() {
      return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), 342) ? this.hotBaaaarGetHotbarAmount() : 1;
   }

   @Overwrite
   public static int getSelectionSize() {
      return 36;
   }

   @Overwrite
   public int getSuitableHotbarSlot() {
      int max = 9;
      if (this.player.level().isClientSide && this.player.level().isClientSide) {
         max = this.hotBaaaarGetHotbarAmount() * 9;
      }

      for(int i = 0; i < max; ++i) {
         int j = (this.selected + i) % max;
         if (((ItemStack)this.items.get(j)).isEmpty()) {
            return j;
         }
      }

      for(int k = 0; k < max; ++k) {
         int l = (this.selected + k) % max;
         if (!((ItemStack)this.items.get(l)).isNotReplaceableByPickAction(this.player, l)) {
            return l;
         }
      }

      return this.selected;
   }
}
