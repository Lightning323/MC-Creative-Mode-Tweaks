package nl.requios.effortlessbuilding.buildmode;

import java.util.List;
import org.lightning323.creative_mode_tweaks.Config;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public abstract class TwoClicksBuildMode extends BaseBuildMode {
   protected BlockEntry firstBlockEntry;

   public void initialize() {
      super.initialize();
      this.firstBlockEntry = null;
   }

   public boolean onClick(BlockSet blocks, BlockPos clickedPos, Player player) {
      super.onClick(blocks, clickedPos, player);
      if (this.clicks == 1) {
         this.firstBlockEntry = new BlockEntry(clickedPos);
         return false;
      } else {
         return true;
      }
   }

   public void findCoordinates(BlockSet blocks, Player player) {
      ShapeFrame frame = describeShape(player);
      if (frame == null) {
         return;
      }
      blocks.clear();

      for(BlockPos pos : expandShape(player, frame)) {
         if (!blocks.containsKey(pos)) {
            blocks.add(new BlockEntry(pos));
         }
      }

      blocks.firstPos = frame.first();
      blocks.lastPos = frame.second() != null ? frame.second() : frame.first();
   }

   /**
    * O(1) coordinate calculation: resolve + clamp the second anchor only.
    * Never enumerates blocks.
    */
   @Override
   public @Nullable ShapeFrame describeShape(Player player) {
      if (this.clicks == 0 || this.firstBlockEntry == null) {
         return null;
      }
      BlockPos firstPos = this.firstBlockEntry.blockPos;
      BlockPos secondPos = this.findSecondPos(player, this.firstBlockEntry.blockPos, true);
      if (secondPos == null) {
         return null;
      }
      int axisLimit = Config.getBuildingMaxBlocksPerAxis(player);
      BlockPos bounded = clampPair(firstPos, secondPos, axisLimit);
      return ShapeFrame.pair(firstPos, bounded, axisLimit,
            ModeOptions.getFill(), ModeOptions.getCubeFill(), ModeOptions.getSides(),
            ModeOptions.getCircleStart(), false);
   }

   /**
    * O(N) block calculation: expand the resolved anchors into positions.
    * Pure integer math — safe to defer off the render thread.
    */
   @Override
   public List<BlockPos> expandShape(Player player, ShapeFrame frame) {
      if (frame.kind() != ShapeFrame.Kind.PAIR) {
         return List.of();
      }
      BlockPos firstPos = frame.first();
      BlockPos secondPos = frame.second();
      return this.getAllBlocks(player, firstPos.getX(), firstPos.getY(), firstPos.getZ(),
            secondPos.getX(), secondPos.getY(), secondPos.getZ());
   }

   static BlockPos clampPair(BlockPos firstPos, BlockPos secondPos, int axisLimit) {
      int x2 = clampAxis(secondPos.getX(), firstPos.getX(), axisLimit);
      int y2 = clampAxis(secondPos.getY(), firstPos.getY(), axisLimit);
      int z2 = clampAxis(secondPos.getZ(), firstPos.getZ(), axisLimit);
      return new BlockPos(x2, y2, z2);
   }

   static int clampAxis(int point, int origin, int axisLimit) {
      if (point - origin >= axisLimit) {
         return origin + axisLimit - 1;
      }
      if (origin - point >= axisLimit) {
         return origin - axisLimit + 1;
      }
      return point;
   }

   public List<BlockPos> getServerBlocks(Player player, BlockPos firstPos, BlockPos secondPos, @Nullable BlockPos thirdPos, @Nullable BlockPos fourthPos) {
      int axisLimit = Config.getBuildingMaxBlocksPerAxis(player);
      int x1 = firstPos.getX();
      int x2 = secondPos.getX();
      int y1 = firstPos.getY();
      int y2 = secondPos.getY();
      int z1 = firstPos.getZ();
      int z2 = secondPos.getZ();
      if (x2 - x1 >= axisLimit) {
         x2 = x1 + axisLimit - 1;
      }

      if (x1 - x2 >= axisLimit) {
         x2 = x1 - axisLimit + 1;
      }

      if (y2 - y1 >= axisLimit) {
         y2 = y1 + axisLimit - 1;
      }

      if (y1 - y2 >= axisLimit) {
         y2 = y1 - axisLimit + 1;
      }

      if (z2 - z1 >= axisLimit) {
         z2 = z1 + axisLimit - 1;
      }

      if (z1 - z2 >= axisLimit) {
         z2 = z1 - axisLimit + 1;
      }

      return this.getAllBlocks(player, x1, y1, z1, x2, y2, z2);
   }

   protected abstract BlockPos findSecondPos(Player var1, BlockPos var2, boolean var3);

   protected abstract List<BlockPos> getAllBlocks(Player var1, int var2, int var3, int var4, int var5, int var6, int var7);
}
