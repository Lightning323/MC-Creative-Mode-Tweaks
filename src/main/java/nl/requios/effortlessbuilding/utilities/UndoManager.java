package nl.requios.effortlessbuilding.utilities;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import nl.requios.effortlessbuilding.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class UndoManager {
   private static final int MAX_STACK_SIZE = 50;
   private static final Map<UUID, Deque<UndoEntry>> undoStacks = new HashMap();
   private static final Map<UUID, Deque<UndoEntry>> redoStacks = new HashMap();

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
               BlockState currentState = level.getBlockState(pos);
               if (!creative) {
                  if (!newState.isAir() && oldState.canBeReplaced()) {
                     if (currentState.equals(newState)) {
                        level.setBlock(pos, oldState, 3);
                        giveBlockItem(player, newState);
                        ++restored;
                     }
                     continue;
                  }

                  if (!oldState.isAir() && newState.isAir()) {
                     if (currentState.isAir()) {
                        Item requiredItem = oldState.getBlock().asItem();
                        if (requiredItem != Items.AIR && InventoryHelper.findTotalItemsInInventory(player, requiredItem) > 0) {
                           InventoryHelper.consumeItems(player, requiredItem, 1);
                           level.setBlock(pos, oldState, 3);
                           PlacedBlockTracker.trackAll(player.getUUID(), entry.dimension(), List.of(pos));
                           ++restored;
                        }
                     }
                     continue;
                  }
               }

               level.setBlock(pos, oldState, 3);
               ++restored;
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

            for(Map.Entry<BlockPos, BlockChange> e : entry.changes().entrySet()) {
               BlockPos pos = (BlockPos)e.getKey();
               BlockChange change = (BlockChange)e.getValue();
               BlockState oldState = change.oldState();
               BlockState newState = change.newState();
               BlockState currentState = level.getBlockState(pos);
               if (!creative) {
                  if (!newState.isAir() && oldState.canBeReplaced()) {
                     if (currentState.equals(oldState)) {
                        Item requiredItem = newState.getBlock().asItem();
                        if (requiredItem != Items.AIR && InventoryHelper.findTotalItemsInInventory(player, requiredItem) > 0) {
                           InventoryHelper.consumeItems(player, requiredItem, 1);
                           level.setBlock(pos, newState, 3);
                           PlacedBlockTracker.trackAll(player.getUUID(), entry.dimension(), List.of(pos));
                           ++reapplied;
                        }
                     }
                     continue;
                  }

                  if (!oldState.isAir() && newState.isAir()) {
                     if (currentState.equals(oldState)) {
                        level.setBlock(pos, newState, 3);
                        giveBlockItem(player, oldState);
                        ++reapplied;
                     }
                     continue;
                  }
               }

               level.setBlock(pos, newState, 3);
               ++reapplied;
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
