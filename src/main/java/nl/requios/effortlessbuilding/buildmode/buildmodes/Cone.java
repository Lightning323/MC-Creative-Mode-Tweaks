package nl.requios.effortlessbuilding.buildmode.buildmodes;

import java.util.List;
import nl.requios.effortlessbuilding.buildmode.ThreeClicksBuildMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

public class Cone extends ThreeClicksBuildMode {
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
      return SlopeFloor.getSlopeFloorBlocks(player, x1, y1, z1, x2, y2, z2, x3, y3, z3);
   }
}
