package nl.requios.effortlessbuilding.buildmode.buildmodes;

import java.util.ArrayList;
import java.util.List;
import nl.requios.effortlessbuilding.buildmode.BuildModes;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import nl.requios.effortlessbuilding.buildmode.ThreeClicksBuildMode;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipeline;
import org.lightning323.creative_mode_tweaks.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Builds a hemisphere extending away from the face selected on the first click. */
public class Dome extends ThreeClicksBuildMode {
   private Direction direction = Direction.UP;

   @Override
   public void initialize() {
      super.initialize();
      this.direction = Direction.UP;
   }

   /** The first clicked face is the dome's outward direction. */
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
       BlockPos firstPos = new BlockPos(x1, y1, z1);
       BlockPos secondPos = new BlockPos(x2, y2, z2);
       return getBaseBlocks(firstPos, secondPos);
    }

    @Override
    protected List<BlockPos> getFinalBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, int x3, int y3, int z3) {
       BlockPos firstPos = new BlockPos(x1, y1, z1);
       BlockPos secondPos = new BlockPos(x2, y2, z2);
       BlockPos thirdPos = new BlockPos(x3, y3, z3);
       return getDomeBlocks(firstPos, secondPos, thirdPos);
    }

    @Override
    protected AABB getIntermediateBoundary(BlockPos firstPos, BlockPos clampedSecond) {
       return domeBounds(firstPos, clampedSecond, firstPos);
    }

    @Override
    protected AABB getFinalBoundary(BlockPos firstPos, BlockPos clampedSecond, BlockPos clampedThird) {
       return domeBounds(firstPos, clampedSecond, clampedThird);
    }

    /**
     * Mirrors DomeBounds' U/V handling: corner mode spans first..second,
     * center mode mirrors second across first. The normal axis always spans
     * base..tip.
     */
    private AABB domeBounds(BlockPos firstPos, BlockPos secondPos, BlockPos thirdPos) {
       Direction.Axis normalAxis = this.direction.getAxis();
       Direction.Axis[] axes = perpendicularAxes(normalAxis);
       Direction.Axis uAxis = axes[0];
       Direction.Axis vAxis = axes[1];
       int base = coordinate(firstPos, normalAxis);
       int tipCoord = coordinate(thirdPos, normalAxis);
       int firstU = coordinate(firstPos, uAxis);
       int secondU = coordinate(secondPos, uAxis);
       int firstV = coordinate(firstPos, vAxis);
       int secondV = coordinate(secondPos, vAxis);
       int minU;
       int maxU;
       int minV;
       int maxV;
       if (ModeOptions.getCircleStart() == ModeOptions.ActionEnum.CIRCLE_START_CORNER) {
          minU = Math.min(firstU, secondU);
          maxU = Math.max(firstU, secondU);
          minV = Math.min(firstV, secondV);
          maxV = Math.max(firstV, secondV);
       } else {
          minU = Math.min(secondU, 2 * firstU - secondU);
          maxU = Math.max(secondU, 2 * firstU - secondU);
          minV = Math.min(secondV, 2 * firstV - secondV);
          maxV = Math.max(secondV, 2 * firstV - secondV);
       }
       int minNormal = Math.min(base, tipCoord);
       int maxNormal = Math.max(base, tipCoord);
       return switch (normalAxis) {
          case X -> toFullBlockAABB(minNormal, minU, minV, maxNormal, maxU, maxV);
          case Y -> toFullBlockAABB(minU, minNormal, minV, maxU, maxNormal, maxV);
          case Z -> toFullBlockAABB(minU, minV, minNormal, maxU, maxV, maxNormal);
       };
    }

   private List<BlockPos> getBaseBlocks(BlockPos firstPos, BlockPos secondPos) {
      DomeBounds bounds = new DomeBounds(firstPos, secondPos, firstPos, this.direction);
      List<BlockPos> blocks = new ArrayList<>();
      for (int u = bounds.minU; u <= bounds.maxU; ++u) {
         for (int v = bounds.minV; v <= bounds.maxV; ++v) {
            if (isInside(bounds, u, v, bounds.base)) {
               if (ModeOptions.getFill() == ModeOptions.ActionEnum.FULL || isBaseEdge(bounds, u, v)) {
                  blocks.add(bounds.position(u, v, bounds.base));
               }
            }
         }
      }

      return blocks;
   }

   private List<BlockPos> getDomeBlocks(BlockPos firstPos, BlockPos secondPos, BlockPos thirdPos) {
      DomeBounds bounds = new DomeBounds(firstPos, secondPos, thirdPos, this.direction);
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

   private static boolean isInside(DomeBounds bounds, int u, int v, int normal) {
      return normalizedDistance(bounds.centerU, bounds.radiusU, u) + normalizedDistance(bounds.centerV, bounds.radiusV, v) + normalizedDistance(bounds.base, bounds.radiusNormal, normal) <= 1.0001D;
   }

   private static boolean isBaseEdge(DomeBounds bounds, int u, int v) {
      return !isInside(bounds, u + 1, v, bounds.base)
         || !isInside(bounds, u - 1, v, bounds.base)
         || !isInside(bounds, u, v + 1, bounds.base)
         || !isInside(bounds, u, v - 1, bounds.base);
   }

   /** A hollow dome has its curved shell and rim, while leaving the base open. */
   private static boolean isSurface(DomeBounds bounds, int u, int v, int normal, int normalStep) {
      return !isInside(bounds, u + 1, v, normal)
         || !isInside(bounds, u - 1, v, normal)
         || !isInside(bounds, u, v + 1, normal)
         || !isInside(bounds, u, v - 1, normal)
         || !isInside(bounds, u, v, normal + normalStep);
   }

   private static double normalizedDistance(double center, double radius, int coordinate) {
      if (radius == 0.0D) {
         return coordinate == center ? 0.0D : Double.POSITIVE_INFINITY;
      }

      double distance = ((double)coordinate - center) / radius;
      return distance * distance;
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

   private static class DomeBounds {
      private final Direction.Axis normalAxis;
      private final Direction.Axis uAxis;
      private final Direction.Axis vAxis;
      private final int base;
      private final int tip;
      private final int minU;
      private final int maxU;
      private final int minV;
      private final int maxV;
      private final double centerU;
      private final double centerV;
      private final double radiusU;
      private final double radiusV;
      private final double radiusNormal;

      private DomeBounds(BlockPos firstPos, BlockPos secondPos, BlockPos thirdPos, Direction direction) {
         this.normalAxis = direction.getAxis();
         Direction.Axis[] axes = perpendicularAxes(this.normalAxis);
         this.uAxis = axes[0];
         this.vAxis = axes[1];
         this.base = coordinate(firstPos, this.normalAxis);
         int firstU = coordinate(firstPos, this.uAxis);
         int secondU = coordinate(secondPos, this.uAxis);
         int firstV = coordinate(firstPos, this.vAxis);
         int secondV = coordinate(secondPos, this.vAxis);
         if (ModeOptions.getCircleStart() == ModeOptions.ActionEnum.CIRCLE_START_CORNER) {
            this.centerU = ((double)firstU + (double)secondU) / 2.0D;
            this.centerV = ((double)firstV + (double)secondV) / 2.0D;
            this.minU = Math.min(firstU, secondU);
            this.maxU = Math.max(firstU, secondU);
            this.minV = Math.min(firstV, secondV);
            this.maxV = Math.max(firstV, secondV);
         } else {
            this.centerU = firstU;
            this.centerV = firstV;
            this.minU = Math.min(secondU, 2 * firstU - secondU);
            this.maxU = Math.max(secondU, 2 * firstU - secondU);
            this.minV = Math.min(secondV, 2 * firstV - secondV);
            this.maxV = Math.max(secondV, 2 * firstV - secondV);
         }

         this.radiusU = Math.abs((double)secondU - this.centerU);
         this.radiusV = Math.abs((double)secondV - this.centerV);
         this.radiusNormal = Math.abs((double)coordinate(thirdPos, this.normalAxis) - this.base);
         this.tip = this.base + direction.getAxisDirection().getStep() * (int)this.radiusNormal;
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
