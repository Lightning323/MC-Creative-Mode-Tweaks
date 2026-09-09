package nl.requios.effortlessbuilding.buildmode.buildmodes;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.AABB;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import nl.requios.effortlessbuilding.buildmode.ThreeClicksBuildMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

public class Cylinder extends ThreeClicksBuildMode {
   public static List<BlockPos> getCylinderBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, int x3, int y3, int z3) {
      List<BlockPos> list = new ArrayList();
      List<BlockPos> circleBlocks = Circle.getCircleBlocks(player, x1, y1, z1, x2, y2, z2);
      int lowest = Math.min(y1, y3);
      int highest = Math.max(y1, y3);

      for(int y = lowest; y <= highest; ++y) {
         for(BlockPos blockPos : circleBlocks) {
            list.add(new BlockPos(blockPos.getX(), y, blockPos.getZ()));
         }
      }

      return list;
   }

   public BlockPos findSecondPos(Player player, BlockPos firstPos, boolean skipRaytrace) {
      return Floor.findFloor(player, firstPos, skipRaytrace);
   }

   public BlockPos findThirdPos(Player player, BlockPos firstPos, BlockPos secondPos, boolean skipRaytrace) {
      return findHeight(player, secondPos, skipRaytrace);
   }

    public List<BlockPos> getIntermediateBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2) {
       return Circle.getCircleBlocks(player, x1, y1, z1, x2, y2, z2);
    }

    public List<BlockPos> getFinalBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, int x3, int y3, int z3) {
       return getCylinderBlocks(player, x1, y1, z1, x2, y2, z2, x3, y3, z3);
    }

    @Override
    protected AABB getIntermediateBoundary(BlockPos firstPos, BlockPos clampedSecond) {
       return Sphere.circleBounds(firstPos, clampedSecond);
    }

    @Override
    protected AABB getFinalBoundary(BlockPos firstPos, BlockPos clampedSecond, BlockPos clampedThird) {
       AABB circle = Sphere.circleBounds(firstPos, clampedSecond);
       int minY = Math.min(firstPos.getY(), clampedThird.getY());
       int maxY = Math.max(firstPos.getY(), clampedThird.getY());
       // circle is already a full-block AABB: recover its inclusive block corners.
       BlockPos circleMin = BlockPos.containing(circle.minX, circle.minY, circle.minZ);
       BlockPos circleMax = BlockPos.containing(circle.maxX - 0.001D, circle.maxY - 0.001D, circle.maxZ - 0.001D);
       return toFullBlockAABB(circleMin.getX(), minY, circleMin.getZ(), circleMax.getX(), maxY, circleMax.getZ());
    }
}
