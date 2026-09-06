package nl.requios.effortlessbuilding.buildmode;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class BuildSettings {
   public static final BuildSettings CLIENT = new BuildSettings();
   private ReplaceMode replaceMode;

   public BuildSettings() {
      this.replaceMode = ReplaceMode.ONLY_AIR;
   }

   public void cycleReplaceMode() {
      ReplaceMode[] values = ReplaceMode.values();
      this.replaceMode = values[(this.replaceMode.ordinal() + 1) % values.length];
   }

   public void setReplaceMode(ReplaceMode mode) {
      this.replaceMode = mode;
   }

   public ReplaceMode getReplaceMode() {
      return !this.canReplace() ? ReplaceMode.ONLY_AIR : this.replaceMode;
   }

   public ModeOptions.ActionEnum getReplaceModeActionEnum() {
      ModeOptions.ActionEnum var10000;
      switch (this.getReplaceMode().ordinal()) {
         case 0 -> var10000 = ModeOptions.ActionEnum.REPLACE_ONLY_AIR;
         case 1 -> var10000 = ModeOptions.ActionEnum.REPLACE_BLOCKS_AND_AIR;
         case 2 -> var10000 = ModeOptions.ActionEnum.REPLACE_ONLY_BLOCKS;
         case 3 -> var10000 = ModeOptions.ActionEnum.REPLACE_FILTERED_BY_OFFHAND;
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   public boolean shouldOffsetStartPosition() {
      return this.getReplaceMode() != ReplaceMode.ONLY_AIR;
   }

   public static boolean canPlaceAt(Level level, BlockPos pos, ReplaceMode replaceMode, ItemStack offHandStack) {
      BlockState existing = level.getBlockState(pos);
      boolean var10000;
      switch (replaceMode.ordinal()) {
         case 0:
            var10000 = existing.canBeReplaced();
            break;
         case 1:
            var10000 = true;
            break;
         case 2:
            var10000 = !existing.isAir();
            break;
         case 3:
            if (existing.canBeReplaced()) {
               var10000 = true;
            } else if (offHandStack.isEmpty()) {
               var10000 = false;
            } else {
               Item existingBlockItem = existing.getBlock().asItem();
               var10000 = offHandStack.getItem() == existingBlockItem;
            }
            break;
         default:
            throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   private boolean canReplace() {
      Minecraft mc = Minecraft.getInstance();
      return mc.player != null;
   }

   public static enum ReplaceMode {
      ONLY_AIR,
      BLOCKS_AND_AIR,
      ONLY_BLOCKS,
      FILTERED_BY_OFFHAND;

      // $FF: synthetic method
      private static ReplaceMode[] $values() {
         return new ReplaceMode[]{ONLY_AIR, BLOCKS_AND_AIR, ONLY_BLOCKS, FILTERED_BY_OFFHAND};
      }
   }
}
