package nl.requios.effortlessbuilding.buildmode.buildmodes;

import java.util.ArrayList;
import java.util.List;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import nl.requios.effortlessbuilding.buildmode.RaycastToPlane;
import nl.requios.effortlessbuilding.buildmode.TwoClicksBuildMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

public class Line extends TwoClicksBuildMode {
   public static BlockPos findLine(Player player, BlockPos firstPos, boolean skipRaytrace) {
      // Plane-only hit: registers even when the vanilla raycast hits no block.
      // skipRaytrace is kept for signature compatibility and ignored.
      return RaycastToPlane.findLine(player, firstPos);
   }

   public static List<BlockPos> getLineBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2) {
      // Lines run along exactly one axis: length + 1 is the exact count.
      long len = x1 != x2 ? Math.abs((long) x2 - x1) : y1 != y2 ? Math.abs((long) y2 - y1) : Math.abs((long) z2 - z1);
      List<BlockPos> list = new ArrayList<>((int) Math.min(len + 1L, 131072));
      if (x1 != x2) {
         addXLineBlocks(list, x1, x2, y1, z1);
      } else if (y1 != y2) {
         addYLineBlocks(list, y1, y2, x1, z1);
      } else {
         addZLineBlocks(list, z1, z2, x1, y1);
      }

      return list;
   }

   public static void addXLineBlocks(List<BlockPos> list, int x1, int x2, int y, int z) {
      forEachXLineBlocks(x1, x2, y, z, packed -> list.add(BlockPos.of(packed)));
   }

   /** Bare-int twin of {@link #addXLineBlocks}: same order, packed longs, no allocation. */
   public static void forEachXLineBlocks(int x1, int x2, int y, int z, LongConsumer out) {
      int step = x1 < x2 ? 1 : -1;
      for (int x = x1; ; x += step) {
         out.accept(BlockPos.asLong(x, y, z));
         if (x == x2) {
            break;
         }
      }
   }

   public static void addYLineBlocks(List<BlockPos> list, int y1, int y2, int x, int z) {
      forEachYLineBlocks(y1, y2, x, z, packed -> list.add(BlockPos.of(packed)));
   }

   /** Bare-int twin of {@link #addYLineBlocks}: same order, packed longs, no allocation. */
   public static void forEachYLineBlocks(int y1, int y2, int x, int z, LongConsumer out) {
      int step = y1 < y2 ? 1 : -1;
      for (int y = y1; ; y += step) {
         out.accept(BlockPos.asLong(x, y, z));
         if (y == y2) {
            break;
         }
      }
   }

   public static void addZLineBlocks(List<BlockPos> list, int z1, int z2, int x, int y) {
      forEachZLineBlocks(z1, z2, x, y, packed -> list.add(BlockPos.of(packed)));
   }

   /** Bare-int twin of {@link #addZLineBlocks}: same order, packed longs, no allocation. */
   public static void forEachZLineBlocks(int z1, int z2, int x, int y, LongConsumer out) {
      int step = z1 < z2 ? 1 : -1;
      for (int z = z1; ; z += step) {
         out.accept(BlockPos.asLong(x, y, z));
         if (z == z2) {
            break;
         }
      }
   }

   protected BlockPos findSecondPos(Player player, BlockPos firstPos, boolean skipRaytrace) {
      return findLine(player, firstPos, skipRaytrace);
   }

   protected List<BlockPos> getAllBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2) {
      return getLineBlocks(player, x1, y1, z1, x2, y2, z2);
   }

   @Override
   protected void forEachAllBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, LongConsumer out) {
      if (x1 != x2) {
         forEachXLineBlocks(x1, x2, y1, z1, out);
      } else if (y1 != y2) {
         forEachYLineBlocks(y1, y2, x1, z1, out);
      } else {
         forEachZLineBlocks(z1, z2, x1, y1, out);
      }
   }
}
