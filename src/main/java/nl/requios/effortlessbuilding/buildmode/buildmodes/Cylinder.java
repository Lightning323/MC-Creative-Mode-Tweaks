package nl.requios.effortlessbuilding.buildmode.buildmodes;

import java.util.ArrayList;
import java.util.List;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import net.minecraft.world.phys.AABB;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import nl.requios.effortlessbuilding.buildmode.ThreeClicksBuildMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

public class Cylinder extends ThreeClicksBuildMode {
   public static List<BlockPos> getCylinderBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, int x3, int y3, int z3) {
      List<BlockPos> circleBlocks = Circle.getCircleBlocks(player, x1, y1, z1, x2, y2, z2);
      int lowest = Math.min(y1, y3);
      int highest = Math.max(y1, y3);
      // Exact: every base disc is extruded over the full height.
      List<BlockPos> list = new ArrayList<>((int) Math.min((long) circleBlocks.size() * (highest - lowest + 1L), 131072));

      for(int y = lowest; y <= highest; ++y) {
         for(BlockPos blockPos : circleBlocks) {
            list.add(new BlockPos(blockPos.getX(), y, blockPos.getZ()));
         }
      }

      return list;
   }

   /**
    * Bare-int twin of {@link #getCylinderBlocks}: reuses the circle's packed
    * emitter and extrudes over the height — no intermediate disc list, no
    * {@code BlockPos} allocation in the generator.
    */
   public static void forEachCylinderBlocks(int x1, int y1, int z1, int x2, int y2, int z2, int y3, boolean full, LongConsumer out) {
      int lowest = Math.min(y1, y3);
      int highest = Math.max(y1, y3);
      // Per-height slice around the base disc. The slice buffer is tiny
      // (one disc); the volume-length loop below streams packed longs.
      LongArrayList disc = new LongArrayList();
      Circle.forEachCircleBlocksOption(x1, y1, z1, x2, y2, z2, full, disc::add);
      for (int y = lowest; y <= highest; ++y) {
         for (int i = 0, n = disc.size(); i < n; i++) {
            long packed = disc.getLong(i);
            out.accept(BlockPos.asLong(BlockPos.getX(packed), y, BlockPos.getZ(packed)));
         }
      }
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

    @Override
    protected void forEachIntermediateBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, LongConsumer out) {
       Circle.forEachCircleBlocksOption(x1, y1, z1, x2, y2, z2, ModeOptions.getFill() == ModeOptions.ActionEnum.FULL, out);
    }

    public List<BlockPos> getFinalBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, int x3, int y3, int z3) {
       return getCylinderBlocks(player, x1, y1, z1, x2, y2, z2, x3, y3, z3);
    }

    @Override
    protected void forEachFinalBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, int x3, int y3, int z3, LongConsumer out) {
       forEachCylinderBlocks(x1, y1, z1, x2, y2, z2, y3, ModeOptions.getFill() == ModeOptions.ActionEnum.FULL, out);
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
