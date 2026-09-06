package nl.requios.effortlessbuilding.buildpipeline;

import java.util.Map;
import org.lightning323.creative_mode_tweaks.Config;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import nl.requios.effortlessbuilding.utilities.BlockStatus;
import nl.requios.effortlessbuilding.utilities.InventoryHelper;
import nl.requios.effortlessbuilding.utilities.PlacedBlockTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class ConstraintSystem implements IBuildSystem {
   public static final ConstraintSystem INSTANCE = new ConstraintSystem();
   private static final ThreadLocal<PlacementContext> PLACEMENT_CTX = new ThreadLocal();

   public static void setPlacementContext(PlacementContext ctx) {
      PLACEMENT_CTX.set(ctx);
   }

   public static void clearPlacementContext() {
      PLACEMENT_CTX.remove();
   }

   public void processBlocks(BlockSet blocks, Player player, BuildPipeline.BuildState action) {
      Level level = player.level();
      boolean isBreaking = action == BuildPipeline.BuildState.BREAKING;

      for(Map.Entry<BlockPos, BlockEntry> mapEntry : blocks.entrySet()) {
         BlockEntry entry = (BlockEntry)mapEntry.getValue();
         if (entry.isValid()) {
            BlockPos pos = (BlockPos)mapEntry.getKey();
            if (!SableCompat.isWithinActiveSelection(level, pos)) {
               entry.markRejected(BlockStatus.OUTSIDE_REACH);
            } else if (!this.isWithinAngelPlacementDistance(player, pos)) {
               entry.markRejected(BlockStatus.OUTSIDE_REACH);
            } else if (!SableCompat.isWithinBuildBounds(level, pos)) {
               entry.markRejected(BlockStatus.WORLD_BORDER);
            }
         }
      }

      int maxBlocks = Config.getBuildingMaxBlocksPlaced(player);
      int validCount = 0;

      for(BlockEntry entry : blocks.values()) {
         if (entry.isValid()) {
            ++validCount;
            if (validCount > maxBlocks) {
               entry.markRejected(BlockStatus.MAX_BLOCKS_EXCEEDED);
            }
         }
      }

      boolean protectTiles = this.getProtectTileEntities();
      if (protectTiles) {
         for(Map.Entry<BlockPos, BlockEntry> mapEntry : blocks.entrySet()) {
            BlockEntry entry = (BlockEntry)mapEntry.getValue();
            if (entry.isValid() && level.getBlockEntity((BlockPos)mapEntry.getKey()) != null) {
               entry.markRejected(BlockStatus.PROTECTED_TILE_ENTITY);
            }
         }
      }

      if (!player.getAbilities().instabuild) {
         if (isBreaking && !Config.BUILDING_SURVIVAL_ALLOW_BREAKING.get()) {
            for(BlockEntry entry : blocks.values()) {
               entry.markRejected(BlockStatus.BREAKING_DISABLED);
            }

         } else {
            for(Map.Entry<BlockPos, BlockEntry> mapEntry : blocks.entrySet()) {
               BlockEntry entry = (BlockEntry)mapEntry.getValue();
               if (entry.isValid()) {
                  BlockPos pos = (BlockPos)mapEntry.getKey();
                  BlockState state = level.getBlockState(pos);
                  if (isBreaking) {
                     if (state.isAir()) {
                        continue;
                     }
                  } else if (state.canBeReplaced()) {
                     continue;
                  }

                  if (Config.BUILDING_SURVIVAL_ONLY_PLACED_BLOCKS.get() && !PlacedBlockTracker.isTrackedAnySide(player, level, pos)) {
                     entry.markRejected(BlockStatus.NOT_PLACED_BY_PLAYER);
                  } else {
                     if (Config.BUILDING_SURVIVAL_MAX_HARDNESS.get() >= 0.0D) {
                        float hardness = state.getDestroySpeed(level, pos);
                        if (hardness > Config.BUILDING_SURVIVAL_MAX_HARDNESS.get()) {
                           entry.markRejected(BlockStatus.TOO_HARD);
                           continue;
                        }
                     }

                     if (Config.BUILDING_SURVIVAL_REQUIRE_TOOLS.get() && state.requiresCorrectToolForDrops() && !InventoryHelper.hasCorrectToolForBlock(player, state)) {
                        entry.markRejected(BlockStatus.MISSING_TOOL);
                     }
                  }
               }
            }

         }
      }
   }

   private boolean getProtectTileEntities() {
      PlacementContext ctx = (PlacementContext)PLACEMENT_CTX.get();
      if (ctx != null) {
         return ctx.protectTileEntities();
      } else {
         try {
            return Config.BUILDING_PROTECT_TILE_ENTITIES.get();
         } catch (Exception var3) {
            return false;
         }
      }
   }

   private boolean isWithinAngelPlacementDistance(Player player, BlockPos pos) {
      PlacementContext ctx = (PlacementContext)PLACEMENT_CTX.get();
      if (ctx == null || !ctx.angelPlacement()) {
         return true;
      }

      Vec3 eyePosition = SableCompat.getPlayerEyePosition(player);
      double dx = Math.max((double)pos.getX() - eyePosition.x, Math.max(0.0D, eyePosition.x - (double)(pos.getX() + 1)));
      double dy = Math.max((double)pos.getY() - eyePosition.y, Math.max(0.0D, eyePosition.y - (double)(pos.getY() + 1)));
      double dz = Math.max((double)pos.getZ() - eyePosition.z, Math.max(0.0D, eyePosition.z - (double)(pos.getZ() + 1)));
      double maxDistance = (double)Config.getAngelPlacementDistance(player);
      return dx * dx + dy * dy + dz * dz <= maxDistance * maxDistance;
   }

   public static record PlacementContext(boolean protectTileEntities, boolean angelPlacement) {
   }
}
