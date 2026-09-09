package nl.requios.effortlessbuilding.buildmode.buildmodes;

import java.util.ArrayList;
import java.util.List;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import nl.requios.effortlessbuilding.buildmode.TwoClicksBuildMode;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

public class Circle extends TwoClicksBuildMode {
   public static List<BlockPos> getCircleBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2) {
      long dx = Math.abs((long) x2 - x1) + 1L;
      long dz = Math.abs((long) z2 - z1) + 1L;
      boolean full = ModeOptions.getFill() == ModeOptions.ActionEnum.FULL;
      // Full discs fill the rect; hollow ones only trace the rim.
      List<BlockPos> list = new ArrayList<>((int) Math.min(full ? dx * dz : 4L * (dx + dz), 131072));
      float centerX = (float)x1;
      float centerZ = (float)z1;
      if (ModeOptions.getCircleStart() == ModeOptions.ActionEnum.CIRCLE_START_CORNER) {
         centerX = (float)x1 + (float)(x2 - x1) / 2.0F;
         centerZ = (float)z1 + (float)(z2 - z1) / 2.0F;
      } else {
         x1 = (int)(centerX - ((float)x2 - centerX));
         z1 = (int)(centerZ - ((float)z2 - centerZ));
      }

      float radiusX = Mth.abs((float)x2 - centerX);
      float radiusZ = Mth.abs((float)z2 - centerZ);
      if (full) {
         addCircleBlocks(list, x1, y1, z1, x2, y2, z2, centerX, centerZ, radiusX, radiusZ);
      } else {
         addHollowCircleBlocks(list, x1, y1, z1, x2, y2, z2, centerX, centerZ, radiusX, radiusZ);
      }

      return list;
   }

   public static void addCircleBlocks(List<BlockPos> list, int x1, int y1, int z1, int x2, int y2, int z2, float centerX, float centerZ, float radiusX, float radiusZ) {
      forEachCircleBlocks(x1, y1, z1, x2, y2, z2, centerX, centerZ, radiusX, radiusZ, packed -> list.add(BlockPos.of(packed)));
   }

   /** Bare-int twin of {@link #addCircleBlocks}: same order, packed longs, no allocation. */
   public static void forEachCircleBlocks(int x1, int y1, int z1, int x2, int y2, int z2, float centerX, float centerZ, float radiusX, float radiusZ, LongConsumer out) {
      int xStep = x1 < x2 ? 1 : -1;
      int zStep = z1 < z2 ? 1 : -1;
      for (int l = x1; ; l += xStep) {
         for (int n = z1; ; n += zStep) {
            float distance = distance((float) l, (float) n, centerX, centerZ);
            float radius = calculateEllipseRadius(centerX, centerZ, radiusX, radiusZ, l, n);
            if (distance < radius + 0.4F) {
               out.accept(BlockPos.asLong(l, y1, n));
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

   public static void addHollowCircleBlocks(List<BlockPos> list, int x1, int y1, int z1, int x2, int y2, int z2, float centerX, float centerZ, float radiusX, float radiusZ) {
      forEachHollowCircleBlocks(x1, y1, z1, x2, y2, z2, centerX, centerZ, radiusX, radiusZ, packed -> list.add(BlockPos.of(packed)));
   }

   /** Bare-int twin of {@link #addHollowCircleBlocks}: same order, packed longs, no allocation. */
   public static void forEachHollowCircleBlocks(int x1, int y1, int z1, int x2, int y2, int z2, float centerX, float centerZ, float radiusX, float radiusZ, LongConsumer out) {
      int xStep = x1 < x2 ? 1 : -1;
      int zStep = z1 < z2 ? 1 : -1;
      for (int l = x1; ; l += xStep) {
         for (int n = z1; ; n += zStep) {
            float distance = distance((float) l, (float) n, centerX, centerZ);
            float radius = calculateEllipseRadius(centerX, centerZ, radiusX, radiusZ, l, n);
            if (distance < radius + 0.4F && distance > radius - 0.6F) {
               out.accept(BlockPos.asLong(l, y1, n));
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

   /**
    * Streams {@link #getCircleBlocks} as packed longs: same center/radius
    * resolution, no intermediate list.
    */
   public static void forEachCircleBlocksOption(int x1, int y1, int z1, int x2, int y2, int z2, boolean full, LongConsumer out) {
      float centerX = (float) x1;
      float centerZ = (float) z1;
      int bx1 = x1;
      int bz1 = z1;
      if (ModeOptions.getCircleStart() == ModeOptions.ActionEnum.CIRCLE_START_CORNER) {
         centerX = (float) x1 + (float) (x2 - x1) / 2.0F;
         centerZ = (float) z1 + (float) (z2 - z1) / 2.0F;
      } else {
         bx1 = (int) (centerX - ((float) x2 - centerX));
         bz1 = (int) (centerZ - ((float) z2 - centerZ));
      }

      float radiusX = Mth.abs((float) x2 - centerX);
      float radiusZ = Mth.abs((float) z2 - centerZ);
      if (full) {
         forEachCircleBlocks(bx1, y1, bz1, x2, y2, z2, centerX, centerZ, radiusX, radiusZ, out);
      } else {
         forEachHollowCircleBlocks(bx1, y1, bz1, x2, y2, z2, centerX, centerZ, radiusX, radiusZ, out);
      }
   }

   private static float distance(float x1, float z1, float x2, float z2) {
      return Mth.sqrt((x2 - x1) * (x2 - x1) + (z2 - z1) * (z2 - z1));
   }

   public static float calculateEllipseRadius(float centerX, float centerZ, float radiusX, float radiusZ, int x, int z) {
      float theta = (float)Mth.atan2((double)((float)z - centerZ), (double)((float)x - centerX));
      float part1 = radiusX * radiusX * Mth.sin(theta) * Mth.sin(theta);
      float part2 = radiusZ * radiusZ * Mth.cos(theta) * Mth.cos(theta);
      return radiusX * radiusZ / Mth.sqrt(part1 + part2);
   }

    protected BlockPos findSecondPos(Player player, BlockPos firstPos, boolean skipRaytrace) {
       return Floor.findFloor(player, firstPos, skipRaytrace);
    }

    @Override
    protected AABB boundaryForPoints(BlockPos firstPos, BlockPos clampedSecond) {
       int minX;
       int maxX;
       int minZ;
       int maxZ;
       if (ModeOptions.getCircleStart() == ModeOptions.ActionEnum.CIRCLE_START_CENTER) {
          // Center mode mirrors the hovered radius to the opposite side.
          int mirroredX = 2 * firstPos.getX() - clampedSecond.getX();
          int mirroredZ = 2 * firstPos.getZ() - clampedSecond.getZ();
          minX = Math.min(clampedSecond.getX(), mirroredX);
          maxX = Math.max(clampedSecond.getX(), mirroredX);
          minZ = Math.min(clampedSecond.getZ(), mirroredZ);
          maxZ = Math.max(clampedSecond.getZ(), mirroredZ);
       } else {
          minX = Math.min(firstPos.getX(), clampedSecond.getX());
          maxX = Math.max(firstPos.getX(), clampedSecond.getX());
          minZ = Math.min(firstPos.getZ(), clampedSecond.getZ());
          maxZ = Math.max(firstPos.getZ(), clampedSecond.getZ());
       }
       int y = firstPos.getY();
       return toFullBlockAABB(minX, y, minZ, maxX, y, maxZ);
    }

   protected List<BlockPos> getAllBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2) {
      return getCircleBlocks(player, x1, y1, z1, x2, y2, z2);
   }

   @Override
   protected void forEachAllBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, LongConsumer out) {
      forEachCircleBlocksOption(x1, y1, z1, x2, y2, z2, ModeOptions.getFill() == ModeOptions.ActionEnum.FULL, out);
   }
}
