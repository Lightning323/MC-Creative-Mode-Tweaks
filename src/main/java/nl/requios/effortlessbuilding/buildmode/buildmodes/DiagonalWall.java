package nl.requios.effortlessbuilding.buildmode.buildmodes;

import java.util.ArrayList;
import java.util.List;
import nl.requios.effortlessbuilding.buildmode.ThreeClicksBuildMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

public class DiagonalWall extends ThreeClicksBuildMode {
   public static List<BlockPos> getDiagonalWallBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, int x3, int y3, int z3) {
      List<BlockPos> diagonalLineBlocks = DiagonalLine.getDiagonalLineBlocks(player, x1, y1, z1, x2, y2, z2, 1.0F);
      int lowest = Math.min(y1, y3);
      int highest = Math.max(y1, y3);
      // Exact: the diagonal is extruded over the full height.
      List<BlockPos> list = new ArrayList<>((int) Math.min((long) diagonalLineBlocks.size() * (highest - lowest + 1L), 131072));

      for(int y = lowest; y <= highest; ++y) {
         for(BlockPos blockPos : diagonalLineBlocks) {
            list.add(new BlockPos(blockPos.getX(), y, blockPos.getZ()));
         }
      }

      return list;
   }

   protected BlockPos findSecondPos(Player player, BlockPos firstPos, boolean skipRaytrace) {
      return Floor.findFloor(player, firstPos, skipRaytrace);
   }

   protected boolean supportsTwoPointBuild() {
      return true;
   }

   protected BlockPos findThirdPos(Player player, BlockPos firstPos, BlockPos secondPos, boolean skipRaytrace) {
      return findHeight(player, secondPos, skipRaytrace);
   }

   protected List<BlockPos> getIntermediateBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2) {
      return DiagonalLine.getDiagonalLineBlocks(player, x1, y1, z1, x2, y2, z2, 1.0F);
   }

   protected List<BlockPos> getFinalBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, int x3, int y3, int z3) {
      return getDiagonalWallBlocks(player, x1, y1, z1, x2, y2, z2, x3, y3, z3);
   }
}
