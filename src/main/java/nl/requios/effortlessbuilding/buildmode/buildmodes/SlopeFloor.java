package nl.requios.effortlessbuilding.buildmode.buildmodes;

import java.util.ArrayList;
import java.util.List;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import nl.requios.effortlessbuilding.buildmode.ThreeClicksBuildMode;
import org.lightning323.creative_mode_tweaks.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

public class SlopeFloor extends ThreeClicksBuildMode {
   public static List<BlockPos> getSlopeFloorBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, int x3, int y3, int z3) {
      List<BlockPos> list = new ArrayList();
      int axisLimit = Config.getBuildingMaxBlocksPerAxis(player);
      boolean onXAxis = true;
      int xLength = Math.abs(x2 - x1);
      int zLength = Math.abs(z2 - z1);
      if (ModeOptions.getRaisedEdge() == ModeOptions.ActionEnum.SHORT_EDGE) {
         if (zLength > xLength) {
            onXAxis = false;
         }
      } else if (zLength <= xLength) {
         onXAxis = false;
      }

      if (onXAxis) {
         List<BlockPos> diagonalLineBlocks = DiagonalLine.getDiagonalLineBlocks(player, x1, y1, z1, x2, y3, z1, 1.0F);
         int lowest = Math.min(z1, z2);
         int highest = Math.max(z1, z2);
         if (highest - lowest >= axisLimit) {
            highest = lowest + axisLimit - 1;
         }

         for(int z = lowest; z <= highest; ++z) {
            for(BlockPos blockPos : diagonalLineBlocks) {
               list.add(new BlockPos(blockPos.getX(), blockPos.getY(), z));
            }
         }
      } else {
         List<BlockPos> diagonalLineBlocks = DiagonalLine.getDiagonalLineBlocks(player, x1, y1, z1, x1, y3, z2, 1.0F);
         int lowest = Math.min(x1, x2);
         int highest = Math.max(x1, x2);
         if (highest - lowest >= axisLimit) {
            highest = lowest + axisLimit - 1;
         }

         for(int x = lowest; x <= highest; ++x) {
            for(BlockPos blockPos : diagonalLineBlocks) {
               list.add(new BlockPos(x, blockPos.getY(), blockPos.getZ()));
            }
         }
      }

      return list;
   }

   protected BlockPos findSecondPos(Player player, BlockPos firstPos, boolean skipRaytrace) {
      return Floor.findFloor(player, firstPos, skipRaytrace);
   }

   protected BlockPos findThirdPos(Player player, BlockPos firstPos, BlockPos secondPos, boolean skipRaytrace) {
      return findHeight(player, secondPos, skipRaytrace);
   }

   protected List<BlockPos> getIntermediateBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2) {
      return Floor.getFloorBlocks(player, x1, y1, z1, x2, y2, z2);
   }

   protected List<BlockPos> getFinalBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, int x3, int y3, int z3) {
      return getSlopeFloorBlocks(player, x1, y1, z1, x2, y2, z2, x3, y3, z3);
   }
}
