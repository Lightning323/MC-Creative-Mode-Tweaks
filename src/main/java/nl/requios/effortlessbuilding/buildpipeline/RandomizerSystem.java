package nl.requios.effortlessbuilding.buildpipeline;

import java.util.Iterator;
import java.util.List;
import nl.requios.effortlessbuilding.item.RandomizerToolData;
import nl.requios.effortlessbuilding.item.RandomizerToolItem;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

public final class RandomizerSystem implements IBuildSystem {
   public static final RandomizerSystem INSTANCE = new RandomizerSystem();

   private RandomizerSystem() {
   }

   public void processBlocks(BlockSet blocks, Player player, BuildPipeline.BuildState action) {
      if (action == BuildPipeline.BuildState.PLACING) {
         ItemStack tool = player.getMainHandItem();
         if (tool.getItem() instanceof RandomizerToolItem) {
            List<ItemStack> slots = RandomizerToolData.getStacks(tool);
            List<Integer> ratios = RandomizerToolData.getRatios(tool);
            long totalRatio = 0L;

            for(int i = 0; i < 9; ++i) {
               ItemStack stack = (ItemStack)slots.get(i);
               if (stack.isEmpty() || stack.getItem() instanceof BlockItem && BuildPipeline.isBuildTriggerItem(stack)) {
                  totalRatio += (long)(Integer)ratios.get(i);
               }
            }

            if (totalRatio == 0L) {
               blocks.clear();
            } else {
               Iterator<BlockEntry> iterator = blocks.values().iterator();

               while(iterator.hasNext()) {
                  BlockEntry entry = (BlockEntry)iterator.next();
                  ItemStack choice = getChoice(slots, ratios, randomIndex(entry.blockPos.asLong(), totalRatio));
                  if (choice.isEmpty()) {
                     iterator.remove();
                  } else {
                     BlockItem blockItem = (BlockItem)choice.getItem();
                     entry.item = blockItem;
                     entry.blockState = blockItem.getBlock().defaultBlockState();
                  }
               }

            }
         }
      }
   }

   private static ItemStack getChoice(List<ItemStack> slots, List<Integer> ratios, long selection) {
      long upperBound = 0L;

      for(int i = 0; i < 9; ++i) {
         ItemStack stack = (ItemStack)slots.get(i);
         if (stack.isEmpty() || stack.getItem() instanceof BlockItem && BuildPipeline.isBuildTriggerItem(stack)) {
            upperBound += (long)(Integer)ratios.get(i);
            if (selection < upperBound) {
               return stack;
            }
         }
      }

      return ItemStack.EMPTY;
   }

   private static long randomIndex(long position, long size) {
      long value = position + -7046029254386353131L;
      value = (value ^ value >>> 30) * -4658895280553007687L;
      value = (value ^ value >>> 27) * -7723592293110705685L;
      value ^= value >>> 31;
      return Math.floorMod(value, size);
   }
}
