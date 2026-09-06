package nl.requios.effortlessbuilding.utilities;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class BreakDisplayTracker {
   public Map<Item, Integer> breakable = new LinkedHashMap();
   public Map<Item, Integer> rejected = new LinkedHashMap();
   public Set<Item> missingTools = new LinkedHashSet();

   public void initialize() {
      this.breakable.clear();
      this.rejected.clear();
      this.missingTools.clear();
   }

   public void compute(Player player, BlockSet blockSet) {
      this.initialize();
      Level level = player.level();
      if (level != null) {
         for(Map.Entry<BlockPos, BlockEntry> mapEntry : blockSet.entrySet()) {
            BlockPos pos = (BlockPos)mapEntry.getKey();
            BlockEntry entry = (BlockEntry)mapEntry.getValue();
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
               Item blockItem = state.getBlock().asItem();
               if (blockItem != Items.AIR) {
                  if (entry.isValid()) {
                     this.breakable.merge(blockItem, 1, Integer::sum);
                  } else {
                     this.rejected.merge(blockItem, 1, Integer::sum);
                     if (entry.getStatus() == BlockStatus.MISSING_TOOL) {
                        this.collectMissingToolHint(state);
                     }
                  }
               }
            }
         }

      }
   }

   private void collectMissingToolHint(BlockState state) {
      if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
         this.missingTools.add(Items.IRON_PICKAXE);
      } else if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
         this.missingTools.add(Items.IRON_AXE);
      } else if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
         this.missingTools.add(Items.IRON_SHOVEL);
      } else if (state.is(BlockTags.MINEABLE_WITH_HOE)) {
         this.missingTools.add(Items.IRON_HOE);
      }

   }

   public boolean hasRejected() {
      return !this.rejected.isEmpty();
   }

   public boolean hasMissingTools() {
      return !this.missingTools.isEmpty();
   }

   public int getTotalBreakable() {
      int sum = 0;

      for(int v : this.breakable.values()) {
         sum += v;
      }

      return sum;
   }

   public int getTotalRejected() {
      int sum = 0;

      for(int v : this.rejected.values()) {
         sum += v;
      }

      return sum;
   }
}
