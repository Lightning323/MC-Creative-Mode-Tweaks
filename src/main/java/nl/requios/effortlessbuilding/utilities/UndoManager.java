package nl.requios.effortlessbuilding.utilities;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import nl.requios.effortlessbuilding.Constants;
import nl.requios.effortlessbuilding.buildpipeline.BreakHunger;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import org.lightning323.creative_mode_tweaks.Config;

public class UndoManager {
   private static final Map<UUID, Deque<UndoEntry>> undoStacks = new HashMap();
   private static final Map<UUID, Deque<UndoEntry>> redoStacks = new HashMap();
   private static final int MAX_STACK_SIZE = 50;

   public static void recordOperation(ServerPlayer player, ResourceKey<Level> dimension, Map<BlockPos, BlockChange> changes) {
      if (!changes.isEmpty()) {
         UUID id = player.getUUID();
         Deque<UndoEntry> undoStack = (Deque)undoStacks.computeIfAbsent(id, (k) -> new ArrayDeque());
         undoStack.push(new UndoEntry(dimension, changes));
         if (undoStack.size() > 50) {
            ((ArrayDeque)undoStack).removeLast();
         }

         ((Deque)redoStacks.computeIfAbsent(id, (k) -> new ArrayDeque())).clear();
      }
   }

   public static int undo(ServerPlayer player) {
      UUID id = player.getUUID();
      Deque<UndoEntry> undoStack = (Deque)undoStacks.get(id);
      if (undoStack != null && !undoStack.isEmpty()) {
         UndoEntry entry = (UndoEntry)undoStack.pop();
         ServerLevel level = player.server.getLevel(entry.dimension());
         if (level == null) {
            Constants.LOG.warn("[EffortlessBuilding] Cannot undo: dimension {} no longer loaded", entry.dimension());
            return -1;
         } else {
            boolean creative = player.isCreative();
            int restored = 0;

            for(Map.Entry<BlockPos, BlockChange> e : entry.changes().entrySet()) {
               BlockPos pos = (BlockPos)e.getKey();
               BlockChange change = (BlockChange)e.getValue();
               BlockState oldState = change.oldState();
               BlockState newState = change.newState();
               if (creative) {
                  level.setBlock(pos, oldState, 3);
                  ++restored;
                  continue;
               }
               if (!newState.isAir() && oldState.canBeReplaced()) {
                  restored += undoPlace(level, player, entry.dimension(), pos, oldState, newState);
               } else if (!oldState.isAir() && newState.isAir()) {
                  restored += undoBreak(level, player, entry.dimension(), pos, oldState);
               } else if (!oldState.isAir() && !newState.isAir()) {
                  restored += undoReplace(level, player, entry.dimension(), pos, oldState, newState);
               }
            }

            Deque<UndoEntry> redoStack = (Deque)redoStacks.computeIfAbsent(id, (k) -> new ArrayDeque());
            redoStack.push(entry);
            if (redoStack.size() > 50) {
               ((ArrayDeque)redoStack).removeLast();
            }

            return restored;
         }
      } else {
         return -1;
      }
   }

   public static int redo(ServerPlayer player) {
      UUID id = player.getUUID();
      Deque<UndoEntry> redoStack = (Deque)redoStacks.get(id);
      if (redoStack != null && !redoStack.isEmpty()) {
         UndoEntry entry = (UndoEntry)redoStack.pop();
         ServerLevel level = player.server.getLevel(entry.dimension());
         if (level == null) {
            Constants.LOG.warn("[EffortlessBuilding] Cannot redo: dimension {} no longer loaded", entry.dimension());
            return -1;
         } else {
            boolean creative = player.isCreative();
            int reapplied = 0;

            // Hunger for re-breaking, mirroring the break path (creative exempt).
            double hungerMult = creative ? 0.0D : Config.BUILDING_SURVIVAL_BREAK_HUNGER_HARDNESS_MULT.get();
            BreakHunger.Batch hunger = hungerMult > 0.0D
                    ? new BreakHunger.Batch(player, hungerMult, Config.BUILDING_SURVIVAL_BREAK_HUNGER_TOOL_MULT.get())
                    : null;

            for(Map.Entry<BlockPos, BlockChange> e : entry.changes().entrySet()) {
               BlockPos pos = (BlockPos)e.getKey();
               BlockChange change = (BlockChange)e.getValue();
               BlockState oldState = change.oldState();
               BlockState newState = change.newState();
               if (creative) {
                  level.setBlock(pos, newState, 3);
                  ++reapplied;
                  continue;
               }
               if (!newState.isAir() && oldState.canBeReplaced()) {
                  reapplied += redoPlace(level, player, entry.dimension(), pos, oldState, newState);
               } else if (!oldState.isAir() && newState.isAir()) {
                  reapplied += redoBreak(level, player, entry.dimension(), pos, oldState, hunger);
               } else if (!oldState.isAir() && !newState.isAir()) {
                  reapplied += redoReplace(level, player, entry.dimension(), pos, oldState, newState);
               }
            }

            if (hunger != null && hunger.total() > 0.0F) {
               player.causeFoodExhaustion(hunger.total());
            }

            Deque<UndoEntry> undoStack = (Deque)undoStacks.computeIfAbsent(id, (k) -> new ArrayDeque());
            undoStack.push(entry);
            if (undoStack.size() > 50) {
               ((ArrayDeque)undoStack).removeLast();
            }

            return reapplied;
         }
      } else {
         return -1;
      }
   }

   public static void clearPlayer(UUID playerId) {
      undoStacks.remove(playerId);
      redoStacks.remove(playerId);
   }

   // -- survival helpers (world + inventory stay consistent) ------------------

   /**
    * Undo of a place into replaceable ground: take the placed block back and
    * refund its item (or its bucket for fluids).
    */
   private static int undoPlace(ServerLevel level, ServerPlayer player, ResourceKey<Level> dimension,
                                BlockPos pos, BlockState oldState, BlockState newState) {
      if (!level.getBlockState(pos).equals(newState)) {
         return 0;
      }
      level.setBlock(pos, oldState, 3);
      PlacedBlockTracker.untrack(player.getUUID(), dimension, pos);
      refundPlaced(player, newState);
      return 1;
   }

   /**
    * Undo of a break: re-place the old block, consuming what breaking it had
    * given (recomputed with the same tool rules as the break path, so e.g.
    * stone costs the cobblestone it dropped — not a stone block).
    */
   private static int undoBreak(ServerLevel level, ServerPlayer player, ResourceKey<Level> dimension,
                                BlockPos pos, BlockState oldState) {
      if (!level.getBlockState(pos).isAir()) {
         return 0;
      }
      List<ItemStack> cost = computeDrops(level, pos, oldState, player);
      if (!hasStacks(player, cost)) {
         return 0;
      }
      consumeStacks(player, cost);
      level.setBlock(pos, oldState, 3);
      PlacedBlockTracker.trackAll(player.getUUID(), dimension, List.of(pos));
      return 1;
   }

   /**
    * Undo of a solid-for-solid replace: restore the old block and refund the
    * placed one. The drops the replace handed out are taken back on a
    * best-effort basis (only when fully present — the world restoration is
    * never held hostage by an already-spent windfall). Tool interactions
    * that placed no item (tilling etc.) just swap back.
    */
   private static int undoReplace(ServerLevel level, ServerPlayer player, ResourceKey<Level> dimension,
                                  BlockPos pos, BlockState oldState, BlockState newState) {
      if (!level.getBlockState(pos).equals(newState)) {
         return 0;
      }
      if (isRealPlacement(newState)) {
         List<ItemStack> windfall = computeDrops(level, pos, oldState, player);
         if (hasStacks(player, windfall)) {
            consumeStacks(player, windfall);
         }
      }
      level.setBlock(pos, oldState, 3);
      PlacedBlockTracker.untrack(player.getUUID(), dimension, pos);
      refundPlaced(player, newState);
      return 1;
   }

   /** Redo of a place: consume the block (or filled bucket) and set it again. */
   private static int redoPlace(ServerLevel level, ServerPlayer player, ResourceKey<Level> dimension,
                                BlockPos pos, BlockState oldState, BlockState newState) {
      if (!level.getBlockState(pos).equals(oldState)) {
         return 0;
      }
      if (!newState.getFluidState().isEmpty()) {
         if (!consumeFilledBucket(player, newState.getFluidState().getType())) {
            return 0;
         }
      } else {
         Item requiredItem = newState.getBlock().asItem();
         if (requiredItem == Items.AIR
                 || InventoryHelper.findTotalItemsInInventory(player, requiredItem) <= 0) {
            return 0;
         }
         InventoryHelper.consumeItems(player, requiredItem, 1);
      }
      level.setBlock(pos, newState, 3);
      PlacedBlockTracker.trackAll(player.getUUID(), dimension, List.of(pos));
      return 1;
   }

   /** Redo of a break: break again, handing out drops and hunger like the break path. */
   private static int redoBreak(ServerLevel level, ServerPlayer player, ResourceKey<Level> dimension,
                                BlockPos pos, BlockState oldState, BreakHunger.Batch hunger) {
      if (!level.getBlockState(pos).equals(oldState)) {
         return 0;
      }
      if (hunger != null) {
         hunger.add(level, pos, oldState);
      }
      if (Config.BUILDING_SURVIVAL_USE_DURABILITY.get()) {
         InventoryHelper.damageCorrectTool(player, oldState);
      }
      // Compute before setBlock removes the block entity.
      List<ItemStack> drops = computeDrops(level, pos, oldState, player);
      level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
      PlacedBlockTracker.untrack(player.getUUID(), dimension, pos);
      giveStacks(player, drops);
      return 1;
   }

   /** Redo of a replace: like a fresh replace — consume new, hand out old drops. */
   private static int redoReplace(ServerLevel level, ServerPlayer player, ResourceKey<Level> dimension,
                                  BlockPos pos, BlockState oldState, BlockState newState) {
      if (!level.getBlockState(pos).equals(oldState)) {
         return 0;
      }
      if (!newState.getFluidState().isEmpty()) {
         if (!consumeFilledBucket(player, newState.getFluidState().getType())) {
            return 0;
         }
      } else if (isRealPlacement(newState)) {
         Item requiredItem = newState.getBlock().asItem();
         if (requiredItem == Items.AIR
                 || InventoryHelper.findTotalItemsInInventory(player, requiredItem) <= 0) {
            return 0;
         }
         InventoryHelper.consumeItems(player, requiredItem, 1);
         if (Config.BUILDING_SURVIVAL_USE_DURABILITY.get()) {
            InventoryHelper.damageCorrectTool(player, oldState);
         }
      }
      // Compute before setBlock removes the block entity.
      List<ItemStack> drops = computeDrops(level, pos, oldState, player);
      level.setBlock(pos, newState, 3);
      PlacedBlockTracker.trackAll(player.getUUID(), dimension, List.of(pos));
      giveStacks(player, drops);
      return 1;
   }

   /**
    * True for states placed from an item (real replace/place ops). Tool
    * interactions (tilling, path-making, …) produce itemless states and carry
    * no inventory side effects. Fluids are handled via buckets instead.
    */
   private static boolean isRealPlacement(BlockState state) {
      return state.getFluidState().isEmpty() && state.getBlock().asItem() != Items.AIR;
   }

   /** Refund for taking a placed block back: its item, or its bucket for fluids. */
   private static void refundPlaced(ServerPlayer player, BlockState placedState) {
      if (!placedState.getFluidState().isEmpty()) {
         refundBucket(player, placedState.getFluidState().getType());
      } else {
         giveBlockItem(player, placedState);
      }
   }

   /** Undo consumed filled→empty; restore needs empty→filled. No empty bucket, no refund. */
   private static void refundBucket(ServerPlayer player, Fluid fluid) {
      Item filled = fluid.getBucket();
      if (filled == null || filled == Items.AIR) {
         return;
      }
      if (InventoryHelper.consumeItems(player, Items.BUCKET, 1) == 1) {
         InventoryHelper.giveOrDropItems(player, filled, 1);
      }
   }

   /** Redo needs filled→empty. */
   private static boolean consumeFilledBucket(ServerPlayer player, Fluid fluid) {
      Item filled = fluid.getBucket();
      if (filled == null || filled == Items.AIR) {
         return false;
      }
      if (InventoryHelper.findTotalItemsInInventory(player, filled) <= 0) {
         return false;
      }
      InventoryHelper.consumeItems(player, filled, 1);
      InventoryHelper.giveOrDropItems(player, Items.BUCKET, 1);
      return true;
   }

   /** Drops breaking {@code state} hands out, using the break path's tool rules. */
   private static List<ItemStack> computeDrops(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
      ItemStack toolForDrops = Config.BUILDING_SURVIVAL_REQUIRE_TOOLS.get()
              ? InventoryHelper.findCorrectTool(player, state)
              : player.getMainHandItem();
      BlockEntity blockEntity = level.getBlockEntity(pos);
      List<ItemStack> drops = Block.getDrops(state, level, pos, blockEntity, player, toolForDrops);
      List<ItemStack> result = new ArrayList<>(drops.size());
      for (ItemStack drop : drops) {
         if (!drop.isEmpty()) {
            result.add(drop.copy());
         }
      }
      return result;
   }

   private static Map<Item, Integer> aggregate(List<ItemStack> stacks) {
      Map<Item, Integer> need = new HashMap<>();
      for (ItemStack stack : stacks) {
         if (!stack.isEmpty()) {
            need.merge(stack.getItem(), stack.getCount(), Integer::sum);
         }
      }
      return need;
   }

   private static boolean hasStacks(Player player, List<ItemStack> stacks) {
      for (Map.Entry<Item, Integer> need : aggregate(stacks).entrySet()) {
         if (InventoryHelper.findTotalItemsInInventory(player, need.getKey()) < need.getValue()) {
            return false;
         }
      }
      return true;
   }

   private static void consumeStacks(Player player, List<ItemStack> stacks) {
      for (Map.Entry<Item, Integer> need : aggregate(stacks).entrySet()) {
         InventoryHelper.consumeItems(player, need.getKey(), need.getValue());
      }
   }

   private static void giveStacks(Player player, List<ItemStack> stacks) {
      for (ItemStack stack : stacks) {
         InventoryHelper.giveOrDropItems(player, stack.getItem(), stack.getCount());
      }
   }

   private static void giveBlockItem(ServerPlayer player, BlockState state) {
      Item item = state.getBlock().asItem();
      if (item != Items.AIR) {
         InventoryHelper.giveOrDropItems(player, item, 1);
      }

   }

   public static record BlockChange(BlockState oldState, BlockState newState) {
   }

   public static record UndoEntry(ResourceKey<Level> dimension, Map<BlockPos, BlockChange> changes) {
   }
}
