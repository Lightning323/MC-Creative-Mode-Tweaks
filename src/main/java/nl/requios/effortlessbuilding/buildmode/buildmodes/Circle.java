package nl.requios.effortlessbuilding.buildmode.buildmodes;

import java.util.ArrayList;
import java.util.List;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import nl.requios.effortlessbuilding.buildmode.RaycastToPlane;
import nl.requios.effortlessbuilding.buildmode.ThreeClicksBuildMode;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Flat disc in any orientation. The first clicked face is the disc normal
 * (like {@link Dome}); the look ray stops at that imaginary plane even in
 * mid-air (see {@link RaycastToPlane}).
 *
 * <p>Point-count option (reuses {@code POINT_BUILD}): 2 points always draws a
 * true circle (placed on the 2nd click), 3 points adds a perpendicular stretch
 * on the 3rd click to draw an ellipse:</p>
 * <ul>
 *   <li>Corner start: the center is the middle of points 1-2 and both sit on
 *       the circumference (like {@link Sphere}'s two-point placement).</li>
 *   <li>Center start: point 1 is the middle, point 2 sits on the
 *       circumference and sets the radius.</li>
 *   <li>3-point ellipse: points 1-2 fix the first axis as above; the third
 *       point's distance perpendicular to that axis (in the disc plane) sets
 *       the second axis. A negligible stretch collapses back to a circle.</li>
 * </ul>
 */
public class Circle extends ThreeClicksBuildMode {
   /** Outward normal of the disc plane, from the first clicked face. */
   private Direction direction = Direction.UP;
   /** Captured at first click: 3-point mode draws the ellipse, 2-point places a circle. */
   private boolean ellipseThreePoint;

   @Override
   public void initialize() {
      super.initialize();
      this.direction = Direction.UP;
      this.ellipseThreePoint = false;
   }

   /** The first clicked face is the disc's outward direction. */
   @Override
   public void setFirstClickFace(Direction face) {
      this.direction = face;
   }

   @Override
   protected boolean supportsTwoPointBuild() {
      // Always plane-based; the 2pt/3pt circle option is captured separately
      // and must not trigger the raw-click two-point machinery.
      return false;
   }

   @Override
   public boolean onClick(BlockSet blocks, BlockPos clickedPos, Player player) {
      if (this.clicks == 0) {
         this.ellipseThreePoint = ModeOptions.getPointBuild() == ModeOptions.ActionEnum.THREE_POINT_BUILD;
      }
      ++this.clicks;
      if (this.clicks == 1) {
         this.firstBlockEntry = new BlockEntry(clickedPos);
         this.secondBlockEntry = null;
         return false;
      }
      if (!this.ellipseThreePoint) {
         // 2-point circle: place on the 2nd click from the live plane hit.
         BlockPos secondPos = this.findSecondPos(player, this.firstBlockEntry.blockPos, true);
         if (secondPos == null) {
            this.clicks = 1;
            return false;
         }
         this.secondBlockEntry = new BlockEntry(secondPos);
         return true;
      }
      if (this.clicks == 2) {
         BlockPos secondPos = this.findSecondPos(player, this.firstBlockEntry.blockPos, true);
         if (secondPos == null) {
            this.clicks = 1;
            return false;
         }
         this.secondBlockEntry = new BlockEntry(secondPos);
         return false;
      }
      return true;
   }

   @Override
   public AABB getClientBoundary(Player player) {
      if (this.clicks == 0 || this.firstBlockEntry == null || this.firstBlockEntry.blockPos == null) {
         return null;
      }
      BlockPos firstPos = this.firstBlockEntry.blockPos;
      if (!this.ellipseThreePoint || this.clicks == 1) {
         BlockPos secondPos = this.clicks >= 2 && this.secondBlockEntry != null && this.secondBlockEntry.blockPos != null
               ? this.secondBlockEntry.blockPos
               : this.findSecondPos(player, firstPos, true);
         if (secondPos == null) {
            return null;
         }
         return circleBounds(firstPos, limitToBuildRange(player, firstPos, secondPos), this.direction);
      }
      if (this.secondBlockEntry == null || this.secondBlockEntry.blockPos == null) {
         return null;
      }
      BlockPos secondPos = this.secondBlockEntry.blockPos;
      BlockPos thirdPos = this.findThirdPos(player, firstPos, secondPos, true);
      if (thirdPos == null) {
         return null;
      }
      return ellipseBounds(firstPos,
            limitToBuildRange(player, firstPos, secondPos),
            limitToBuildRange(player, firstPos, thirdPos), this.direction);
   }

   @Override
   public void getPlacementBlocks(BlockSet blocks, Player player, boolean fast) {
      if (this.clicks == 0 || this.firstBlockEntry == null || this.firstBlockEntry.blockPos == null) {
         return;
      }
      BlockPos firstPos = this.firstBlockEntry.blockPos;
      if (!this.ellipseThreePoint || this.clicks == 1) {
         BlockPos secondPos = this.clicks >= 2 && this.secondBlockEntry != null && this.secondBlockEntry.blockPos != null
               ? this.secondBlockEntry.blockPos
               : this.findSecondPos(player, firstPos, true);
         if (secondPos == null) {
            return;
         }
         secondPos = limitToBuildRange(player, firstPos, secondPos);
         blocks.clear();
         if (fast) {
            forEachOrientedCircle(firstPos, secondPos, this.direction,
                  ModeOptions.getFill() == ModeOptions.ActionEnum.FULL, blocks::addPacked);
         } else {
            blocks.addAllPositions(getOrientedCircleBlocks(firstPos, secondPos, this.direction));
         }
         blocks.firstPos = firstPos;
         blocks.lastPos = secondPos;
         return;
      }
      if (this.secondBlockEntry == null || this.secondBlockEntry.blockPos == null) {
         return;
      }
      BlockPos secondPos = limitToBuildRange(player, firstPos, this.secondBlockEntry.blockPos);
      BlockPos thirdPos = this.findThirdPos(player, firstPos, this.secondBlockEntry.blockPos, true);
      if (thirdPos == null) {
         return;
      }
      thirdPos = limitToBuildRange(player, firstPos, thirdPos);
      blocks.clear();
      if (fast) {
         forEachOrientedEllipse(firstPos, secondPos, thirdPos, this.direction,
               ModeOptions.getFill() == ModeOptions.ActionEnum.FULL, blocks::addPacked);
      } else {
         blocks.addAllPositions(getOrientedEllipseBlocks(firstPos, secondPos, thirdPos, this.direction));
      }
      blocks.firstPos = firstPos;
      blocks.lastPos = thirdPos;
   }

   @Override
   public @Nullable BlockPos getIntermediatePos() {
      return this.ellipseThreePoint && this.secondBlockEntry != null ? this.secondBlockEntry.blockPos : null;
   }

   @Override
   public ModeOptions.ActionEnum getPointBuildAction() {
      return this.ellipseThreePoint ? ModeOptions.ActionEnum.THREE_POINT_BUILD : ModeOptions.ActionEnum.TWO_POINT_BUILD;
   }

   /**
    * Action-bar readout: 2-point selections are perfect circles by
    * construction; ellipse selections report a perfect circle when both axes
    * match, otherwise the simplified axis ratio (larger first).
    */
   @Override
   public @Nullable String getShapeInfo(Player player) {
      if (this.clicks == 0 || this.firstBlockEntry == null || this.firstBlockEntry.blockPos == null) {
         return null;
      }
      BlockPos firstPos = this.firstBlockEntry.blockPos;
      BlockPos liveSecond = this.findSecondPos(player, firstPos, true);
      BlockPos storedSecond = this.secondBlockEntry != null ? this.secondBlockEntry.blockPos : null;
      BlockPos secondPos = this.clicks >= 2 && storedSecond != null ? storedSecond : liveSecond;
      if (secondPos == null) {
         return null;
      }
      secondPos = limitToBuildRange(player, firstPos, secondPos);
      if (!this.ellipseThreePoint || this.clicks == 1) {
         return circleInfo(firstPos, secondPos, this.direction);
      }
      BlockPos thirdPos = this.findThirdPos(player, firstPos, secondPos, true);
      if (thirdPos == null) {
         return circleInfo(firstPos, secondPos, this.direction);
      }
      return ellipseInfo(firstPos, secondPos, limitToBuildRange(player, firstPos, thirdPos), this.direction);
   }

   /** "perfect circle, diameter X", or null for a single block (the count line suffices). */
   static @Nullable String circleInfo(BlockPos firstPos, BlockPos secondPos, Direction normal) {
      Disc disc = Disc.circle(firstPos, secondPos, normal);
      if (disc.ra < MIN_STRETCH) {
         return null;
      }
      return "perfect circle, diameter " + formatBlocks(2.0D * disc.ra);
   }

   /** Perfect circle when both axes match, otherwise the simplified axis ratio. */
   static @Nullable String ellipseInfo(BlockPos firstPos, BlockPos secondPos, BlockPos thirdPos, Direction normal) {
      Disc disc = Disc.ellipse(firstPos, secondPos, thirdPos, normal);
      if (disc.ra < MIN_STRETCH) {
         return null;
      }
      double effB = disc.rb > 0.0D ? disc.rb : disc.ra;
      if (Math.abs(disc.ra - effB) <= 1.0E-6D) {
         return "perfect circle, diameter " + formatBlocks(2.0D * disc.ra);
      }
      return "ellipse " + ratio(disc.ra, effB);
   }

   private static String formatBlocks(double value) {
      long rounded = Math.round(value);
      if (Math.abs(value - rounded) < 1.0E-6D) {
         return Long.toString(rounded);
      }
      return String.format(java.util.Locale.ROOT, "%.1f", value);
   }

   /** Simplified larger-first ratio, e.g. 5:4 or 1:2 (halves cleared first). */
   private static String ratio(double a, double b) {
      long x = Math.round(Math.max(a, b) * 2.0D);
      long y = Math.round(Math.min(a, b) * 2.0D);
      if (y <= 0L) {
         return x + ":0";
      }
      long g = gcd(x, y);
      return (x / g) + ":" + (y / g);
   }

   private static long gcd(long a, long b) {
      while (b != 0L) {
         long t = a % b;
         a = b;
         b = t;
      }
      return Math.max(a, 1L);
   }

   @Override
   public List<BlockPos> getServerBlocks(Player player, BlockPos firstPos, BlockPos secondPos, @Nullable BlockPos thirdPos, @Nullable BlockPos fourthPos) {
      // 2-point placements arrive with a null third point and still place a circle.
      return this.getPlacementBlocks(player, firstPos, secondPos, thirdPos, fourthPos);
   }

   @Override
   protected List<BlockPos> getIntermediateBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2) {
      return getOrientedCircleBlocks(new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2), this.direction);
   }

   @Override
   protected List<BlockPos> getFinalBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, int x3, int y3, int z3) {
      return getOrientedEllipseBlocks(new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2), new BlockPos(x3, y3, z3), this.direction);
   }

   @Override
   protected void forEachIntermediateBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, LongConsumer out) {
      forEachOrientedCircle(new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2), this.direction,
            ModeOptions.getFill() == ModeOptions.ActionEnum.FULL, out);
   }

   @Override
   protected void forEachFinalBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, int x3, int y3, int z3, LongConsumer out) {
      forEachOrientedEllipse(new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2), new BlockPos(x3, y3, z3), this.direction,
            ModeOptions.getFill() == ModeOptions.ActionEnum.FULL, out);
   }

   protected BlockPos findSecondPos(Player player, BlockPos firstPos, boolean skipRaytrace) {
      // Plane-only hit: registers even when the vanilla raycast hits no block.
      // skipRaytrace is kept for signature compatibility and ignored.
      return RaycastToPlane.findNormalPlane(this.direction.getAxis(), player, firstPos);
   }

   protected BlockPos findThirdPos(Player player, BlockPos firstPos, BlockPos secondPos, boolean skipRaytrace) {
      // The stretch point stays coplanar: same disc plane through the anchor.
      // skipRaytrace is kept for signature compatibility and ignored.
      return RaycastToPlane.findNormalPlane(this.direction.getAxis(), player, firstPos);
   }

   // -- oriented disc/ellipse shapes (all facings) ----------------------------

   /** Fill fringe (outer) and hollow ring (inner) widths, in blocks. Matches the legacy flat disc. */
   private static final float OUTER_FRINGE = 0.4F;
   private static final float INNER_FRINGE = 0.6F;
   /** Perpendicular stretch below half a block collapses back to a circle. */
   private static final double MIN_STRETCH = 0.5D;

   /** True circle through the first two points on the disc plane. */
   public static List<BlockPos> getOrientedCircleBlocks(BlockPos firstPos, BlockPos secondPos, Direction normal) {
      List<BlockPos> list = new ArrayList<>(4096);
      forEachOrientedCircle(firstPos, secondPos, normal,
            ModeOptions.getFill() == ModeOptions.ActionEnum.FULL, packed -> list.add(BlockPos.of(packed)));
      return list;
   }

   /** Bare-int twin of {@link #getOrientedCircleBlocks}: same blocks, packed longs, no allocation. */
   public static void forEachOrientedCircle(BlockPos firstPos, BlockPos secondPos, Direction normal, boolean full, LongConsumer out) {
      Disc disc = Disc.circle(firstPos, secondPos, normal);
      disc.emit(full, out);
   }

   /**
    * Ellipse from three coplanar points: points 1-2 fix the first axis (same
    * center/radius rules as {@link #getOrientedCircleBlocks}), the third
    * point's distance perpendicular to that axis sets the second axis. A
    * degenerate third point (on the axis, or equal to the second) collapses to
    * a circle, so 2-point packets ({@code thirdPos == secondPos}) just work.
    */
   public static List<BlockPos> getOrientedEllipseBlocks(BlockPos firstPos, BlockPos secondPos, BlockPos thirdPos, Direction normal) {
      List<BlockPos> list = new ArrayList<>(4096);
      forEachOrientedEllipse(firstPos, secondPos, thirdPos, normal,
            ModeOptions.getFill() == ModeOptions.ActionEnum.FULL, packed -> list.add(BlockPos.of(packed)));
      return list;
   }

   /** Bare-int twin of {@link #getOrientedEllipseBlocks}: same blocks, packed longs, no allocation. */
   public static void forEachOrientedEllipse(BlockPos firstPos, BlockPos secondPos, BlockPos thirdPos, Direction normal, boolean full, LongConsumer out) {
      Disc disc = Disc.ellipse(firstPos, secondPos, thirdPos, normal);
      disc.emit(full, out);
   }

   /** Full-block preview boundary of the 2-point circle. */
   static AABB circleBounds(BlockPos firstPos, BlockPos secondPos, Direction normal) {
      return Disc.circle(firstPos, secondPos, normal).bounds();
   }

   /** Full-block preview boundary of the 3-point ellipse. */
   static AABB ellipseBounds(BlockPos firstPos, BlockPos secondPos, BlockPos thirdPos, Direction normal) {
      return Disc.ellipse(firstPos, secondPos, thirdPos, normal).bounds();
   }

    /**
     * Disc on the plane through {@code base} with the given normal: center
     * ({@code cu}, {@code cv}) in plane coordinates, semi-axes {@code ra}
     * along unit ({@code ax}, {@code ay}) and {@code rb} along the
     * perpendicular. A near-zero {@code rb} is a circle.
     *
     * <p>Package-visible so {@link Cone} can reuse the same base math for its
     * tapered layers (same center/axes, scaled radii per height).</p>
     */
    record Disc(Direction normal, Direction.Axis uAxis, Direction.Axis vAxis, int base,
                       double cu, double cv, double ax, double ay, double ra, double rb) {
      static Disc circle(BlockPos firstPos, BlockPos secondPos, Direction normal) {
         Direction.Axis[] axes = RaycastToPlane.perpendicularAxes(normal.getAxis());
         Direction.Axis uAxis = axes[0];
         Direction.Axis vAxis = axes[1];
         int base = RaycastToPlane.coordinate(firstPos, normal.getAxis());
         double u1 = RaycastToPlane.coordinate(firstPos, uAxis);
         double v1 = RaycastToPlane.coordinate(firstPos, vAxis);
         double u2 = RaycastToPlane.coordinate(secondPos, uAxis);
         double v2 = RaycastToPlane.coordinate(secondPos, vAxis);
         if (ModeOptions.getCircleStart() == ModeOptions.ActionEnum.CIRCLE_START_CORNER) {
            double cu = (u1 + u2) / 2.0D;
            double cv = (v1 + v2) / 2.0D;
            double du = u2 - u1;
            double dv = v2 - v1;
            double r = Math.sqrt(du * du + dv * dv) / 2.0D;
            double[] unit = unit(du, dv);
            return new Disc(normal, uAxis, vAxis, base, cu, cv, unit[0], unit[1], r, 0.0D);
         }
         double du = u2 - u1;
         double dv = v2 - v1;
         double r = Math.sqrt(du * du + dv * dv);
         double[] unit = unit(du, dv);
         return new Disc(normal, uAxis, vAxis, base, u1, v1, unit[0], unit[1], r, 0.0D);
      }

      static Disc ellipse(BlockPos firstPos, BlockPos secondPos, BlockPos thirdPos, Direction normal) {
         Disc baseDisc = circle(firstPos, secondPos, normal);
         if (baseDisc.ra <= 0.0D) {
            return baseDisc;
         }
         // Perpendicular unit (-ay, ax) in plane coordinates.
         double bx = -baseDisc.ay;
         double by = baseDisc.ax;
         double wu = RaycastToPlane.coordinate(thirdPos, baseDisc.uAxis) - baseDisc.cu;
         double wv = RaycastToPlane.coordinate(thirdPos, baseDisc.vAxis) - baseDisc.cv;
         double rb = Math.abs(wu * bx + wv * by);
         if (rb < MIN_STRETCH) {
            return baseDisc;
         }
         return new Disc(baseDisc.normal, baseDisc.uAxis, baseDisc.vAxis, baseDisc.base,
               baseDisc.cu, baseDisc.cv, baseDisc.ax, baseDisc.ay, baseDisc.ra, rb);
      }

      private static double[] unit(double du, double dv) {
         double len = Math.sqrt(du * du + dv * dv);
         if (len <= 0.0D) {
            return new double[]{1.0D, 0.0D};
         }
         return new double[]{du / len, dv / len};
      }

      /** Normalized radius test: {@code q < 1} fills, the inner {@code q} cuts the hollow ring. */
      private double normalized(double u, double v, double ra, double rb) {
         double du = u - this.cu;
         double dv = v - this.cv;
         double pa = project(du, dv, this.ax, this.ay, ra);
         double bx = -this.ay;
         double by = this.ax;
         double pb = project(du, dv, bx, by, rb);
         if (!Double.isFinite(pa) || !Double.isFinite(pb)) {
            return Double.POSITIVE_INFINITY;
         }
         return pa * pa + pb * pb;
      }

      private static double project(double du, double dv, double ux, double uy, double radius) {
         if (radius > 0.0D) {
            return (du * ux + dv * uy) / radius;
         }
         return du == 0.0D && dv == 0.0D ? 0.0D : Double.POSITIVE_INFINITY;
      }

      void emit(boolean full, LongConsumer out) {
         // Plane extents of the (possibly rotated) ellipse, plus a block for the ring fringe.
         // A circle (rb == 0) spans its radius in every direction, not just along its axis.
         double effB = this.rb > 0.0D ? this.rb : this.ra;
         double bx = -this.ay;
         double by = this.ax;
         double halfU = Math.sqrt(this.ra * this.ax * (this.ra * this.ax) + effB * bx * (effB * bx)) + 1.0D;
         double halfV = Math.sqrt(this.ra * this.ay * (this.ra * this.ay) + effB * by * (effB * by)) + 1.0D;
         int minU = (int) Math.floor(this.cu - halfU);
         int maxU = (int) Math.ceil(this.cu + halfU);
         int minV = (int) Math.floor(this.cv - halfV);
         int maxV = (int) Math.ceil(this.cv + halfV);
         double outerA = this.ra + OUTER_FRINGE;
         double outerB = effB + OUTER_FRINGE;
         double innerA = this.ra - INNER_FRINGE;
         double innerB = effB - INNER_FRINGE;
         boolean hasHole = innerA > 0.0D && innerB > 0.0D;
         for (int u = minU; u <= maxU; u++) {
            for (int v = minV; v <= maxV; v++) {
               if (this.normalized(u, v, outerA, outerB) < 1.0D
                     && (full || !hasHole || this.normalized(u, v, innerA, innerB) > 1.0D)) {
                  out.accept(BlockPos.asLong(toX(u, v), toY(u, v), toZ(u, v)));
               }
            }
         }
      }

      private int toX(int u, int v) {
         return switch (this.normal.getAxis()) {
            case X -> this.base;
            case Y -> this.uAxis == Direction.Axis.X ? u : v;
            case Z -> this.uAxis == Direction.Axis.X ? u : v;
         };
      }

      private int toY(int u, int v) {
         return switch (this.normal.getAxis()) {
            case X -> this.uAxis == Direction.Axis.Y ? u : v;
            case Y -> this.base;
            case Z -> this.uAxis == Direction.Axis.Y ? u : v;
         };
      }

      private int toZ(int u, int v) {
         return switch (this.normal.getAxis()) {
            case X -> this.uAxis == Direction.Axis.Z ? u : v;
            case Y -> this.uAxis == Direction.Axis.Z ? u : v;
            case Z -> this.base;
         };
      }

      /**
       * Block containing the disc center: the flood-fill seed hint for
       * disc-based shapes (same center the 2-point circle is drawn around).
       */
      BlockPos centerBlock() {
         double x = switch (this.normal.getAxis()) {
            case X -> this.base;
            case Y, Z -> this.uAxis == Direction.Axis.X ? this.cu : this.cv;
         };
         double y = switch (this.normal.getAxis()) {
            case Y -> this.base;
            case X, Z -> this.uAxis == Direction.Axis.Y ? this.cu : this.cv;
         };
         double z = switch (this.normal.getAxis()) {
            case Z -> this.base;
            case X, Y -> this.uAxis == Direction.Axis.Z ? this.cu : this.cv;
         };
         return BlockPos.containing(x, y, z);
      }

      AABB bounds() {
         double effB = this.rb > 0.0D ? this.rb : this.ra;
         double bx = -this.ay;
         double by = this.ax;
         double halfU = Math.sqrt(this.ra * this.ax * (this.ra * this.ax) + effB * bx * (effB * bx)) + 1.0D;
         double halfV = Math.sqrt(this.ra * this.ay * (this.ra * this.ay) + effB * by * (effB * by)) + 1.0D;
         int minU = (int) Math.floor(this.cu - halfU);
         int maxU = (int) Math.ceil(this.cu + halfU);
         int minV = (int) Math.floor(this.cv - halfV);
         int maxV = (int) Math.ceil(this.cv + halfV);
         return switch (this.normal.getAxis()) {
            case X -> toFullBlockAABB(this.base, minU, minV, this.base, maxU, maxV);
            case Y -> toFullBlockAABB(minU, this.base, minV, maxU, this.base, maxV);
            case Z -> toFullBlockAABB(minU, minV, this.base, maxU, maxV, this.base);
         };
      }
   }

   // -- legacy horizontal disc (kept for Cylinder/Sphere) ---------------------
   // Cylinder and Sphere build their footprints through these entry points, so
   // the axis-aligned bounding-box semantics below must not change.

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
}
