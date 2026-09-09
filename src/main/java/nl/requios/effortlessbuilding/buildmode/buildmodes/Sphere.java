package nl.requios.effortlessbuilding.buildmode.buildmodes;

import java.util.ArrayList;
import java.util.List;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import nl.requios.effortlessbuilding.buildmode.ThreeClicksBuildMode;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

public class Sphere extends ThreeClicksBuildMode {
   public static List<BlockPos> getSphereBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, int x3, int y3, int z3) {
      float centerX = (float)x1;
      float centerY = (float)y1;
      float centerZ = (float)z1;
      if (ModeOptions.getCircleStart() == ModeOptions.ActionEnum.CIRCLE_START_CORNER) {
         centerX = (float)x1 + (float)(x2 - x1) / 2.0F;
         centerY = (float)y1 + (float)(y3 - y1) / 2.0F;
         centerZ = (float)z1 + (float)(z2 - z1) / 2.0F;
      } else {
         x1 = 2 * x1 - x2;
         y1 = 2 * y1 - y3;
         z1 = 2 * z1 - z2;
      }

      float radiusX = Mth.abs((float)x2 - centerX);
      float radiusY = Mth.abs((float)y3 - centerY);
      float radiusZ = Mth.abs((float)z2 - centerZ);
      long dx = Math.abs((long) x3 - x1) + 1L;
      long dy = Math.abs((long) y3 - y1) + 1L;
      long dz = Math.abs((long) z3 - z1) + 1L;
      boolean full = ModeOptions.getFill() == ModeOptions.ActionEnum.FULL;
      // Full balls fill the box; hollow ones only skin its surface.
      long est = full ? dx * dy * dz : 2L * (dx * dy + dy * dz + dx * dz);
      List<BlockPos> list = new ArrayList<>((int) Math.min(est, 131072));
      if (full) {
         addSphereBlocks(list, x1, y1, z1, x3, y3, z3, centerX, centerY, centerZ, radiusX, radiusY, radiusZ);
      } else {
         addHollowSphereBlocks(list, x1, y1, z1, x3, y3, z3, centerX, centerY, centerZ, radiusX, radiusY, radiusZ);
      }

      return list;
   }

   public static void addSphereBlocks(List<BlockPos> list, int x1, int y1, int z1, int x2, int y2, int z2, float centerX, float centerY, float centerZ, float radiusX, float radiusY, float radiusZ) {
      int minX = Math.min(x1, x2);
      int maxX = Math.max(x1, x2);
      int minY = Math.min(y1, y2);
      int maxY = Math.max(y1, y2);
      int minZ = Math.min(z1, z2);
      int maxZ = Math.max(z1, z2);
      // Voxel shells look round when the surface grabs a ~0.4 block fringe,
      // same as the circle filler; expanding each radius keeps that fringe
      // uniform in every direction.
      float outerX = radiusX + 0.4F;
      float outerY = radiusY + 0.4F;
      float outerZ = radiusZ + 0.4F;

      for (int x = minX; x <= maxX; ++x) {
         for (int z = minZ; z <= maxZ; ++z) {
            for (int y = minY; y <= maxY; ++y) {
               if (isInsideEllipsoid(x, y, z, centerX, centerY, centerZ, outerX, outerY, outerZ)) {
                  list.add(new BlockPos(x, y, z));
               }
            }
         }
      }
   }

   public static void addHollowSphereBlocks(List<BlockPos> list, int x1, int y1, int z1, int x2, int y2, int z2, float centerX, float centerY, float centerZ, float radiusX, float radiusY, float radiusZ) {
      int minX = Math.min(x1, x2);
      int maxX = Math.max(x1, x2);
      int minY = Math.min(y1, y2);
      int maxY = Math.max(y1, y2);
      int minZ = Math.min(z1, z2);
      int maxZ = Math.max(z1, z2);
      float outerX = radiusX + 0.4F;
      float outerY = radiusY + 0.4F;
      float outerZ = radiusZ + 0.4F;
      float innerX = radiusX - 0.6F;
      float innerY = radiusY - 0.6F;
      float innerZ = radiusZ - 0.6F;

      for (int x = minX; x <= maxX; ++x) {
         for (int z = minZ; z <= maxZ; ++z) {
            for (int y = minY; y <= maxY; ++y) {
               if (isInsideEllipsoid(x, y, z, centerX, centerY, centerZ, outerX, outerY, outerZ)
                     && !isInsideEllipsoid(x, y, z, centerX, centerY, centerZ, innerX, innerY, innerZ)) {
                  list.add(new BlockPos(x, y, z));
               }
            }
         }
      }
   }

   /**
    * Normalized ellipsoid test: {@code (dx/rx)^2 + (dy/ry)^2 + (dz/rz)^2 <= 1}.
    * A zero/negative radius collapses that axis, so only the center plane can
    * match on it (anything off-center is infinitely far away).
    */
   private static boolean isInsideEllipsoid(int x, int y, int z, float centerX, float centerY, float centerZ, float radiusX, float radiusY, float radiusZ) {
      return normalizedComponent(centerX, radiusX, x)
            + normalizedComponent(centerY, radiusY, y)
            + normalizedComponent(centerZ, radiusZ, z) <= 1.0;
   }

   private static double normalizedComponent(double center, double radius, int coordinate) {
      if (radius <= 0.0) {
         return coordinate == center ? 0.0 : Double.POSITIVE_INFINITY;
      }
      double distance = ((double)coordinate - center) / radius;
      return distance * distance;
   }

   public BlockPos findSecondPos(Player player, BlockPos firstPos, boolean skipRaytrace) {
      return Floor.findFloor(player, firstPos, skipRaytrace);
   }

   @Override
   protected boolean supportsTwoPointBuild() {
      return true;
   }

   @Override
   protected List<BlockPos> getTwoPointBlocks(Player player, BlockPos firstPos, BlockPos secondPos) {
      BlockPos boundedSecond = limitToBuildRange(player, firstPos, secondPos);
      return getTwoPointSphereBlocks(firstPos, boundedSecond);
   }

   @Override
   protected AABB getTwoPointBoundary(BlockPos firstPos, BlockPos boundedSecond) {
      double[] params = twoPointSphereParams(firstPos, boundedSecond);
      double cx = params[0];
      double cy = params[1];
      double cz = params[2];
      double r = params[3];
      return new AABB(Math.floor(cx - r), Math.floor(cy - r), Math.floor(cz - r),
              Math.ceil(cx + r), Math.ceil(cy + r), Math.ceil(cz + r));
   }

   /**
    * Center and uniform radius of a two-point sphere.
    * Center-start: the first click is the centerpoint and the second click
    * sits on the surface, defining the radius. Corner-start: both clicks sit
    * on the circumference with the centerpoint midway between them.
    *
    * @return {centerX, centerY, centerZ, radius}.
    */
   static double[] twoPointSphereParams(BlockPos firstPos, BlockPos secondPos) {
      double dx = (double) secondPos.getX() - firstPos.getX();
      double dy = (double) secondPos.getY() - firstPos.getY();
      double dz = (double) secondPos.getZ() - firstPos.getZ();
      double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (ModeOptions.getCircleStart() == ModeOptions.ActionEnum.CIRCLE_START_CENTER) {
         return new double[]{(double) firstPos.getX(), (double) firstPos.getY(), (double) firstPos.getZ(), dist};
      }
      return new double[]{
              (firstPos.getX() + secondPos.getX()) / 2.0,
              (firstPos.getY() + secondPos.getY()) / 2.0,
              (firstPos.getZ() + secondPos.getZ()) / 2.0,
              dist / 2.0};
   }

   static List<BlockPos> getTwoPointSphereBlocks(BlockPos firstPos, BlockPos secondPos) {
      double[] params = twoPointSphereParams(firstPos, secondPos);
      double cx = params[0];
      double cy = params[1];
      double cz = params[2];
      float r = (float) params[3];
      if (r <= 0.0F) {
         return List.of(firstPos);
      }
      // The surface fringe reaches 0.4 past the radius, so pad by a block.
      int minX = (int) Math.floor(cx - r - 1.0);
      int minY = (int) Math.floor(cy - r - 1.0);
      int minZ = (int) Math.floor(cz - r - 1.0);
      int maxX = (int) Math.ceil(cx + r + 1.0);
      int maxY = (int) Math.ceil(cy + r + 1.0);
      int maxZ = (int) Math.ceil(cz + r + 1.0);
      boolean full = ModeOptions.getFill() == ModeOptions.ActionEnum.FULL;
      long dx = (long) maxX - minX + 1L;
      long dy = (long) maxY - minY + 1L;
      long dz = (long) maxZ - minZ + 1L;
      long est = full ? dx * dy * dz : 2L * (dx * dy + dy * dz + dx * dz);
      List<BlockPos> list = new ArrayList<>((int) Math.min(est, 131072));
      float centerX = (float) cx;
      float centerY = (float) cy;
      float centerZ = (float) cz;
      if (full) {
         addSphereBlocks(list, minX, minY, minZ, maxX, maxY, maxZ, centerX, centerY, centerZ, r, r, r);
      } else {
         addHollowSphereBlocks(list, minX, minY, minZ, maxX, maxY, maxZ, centerX, centerY, centerZ, r, r, r);
      }
      return list;
   }

   public BlockPos findThirdPos(Player player, BlockPos firstPos, BlockPos secondPos, boolean skipRaytrace) {
      return findHeight(player, secondPos, skipRaytrace);
   }

    public List<BlockPos> getIntermediateBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2) {
       return Circle.getCircleBlocks(player, x1, y1, z1, x2, y2, z2);
    }

    public List<BlockPos> getFinalBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, int x3, int y3, int z3) {
       return getSphereBlocks(player, x1, y1, z1, x2, y2, z2, x3, y3, z3);
    }

    @Override
    protected AABB getIntermediateBoundary(BlockPos firstPos, BlockPos clampedSecond) {
       return circleBounds(firstPos, clampedSecond);
    }

    @Override
    protected AABB getFinalBoundary(BlockPos firstPos, BlockPos clampedSecond, BlockPos clampedThird) {
       if (ModeOptions.getCircleStart() == ModeOptions.ActionEnum.CIRCLE_START_CENTER) {
          int mirroredX = 2 * firstPos.getX() - clampedSecond.getX();
          int mirroredY = 2 * firstPos.getY() - clampedThird.getY();
          int mirroredZ = 2 * firstPos.getZ() - clampedSecond.getZ();
          return toFullBlockAABB(
                 Math.min(mirroredX, clampedSecond.getX()),
                 Math.min(mirroredY, clampedThird.getY()),
                 Math.min(mirroredZ, clampedSecond.getZ()),
                 Math.max(mirroredX, clampedSecond.getX()),
                 Math.max(mirroredY, clampedThird.getY()),
                 Math.max(mirroredZ, clampedSecond.getZ()));
       }
       return super.getFinalBoundary(firstPos, clampedSecond, clampedThird);
    }

    static AABB circleBounds(BlockPos firstPos, BlockPos clampedSecond) {
       int minX;
       int maxX;
       int minZ;
       int maxZ;
       if (ModeOptions.getCircleStart() == ModeOptions.ActionEnum.CIRCLE_START_CENTER) {
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
}
