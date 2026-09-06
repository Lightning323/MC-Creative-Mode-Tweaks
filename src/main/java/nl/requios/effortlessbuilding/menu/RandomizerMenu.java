package nl.requios.effortlessbuilding.menu;

import java.util.ArrayList;
import java.util.List;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipeline;
import nl.requios.effortlessbuilding.item.RandomizerToolData;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class RandomizerMenu extends AbstractContainerMenu {
   private static final int GHOST_SLOT_END = 9;
   private static final int PLAYER_MAIN_END = 36;
   private static final int PLAYER_SLOT_END = 45;
   private final Container ghostSlots;
   private final ItemStack tool;
   private final int[] ratios;

   public RandomizerMenu(int containerId, Inventory playerInventory) {
      this(containerId, playerInventory, playerInventory.player.getMainHandItem());
   }

   public RandomizerMenu(int containerId, Inventory playerInventory, ItemStack tool) {
      super(ModMenus.RANDOMIZER, containerId);
      this.ghostSlots = new SimpleContainer(9);
      this.ratios = new int[9];
      this.tool = tool;
      List<ItemStack> configured = RandomizerToolData.getStacks(tool);
      List<Integer> configuredRatios = RandomizerToolData.getRatios(tool);

//      for( int i = 0; i < 9; ++i) {
//         this.ghostSlots.setItem(i, (ItemStack)configured.get(i));
//         this.ratios[i] = (Integer)configuredRatios.get(i);
//         this.addSlot(new GhostSlot(this.ghostSlots, i, 8 + i * 18, 36));
//         this.addDataSlot(new DataSlot() {
//            public int get() {
//               return RandomizerMenu.this.ratios[i];
//            }
//
//            public void set(int value) {
//               RandomizerMenu.this.ratios[i] = Math.max(0, value);
//            }
//         });
//      }

      for(int row = 0; row < 3; ++row) {
         for(int column = 0; column < 9; ++column) {
            this.addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 68 + row * 18));
         }
      }

      for(int column = 0; column < 9; ++column) {
         this.addSlot(new Slot(playerInventory, column, 8 + column * 18, 126));
      }

   }

   public void clicked(int slotId, int button, ClickType clickType, Player player) {
      if (slotId >= 0 && slotId < 9) {
         ItemStack source = clickType == ClickType.SWAP && button >= 0 && button < 9 ? player.getInventory().getItem(button) : this.getCarried();
         if (isSafePaletteBlock(source)) {
            this.ghostSlots.setItem(slotId, source.copyWithCount(1));
            if (this.ratios[slotId] == 0) {
               this.ratios[slotId] = 1;
            }
         } else if (clickType == ClickType.PICKUP && source.isEmpty()) {
            this.ghostSlots.setItem(slotId, ItemStack.EMPTY);
            if (this.ratios[slotId] == 1) {
               this.ratios[slotId] = 0;
            }
         }

         this.savePalette(player);
      } else {
         super.clicked(slotId, button, clickType, player);
      }
   }

   public ItemStack quickMoveStack(Player player, int index) {
      if (index >= 0 && index < this.slots.size()) {
         Slot slot = (Slot)this.slots.get(index);
         if (!slot.hasItem()) {
            return ItemStack.EMPTY;
         } else {
            ItemStack stack = slot.getItem();
            if (index >= 9 && isSafePaletteBlock(stack)) {
               for(int i = 0; i < 9; ++i) {
                  if (this.ghostSlots.getItem(i).isEmpty()) {
                     this.ghostSlots.setItem(i, stack.copyWithCount(1));
                     this.ratios[i] = 1;
                     this.savePalette(player);
                     return ItemStack.EMPTY;
                  }
               }
            }

            ItemStack original = stack.copy();
            if (index >= 9 && index < 36) {
               if (!this.moveItemStackTo(stack, 36, 45, false)) {
                  return ItemStack.EMPTY;
               }
            } else {
               if (index < 36 || index >= 45) {
                  return ItemStack.EMPTY;
               }

               if (!this.moveItemStackTo(stack, 9, 36, false)) {
                  return ItemStack.EMPTY;
               }
            }

            if (stack.isEmpty()) {
               slot.setByPlayer(ItemStack.EMPTY);
            } else {
               slot.setChanged();
            }

            return original;
         }
      } else {
         return ItemStack.EMPTY;
      }
   }

   public boolean clickMenuButton(Player player, int id) {
      if (id >= 0 && id < 18) {
         int slot = id % 9;
         int delta = id < 9 ? 1 : -1;
         if (delta < 0 && this.ratios[slot] == 0) {
            return false;
         } else if (delta > 0 && this.ratios[slot] == Integer.MAX_VALUE) {
            return false;
         } else {
            int[] var10000 = this.ratios;
            var10000[slot] += delta;
            this.savePalette(player);
            return true;
         }
      } else {
         return false;
      }
   }

   public int getRatio(int slot) {
      return slot >= 0 && slot < 9 ? this.ratios[slot] : 0;
   }

   public boolean adjustRatioClient(int slot, int delta) {
      if (slot >= 0 && slot < 9 && delta != 0) {
         if (delta < 0 && this.ratios[slot] == 0) {
            return false;
         } else if (delta > 0 && this.ratios[slot] == Integer.MAX_VALUE) {
            return false;
         } else {
            int[] var10000 = this.ratios;
            var10000[slot] += delta > 0 ? 1 : -1;
            return true;
         }
      } else {
         return false;
      }
   }

   private void savePalette(Player player) {
      List<Item> items = new ArrayList(9);

      for(int i = 0; i < 9; ++i) {
         ItemStack stack = this.ghostSlots.getItem(i);
         items.add(stack.isEmpty() ? Items.AIR : stack.getItem());
      }

      List<Integer> configuredRatios = new ArrayList(9);

      for(int ratio : this.ratios) {
         configuredRatios.add(ratio);
      }

      RandomizerToolData.setConfiguration(this.tool, items, configuredRatios);
      player.getInventory().setChanged();
      this.broadcastChanges();
   }

   private static boolean isSafePaletteBlock(ItemStack stack) {
      return stack.getItem() instanceof BlockItem && BuildPipeline.isBuildTriggerItem(stack) && stack.getComponentsPatch().isEmpty();
   }

   public boolean stillValid(Player player) {
      return player.isAlive();
   }

   private static final class GhostSlot extends Slot {
      private GhostSlot(Container container, int slot, int x, int y) {
         super(container, slot, x, y);
      }

      public boolean mayPlace(ItemStack stack) {
         return false;
      }

      public boolean mayPickup(Player player) {
         return false;
      }
   }
}
