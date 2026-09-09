package nl.requios.effortlessbuilding.buildmode.buildmodes;

import java.util.ArrayList;
import java.util.List;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import nl.requios.effortlessbuilding.buildmode.ThreeClicksBuildMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

public class Cube extends ThreeClicksBuildMode {
   public static List<BlockPos> getFloorBlocksUsingCubeFill(Player player, int x1, int y1, int z1, int x2, int y2, int z2) {
      long dx = Math.abs((long) x2 - x1) + 1L;
      long dz = Math.abs((long) z2 - z1) + 1L;
      // Skeleton only traces the floor edge; anything else fills it.
      long est = ModeOptions.getCubeFill() == ModeOptions.ActionEnum.CUBE_SKELETON ? 2L * (dx + dz) : dx * dz;
      List<BlockPos> list = new ArrayList<>((int) Math.min(est, 131072));
      if (ModeOptions.getCubeFill() == ModeOptions.ActionEnum.CUBE_SKELETON) {
         Floor.addHollowFloorBlocks(list, x1, x2, y1, z1, z2);
      } else {
         Floor.addFloorBlocks(list, x1, x2, y1, z1, z2);
      }

      return list;
   }

   public static List<BlockPos> getCubeBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2) {
      long dx = Math.abs((long) x2 - x1) + 1L;
      long dy = Math.abs((long) y2 - y1) + 1L;
      long dz = Math.abs((long) z2 - z1) + 1L;
      List<BlockPos> list;
      switch (ModeOptions.getCubeFill()) {
         // Full/hollow fill faces (upper bound); skeleton only traces edges.
         case CUBE_FULL -> {
            list = new ArrayList<>((int) Math.min(dx * dy * dz, 131072));
            addCubeBlocks(list, x1, x2, y1, y2, z1, z2);
         }
         case CUBE_HOLLOW -> {
            list = new ArrayList<>((int) Math.min(2L * (dx * dy + dy * dz + dx * dz), 131072));
            addHollowCubeBlocks(list, x1, x2, y1, y2, z1, z2);
         }
         case CUBE_SKELETON -> {
            list = new ArrayList<>((int) Math.min(4L * (dx + dy + dz), 131072));
            addSkeletonCubeBlocks(list, x1, x2, y1, y2, z1, z2);
         }
         // Unreachable with today's fill modes; throws loudly if one is added.
         default -> throw new IllegalStateException("Unknown cube fill: " + ModeOptions.getCubeFill());      }

      return list;
   }

   public static void addCubeBlocks(List<BlockPos> list, int x1, int x2, int y1, int y2, int z1, int z2) {
      forEachCubeBlocks(x1, x2, y1, y2, z1, z2, packed -> list.add(BlockPos.of(packed)));
   }

   /** Bare-int twin of {@link #addCubeBlocks}: same order, packed longs, no allocation. */
   public static void forEachCubeBlocks(int x1, int x2, int y1, int y2, int z1, int z2, LongConsumer out) {
      int xStep = x1 < x2 ? 1 : -1;
      int zStep = z1 < z2 ? 1 : -1;
      int yStep = y1 < y2 ? 1 : -1;
      for (int l = x1; ; l += xStep) {
         for (int n = z1; ; n += zStep) {
            for (int m = y1; ; m += yStep) {
               out.accept(BlockPos.asLong(l, m, n));
               if (m == y2) {
                  break;
               }
            }
            if (n == z2) {
               break;
            }
         }
         if (l == x2) {
            break;
         }
      }
   }

   public static void addHollowCubeBlocks(List<BlockPos> list, int x1, int x2, int y1, int y2, int z1, int z2) {
      forEachHollowCubeBlocks(x1, x2, y1, y2, z1, z2, packed -> list.add(BlockPos.of(packed)));
   }

   /** Bare-int twin of {@link #addHollowCubeBlocks}: same order, packed longs, no allocation. */
   public static void forEachHollowCubeBlocks(int x1, int x2, int y1, int y2, int z1, int z2, LongConsumer out) {
      Wall.forEachXWallBlocks(x1, y1, y2, z1, z2, out);
      Wall.forEachXWallBlocks(x2, y1, y2, z1, z2, out);
      Wall.forEachZWallBlocks(x1, x2, y1, y2, z1, out);
      Wall.forEachZWallBlocks(x1, x2, y1, y2, z2, out);
      Floor.forEachFloorBlocks(x1, x2, y1, z1, z2, out);
      Floor.forEachFloorBlocks(x1, x2, y2, z1, z2, out);
   }

   public static void addSkeletonCubeBlocks(List<BlockPos> list, int x1, int x2, int y1, int y2, int z1, int z2) {
      Line.addXLineBlocks(list, x1, x2, y1, z1);
      Line.addXLineBlocks(list, x1, x2, y1, z2);
      Line.addXLineBlocks(list, x1, x2, y2, z1);
      Line.addXLineBlocks(list, x1, x2, y2, z2);
      Line.addYLineBlocks(list, y1, y2, x1, z1);
      Line.addYLineBlocks(list, y1, y2, x1, z2);
      Line.addYLineBlocks(list, y1, y2, x2, z1);
      Line.addYLineBlocks(list, y1, y2, x2, z2);
      Line.addZLineBlocks(list, z1, z2, x1, y1);
      Line.addZLineBlocks(list, z1, z2, x1, y2);
      Line.addZLineBlocks(list, z1, z2, x2, y1);
      Line.addZLineBlocks(list, z1, z2, x2, y2);
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
      return getFloorBlocksUsingCubeFill(player, x1, y1, z1, x2, y2, z2);
   }

   @Override
   protected void forEachIntermediateBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, LongConsumer out) {
      if (ModeOptions.getCubeFill() == ModeOptions.ActionEnum.CUBE_SKELETON) {
         Floor.forEachHollowFloorBlocks(x1, x2, y1, z1, z2, out);
      } else {
         Floor.forEachFloorBlocks(x1, x2, y1, z1, z2, out);
      }
   }

   protected List<BlockPos> getFinalBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, int x3, int y3, int z3) {
      return getCubeBlocks(player, x1, y1, z1, x3, y3, z3);
   }

   @Override
   protected void forEachFinalBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, int x3, int y3, int z3, LongConsumer out) {
      switch (ModeOptions.getCubeFill()) {
         case CUBE_FULL -> forEachCubeBlocks(x1, x3, y1, y3, z1, z3, out);
         case CUBE_HOLLOW -> forEachHollowCubeBlocks(x1, x3, y1, y3, z1, z3, out);
         case CUBE_SKELETON -> {
            // Same edge trace as addSkeletonCubeBlocks, streaming packed longs.
            Line.forEachXLineBlocks(x1, x3, y1, z1, out);
            Line.forEachXLineBlocks(x1, x3, y1, z3, out);
            Line.forEachXLineBlocks(x1, x3, y3, z1, out);
            Line.forEachXLineBlocks(x1, x3, y3, z3, out);
            Line.forEachYLineBlocks(y1, y3, x1, z1, out);
            Line.forEachYLineBlocks(y1, y3, x1, z3, out);
            Line.forEachYLineBlocks(y1, y3, x3, z1, out);
            Line.forEachYLineBlocks(y1, y3, x3, z3, out);
            Line.forEachZLineBlocks(z1, z3, x1, y1, out);
            Line.forEachZLineBlocks(z1, z3, x1, y3, out);
            Line.forEachZLineBlocks(z1, z3, x3, y1, out);
            Line.forEachZLineBlocks(z1, z3, x3, y3, out);
         }
         default -> throw new IllegalStateException("Unknown cube fill: " + ModeOptions.getCubeFill());
      }
   }
}
