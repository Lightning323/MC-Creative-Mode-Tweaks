package nl.requios.effortlessbuilding.buildmode.buildmodes;

import java.util.ArrayList;
import java.util.List;
import nl.requios.effortlessbuilding.buildmode.BuildModes;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import nl.requios.effortlessbuilding.buildmode.ThreeClicksBuildMode;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipeline;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lightning323.creative_mode_tweaks.Config;

/** Builds a triangular or four-sided pyramid outward from the face clicked first. */
public class Pyramid extends ThreeClicksBuildMode {
   private Direction direction = Direction.UP;

   @Override
   public void initialize() {
      super.initialize();
      this.direction = Direction.UP;
   }

   /** The first clicked face determines the base plane and the outward-facing tip. */
   @Override
   public void setFirstClickFace(Direction face) {
      this.direction = face;
   }

   @Override
   protected BlockPos findSecondPos(Player player, BlockPos firstPos, boolean skipRaytrace) {
      Vec3 start = BuildPipeline.getPlayerEyePosition(player);
      Vec3 look = BuildPipeline.getPlayerLookVec(player);
      Vec3 planeBound = switch (this.direction.getAxis()) {
         case X -> BuildModes.findXBound(firstPos.getX(), start, look);
         case Y -> BuildModes.findYBound(firstPos.getY(), start, look);
         case Z -> BuildModes.findZBound(firstPos.getZ(), start, look);
      };
      double distanceToPlayerSq = planeBound.subtract(start).lengthSqr();
      return BuildModes.isCriteriaValid(start, look, Config.getBuildingReach(player), player, skipRaytrace, planeBound, planeBound, distanceToPlayerSq)
         ? BlockPos.containing(planeBound)
         : null;
   }

   @Override
   protected BlockPos findThirdPos(Player player, BlockPos firstPos, BlockPos secondPos, boolean skipRaytrace) {
      Vec3 start = BuildPipeline.getPlayerEyePosition(player);
      Vec3 look = BuildPipeline.getPlayerLookVec(player);
      List<HeightCandidate> candidates = new ArrayList<>(2);

      for (Direction.Axis axis : perpendicularAxes(this.direction.getAxis())) {
         Vec3 planeBound = findAxisBound(axis, coordinate(secondPos, axis), start, look);
         Vec3 lineBound = pointOnDirectionAxis(planeBound, secondPos, this.direction.getAxis());
         candidates.add(new HeightCandidate(planeBound, lineBound, start));
      }

      double reach = Config.getBuildingReach(player);
      candidates.removeIf(candidate -> !BuildModes.isCriteriaValid(start, look, reach, player, skipRaytrace, candidate.lineBound, candidate.planeBound, candidate.distanceToPlayerSq));
      if (candidates.isEmpty()) {
         return null;
      }

      HeightCandidate selected = candidates.getFirst();
      for (int i = 1; i < candidates.size(); ++i) {
         HeightCandidate candidate = candidates.get(i);
         if (candidate.distanceToLineSq < selected.distanceToLineSq || candidate.distanceToLineSq < 2.0D && selected.distanceToLineSq < 2.0D && candidate.distanceToPlayerSq < selected.distanceToPlayerSq) {
            selected = candidate;
         }
      }

      return BlockPos.containing(selected.lineBound);
   }

   @Override
   protected List<BlockPos> getIntermediateBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2) {
      return getBaseBlocks(new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2));
   }

    @Override
    protected List<BlockPos> getFinalBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, int x3, int y3, int z3) {
       return getPyramidBlocks(new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2), new BlockPos(x3, y3, z3));
    }

    @Override
    protected AABB getIntermediateBoundary(BlockPos firstPos, BlockPos clampedSecond) {
       return pyramidBounds(firstPos, clampedSecond, firstPos);
    }

    @Override
    protected AABB getFinalBoundary(BlockPos firstPos, BlockPos clampedSecond, BlockPos clampedThird) {
       return pyramidBounds(firstPos, clampedSecond, clampedThird);
    }

    /**
     * Reuses the real base-corner math (square vs triangle, corner vs center)
     * so squared/triangular bases that extend beyond the raw second point are
     * still inside the boundary. The normal axis spans base..tip.
     */
    private AABB pyramidBounds(BlockPos firstPos, BlockPos secondPos, BlockPos thirdPos) {
       PyramidBounds bounds = new PyramidBounds(firstPos, secondPos, thirdPos, this.direction);
       int minNormal = Math.min(bounds.base, bounds.tip);
       int maxNormal = Math.max(bounds.base, bounds.tip);
       return switch (bounds.normalAxis) {
          case X -> toFullBlockAABB(minNormal, bounds.minU, bounds.minV, maxNormal, bounds.maxU, bounds.maxV);
          case Y -> toFullBlockAABB(bounds.minU, minNormal, bounds.minV, bounds.maxU, maxNormal, bounds.maxV);
          case Z -> toFullBlockAABB(bounds.minU, bounds.minV, minNormal, bounds.maxU, bounds.maxV, maxNormal);
       };
    }

   private List<BlockPos> getBaseBlocks(BlockPos firstPos, BlockPos secondPos) {
      PyramidBounds bounds = new PyramidBounds(firstPos, secondPos, firstPos, this.direction);
      List<BlockPos> blocks = new ArrayList<>();

      for (int u = bounds.minU; u <= bounds.maxU; ++u) {
         for (int v = bounds.minV; v <= bounds.maxV; ++v) {
            if (isInside(bounds, u, v, bounds.base) && (ModeOptions.getFill() == ModeOptions.ActionEnum.FULL || isBaseEdge(bounds, u, v))) {
               blocks.add(bounds.position(u, v, bounds.base));
            }
         }
      }

      return blocks;
   }

   private List<BlockPos> getPyramidBlocks(BlockPos firstPos, BlockPos secondPos, BlockPos thirdPos) {
      PyramidBounds bounds = new PyramidBounds(firstPos, secondPos, thirdPos, this.direction);
      List<BlockPos> blocks = new ArrayList<>();
      int normalStep = this.direction.getAxisDirection().getStep();

      for (int normal = bounds.base; normalStep > 0 ? normal <= bounds.tip : normal >= bounds.tip; normal += normalStep) {
         for (int u = bounds.minU; u <= bounds.maxU; ++u) {
            for (int v = bounds.minV; v <= bounds.maxV; ++v) {
               if (isInside(bounds, u, v, normal) && (ModeOptions.getFill() == ModeOptions.ActionEnum.FULL || isSurface(bounds, u, v, normal, normalStep))) {
                  blocks.add(bounds.position(u, v, normal));
               }
            }
         }
      }

      return blocks;
   }

   private static boolean isInside(PyramidBounds bounds, int u, int v, int normal) {
      int normalDistance = (normal - bounds.base) * bounds.normalStep;
      if (normalDistance < 0 || normalDistance > bounds.height) {
         return false;
      }

      if (normalDistance == bounds.height && bounds.height > 0) {
         return u == (int)Math.round(bounds.centerU) && v == (int)Math.round(bounds.centerV);
      }

      double progress = bounds.height == 0 ? 0.0D : (double)normalDistance / (double)bounds.height;
      return bounds.contains(u, v, progress);
   }

   private static boolean isBaseEdge(PyramidBounds bounds, int u, int v) {
      return !isInside(bounds, u + 1, v, bounds.base)
         || !isInside(bounds, u - 1, v, bounds.base)
         || !isInside(bounds, u, v + 1, bounds.base)
         || !isInside(bounds, u, v - 1, bounds.base);
   }

   /** A hollow pyramid includes its sloped shell and base rim. */
   private static boolean isSurface(PyramidBounds bounds, int u, int v, int normal, int normalStep) {
      return !isInside(bounds, u + 1, v, normal)
         || !isInside(bounds, u - 1, v, normal)
         || !isInside(bounds, u, v + 1, normal)
         || !isInside(bounds, u, v - 1, normal)
         || !isInside(bounds, u, v, normal + normalStep);
   }

   private static double lerp(double start, double end, double progress) {
      return start + (end - start) * progress;
   }

   private static Direction.Axis[] perpendicularAxes(Direction.Axis normalAxis) {
      return switch (normalAxis) {
         case X -> new Direction.Axis[]{Direction.Axis.Y, Direction.Axis.Z};
         case Y -> new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z};
         case Z -> new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Y};
      };
   }

   private static Vec3 findAxisBound(Direction.Axis axis, int coordinate, Vec3 start, Vec3 look) {
      return switch (axis) {
         case X -> BuildModes.findXBound(coordinate, start, look);
         case Y -> BuildModes.findYBound(coordinate, start, look);
         case Z -> BuildModes.findZBound(coordinate, start, look);
      };
   }

   private static Vec3 pointOnDirectionAxis(Vec3 point, BlockPos base, Direction.Axis directionAxis) {
      return switch (directionAxis) {
         case X -> new Vec3(point.x, base.getY(), base.getZ());
         case Y -> new Vec3(base.getX(), point.y, base.getZ());
         case Z -> new Vec3(base.getX(), base.getY(), point.z);
      };
   }

   private static int coordinate(BlockPos pos, Direction.Axis axis) {
      return switch (axis) {
         case X -> pos.getX();
         case Y -> pos.getY();
         case Z -> pos.getZ();
      };
   }

   private static class HeightCandidate {
      private final Vec3 planeBound;
      private final Vec3 lineBound;
      private final double distanceToLineSq;
      private final double distanceToPlayerSq;

      private HeightCandidate(Vec3 planeBound, Vec3 lineBound, Vec3 start) {
         this.planeBound = planeBound;
         this.lineBound = lineBound;
         this.distanceToLineSq = lineBound.subtract(planeBound).lengthSqr();
         this.distanceToPlayerSq = planeBound.subtract(start).lengthSqr();
      }
   }

   private static class Point {
      private final double u;
      private final double v;

      private Point(double u, double v) {
         this.u = u;
         this.v = v;
      }

      private Point moveToward(double targetU, double targetV, double progress) {
         return new Point(lerp(this.u, targetU, progress), lerp(this.v, targetV, progress));
      }

      private Point translate(double offsetU, double offsetV) {
         return new Point(this.u + offsetU, this.v + offsetV);
      }
   }

   private static class PyramidBounds {
      private final Direction.Axis normalAxis;
      private final Direction.Axis uAxis;
      private final Direction.Axis vAxis;
      private final int base;
      private final int tip;
      private final int normalStep;
      private final int height;
      private final int minU;
      private final int maxU;
      private final int minV;
      private final int maxV;
      private final double centerU;
      private final double centerV;
      private final Point[] corners;

      private PyramidBounds(BlockPos firstPos, BlockPos secondPos, BlockPos thirdPos, Direction direction) {
         this.normalAxis = direction.getAxis();
         Direction.Axis[] axes = perpendicularAxes(this.normalAxis);
         this.uAxis = axes[0];
         this.vAxis = axes[1];
         this.base = coordinate(firstPos, this.normalAxis);
         this.normalStep = direction.getAxisDirection().getStep();
         int firstU = coordinate(firstPos, this.uAxis);
         int firstV = coordinate(firstPos, this.vAxis);
         int secondU = coordinate(secondPos, this.uAxis);
         int secondV = coordinate(secondPos, this.vAxis);
         this.height = Math.abs(coordinate(thirdPos, this.normalAxis) - this.base);
         this.tip = this.base + this.normalStep * this.height;
         this.corners = ModeOptions.getSides() == ModeOptions.ActionEnum.THREE_SIDED
            ? createTriangle(firstU, firstV, secondU, secondV)
            : createSquare(firstU, firstV, secondU, secondV);

         double centerU = 0.0D;
         double centerV = 0.0D;
         double minU = Double.POSITIVE_INFINITY;
         double maxU = Double.NEGATIVE_INFINITY;
         double minV = Double.POSITIVE_INFINITY;
         double maxV = Double.NEGATIVE_INFINITY;

         for (Point corner : this.corners) {
            centerU += corner.u;
            centerV += corner.v;
            minU = Math.min(minU, corner.u);
            maxU = Math.max(maxU, corner.u);
            minV = Math.min(minV, corner.v);
            maxV = Math.max(maxV, corner.v);
         }

         this.centerU = centerU / (double)this.corners.length;
         this.centerV = centerV / (double)this.corners.length;
         this.minU = (int)Math.floor(minU);
         this.maxU = (int)Math.ceil(maxU);
         this.minV = (int)Math.floor(minV);
         this.maxV = (int)Math.ceil(maxV);
      }

      /** Both base shapes are regular: an equilateral triangle or a square. */
      private static Point[] createSquare(int firstU, int firstV, int secondU, int secondV) {
         int deltaU = secondU - firstU;
         int deltaV = secondV - firstV;
         int size = Math.max(Math.abs(deltaU), Math.abs(deltaV));
         if (ModeOptions.getCircleStart() == ModeOptions.ActionEnum.CIRCLE_START_CENTER) {
            return new Point[]{
               new Point(firstU - size, firstV - size),
               new Point(firstU + size, firstV - size),
               new Point(firstU + size, firstV + size),
               new Point(firstU - size, firstV + size)
            };
         }

         int stepU = signOrPositive(deltaU);
         int stepV = signOrPositive(deltaV);
         return new Point[]{
            new Point(firstU, firstV),
            new Point(firstU + stepU * size, firstV),
            new Point(firstU + stepU * size, firstV + stepV * size),
            new Point(firstU, firstV + stepV * size)
         };
      }

      private static Point[] createTriangle(int firstU, int firstV, int secondU, int secondV) {
         int deltaU = secondU - firstU;
         int deltaV = secondV - firstV;
         boolean baseAlongU = Math.abs(deltaU) >= Math.abs(deltaV);
         int baseDelta = Math.abs(baseAlongU ? deltaU : deltaV);
         int perpendicularDelta = Math.abs(baseAlongU ? deltaV : deltaU);
         int baseStep = signOrPositive(baseAlongU ? deltaU : deltaV);
         int perpendicularStep = signOrPositive(baseAlongU ? deltaV : deltaU);
         double baseU = baseAlongU ? baseStep : 0.0D;
         double baseV = baseAlongU ? 0.0D : baseStep;
         double perpendicularU = baseAlongU ? 0.0D : perpendicularStep;
         double perpendicularV = baseAlongU ? perpendicularStep : 0.0D;
         double side;

         if (ModeOptions.getCircleStart() == ModeOptions.ActionEnum.CIRCLE_START_CENTER) {
            side = Math.max(2.0D * (double)baseDelta, Math.sqrt(3.0D) * (double)perpendicularDelta);
            side = Math.max(2.0D, Math.ceil(side));
            double height = Math.sqrt(3.0D) * side / 2.0D;
            Point center = new Point(firstU, firstV);
            Point firstBaseCorner = center.translate(-baseU * side / 2.0D - perpendicularU * height / 3.0D, -baseV * side / 2.0D - perpendicularV * height / 3.0D);
            Point secondBaseCorner = center.translate(baseU * side / 2.0D - perpendicularU * height / 3.0D, baseV * side / 2.0D - perpendicularV * height / 3.0D);
            Point tipCorner = center.translate(perpendicularU * height * 2.0D / 3.0D, perpendicularV * height * 2.0D / 3.0D);
            return new Point[]{firstBaseCorner, secondBaseCorner, tipCorner};
         }

         side = Math.max((double)baseDelta, (double)perpendicularDelta * 2.0D / Math.sqrt(3.0D));
         side = Math.max(2.0D, Math.ceil(side));
         double height = Math.sqrt(3.0D) * side / 2.0D;
         Point firstBaseCorner = new Point(firstU, firstV);
         Point secondBaseCorner = firstBaseCorner.translate(baseU * side, baseV * side);
         Point tipCorner = firstBaseCorner.translate(baseU * side / 2.0D + perpendicularU * height, baseV * side / 2.0D + perpendicularV * height);
         return new Point[]{firstBaseCorner, secondBaseCorner, tipCorner};
      }

      private boolean contains(int u, int v, double progress) {
         boolean hasPositive = false;
         boolean hasNegative = false;

         for (int i = 0; i < this.corners.length; ++i) {
            Point first = this.corners[i].moveToward(this.centerU, this.centerV, progress);
            Point second = this.corners[(i + 1) % this.corners.length].moveToward(this.centerU, this.centerV, progress);
            double cross = ((double)u - first.u) * (second.v - first.v) - ((double)v - first.v) * (second.u - first.u);
            hasPositive |= cross > 0.001D;
            hasNegative |= cross < -0.001D;
            if (hasPositive && hasNegative) {
               return false;
            }
         }

         return true;
      }

      private static int signOrPositive(int value) {
         return value < 0 ? -1 : 1;
      }

      private BlockPos position(int u, int v, int normal) {
         return switch (this.normalAxis) {
            case X -> new BlockPos(normal, u, v);
            case Y -> new BlockPos(u, normal, v);
            case Z -> new BlockPos(u, v, normal);
         };
      }
   }
}
