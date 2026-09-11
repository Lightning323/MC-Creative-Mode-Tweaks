package nl.requios.effortlessbuilding.buildmode.buildmodes;

import java.util.ArrayList;
import java.util.List;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import nl.requios.effortlessbuilding.buildmode.RaycastToPlane;
import nl.requios.effortlessbuilding.buildmode.ThreeClicksBuildMode;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipeline;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class Cube extends ThreeClicksBuildMode {
   /**
    * Initial-plane normal from the player's look (no new options): the plane
    * faces the player. Matches Plane AUTO: Y when looking mostly up/down,
    * otherwise the dominant horizontal axis.
    */
   static Direction.Axis dominantAxis(Player player) {
      Vec3 look = BuildPipeline.getPlayerLookVec(player);
      double absX = Math.abs(look.x);
      double absY = Math.abs(look.y);
      double absZ = Math.abs(look.z);
      if (absY > Math.max(absX, absZ)) {
         return Direction.Axis.Y;
      }
      return absX > absZ ? Direction.Axis.X : Direction.Axis.Z;
   }

   /**
    * Extrusion axis for the third click: the normal of the initial plane,
    * inferred from which coordinate second shares with first. When second
    * sits on several planes through first (edge/point), the current look
    * breaks the tie towards the plane it would have picked.
    */
   static Direction.Axis extrusionAxis(BlockPos firstPos, BlockPos secondPos, Player player) {
      boolean matchX = firstPos.getX() == secondPos.getX();
      boolean matchY = firstPos.getY() == secondPos.getY();
      boolean matchZ = firstPos.getZ() == secondPos.getZ();
      int matches = (matchX ? 1 : 0) + (matchY ? 1 : 0) + (matchZ ? 1 : 0);
      if (matches == 1) {
         if (matchY) {
            return Direction.Axis.Y;
         }
         return matchX ? Direction.Axis.X : Direction.Axis.Z;
      }
      if (matches > 1) {
         Direction.Axis lookAxis = dominantAxis(player);
         if ((lookAxis == Direction.Axis.Y && matchY)
               || (lookAxis == Direction.Axis.X && matchX)
               || (lookAxis == Direction.Axis.Z && matchZ)) {
            return lookAxis;
         }
      }
      // Deterministic fallback preserves legacy floor behavior; also covers
      // the degenerate single-block case where every axis matches.
      if (matchY) {
         return Direction.Axis.Y;
      }
      if (matchX) {
         return Direction.Axis.X;
      }
      if (matchZ) {
         return Direction.Axis.Z;
      }
      return dominantAxis(player);
   }

   /**
    * Initial-plane normal for explicit points (preview/server). Same matching
    * as {@link #extrusionAxis} but deterministic when ambiguous, so server
    * placements don't depend on look at place time.
    */
   static Direction.Axis initialPlaneAxis(int x1, int y1, int z1, int x2, int y2, int z2, Player player) {
      boolean matchX = x1 == x2;
      boolean matchY = y1 == y2;
      boolean matchZ = z1 == z2;
      int matches = (matchX ? 1 : 0) + (matchY ? 1 : 0) + (matchZ ? 1 : 0);
      if (matches == 1) {
         if (matchY) {
            return Direction.Axis.Y;
         }
         return matchX ? Direction.Axis.X : Direction.Axis.Z;
      }
      if (matches > 1 && player != null) {
         Direction.Axis lookAxis = dominantAxis(player);
         if ((lookAxis == Direction.Axis.Y && matchY)
               || (lookAxis == Direction.Axis.X && matchX)
               || (lookAxis == Direction.Axis.Z && matchZ)) {
            return lookAxis;
         }
      }
      if (matchY) {
         return Direction.Axis.Y;
      }
      if (matchX) {
         return Direction.Axis.X;
      }
      if (matchZ) {
         return Direction.Axis.Z;
      }
      return Direction.Axis.Y;
   }

   public static List<BlockPos> getWallBlocksUsingCubeFill(int x1, int y1, int z1, int x2, int y2, int z2, Direction.Axis wallAxis) {
      boolean skeleton = ModeOptions.getCubeFill() == ModeOptions.ActionEnum.CUBE_SKELETON;
      if (wallAxis == Direction.Axis.X) {
         long dy = Math.abs((long) y2 - y1) + 1L;
         long dz = Math.abs((long) z2 - z1) + 1L;
         long est = skeleton ? 2L * (dy + dz) : dy * dz;
         List<BlockPos> list = new ArrayList<>((int) Math.min(est, 131072));
         if (skeleton) {
            Wall.addXHollowWallBlocks(list, x1, y1, y2, z1, z2);
         } else {
            Wall.addXWallBlocks(list, x1, y1, y2, z1, z2);
         }
         return list;
      }
      long dx = Math.abs((long) x2 - x1) + 1L;
      long dy = Math.abs((long) y2 - y1) + 1L;
      long est = skeleton ? 2L * (dx + dy) : dx * dy;
      List<BlockPos> list = new ArrayList<>((int) Math.min(est, 131072));
      if (skeleton) {
         Wall.addZHollowWallBlocks(list, x1, x2, y1, y2, z1);
      } else {
         Wall.addZWallBlocks(list, x1, x2, y1, y2, z1);
      }
      return list;
   }

   static void forEachWallBlocksUsingCubeFill(int x1, int y1, int z1, int x2, int y2, int z2, Direction.Axis wallAxis, LongConsumer out) {
      boolean skeleton = ModeOptions.getCubeFill() == ModeOptions.ActionEnum.CUBE_SKELETON;
      if (wallAxis == Direction.Axis.X) {
         if (skeleton) {
            Wall.forEachXHollowWallBlocks(x1, y1, y2, z1, z2, out);
         } else {
            Wall.forEachXWallBlocks(x1, y1, y2, z1, z2, out);
         }
         return;
      }
      if (skeleton) {
         Wall.forEachZHollowWallBlocks(x1, x2, y1, y2, z1, out);
      } else {
         Wall.forEachZWallBlocks(x1, x2, y1, y2, z1, out);
      }
   }
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
      return switch (dominantAxis(player)) {
         case Y -> Floor.findFloor(player, firstPos, skipRaytrace);
         case X -> RaycastToPlane.findWallOnAxis(player, firstPos, Direction.Axis.X);
         case Z -> RaycastToPlane.findWallOnAxis(player, firstPos, Direction.Axis.Z);
      };
   }

   protected boolean supportsTwoPointBuild() {
      return true;
   }

   protected BlockPos findThirdPos(Player player, BlockPos firstPos, BlockPos secondPos, boolean skipRaytrace) {
      Direction.Axis extrusion = extrusionAxis(firstPos, secondPos, player);
      return switch (extrusion) {
         case Y -> findHeight(player, secondPos, skipRaytrace);
         case X, Z -> RaycastToPlane.findPerpendicularHeight(extrusion, player, secondPos);
      };
   }

   protected List<BlockPos> getIntermediateBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2) {
      Direction.Axis axis = initialPlaneAxis(x1, y1, z1, x2, y2, z2, player);
      return switch (axis) {
         case Y -> getFloorBlocksUsingCubeFill(player, x1, y1, z1, x2, y2, z2);
         case X, Z -> getWallBlocksUsingCubeFill(x1, y1, z1, x2, y2, z2, axis);
      };
   }

   @Override
   protected void forEachIntermediateBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, LongConsumer out) {
      Direction.Axis axis = initialPlaneAxis(x1, y1, z1, x2, y2, z2, player);
      if (axis == Direction.Axis.Y) {
         if (ModeOptions.getCubeFill() == ModeOptions.ActionEnum.CUBE_SKELETON) {
            Floor.forEachHollowFloorBlocks(x1, x2, y1, z1, z2, out);
         } else {
            Floor.forEachFloorBlocks(x1, x2, y1, z1, z2, out);
         }
         return;
      }
      forEachWallBlocksUsingCubeFill(x1, y1, z1, x2, y2, z2, axis, out);
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
