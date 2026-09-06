package nl.requios.effortlessbuilding.buildmode;

import nl.requios.effortlessbuilding.utilities.BlockSet;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public class BuildModes {
   public static final BuildModes CLIENT = new BuildModes();
   private BuildModeEnum buildMode;
   private BuildModeEnum previousBuildMode;
   private BuildModeEnum beforeDisabledBuildMode;
   private Runnable beforeDisable;

   public BuildModes() {
      this.buildMode = BuildModeEnum.DISABLED;
      this.previousBuildMode = BuildModeEnum.DISABLED;
      this.beforeDisable = () -> {
      };
   }

   public void findCoordinates(BlockSet blocks, Player player) {
      this.buildMode.instance.findCoordinates(blocks, player);
   }

   public BuildModeEnum getBuildMode() {
      return this.buildMode;
   }

   public void setBuildMode(BuildModeEnum buildMode) {
      if (this.buildMode != BuildModeEnum.DISABLED && buildMode == BuildModeEnum.DISABLED) {
         this.beforeDisable.run();
      }

      this.buildMode = buildMode;
   }

   public void setBeforeDisable(Runnable beforeDisable) {
      this.beforeDisable = beforeDisable;
   }

   public void activatePreviousBuildMode() {
      BuildModeEnum temp = this.buildMode;
      this.setBuildMode(this.previousBuildMode);
      this.previousBuildMode = temp;
   }

   public void activateDisableBuildModeToggle() {
      if (this.buildMode == BuildModeEnum.DISABLED) {
         this.setBuildMode(this.beforeDisabledBuildMode);
      } else {
         this.beforeDisabledBuildMode = this.buildMode;
         this.setBuildMode(BuildModeEnum.DISABLED);
      }

   }

   public void onCancel() {
      this.getBuildMode().instance.initialize();
   }

   public static Vec3 findXBound(double x, Vec3 start, Vec3 look) {
      double y = (x - start.x) / look.x * look.y + start.y;
      double z = (x - start.x) / look.x * look.z + start.z;
      return new Vec3(x, y, z);
   }

   public static Vec3 findYBound(double y, Vec3 start, Vec3 look) {
      double x = (y - start.y) / look.y * look.x + start.x;
      double z = (y - start.y) / look.y * look.z + start.z;
      return new Vec3(x, y, z);
   }

   public static Vec3 findZBound(double z, Vec3 start, Vec3 look) {
      double x = (z - start.z) / look.z * look.x + start.x;
      double y = (z - start.z) / look.z * look.y + start.y;
      return new Vec3(x, y, z);
   }

   public static boolean isCriteriaValid(Vec3 start, Vec3 look, int reach, Player player, boolean skipRaytrace, Vec3 lineBound, Vec3 planeBound, double distToPlayerSq) {
      boolean intersects = false;
      if (!skipRaytrace) {
         ClipContext rayTraceContext = new ClipContext(start, lineBound, Block.COLLIDER, Fluid.NONE, player);
         BlockHitResult rayTraceResult = player.level().clip(rayTraceContext);
         intersects = rayTraceResult != null && rayTraceResult.getType() == Type.BLOCK && planeBound.subtract(rayTraceResult.getLocation()).lengthSqr() > (double)4.0F;
      }

      return planeBound.subtract(start).dot(look) > (double)0.0F && distToPlayerSq > (double)2.0F && distToPlayerSq < (double)(reach * reach) && !intersects;
   }
}
