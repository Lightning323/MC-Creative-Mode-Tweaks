package nl.requios.effortlessbuilding.buildmode;

import nl.requios.effortlessbuilding.utilities.BlockSet;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

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

   public BuildModeEnum getBuildMode() {
      return this.buildMode;
   }

   public void setBuildMode(BuildModeEnum buildMode) {
      if (this.buildMode != buildMode) {
         this.buildMode.instance.onBuildModeDeselected();
         BuildSelectionGuard.CLIENT.reset();
      }

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
      this.getBuildMode().instance.onCancel();
      BuildSelectionGuard.CLIENT.reset();
   }

   /**
    * @deprecated Use {@link RaycastToPlane#intersectXPlane} instead. Kept for
    *             compatibility; now delegates to the shared plane utils.
    */
   @Deprecated
   public static Vec3 findXBound(double x, Vec3 start, Vec3 look) {
      Vec3 hit = RaycastToPlane.intersectXPlane(x, start, look);
      if (hit != null) {
         return hit;
      }
      // Legacy fallback preserved the raw formula even for parallel/behind rays
      // (callers validated afterwards). Keep returning a finite vector here so
      // old callers behave as before; new code should use RaycastToPlane directly.
      double y = (x - start.x) / look.x * look.y + start.y;
      double z = (x - start.x) / look.x * look.z + start.z;
      return new Vec3(x, y, z);
   }

   /**
    * @deprecated Use {@link RaycastToPlane#intersectYPlane} instead.
    */
   @Deprecated
   public static Vec3 findYBound(double y, Vec3 start, Vec3 look) {
      Vec3 hit = RaycastToPlane.intersectYPlane(y, start, look);
      if (hit != null) {
         return hit;
      }
      double x = (y - start.y) / look.y * look.x + start.x;
      double z = (y - start.y) / look.y * look.z + start.z;
      return new Vec3(x, y, z);
   }

   /**
    * @deprecated Use {@link RaycastToPlane#intersectZPlane} instead.
    */
   @Deprecated
   public static Vec3 findZBound(double z, Vec3 start, Vec3 look) {
      Vec3 hit = RaycastToPlane.intersectZPlane(z, start, look);
      if (hit != null) {
         return hit;
      }
      double x = (z - start.z) / look.z * look.x + start.x;
      double y = (z - start.z) / look.z * look.y + start.y;
      return new Vec3(x, y, z);
   }

   /**
    * Plane-only validity: the look ray stops at {@code planeBound} and registers,
    * even when the vanilla block raycast hits nothing.
    *
    * @deprecated Use {@link RaycastToPlane#isValidPlaneHit} instead. The old
    *             block-coincidence {@code clip()} check was removed on purpose:
    *             it only accepted plane hits near a real block hit, which made
    *             air building impossible. {@code skipRaytrace} and the
    *             {@code lineBound}/{@code player} parameters are now ignored
    *             (kept for signature compatibility).
    */
   @Deprecated
   public static boolean isCriteriaValid(Vec3 start, Vec3 look, double reach, Player player, boolean skipRaytrace, Vec3 lineBound, Vec3 planeBound, double distToPlayerSq) {
      if (planeBound == null || !Double.isFinite(distToPlayerSq)) {
         return false;
      }
      return RaycastToPlane.isValidPlaneHit(planeBound, start, look, reach);
   }
}
