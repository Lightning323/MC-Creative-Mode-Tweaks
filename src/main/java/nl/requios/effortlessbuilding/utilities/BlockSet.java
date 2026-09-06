package nl.requios.effortlessbuilding.utilities;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import nl.requios.effortlessbuilding.Constants;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;

public class BlockSet extends LinkedHashMap<BlockPos, BlockEntry> implements Iterable<BlockEntry> {
   public static boolean logging = true;
   public BlockPos firstPos;
   public BlockPos lastPos;
   public boolean skipFirst;

   public BlockSet() {
   }

   public BlockSet(BlockSet blockSet) {
      super(blockSet);
      this.firstPos = blockSet.firstPos;
      this.lastPos = blockSet.lastPos;
      this.skipFirst = blockSet.skipFirst;
   }

   public BlockSet(List<BlockEntry> blockEntries, BlockPos firstPos, BlockPos lastPos, boolean skipFirst) {
      for(BlockEntry blockEntry : blockEntries) {
         this.add(blockEntry);
      }

      this.firstPos = firstPos;
      this.lastPos = lastPos;
      this.skipFirst = skipFirst;
   }

   public void setStartPos(BlockEntry startPos) {
      this.clear();
      this.add(startPos);
      this.firstPos = startPos.blockPos;
      this.lastPos = startPos.blockPos;
   }

   public void add(BlockEntry blockEntry) {
      if (!this.containsKey(blockEntry.blockPos)) {
         this.put(blockEntry.blockPos, blockEntry);
      } else if (logging) {
         Constants.LOG.debug("BlockSet already contains block at {}", blockEntry.blockPos);
      }

   }

   public void truncate(int maxSize) {
      if (this.size() > maxSize) {
         Iterator<BlockPos> iter = this.keySet().iterator();
         int count = 0;

         while(iter.hasNext()) {
            iter.next();
            ++count;
            if (count > maxSize) {
               iter.remove();
            }
         }

      }
   }

   public void sortByDistance() {
      if (this.firstPos != null) {
         List<Map.Entry<BlockPos, BlockEntry>> entries = new ArrayList(this.entrySet());
         entries.sort(Comparator.comparingDouble((e) -> ((BlockPos)e.getKey()).distSqr(this.firstPos)));
         this.clear();

         for(Map.Entry<BlockPos, BlockEntry> entry : entries) {
            this.put((BlockPos)entry.getKey(), (BlockEntry)entry.getValue());
         }

      }
   }

   public List<Map.Entry<BlockPos, BlockEntry>> validEntries() {
      return (List)this.entrySet().stream().filter((e) -> ((BlockEntry)e.getValue()).isValid()).collect(Collectors.toList());
   }

   public List<Map.Entry<BlockPos, BlockEntry>> rejectedEntries() {
      return (List)this.entrySet().stream().filter((e) -> !((BlockEntry)e.getValue()).isValid()).collect(Collectors.toList());
   }

   public List<BlockPos> validPositions() {
      List<BlockPos> result = new ArrayList();

      for(Map.Entry<BlockPos, BlockEntry> entry : this.entrySet()) {
         if (((BlockEntry)entry.getValue()).isValid()) {
            result.add((BlockPos)entry.getKey());
         }
      }

      return result;
   }

   public int validCount() {
      int count = 0;

      for(BlockEntry entry : this.values()) {
         if (entry.isValid()) {
            ++count;
         }
      }

      return count;
   }

   public void resetAllStatuses() {
      for(BlockEntry entry : this.values()) {
         entry.resetStatus();
      }

   }

   public @NotNull Iterator<BlockEntry> iterator() {
      return this.values().iterator();
   }
}
