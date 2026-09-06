package nl.requios.effortlessbuilding.utilities;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class InventoryHelper {
   public static int findTotalItemsInInventory(Player player, Item item) {
      int total = 0;
      Inventory inv = player.getInventory();

      for(int i = 0; i < inv.getContainerSize(); ++i) {
         ItemStack stack = inv.getItem(i);
         if (stack.is(item)) {
            total += stack.getCount();
         }
      }

      return total;
   }

   public static int consumeItems(Player player, Item item, int count) {
      if (count <= 0) {
         return 0;
      } else {
         int remaining = count;
         Inventory inv = player.getInventory();
         ItemStack mainHand = player.getMainHandItem();
         if (mainHand.is(item)) {
            int take = Math.min(count, mainHand.getCount());
            mainHand.shrink(take);
            remaining = count - take;
            if (remaining <= 0) {
               return count;
            }
         }

         for(int i = 0; i < inv.getContainerSize(); ++i) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(item)) {
               int take = Math.min(remaining, stack.getCount());
               stack.shrink(take);
               remaining -= take;
               if (remaining <= 0) {
                  return count;
               }
            }
         }

         return count - remaining;
      }
   }

   public static void giveOrDropItems(Player player, Item item, int count) {
      if (count > 0) {
         int batchSize;
         for(int remaining = count; remaining > 0; remaining -= batchSize) {
            batchSize = Math.min(remaining, item.getDefaultMaxStackSize());
            ItemStack stack = new ItemStack(item, batchSize);
            if (!player.getInventory().add(stack)) {
               ItemEntity drop = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack);
               drop.setNoPickUpDelay();
               player.level().addFreshEntity(drop);
            }
         }

      }
   }

   public static boolean hasCorrectToolForBlock(Player player, BlockState state) {
      if (!state.requiresCorrectToolForDrops()) {
         return true;
      } else {
         Inventory inv = player.getInventory();

         for(int i = 0; i < inv.getContainerSize(); ++i) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.isCorrectToolForDrops(state)) {
               return true;
            }
         }

         return false;
      }
   }

   public static ItemStack findCorrectTool(Player player, BlockState state) {
      Inventory inv = player.getInventory();

      for(int i = 0; i < inv.getContainerSize(); ++i) {
         ItemStack stack = inv.getItem(i);
         if (!stack.isEmpty() && stack.isCorrectToolForDrops(state)) {
            return stack;
         }
      }

      return player.getMainHandItem();
   }

   public static void damageCorrectTool(Player player, BlockState state) {
      if (player instanceof ServerPlayer serverPlayer) {
         ServerLevel serverLevel = serverPlayer.serverLevel();
         Inventory inv = player.getInventory();

         for(int i = 0; i < inv.getContainerSize(); ++i) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.isDamageableItem() && stack.isCorrectToolForDrops(state)) {
               stack.hurtAndBreak(1, serverLevel, serverPlayer, (item) -> {
               });
               return;
            }
         }

         ItemStack mainHand = player.getMainHandItem();
         if (!mainHand.isEmpty() && mainHand.isDamageableItem()) {
            mainHand.hurtAndBreak(1, serverLevel, serverPlayer, (item) -> {
            });
         }

      }
   }
}
