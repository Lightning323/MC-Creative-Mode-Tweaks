package nl.requios.effortlessbuilding.buildpipeline;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Bridges build-mode coordinate calculations to Sable's plot coordinate system.
 *
 * <p>Sable stores a moving contraption's blocks in a distant plot, while players
 * and cameras remain in normal world space. The companion API supplies a safe
 * no-op implementation when Sable is not present, so callers can use this for
 * every build operation.</p>
 */
public final class SableCompat {
   private static final ThreadLocal<@Nullable SelectionContext> ACTIVE_SELECTION = new ThreadLocal<>();

   private SableCompat() {
   }

   public static void withSelection(Level level, BlockPos origin, Runnable operation) {
      try (SelectionScope ignored = pushSelection(level, origin)) {
         operation.run();
      }
   }

   public static <T> T withSelection(Level level, BlockPos origin, Supplier<T> operation) {
      try (SelectionScope ignored = pushSelection(level, origin)) {
         return operation.get();
      }
   }

   public static SelectionScope pushSelection(Level level, BlockPos origin) {
      SelectionContext previous = ACTIVE_SELECTION.get();
      SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, origin);
      ACTIVE_SELECTION.set(new SelectionContext(subLevel));
      return new SelectionScope(previous);
   }

   public static Vec3 getPlayerEyePosition(Player player) {
      return toSelectionSpace(player.getEyePosition());
   }

   public static Vec3 getPlayerLookVector(Player player) {
      Vec3 look = player.getLookAngle();
      SelectionContext context = ACTIVE_SELECTION.get();
      if (context == null || context.subLevel == null) {
         return look;
      }

      Vector3d local = context.subLevel.logicalPose().transformNormalInverse(new Vector3d(look.x, look.y, look.z), new Vector3d());
      if (local.lengthSquared() > 0.0) {
         local.normalize();
      }

      return new Vec3(local.x, local.y, local.z);
   }

   public static boolean isWithinActiveSelection(Level level, BlockPos pos) {
      SelectionContext context = ACTIVE_SELECTION.get();
      if (context == null) {
         return true;
      }

      SubLevelAccess targetSubLevel = SableCompanion.INSTANCE.getContaining(level, pos);
      UUID targetId = targetSubLevel != null ? targetSubLevel.getUniqueId() : null;
      return Objects.equals(context.subLevelId, targetId);
   }

   public static boolean isWithinBuildBounds(Level level, BlockPos pos) {
      if (level.isOutsideBuildHeight(pos)) {
         return false;
      }

      Vec3 globalCenter = SableCompanion.INSTANCE.projectOutOfSubLevel(level, new Vec3((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5));
      return level.getWorldBorder().isWithinBounds(BlockPos.containing(globalCenter));
   }

   public static void translateToBlock(PoseStack poseStack, Level level, BlockPos pos, double camX, double camY, double camZ) {
      SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, pos);
      if (subLevel == null) {
         poseStack.translate((double)pos.getX() - camX, (double)pos.getY() - camY, (double)pos.getZ() - camZ);
         return;
      }

      Pose3dc pose = subLevel.logicalPose();
      Vector3d globalOrigin = pose.transformPosition(new Vector3d(pos.getX(), pos.getY(), pos.getZ()), new Vector3d());
      poseStack.translate(globalOrigin.x - camX, globalOrigin.y - camY, globalOrigin.z - camZ);
      poseStack.mulPose(new Quaternionf(pose.orientation()));
      Vector3dc scale = pose.scale();
      poseStack.scale((float)scale.x(), (float)scale.y(), (float)scale.z());
   }

   private static Vec3 toSelectionSpace(Vec3 globalPosition) {
      SelectionContext context = ACTIVE_SELECTION.get();
      if (context == null || context.subLevel == null) {
         return globalPosition;
      }

      Vector3d local = context.subLevel.logicalPose().transformPositionInverse(new Vector3d(globalPosition.x, globalPosition.y, globalPosition.z), new Vector3d());
      return new Vec3(local.x, local.y, local.z);
   }

   private static final class SelectionContext {
      private final @Nullable SubLevelAccess subLevel;
      private final @Nullable UUID subLevelId;

      private SelectionContext(@Nullable SubLevelAccess subLevel) {
         this.subLevel = subLevel;
         this.subLevelId = subLevel != null ? subLevel.getUniqueId() : null;
      }
   }

   public static final class SelectionScope implements AutoCloseable {
      private final @Nullable SelectionContext previous;

      private SelectionScope(@Nullable SelectionContext previous) {
         this.previous = previous;
      }

      public void close() {
         if (this.previous == null) {
            ACTIVE_SELECTION.remove();
         } else {
            ACTIVE_SELECTION.set(this.previous);
         }
      }
   }
}
