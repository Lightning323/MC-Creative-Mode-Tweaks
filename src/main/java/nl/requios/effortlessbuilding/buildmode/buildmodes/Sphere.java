package nl.requios.effortlessbuilding.buildmode.buildmodes;

import java.util.ArrayList;
import java.util.List;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import nl.requios.effortlessbuilding.buildmode.RaycastToPlane;
import nl.requios.effortlessbuilding.buildmode.ThreeClicksBuildMode;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipeline;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lightning323.creative_mode_tweaks.Config;

/**
 * Ball in any direction. The 2-point placement is a true sphere (center and
 * uniform radius from the first two clicks, honoring {@code CIRCLE_START}).
 *
 * <p>The 3-point placement starts from exactly that same sphere: points 1-2
 * define center and radius through {@link #twoPointSphereParams}, just like
 * the 2-point mode. The third point's full 3D offset from that center then
 * defines the ellipsoid: its distance sets the polar radius and its direction
 * sets the polar axis (a spheroid). Any direction and any distance works; a
 * third point at/near the center (or at the same distance as the radius, e.g.
 * {@code thirdPos == secondPos}) collapses back to the undeformed sphere.</p>
 *
 * <p>Clicks are direct in both modes: every point is the block actually
 * pointed at (no floor-plane or height-line projection), so points 1-2 land
 * exactly where a 2-point selection would put them and the third handle can
 * be pointed anywhere. The in-progress second/third points follow the live
 * crosshair block; pointing at the sky falls back to the undeformed sphere.</p>
 */
public class Sphere extends ThreeClicksBuildMode {
   /** Third-point distance from the center below half a block stays a sphere. */
   private static final double MIN_POLAR = 0.5D;
   /** Captured at first click: 3-point stores real clicked blocks for every point. */
   private boolean threePoint;
   /** Ellipsoid handle stored on the 3rd click (sent to the server as thirdPos). */
   private @Nullable BlockPos storedThird;
   /** Live crosshair block published via {@link #setPreviewPoint}: the in-progress second/third point. */
   private @Nullable BlockPos previewHover;

   @Override
   public void initialize() {
      super.initialize();
      this.threePoint = false;
      this.storedThird = null;
      this.previewHover = null;
   }

   @Override
   public boolean onClick(BlockSet blocks, BlockPos clickedPos, Player player) {
      if (this.clicks == 0) {
         if (ModeOptions.isTwoPointBuild()) {
            return super.onClick(blocks, clickedPos, player);
         }
         this.threePoint = true;
         this.storedThird = null;
         ++this.clicks;
         this.firstBlockEntry = new BlockEntry(clickedPos);
         this.secondBlockEntry = null;
         return false;
      }
      if (!this.threePoint) {
         return super.onClick(blocks, clickedPos, player);
      }
      if (this.clicks == 1) {
         // Second point is the block actually clicked, exactly like 2-point mode.
         ++this.clicks;
         this.secondBlockEntry = new BlockEntry(clickedPos);
         return false;
      }
      // Third click: the block under the crosshair is the ellipsoid handle.
      ++this.clicks;
      this.storedThird = clickedPos;
      return true;
   }

   @Override
   public boolean usesDirectSecondPoint() {
      // Every point is a directly pointed-at block (2-point flow and up),
      // so clicks and previews resolve real targets in any direction.
      return true;
   }

   @Override
   public void setPreviewPoint(@Nullable BlockPos pos) {
      super.setPreviewPoint(pos);
      this.previewHover = pos;
   }

   @Override
   public @Nullable BlockPos getThirdSelectionPos() {
      return this.threePoint ? this.storedThird : null;
   }

   public static List<BlockPos> getSphereBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, int x3, int y3, int z3) {
      long dx = Math.abs((long) x3 - x1) + 1L;
      long dy = Math.abs((long) y3 - y1) + 1L;
      long dz = Math.abs((long) z3 - z1) + 1L;
      boolean full = ModeOptions.getFill() == ModeOptions.ActionEnum.FULL;
      // Full balls fill the box; hollow ones only skin its surface.
      long est = full ? dx * dy * dz : 2L * (dx * dy + dy * dz + dx * dz);
      List<BlockPos> list = new ArrayList<>((int) Math.min(est, 131072));
      forEachSphere(x1, y1, z1, x2, y2, z2, x3, y3, z3, full, packed -> list.add(BlockPos.of(packed)));
      return list;
   }

   /**
    * Shared center/radius resolution behind {@link #getSphereBlocks} and the
    * streaming preview: points 1-2 resolve through
    * {@link #twoPointSphereParams} (exactly like the 2-point placement) and
    * the third point's 3D offset from that center sets the polar axis and
    * radius (see {@link #forEachSpheroid}). Same shape as
    * {@link #getSphereBlocks}, packed longs.
    */
   public static void forEachSphere(int x1, int y1, int z1, int x2, int y2, int z2, int x3, int y3, int z3, boolean full, LongConsumer out) {
      forEachSpheroid(new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2), new BlockPos(x3, y3, z3), full, out);
   }

   /**
    * Spheroid from three points: the first two fix center and equatorial
    * radius exactly like {@link #getTwoPointSphereBlocks}; the third point's
    * distance from that center is the polar radius along the center-to-third
    * direction, so the third point itself always sits on the surface. A third
    * point nearer than {@link #MIN_POLAR} to the center (or a zero base
    * radius) falls back to the undeformed sphere.
    */
   static void forEachSpheroid(BlockPos firstPos, BlockPos secondPos, BlockPos thirdPos, boolean full, LongConsumer out) {
      double[] base = twoPointSphereParams(firstPos, secondPos);
      double cx = base[0];
      double cy = base[1];
      double cz = base[2];
      double r = base[3];
      if (r <= 0.0D) {
         out.accept(firstPos.asLong());
         return;
      }
      double wx = (double) thirdPos.getX() - cx;
      double wy = (double) thirdPos.getY() - cy;
      double wz = (double) thirdPos.getZ() - cz;
      double polar = Math.sqrt(wx * wx + wy * wy + wz * wz);
      if (polar < MIN_POLAR) {
         // Undeformed: emit exactly the 2-point sphere.
         forEachTwoPointSphereBlocks(firstPos, secondPos, full, out);
         return;
      }
      double ux = wx / polar;
      double uy = wy / polar;
      double uz = wz / polar;
      // Voxel shells keep the same ~0.4 block outer fringe / 0.6 inner cut as
      // the axis-aligned range below, applied per principal radius instead.
      double eqOuter = r + 0.4D;
      double eqInner = r - 0.6D;
      double polOuter = polar + 0.4D;
      double polInner = polar - 0.6D;
      double ex = Math.sqrt(eqOuter * eqOuter + (polOuter * polOuter - eqOuter * eqOuter) * ux * ux) + 1.0D;
      double ey = Math.sqrt(eqOuter * eqOuter + (polOuter * polOuter - eqOuter * eqOuter) * uy * uy) + 1.0D;
      double ez = Math.sqrt(eqOuter * eqOuter + (polOuter * polOuter - eqOuter * eqOuter) * uz * uz) + 1.0D;
      int minX = (int) Math.floor(cx - ex);
      int maxX = (int) Math.ceil(cx + ex);
      int minY = (int) Math.floor(cy - ey);
      int maxY = (int) Math.ceil(cy + ey);
      int minZ = (int) Math.floor(cz - ez);
      int maxZ = (int) Math.ceil(cz + ez);
      for (int x = minX; x <= maxX; ++x) {
         for (int z = minZ; z <= maxZ; ++z) {
            for (int y = minY; y <= maxY; ++y) {
               double dx = (double) x - cx;
               double dy = (double) y - cy;
               double dz = (double) z - cz;
               double t = dx * ux + dy * uy + dz * uz;
               double perpSq = dx * dx + dy * dy + dz * dz - t * t;
               if (perpSq < 0.0D && perpSq > -1.0E-9D) {
                  perpSq = 0.0D;
               }
               if (spheroidQ(perpSq, t, eqOuter, polOuter) <= 1.0D
                     && (full || spheroidQ(perpSq, t, eqInner, polInner) > 1.0D)) {
                  out.accept(BlockPos.asLong(x, y, z));
               }
            }
         }
      }
   }

   /**
    * Normalized spheroid test: {@code (perp/req)^2 + (t/rpol)^2 <= 1} where
    * {@code t} runs along the polar axis and {@code perpSq} is the squared
    * distance from it. Mirrors {@link #normalizedComponent}: a
    * zero/negative radius collapses that direction, so only exact-axis
    * points can match on it.
    */
   private static double spheroidQ(double perpSq, double t, double eqRadius, double polRadius) {
      double eq;
      if (eqRadius <= 0.0D) {
         eq = perpSq == 0.0D ? 0.0D : Double.POSITIVE_INFINITY;
      } else {
         eq = perpSq / (eqRadius * eqRadius);
      }
      double tt = t * t;
      double pol;
      if (polRadius <= 0.0D) {
         pol = tt == 0.0D ? 0.0D : Double.POSITIVE_INFINITY;
      } else {
         pol = tt / (polRadius * polRadius);
      }
      return eq + pol;
   }

   public static void addSphereBlocks(List<BlockPos> list, int x1, int y1, int z1, int x2, int y2, int z2, float centerX, float centerY, float centerZ, float radiusX, float radiusY, float radiusZ) {
      forEachSphereRange(x1, y1, z1, x2, y2, z2, centerX, centerY, centerZ, radiusX, radiusY, radiusZ, false, packed -> list.add(BlockPos.of(packed)));
   }

   public static void addHollowSphereBlocks(List<BlockPos> list, int x1, int y1, int z1, int x2, int y2, int z2, float centerX, float centerY, float centerZ, float radiusX, float radiusY, float radiusZ) {
      forEachSphereRange(x1, y1, z1, x2, y2, z2, centerX, centerY, centerZ, radiusX, radiusY, radiusZ, true, packed -> list.add(BlockPos.of(packed)));
   }

   /**
    * Bare-int twin of {@link #addSphereBlocks} / {@link #addHollowSphereBlocks}:
    * same scan order and fringe math, packed longs, no allocation.
    */
   public static void forEachSphereRange(int x1, int y1, int z1, int x2, int y2, int z2, float centerX, float centerY, float centerZ, float radiusX, float radiusY, float radiusZ, boolean hollow, LongConsumer out) {
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
      float innerX = radiusX - 0.6F;
      float innerY = radiusY - 0.6F;
      float innerZ = radiusZ - 0.6F;

      for (int x = minX; x <= maxX; ++x) {
         for (int z = minZ; z <= maxZ; ++z) {
            for (int y = minY; y <= maxY; ++y) {
               if (isInsideEllipsoid(x, y, z, centerX, centerY, centerZ, outerX, outerY, outerZ)
                     && (!hollow || !isInsideEllipsoid(x, y, z, centerX, centerY, centerZ, innerX, innerY, innerZ))) {
                  out.accept(BlockPos.asLong(x, y, z));
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
    protected void forEachTwoPointBlocks(Player player, BlockPos firstPos, BlockPos secondPos, LongConsumer out) {
       BlockPos boundedSecond = limitToBuildRange(player, firstPos, secondPos);
       forEachTwoPointSphereBlocks(firstPos, boundedSecond, ModeOptions.getFill() == ModeOptions.ActionEnum.FULL, out);
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
      boolean full = ModeOptions.getFill() == ModeOptions.ActionEnum.FULL;
      // Rough presize only; the emitter below may yield fewer (hollow) blocks.
      List<BlockPos> list = new ArrayList<>(4096);
      forEachTwoPointSphereBlocks(firstPos, secondPos, full, packed -> list.add(BlockPos.of(packed)));
      return list;
   }

   /** Bare-int twin of {@link #getTwoPointSphereBlocks}: same bounds math, packed longs. */
   static void forEachTwoPointSphereBlocks(BlockPos firstPos, BlockPos secondPos, boolean full, LongConsumer out) {
      double[] params = twoPointSphereParams(firstPos, secondPos);
      double cx = params[0];
      double cy = params[1];
      double cz = params[2];
      float r = (float) params[3];
      if (r <= 0.0F) {
         out.accept(firstPos.asLong());
         return;
      }
      // The surface fringe reaches 0.4 past the radius, so pad by a block.
      int minX = (int) Math.floor(cx - r - 1.0);
      int minY = (int) Math.floor(cy - r - 1.0);
      int minZ = (int) Math.floor(cz - r - 1.0);
      int maxX = (int) Math.ceil(cx + r + 1.0);
      int maxY = (int) Math.ceil(cy + r + 1.0);
      int maxZ = (int) Math.ceil(cz + r + 1.0);
      float centerX = (float) cx;
      float centerY = (float) cy;
      float centerZ = (float) cz;
      forEachSphereRange(minX, minY, minZ, maxX, maxY, maxZ, centerX, centerY, centerZ, r, r, r, !full, out);
   }

   public @Nullable BlockPos findThirdPos(Player player, BlockPos firstPos, BlockPos secondPos, boolean skipRaytrace) {
      // Mid-air-capable resolver kept for the abstract contract: the look
      // ray's closest approach to the sphere center from points 1-2. The live
      // click/preview flow uses directly pointed-at blocks instead (see
      // {@link #usesDirectSecondPoint} and {@link #previewHover}), so this is
      // currently only a fallback for callers without a hover point.
      // skipRaytrace is kept for signature compatibility and ignored.
      double[] base = twoPointSphereParams(firstPos, secondPos);
      Vec3 center = new Vec3(base[0], base[1], base[2]);
      Vec3 eye = BuildPipeline.getPlayerEyePosition(player);
      Vec3 look = BuildPipeline.getPlayerLookVec(player);
      double len = look.length();
      if (len <= 1.0E-9D || !Double.isFinite(len)) {
         return null;
      }
      Vec3 unit = look.scale(1.0D / len);
      double reach = (double) Config.getBuildingReach(player);
      double t = center.subtract(eye).dot(unit);
      if (!Double.isFinite(t) || t <= 0.0D) {
         return null;
      }
      Vec3 hit = eye.add(unit.scale(t));
      if (!RaycastToPlane.isValidPlaneHit(hit, eye, unit, reach)) {
         return null;
      }
      return BlockPos.containing(hit);
   }

    @Override
    public void getPlacementBlocks(BlockSet blocks, Player player, boolean fast) {
       if (!this.threePoint) {
          super.getPlacementBlocks(blocks, player, fast);
          return;
       }
       if (this.clicks == 0 || this.firstBlockEntry == null || this.firstBlockEntry.blockPos == null) {
          return;
       }
       BlockPos firstPos = this.firstBlockEntry.blockPos;
       boolean full = ModeOptions.getFill() == ModeOptions.ActionEnum.FULL;
       BlockPos secondPos = this.clicks >= 2 && this.secondBlockEntry != null && this.secondBlockEntry.blockPos != null
             ? this.secondBlockEntry.blockPos
             : this.previewHover;
       if (secondPos == null) {
          return;
       }
       secondPos = limitToBuildRange(player, firstPos, secondPos);
       if (this.clicks == 1) {
          // Same live sphere the 2-point mode shows.
          blocks.clear();
          if (fast) {
             forEachTwoPointSphereBlocks(firstPos, secondPos, full, blocks::addPacked);
          } else {
             blocks.addAllPositions(getTwoPointSphereBlocks(firstPos, secondPos));
          }
          blocks.firstPos = firstPos;
          blocks.lastPos = secondPos;
          return;
       }
       BlockPos thirdPos = this.storedThird != null ? this.storedThird : this.previewHover;
       if (thirdPos == null) {
          // No handle yet (sky): show the undeformed base sphere.
          blocks.clear();
          if (fast) {
             forEachTwoPointSphereBlocks(firstPos, secondPos, full, blocks::addPacked);
          } else {
             blocks.addAllPositions(getTwoPointSphereBlocks(firstPos, secondPos));
          }
          blocks.firstPos = firstPos;
          blocks.lastPos = secondPos;
          return;
       }
       thirdPos = limitToBuildRange(player, firstPos, thirdPos);
       blocks.clear();
       if (fast) {
          forEachSpheroid(firstPos, secondPos, thirdPos, full, blocks::addPacked);
       } else {
          blocks.addAllPositions(getSphereBlocks(player,
                firstPos.getX(), firstPos.getY(), firstPos.getZ(),
                secondPos.getX(), secondPos.getY(), secondPos.getZ(),
                thirdPos.getX(), thirdPos.getY(), thirdPos.getZ()));
       }
       blocks.firstPos = firstPos;
       blocks.lastPos = thirdPos;
    }

    @Override
    public AABB getClientBoundary(Player player) {
       if (!this.threePoint) {
          return super.getClientBoundary(player);
       }
       if (this.clicks == 0 || this.firstBlockEntry == null || this.firstBlockEntry.blockPos == null) {
          return null;
       }
       BlockPos firstPos = this.firstBlockEntry.blockPos;
       BlockPos secondPos = this.clicks >= 2 && this.secondBlockEntry != null && this.secondBlockEntry.blockPos != null
             ? this.secondBlockEntry.blockPos
             : this.previewHover;
       if (secondPos == null) {
          return null;
       }
       BlockPos boundedSecond = limitToBuildRange(player, firstPos, secondPos);
       if (this.clicks == 1) {
          return getTwoPointBoundary(firstPos, boundedSecond);
       }
       BlockPos thirdPos = this.storedThird != null ? this.storedThird : this.previewHover;
       if (thirdPos == null) {
          return getTwoPointBoundary(firstPos, boundedSecond);
       }
       return getFinalBoundary(firstPos, boundedSecond, limitToBuildRange(player, firstPos, thirdPos));
    }

     public List<BlockPos> getIntermediateBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2) {
        return getTwoPointSphereBlocks(new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2));
     }

     @Override
     protected void forEachIntermediateBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, LongConsumer out) {
        forEachTwoPointSphereBlocks(new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2),
              ModeOptions.getFill() == ModeOptions.ActionEnum.FULL, out);
     }

    public List<BlockPos> getFinalBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, int x3, int y3, int z3) {
       return getSphereBlocks(player, x1, y1, z1, x2, y2, z2, x3, y3, z3);
    }

    @Override
    protected void forEachFinalBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, int x3, int y3, int z3, LongConsumer out) {
       forEachSphere(x1, y1, z1, x2, y2, z2, x3, y3, z3, ModeOptions.getFill() == ModeOptions.ActionEnum.FULL, out);
    }

     @Override
     protected AABB getIntermediateBoundary(BlockPos firstPos, BlockPos clampedSecond) {
        return getTwoPointBoundary(firstPos, clampedSecond);
     }

    @Override
    protected AABB getFinalBoundary(BlockPos firstPos, BlockPos clampedSecond, BlockPos clampedThird) {
       // Tight box around the same spheroid the final pass emits.
       double[] base = twoPointSphereParams(firstPos, clampedSecond);
       double cx = base[0];
       double cy = base[1];
       double cz = base[2];
       double r = base[3];
       if (r <= 0.0D) {
          return toFullBlockAABB(firstPos.getX(), firstPos.getY(), firstPos.getZ(),
                firstPos.getX(), firstPos.getY(), firstPos.getZ());
       }
       double wx = (double) clampedThird.getX() - cx;
       double wy = (double) clampedThird.getY() - cy;
       double wz = (double) clampedThird.getZ() - cz;
       double polar = Math.sqrt(wx * wx + wy * wy + wz * wz);
       if (polar < MIN_POLAR) {
          return getTwoPointBoundary(firstPos, clampedSecond);
       }
       double ux = wx / polar;
       double uy = wy / polar;
       double uz = wz / polar;
       double ex = Math.sqrt(r * r + (polar * polar - r * r) * ux * ux);
       double ey = Math.sqrt(r * r + (polar * polar - r * r) * uy * uy);
       double ez = Math.sqrt(r * r + (polar * polar - r * r) * uz * uz);
       return new AABB(Math.floor(cx - ex), Math.floor(cy - ey), Math.floor(cz - ez),
               Math.ceil(cx + ex), Math.ceil(cy + ey), Math.ceil(cz + ez));
    }


}
