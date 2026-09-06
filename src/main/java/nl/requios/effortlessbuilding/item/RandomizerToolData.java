package nl.requios.effortlessbuilding.item;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

public final class RandomizerToolData {
   public static final int SLOT_COUNT = 9;
   private static final String BLOCKS_TAG = "RandomizerBlocks";
   private static final String RATIOS_TAG = "RandomizerRatios";

   private RandomizerToolData() {
   }

   public static List<Item> getItems(ItemStack tool) {
      List<Item> result = new ArrayList(9);
      ListTag tag = ((CustomData)tool.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)).copyTag().getList("RandomizerBlocks", 8);

      for(int i = 0; i < 9; ++i) {
         Item item = Items.AIR;
         if (i < tag.size()) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString(i));
            if (id != null) {
               item = (Item)BuiltInRegistries.ITEM.get(id);
            }
         }

         result.add(item);
      }

      return result;
   }

   public static List<ItemStack> getStacks(ItemStack tool) {
      return getItems(tool).stream().map((item) -> item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item)).toList();
   }

   public static List<Integer> getRatios(ItemStack tool) {
      CompoundTag root = ((CustomData)tool.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)).copyTag();
      Tag storedRatios = root.get("RandomizerRatios");
      ListTag var10000;
      if (storedRatios instanceof ListTag list) {
         var10000 = list;
      } else {
         var10000 = new ListTag();
      }

      ListTag tag = var10000;
      int[] ratioArray = storedRatios instanceof IntArrayTag ? root.getIntArray("RandomizerRatios") : null;
      List<Integer> result = new ArrayList(9);
      List<Item> items = getItems(tool);
      boolean hasStoredRatios = ratioArray != null || storedRatios instanceof ListTag;

      for(int i = 0; i < 9; ++i) {
         int ratio = ratioArray != null && i < ratioArray.length ? ratioArray[i] : (hasStoredRatios && i < tag.size() ? tag.getInt(i) : (items.get(i) == Items.AIR ? 0 : 1));
         result.add(Math.max(0, ratio));
      }

      return result;
   }

   public static void setItems(ItemStack tool, List<Item> items) {
      setConfiguration(tool, items, getRatios(tool));
   }

   public static void setConfiguration(ItemStack tool, List<Item> items, List<Integer> ratios) {
      CustomData.update(DataComponents.CUSTOM_DATA, tool, (root) -> {
         ListTag blocks = new ListTag();

         for(int i = 0; i < 9; ++i) {
            Item item = i < items.size() ? (Item)items.get(i) : Items.AIR;
            String id = item != null && item != Items.AIR ? BuiltInRegistries.ITEM.getKey(item).toString() : "";
            blocks.add(StringTag.valueOf(id));
         }

         root.put("RandomizerBlocks", blocks);
         int[] storedRatios = new int[9];

         for(int i = 0; i < 9; ++i) {
            int ratio = i < ratios.size() ? (Integer)ratios.get(i) : 0;
            storedRatios[i] = Math.max(0, ratio);
         }

         root.putIntArray("RandomizerRatios", storedRatios);
      });
   }
}
