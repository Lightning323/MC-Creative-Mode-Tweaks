package nl.requios.effortlessbuilding.buildmode.buildmodes;

import java.util.ArrayList;
import java.util.List;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import nl.requios.effortlessbuilding.buildmode.RaycastToPlane;
import nl.requios.effortlessbuilding.buildmode.TwoClicksBuildMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

public class Floor extends TwoClicksBuildMode {
   public static BlockPos findFloor(Player player, BlockPos firstPos, boolean skipRaytrace) {
      // Plane-only hit: registers even when the vanilla raycast hits no block.
      // skipRaytrace is kept for signature compatibility and ignored.
      return RaycastToPlane.findFloor(player, firstPos);
   }

   public static List<BlockPos> getFloorBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2) {
      long dx = Math.abs((long) x2 - x1) + 1L;
      long dz = Math.abs((long) z2 - z1) + 1L;
      boolean full = ModeOptions.getFill() == ModeOptions.ActionEnum.FULL;
      // Full floors fill the rect; hollow ones only trace its edge.
      long est = full ? dx * dz : 2L * (dx + dz);
      List<BlockPos> list = new ArrayList<>((int) Math.min(est, 131072));
      if (full) {
         addFloorBlocks(list, x1, x2, y1, z1, z2);
      } else {
         addHollowFloorBlocks(list, x1, x2, y1, z1, z2);
      }

      return list;
   }

   public static void addFloorBlocks(List<BlockPos> list, int x1, int x2, int y, int z1, int z2) {
      forEachFloorBlocks(x1, x2, y, z1, z2, packed -> list.add(BlockPos.of(packed)));
   }

   /** Bare-int twin of {@link #addFloorBlocks}: same order, packed longs, no allocation. */
   public static void forEachFloorBlocks(int x1, int x2, int y, int z1, int z2, LongConsumer out) {
      int xStep = x1 < x2 ? 1 : -1;
      int zStep = z1 < z2 ? 1 : -1;
      for (int l = x1; ; l += xStep) {
         for (int n = z1; ; n += zStep) {
            out.accept(BlockPos.asLong(l, y, n));
            if (n == z2) {
               break;
            }
         }
         if (l == x2) {
            break;
         }
      }
   }

   public static void addHollowFloorBlocks(List<BlockPos> list, int x1, int x2, int y, int z1, int z2) {
      Line.addXLineBlocks(list, x1, x2, y, z1);
      Line.addXLineBlocks(list, x1, x2, y, z2);
      Line.addZLineBlocks(list, z1, z2, x1, y);
      Line.addZLineBlocks(list, z1, z2, x2, y);
   }

   /** Bare-int twin of {@link #addHollowFloorBlocks}: same order, packed longs, no allocation. */
   public static void forEachHollowFloorBlocks(int x1, int x2, int y, int z1, int z2, LongConsumer out) {
      Line.forEachXLineBlocks(x1, x2, y, z1, out);
      Line.forEachXLineBlocks(x1, x2, y, z2, out);
      Line.forEachZLineBlocks(z1, z2, x1, y, out);
      Line.forEachZLineBlocks(z1, z2, x2, y, out);
   }

   /** Streams {@link #getFloorBlocks} as packed longs: no intermediate list. */
   public static void forEachFloorBlocksOption(int x1, int x2, int y, int z1, int z2, boolean full, LongConsumer out) {
      if (full) {
         forEachFloorBlocks(x1, x2, y, z1, z2, out);
      } else {
         forEachHollowFloorBlocks(x1, x2, y, z1, z2, out);
      }
   }

   protected BlockPos findSecondPos(Player player, BlockPos firstPos, boolean skipRaytrace) {
      return findFloor(player, firstPos, skipRaytrace);
   }

   protected List<BlockPos> getAllBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2) {
      return getFloorBlocks(player, x1, y1, z1, x2, y2, z2);
   }

   @Override
   protected void forEachAllBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, LongConsumer out) {
      forEachFloorBlocksOption(x1, x2, y1, z1, z2, ModeOptions.getFill() == ModeOptions.ActionEnum.FULL, out);
   }
}
