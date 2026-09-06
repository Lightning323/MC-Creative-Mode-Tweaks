package nl.requios.effortlessbuilding.buildmode.buildmodes;

import java.util.ArrayList;
import java.util.List;
import nl.requios.effortlessbuilding.buildmode.ThreeClicksBuildMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class DiagonalLine extends ThreeClicksBuildMode {
   public static List<BlockPos> getDiagonalLineBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, float sampleMultiplier) {
      List<BlockPos> list = new ArrayList();
      Vec3 first = (new Vec3((double)x1, (double)y1, (double)z1)).add((double)0.5F, (double)0.5F, (double)0.5F);
      Vec3 second = (new Vec3((double)x2, (double)y2, (double)z2)).add((double)0.5F, (double)0.5F, (double)0.5F);
      int iterations = (int)Math.ceil(first.distanceTo(second) * (double)sampleMultiplier);

      for(double t = (double)0.0F; t <= (double)1.0F; t += (double)1.0F / (double)iterations) {
         Vec3 lerp = first.add(second.subtract(first).scale(t));
         BlockPos candidate = BlockPos.containing(lerp);
         if (list.isEmpty() || !((BlockPos)list.get(list.size() - 1)).equals(candidate)) {
            list.add(candidate);
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
      return getDiagonalLineBlocks(player, x1, y1, z1, x2, y2, z2, 10.0F);
   }

   protected List<BlockPos> getFinalBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, int x3, int y3, int z3) {
      return getDiagonalLineBlocks(player, x1, y1, z1, x3, y3, z3, 10.0F);
   }
}
