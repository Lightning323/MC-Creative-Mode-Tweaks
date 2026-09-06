package nl.requios.effortlessbuilding.buildpipeline;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lightning323.creative_mode_tweaks.Config;

/** Shared Angel Placement targeting for client previews and server-side validation. */
public final class AngelPlacement {
   private static final double MIN_TARGET_DISTANCE = 4.0D;
   private static final double TARGET_STEP = 0.25D;

   private AngelPlacement() {
   }

   public static @Nullable Target findTarget(Level level, Player player, boolean allowAngelPlacement) {
      double normalTargetDistance = allowAngelPlacement ? MIN_TARGET_DISTANCE : (double)Config.getBuildingReach(player);
      BlockHitResult blockHit = findBlockTarget(level, player, normalTargetDistance);
      if (blockHit != null) {
         return new Target(blockHit, false);
      }

      if (!allowAngelPlacement) {
         return null;
      }

      BlockHitResult angelHit = findAirTarget(level, player);
      return angelHit != null ? new Target(angelHit, true) : null;
   }

   private static @Nullable BlockHitResult findBlockTarget(Level level, Player player, double maxDistance) {
      Vec3 eyePosition = player.getEyePosition();
      Vec3 end = eyePosition.add(player.getLookAngle().scale(maxDistance));
      BlockHitResult hit = level.clip(new ClipContext(eyePosition, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
      return hit.getType() == HitResult.Type.BLOCK ? hit : null;
   }

   private static @Nullable BlockHitResult findAirTarget(Level level, Player player) {
      Vec3 eyePosition = player.getEyePosition();
      Vec3 look = player.getLookAngle();
      Direction face = Direction.getNearest((float)-look.x, (float)-look.y, (float)-look.z);

      for(double distance = (double)Config.getAngelPlacementDistance(player); distance > MIN_TARGET_DISTANCE; distance -= TARGET_STEP) {
         BlockPos pos = BlockPos.containing(eyePosition.add(look.scale(distance)));
         if (level.getBlockState(pos).isAir()) {
            Vec3 hitLocation = Vec3.atCenterOf(pos).add((double)face.getStepX() * 0.5D, (double)face.getStepY() * 0.5D, (double)face.getStepZ() * 0.5D);
            return new BlockHitResult(hitLocation, face, pos, false);
         }
      }

      return null;
   }

   public static boolean isWithinTargetDistance(Player player, BlockPos pos) {
      Vec3 eyePosition = player.getEyePosition();
      double dx = Math.max((double)pos.getX() - eyePosition.x, Math.max(0.0D, eyePosition.x - (double)(pos.getX() + 1)));
      double dy = Math.max((double)pos.getY() - eyePosition.y, Math.max(0.0D, eyePosition.y - (double)(pos.getY() + 1)));
      double dz = Math.max((double)pos.getZ() - eyePosition.z, Math.max(0.0D, eyePosition.z - (double)(pos.getZ() + 1)));
      double maxDistance = (double)Config.getAngelPlacementDistance(player);
      return dx * dx + dy * dy + dz * dz <= maxDistance * maxDistance;
   }

   public record Target(BlockHitResult hit, boolean isAngelTarget) {
   }
}
