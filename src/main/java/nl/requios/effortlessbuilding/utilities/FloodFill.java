package nl.requios.effortlessbuilding.utilities;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Breadth-first flood fill over a shape's block set, used by the flood-fill
 * replace mode: only the air and liquid connected to the seed is placed,
 * everything else is rejected.
 *
 * <p>The seed is the cell a block would be placed into for the current aim
 * (replaceable hits in place, otherwise adjacent), which is open space by
 * construction, so the fill never starts buried inside a solid block;
 * callers fall back to the shape centerpoint when no aim seed is
 * available.</p>
 *
 * <p>Traversal is 6-neighbour (faces only) through shape positions only, and
 * always propagates through air and liquid blocks (water, lava) but never
 * through solid blocks — no block-type matching involved. The
 * flood can never escape the shape: every visited position is a shape
 * position, so the work is bounded by the shape size, which the existing
 * per-axis and max-blocks limits already cap. That bound is what keeps the
 * live preview cheap — same complexity class as the per-block replace check
 * it runs alongside.</p>
 *
 * <p>Hot-loop rules: packed {@code long} positions everywhere (no
 * {@code BlockPos} allocation while traversing), one shared
 * {@link BlockPos.MutableBlockPos} for all world reads, shape-membership
 * tested before the world read.</p>
 */
public final class FloodFill {
   /** Face-neighbour offsets, XYZ triplets. Allocation-free traversal. */
   private static final int[] NEIGHBOURS = {1, 0, 0, -1, 0, 0, 0, 1, 0, 0, -1, 0, 0, 0, 1, 0, 0, -1};

   private FloodFill() {
   }

   /**
    * Bounds-center of the set (floored per-axis mean of the min/max block).
    * The default flood origin ("centerpoint of the shape"); callers snap it
    * into the set via {@link #retainConnected}. Returns
    * {@link BlockPos#ZERO} for an empty set (the retain pass no-ops there).
    */
   public static BlockPos centerOf(BlockSet blocks) {
      boolean any = false;
      int minX = 0, minY = 0, minZ = 0, maxX = 0, maxY = 0, maxZ = 0;
      for (BlockEntry entry : blocks.values()) {
         BlockPos pos = entry.blockPos;
         if (!any) {
            minX = maxX = pos.getX();
            minY = maxY = pos.getY();
            minZ = maxZ = pos.getZ();
            any = true;
         } else {
            int x = pos.getX(), y = pos.getY(), z = pos.getZ();
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
            if (z < minZ) minZ = z;
            if (z > maxZ) maxZ = z;
         }
      }
      if (!any) {
         return BlockPos.ZERO;
      }
      // Block coords are bounded to +/-30M, so the int sums cannot overflow.
      return new BlockPos((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
   }

   /**
    * Marks every shape position that is not air-or-liquid-connected to the
    * seed as {@link BlockStatus#NOT_REPLACEABLE} (first rejection wins, so
    * earlier statuses such as world-border survive). Connected air/liquid
    * positions keep their status. No-op for an empty set.
    *
    * <p>The seed starts the expansion even when it is not a shape position
    * (aimed cell outside the shape, hollow-shape centers); it is only placed
    * when it is a traversable shape position like any other candidate.</p>
    */
   public static void retainConnected(Level level, BlockSet blocks, BlockPos originHint) {
      if (blocks.isEmpty()) {
         return;
      }
      LongOpenHashSet visited = new LongOpenHashSet(blocks.size());
      LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
      long originPacked = originHint.asLong();
      visited.add(originPacked);
      queue.enqueue(originPacked);
      BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();
      while (!queue.isEmpty()) {
         long packed = queue.dequeueLong();
         int x = BlockPos.getX(packed);
         int y = BlockPos.getY(packed);
         int z = BlockPos.getZ(packed);
         for (int i = 0; i < NEIGHBOURS.length; i += 3) {
            int nx = x + NEIGHBOURS[i];
            int ny = y + NEIGHBOURS[i + 1];
            int nz = z + NEIGHBOURS[i + 2];
            long neighbour = BlockPos.asLong(nx, ny, nz);
            if (visited.contains(neighbour) || !blocks.containsKey(neighbour)) {
               continue;
            }
            // Level#getBlockState never loads chunks (missing/out-of-height
            // reads return a default state), so this is side-effect free.
            if (isTraversable(level.getBlockState(scratch.set(nx, ny, nz)))) {
               visited.add(neighbour);
               queue.enqueue(neighbour);
            }
         }
      }
      // A solid centerpoint seeds the expansion but is never placed itself.
      if (blocks.containsKey(originPacked)
            && !isTraversable(level.getBlockState(scratch.set(originHint.getX(), originHint.getY(), originHint.getZ())))) {
         visited.remove(originPacked);
      }
      for (BlockEntry entry : blocks.values()) {
         if (!visited.contains(entry.blockPos.asLong())) {
            entry.markRejected(BlockStatus.NOT_REPLACEABLE);
         }
      }
   }

   /** Air or liquid (water/lava blocks); every solid block is a barrier. */
   private static boolean isTraversable(BlockState state) {
      return state.isAir() || state.getBlock() instanceof LiquidBlock;
   }
}
