package nl.requios.effortlessbuilding.buildmode.buildmodes;

import java.util.List;
import nl.requios.effortlessbuilding.buildmode.BuildModes;
import nl.requios.effortlessbuilding.buildmode.TwoClicksBuildMode;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipeline;
import org.lightning323.creative_mode_tweaks.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Builds a floor or wall based on the axis of the face targeted on the first click. */
public class Plane extends TwoClicksBuildMode {
   private Direction.Axis firstFaceAxis = Direction.Axis.Y;
   private Direction.Axis lastWallAxis = Direction.Axis.Z;


   @Override
   public void initialize() {
      super.initialize();
      this.firstFaceAxis = Direction.Axis.Y;
   }

   @Override
   public void setFirstClickFace(Direction face) {
      Direction.Axis targetedAxis = face.getAxis();
      if (targetedAxis != Direction.Axis.Y) {
         this.lastWallAxis = targetedAxis;
      }
      this.firstFaceAxis = targetedAxis;
   }

   /** Returns the axis of the face targeted when this plane was started. */
   public Direction.Axis planeFace() {
      return this.firstFaceAxis;
   }

   /** Toggles the manually selected plane type between floor and wall. */
   public void togglePlaneType() {
      if (this.planeFace() == Direction.Axis.Y) {
         this.firstFaceAxis = this.lastWallAxis;
      } else {
         this.firstFaceAxis = Direction.Axis.Y;
      }
   }

   public String getPlaneTypeNameKey() {
      return this.planeFace() == Direction.Axis.Y
         ? "creative_mode_tweaks.mode.floor"
         : "creative_mode_tweaks.mode.wall";
   }

   @Override
   protected BlockPos findSecondPos(Player player, BlockPos firstPos, boolean skipRaytrace) {
      Direction.Axis axis = this.updateWallAxisFromLook(player);
      return switch (axis) {
         case Y -> Floor.findFloor(player, firstPos, skipRaytrace);
         case X, Z -> findWallOnAxis(player, firstPos, axis, skipRaytrace);
      };
   }

   @Override
   protected List<BlockPos> getAllBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2) {
      return switch (planeFace(x1, y1, z1, x2, y2, z2)) {
         case Y -> Floor.getFloorBlocks(player, x1, y1, z1, x2, y2, z2);
         case X, Z -> Wall.getWallBlocks(player, x1, y1, z1, x2, y2, z2);
      };
   }

   private static BlockPos findWallOnAxis(Player player, BlockPos firstPos, Direction.Axis axis, boolean skipRaytrace) {
      Vec3 look = BuildPipeline.getPlayerLookVec(player);
      Vec3 start = BuildPipeline.getPlayerEyePosition(player);
      Vec3 planeBound = axis == Direction.Axis.X
         ? BuildModes.findXBound(firstPos.getX(), start, look)
         : BuildModes.findZBound(firstPos.getZ(), start, look);
      double distanceToPlayerSq = planeBound.subtract(start).lengthSqr();
      int reach = Config.getBuildingReach(player);

      return BuildModes.isCriteriaValid(start, look, reach, player, skipRaytrace, planeBound, planeBound, distanceToPlayerSq)
         ? BlockPos.containing(planeBound)
         : null;
   }

   /** Keeps wall previews and placements perpendicular to the current horizontal look direction. */
   private Direction.Axis updateWallAxisFromLook(Player player) {
      if (this.firstFaceAxis == Direction.Axis.Y) {
         return Direction.Axis.Y;
      }

      Vec3 look = BuildPipeline.getPlayerLookVec(player);
      double horizontalX = Math.abs(look.x);
      double horizontalZ = Math.abs(look.z);
      if (horizontalX > horizontalZ) {
         this.lastWallAxis = Direction.Axis.X;
      } else if (horizontalZ > horizontalX) {
         this.lastWallAxis = Direction.Axis.Z;
      }

      this.firstFaceAxis = this.lastWallAxis;
      return this.firstFaceAxis;
   }

   private static Direction.Axis planeFace(int x1, int y1, int z1, int x2, int y2, int z2) {
      if (y1 == y2) {
         return Direction.Axis.Y;
      }
      return x1 == x2 ? Direction.Axis.X : Direction.Axis.Z;
   }

}
