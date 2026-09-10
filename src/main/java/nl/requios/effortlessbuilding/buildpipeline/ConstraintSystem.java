package nl.requios.effortlessbuilding.buildpipeline;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.HashMap;
import java.util.Map;
import org.lightning323.creative_mode_tweaks.Config;
import nl.requios.effortlessbuilding.buildmode.BuildSettings;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import nl.requios.effortlessbuilding.utilities.BlockStatus;
import nl.requios.effortlessbuilding.utilities.InventoryHelper;
import nl.requios.effortlessbuilding.utilities.PlacedBlockTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class ConstraintSystem implements IBuildSystem {
   public static final ConstraintSystem INSTANCE = new ConstraintSystem();
   private static final ThreadLocal<PlacementContext> PLACEMENT_CTX = new ThreadLocal();

   /**
    * Cap for the preview tile-entity memo below: bounds the retained sets so
    * one giant shape can't pin memory.
    */
   private static final int TILE_MEMO_CAP = 131072;
   /**
    * Memo validity window in ticks (~13s at 256 ticks). The memo survives
    * across the shapes of one drag gesture but self-heals if the world
    * changes underneath the preview. Preview staleness here is harmless:
    * placement re-validates server-side at click time.
    */
   private static final long TILE_MEMO_BUCKET_TICKS = 256L;

   // Client-preview tile-entity memo. Instance-scoped and opt-in: the server
   // pipeline keeps using INSTANCE (memo off) so placement validation always
   // reads live world state.
   private boolean previewMemo;
   private Level memoLevel;
   private long memoBucket = Long.MIN_VALUE;
   private boolean memoProtectTiles;
   private final LongOpenHashSet memoScanned = new LongOpenHashSet();
   private final LongOpenHashSet memoTileRejected = new LongOpenHashSet();

   /** Enables the tile-scan memo. Call only for the client preview pipeline. */
   public void setPreviewMemoEnabled(boolean enabled) {
      this.previewMemo = enabled;
      if (!enabled) {
         this.clearTileMemo();
      }
   }

   public static void setPlacementContext(PlacementContext ctx) {
      PLACEMENT_CTX.set(ctx);
   }

   public static void clearPlacementContext() {
      PLACEMENT_CTX.remove();
   }

   public void processBlocks(BlockSet blocks, Player player, BuildPipeline.BuildState action) {
      Level level = player.level();
      boolean isBreaking = action == BuildPipeline.BuildState.BREAKING;

      for (BlockEntry entry : blocks.values()) {
         if (entry.isValid()) {
            BlockPos pos = entry.blockPos;
            if (!SableCompat.isWithinActiveSelection(level, pos)) {
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

      // Survival stock: when the player cannot supply the whole shape, cut
      // it to what can actually be set — red count text plus affordable-only
      // ghosts, identical to the max-blocks cap above. Creative is exempt.
      if (!isBreaking && !player.getAbilities().instabuild) {
         applySurvivalInventoryCap(blocks, player);
      }

      boolean protectTiles = this.getProtectTileEntities();
      if (protectTiles) {
         if (this.useTileMemo(level, protectTiles)) {
            // Same level, same setting, recent bucket: re-apply memoized
            // verdicts and scan only positions never seen under this memo.
            // Dragging mostly revisits positions, so this degrades to O(new).
            for (BlockEntry entry : blocks.values()) {
               long packed = entry.blockPos.asLong();
               if (this.memoTileRejected.contains(packed)) {
                  entry.markRejected(BlockStatus.PROTECTED_TILE_ENTITY);
               } else if (!this.memoScanned.contains(packed) && entry.isValid()
                       && level.getBlockEntity(entry.blockPos) != null) {
                  this.memoScanned.add(packed);
                  this.memoTileRejected.add(packed);
                  entry.markRejected(BlockStatus.PROTECTED_TILE_ENTITY);
               }
            }
            if (this.memoScanned.size() > TILE_MEMO_CAP) {
               this.clearTileMemo();
            }
         } else {
            if (this.previewMemo) {
               this.memoLevel = level;
               this.memoBucket = level.getGameTime() / TILE_MEMO_BUCKET_TICKS;
               this.memoProtectTiles = protectTiles;
               this.memoScanned.clear();
               this.memoTileRejected.clear();
            }
            for (BlockEntry entry : blocks.values()) {
               if (entry.isValid() && level.getBlockEntity(entry.blockPos) != null) {
                  entry.markRejected(BlockStatus.PROTECTED_TILE_ENTITY);
                  if (this.previewMemo) {
                     long packed = entry.blockPos.asLong();
                     this.memoScanned.add(packed);
                     this.memoTileRejected.add(packed);
                  }
               } else if (this.previewMemo && entry.isValid()) {
                  this.memoScanned.add(entry.blockPos.asLong());
               }
            }
         }
      } else if (this.previewMemo) {
         this.clearTileMemo();
      }

      if (!player.getAbilities().instabuild) {
         if (isBreaking && !Config.BUILDING_SURVIVAL_ALLOW_BREAKING.get()) {
            for(BlockEntry entry : blocks.values()) {
               entry.markRejected(BlockStatus.BREAKING_DISABLED);
            }

         } else {
            for (BlockEntry entry : blocks.values()) {
               if (entry.isValid()) {
                  BlockPos pos = entry.blockPos;
                  BlockState state = level.getBlockState(pos);
                  if (isBreaking) {
                     if (state.isAir()) {
                        continue;
                     }
                     // Soft blocks under the break-all threshold bypass
                     // every survival breaking restriction (placed-only,
                     // hardness cap, tool requirement). Unbreakable blocks
                     // report -1, so they can never slip under the check.
                     float softness = state.getDestroySpeed(level, pos);
                     if (softness >= 0.0F && softness < Config.BUILDING_SURVIVAL_MAX_HARDNESS_TO_BREAK_ALL_BLOCKS.get()) {
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

       // Replacement preview for every gamemode: the server filters each
       // position through canPlaceAt at placement time (PacketHandler), so
       // the preview must show the same verdict — otherwise creative shows
       // white ghosts for blocks that will silently never place, and the
       // red rejected overlay effectively only ever appears in survival.
       // Runs last so survival keeps its more specific statuses (first
       // rejection wins). Client-only: BuildSettings.CLIENT touches
       // Minecraft; the server enforces the same rule inline when placing.
       if (!isBreaking && level.isClientSide()) {
          BuildSettings.ReplaceMode replaceMode = BuildSettings.CLIENT.getReplaceMode();
          if (replaceMode != BuildSettings.ReplaceMode.BLOCKS_AND_AIR) {
             ItemStack offHand = player.getOffhandItem();
             for (BlockEntry entry : blocks.values()) {
                if (entry.isValid() && !BuildSettings.canPlaceAt(level, entry.blockPos, replaceMode, offHand)) {
                   entry.markRejected(BlockStatus.NOT_REPLACEABLE);
                }
             }
          }
       }
    }

   /**
    * Survival stock cap. Mirrors the server placement accounting (which
    * places up to the available stock, then stops), so preview and
    * placement agree on the affordable subset in generation order.
    * Entries beyond stock are marked {@link BlockStatus#INSUFFICIENT_ITEMS},
    * which the preview renders exactly like {@code MAX_BLOCKS_EXCEEDED}.
    */
   private static void applySurvivalInventoryCap(BlockSet blocks, Player player) {
      ItemStack held = player.getMainHandItem();
      if (TrowelSystem.isTrowel(held)) {
         Map<Item, Integer> available = TrowelSystem.getHotbarBlockCounts(player);
         Map<Item, Integer> used = new HashMap<>();
         for (BlockEntry entry : blocks.values()) {
            if (!entry.isValid()) {
               continue;
            }
            Item item = entry.item;
            if (!(item instanceof BlockItem) || used.getOrDefault(item, 0) >= available.getOrDefault(item, 0)) {
               entry.markRejected(BlockStatus.INSUFFICIENT_ITEMS);
            } else {
               used.merge(item, 1, Integer::sum);
            }
         }
         return;
      }
      Item heldItem = held.getItem();
      if (!(heldItem instanceof BlockItem)) {
         return;
      }
      int available = !held.getComponentsPatch().isEmpty()
            ? held.getCount()
            : InventoryHelper.findTotalItemsInInventory(player, heldItem);
      int remaining = available;
      for (BlockEntry entry : blocks.values()) {
         if (!entry.isValid()) {
            continue;
         }
         if (remaining <= 0) {
            entry.markRejected(BlockStatus.INSUFFICIENT_ITEMS);
         } else {
            --remaining;
         }
      }
   }

   private boolean useTileMemo(Level level, boolean protectTiles) {
      return this.previewMemo && !this.memoScanned.isEmpty()
            && level == this.memoLevel
            && level.getGameTime() / TILE_MEMO_BUCKET_TICKS == this.memoBucket
            && protectTiles == this.memoProtectTiles;
   }

   private void clearTileMemo() {
      this.memoLevel = null;
      this.memoBucket = Long.MIN_VALUE;
      this.memoScanned.clear();
      this.memoTileRejected.clear();
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

   public static record PlacementContext(boolean protectTileEntities) {
   }
}
