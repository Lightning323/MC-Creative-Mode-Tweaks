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
   private boolean angelPlacement;

   public BuildSettings() {
      this.replaceMode = ReplaceMode.ONLY_AIR;
      this.angelPlacement = false;
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

   public boolean toggleAngelPlacement() {
      this.angelPlacement = !this.angelPlacement;
      return this.angelPlacement;
   }

   public boolean isAngelPlacementEnabled() {
      return this.angelPlacement;
   }

   public ModeOptions.ActionEnum getReplaceModeActionEnum() {
      ModeOptions.ActionEnum var10000;
      switch (this.getReplaceMode().ordinal()) {
         case 0 -> var10000 = ModeOptions.ActionEnum.REPLACE_ONLY_AIR;
         case 1 -> var10000 = ModeOptions.ActionEnum.REPLACE_BLOCKS_AND_AIR;
         case 2 -> var10000 = ModeOptions.ActionEnum.REPLACE_ONLY_BLOCKS;
         case 3 -> var10000 = ModeOptions.ActionEnum.REPLACE_FILTERED_BY_OFFHAND;
         case 4 -> var10000 = ModeOptions.ActionEnum.REPLACE_FLOOD_FILL;
         default -> throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   public boolean shouldOffsetStartPosition() {
      // Flood fill resolves its start like normal block placement (in place
      // for replaceables, adjacent for solids) so the shape starts next to
      // the hit block instead of inside it. All other modes unchanged.
      ReplaceMode mode = this.getReplaceMode();
      return mode != ReplaceMode.ONLY_AIR && mode != ReplaceMode.FLOOD_FILL;
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
         case 4:
            // Flood fill gates on air/liquid connectivity (applied separately
            // by FloodFill): only connected air and liquid is replaced, solid
            // blocks never pass the fill.
            var10000 = true;
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
      FILTERED_BY_OFFHAND,
      FLOOD_FILL;

      // $FF: synthetic method
      private static ReplaceMode[] $values() {
         return new ReplaceMode[]{ONLY_AIR, BLOCKS_AND_AIR, ONLY_BLOCKS, FILTERED_BY_OFFHAND, FLOOD_FILL};
      }
   }
}
