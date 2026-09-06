package nl.requios.effortlessbuilding.buildpipeline;

import java.util.Map;
import nl.requios.effortlessbuilding.config.ClientConfig;
import nl.requios.effortlessbuilding.config.ServerConfig;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import nl.requios.effortlessbuilding.utilities.BlockStatus;
import nl.requios.effortlessbuilding.utilities.InventoryHelper;
import nl.requios.effortlessbuilding.utilities.PlacedBlockTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;

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
      WorldBorder worldBorder = level.getWorldBorder();

      for(Map.Entry<BlockPos, BlockEntry> mapEntry : blocks.entrySet()) {
         BlockEntry entry = (BlockEntry)mapEntry.getValue();
         if (entry.isValid()) {
            BlockPos pos = (BlockPos)mapEntry.getKey();
            if (level.isOutsideBuildHeight(pos) || !worldBorder.isWithinBounds(pos)) {
               entry.markRejected(BlockStatus.WORLD_BORDER);
            }
         }
      }

      int maxBlocks = ServerConfig.INSTANCE.getMaxBlocksPlaced(player);
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
         if (isBreaking && !ServerConfig.INSTANCE.survivalAllowBreaking) {
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

                  if (ServerConfig.INSTANCE.survivalOnlyPlacedBlocks && !PlacedBlockTracker.isTrackedAnySide(player, level, pos)) {
                     entry.markRejected(BlockStatus.NOT_PLACED_BY_PLAYER);
                  } else {
                     if (ServerConfig.INSTANCE.survivalMaxHardness >= 0.0F) {
                        float hardness = state.getDestroySpeed(level, pos);
                        if (hardness > ServerConfig.INSTANCE.survivalMaxHardness) {
                           entry.markRejected(BlockStatus.TOO_HARD);
                           continue;
                        }
                     }

                     if (ServerConfig.INSTANCE.survivalRequireTools && state.requiresCorrectToolForDrops() && !InventoryHelper.hasCorrectToolForBlock(player, state)) {
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
            return ClientConfig.INSTANCE.shouldProtectTileEntities();
         } catch (Exception var3) {
            return false;
         }
      }
   }

   public static record PlacementContext(boolean protectTileEntities) {
   }
}
