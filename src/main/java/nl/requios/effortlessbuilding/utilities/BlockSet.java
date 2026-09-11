package nl.requios.effortlessbuilding.utilities;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.core.BlockPos;
import nl.requios.effortlessbuilding.Constants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Insertion-ordered set of {@link BlockEntry} keyed by packed block position.
 *
 * <p>Backed by a {@link Long2ObjectLinkedOpenHashMap} keyed with
 * {@link BlockPos#asLong()} instead of a {@code LinkedHashMap<BlockPos,
 * BlockEntry>}. Open addressing stores keys in primitive {@code long[]} arrays,
 * so there are no per-entry map nodes and no {@code BlockPos} key objects
 * retained by the table — insertion allocates nothing but the entry itself.
 * Iteration order is still insertion order, which the max-blocks cap and the
 * distance sort both rely on.</p>
 *
 * <p>Every entry's {@link BlockEntry#blockPos} always matches its packed key,
 * so iteration should use the entry's position directly instead of unpacking
 * the key.</p>
 */
public class BlockSet implements Iterable<BlockEntry> {
   public static boolean logging = true;
   public BlockPos firstPos;
   public BlockPos lastPos;
   public boolean skipFirst;

   private final Long2ObjectLinkedOpenHashMap<BlockEntry> entries;

   public BlockSet() {
      this.entries = new Long2ObjectLinkedOpenHashMap<>();
   }

   public BlockSet(int expectedSize) {
      this.entries = new Long2ObjectLinkedOpenHashMap<>(expectedSize);
   }

   public BlockSet(BlockSet other) {
      this.entries = new Long2ObjectLinkedOpenHashMap<>(other.size());
      for (Long2ObjectMap.Entry<BlockEntry> e : other.entries.long2ObjectEntrySet()) {
         this.entries.put(e.getLongKey(), e.getValue());
      }
      this.firstPos = other.firstPos;
      this.lastPos = other.lastPos;
      this.skipFirst = other.skipFirst;
   }

   public BlockSet(List<BlockEntry> blockEntries, BlockPos firstPos, BlockPos lastPos, boolean skipFirst) {
      this.entries = new Long2ObjectLinkedOpenHashMap<>(blockEntries.size());
      for (int i = 0, n = blockEntries.size(); i < n; i++) {
         this.add(blockEntries.get(i));
      }

      this.firstPos = firstPos;
      this.lastPos = lastPos;
      this.skipFirst = skipFirst;
   }

   public void setStartPos(BlockEntry startPos) {
      this.entries.clear();
      this.add(startPos);
      this.firstPos = startPos.blockPos;
      this.lastPos = startPos.blockPos;
   }

   public void add(BlockEntry blockEntry) {
      long key = blockEntry.blockPos.asLong();
      if (!this.entries.containsKey(key)) {
         this.entries.put(key, blockEntry);
      } else if (logging) {
         Constants.LOG.debug("BlockSet already contains block at {}", blockEntry.blockPos);
      }
   }

    /**
     * Bulk insert of plain positions, wrapping each in a {@link BlockEntry}.
     * Indexed loop (no iterator garbage), one table probe per position, same
     * first-wins dedup semantics as {@link #add(BlockEntry)}.
     */
    public void addAllPositions(List<BlockPos> positions) {
       for (int i = 0, n = positions.size(); i < n; i++) {
          BlockPos pos = positions.get(i);
          long key = pos.asLong();
          if (!this.entries.containsKey(key)) {
             this.entries.put(key, new BlockEntry(pos));
          } else if (logging) {
             Constants.LOG.debug("BlockSet already contains block at {}", pos);
          }
       }
    }

    /**
     * Fast-path insert of one packed position ({@link BlockPos#asLong()}).
     * Same first-wins dedup as {@link #add(BlockEntry)}; unpacks to a
     * {@code BlockPos} only for the stored entry. Used by
     * {@code getCommonBlocks} streaming, which emits bare packed longs
     * with no intermediate {@code List<BlockPos>}.
     */
    public void addPacked(long packedPos) {
       if (!this.entries.containsKey(packedPos)) {
          this.entries.put(packedPos, new BlockEntry(BlockPos.of(packedPos)));
       } else if (logging) {
          Constants.LOG.debug("BlockSet already contains block at {}", BlockPos.of(packedPos));
       }
    }

    /** Pre-sizes the table so a known-size stream fill never rehashes mid-way. */
    public void ensureCapacity(int expectedSize) {
       this.entries.ensureCapacity(expectedSize);
    }

   public @Nullable BlockEntry get(BlockPos pos) {
      return pos == null ? null : this.entries.get(pos.asLong());
   }

   public @Nullable BlockEntry get(long packedPos) {
      return this.entries.get(packedPos);
   }

   public boolean containsKey(BlockPos pos) {
      return pos != null && this.entries.containsKey(pos.asLong());
   }

   public boolean containsKey(long packedPos) {
      return this.entries.containsKey(packedPos);
   }

   public int size() {
      return this.entries.size();
   }

   public boolean isEmpty() {
      return this.entries.isEmpty();
   }

   public void clear() {
      this.entries.clear();
   }

   /** Snapshot of entries in insertion order (for transforms that append while iterating). */
   public List<BlockEntry> snapshotEntries() {
      return new ArrayList<>(this.entries.values());
   }

   /** Block positions in insertion order; allocates one list plus one immutable pos per entry. */
   public List<BlockPos> copyPositions() {
      List<BlockPos> result = new ArrayList<>(this.entries.size());
      LongIterator it = this.entries.keySet().iterator();
      while (it.hasNext()) {
         result.add(BlockPos.of(it.nextLong()));
      }
      return result;
   }

   /** Direct access to entries in insertion order; no {@code Map.Entry} objects. */
   public Iterable<BlockEntry> values() {
      return this.entries.values();
   }

   public void truncate(int maxSize) {
      if (this.entries.size() <= maxSize) {
         return;
      }
      LongIterator it = this.entries.keySet().iterator();
      int count = 0;
      while (it.hasNext()) {
         it.nextLong();
         if (++count > maxSize) {
            it.remove();
         }
      }
   }

   public void sortByDistance() {
      if (this.firstPos == null || this.entries.size() < 2) {
         return;
      }
      List<BlockEntry> ordered = new ArrayList<>(this.entries.values());
      BlockPos origin = this.firstPos;
      ordered.sort(Comparator.comparingDouble((entry) -> entry.blockPos.distSqr(origin)));
      this.entries.clear();
      for (int i = 0, n = ordered.size(); i < n; i++) {
         BlockEntry entry = ordered.get(i);
         this.entries.put(entry.blockPos.asLong(), entry);
      }
   }

   public List<BlockEntry> validEntries() {
      List<BlockEntry> result = new ArrayList<>(this.entries.size());
      for (BlockEntry entry : this.entries.values()) {
         if (entry.isValid()) {
            result.add(entry);
         }
      }
      return result;
   }

   public List<BlockEntry> rejectedEntries() {
      List<BlockEntry> result = new ArrayList<>();
      for (BlockEntry entry : this.entries.values()) {
         if (!entry.isValid()) {
            result.add(entry);
         }
      }
      return result;
   }

   public boolean hasEntriesWithStatus(BlockStatus status) {
      for (BlockEntry entry : this.entries.values()) {
         if (entry.getStatus() == status) {
            return true;
         }
      }
      return false;
   }

   public List<BlockPos> validPositions() {
      List<BlockPos> result = new ArrayList<>(this.entries.size());
      for (BlockEntry entry : this.entries.values()) {
         if (entry.isValid()) {
            result.add(entry.blockPos);
         }
      }
      return result;
   }

   public int validCount() {
      int count = 0;
      for (BlockEntry entry : this.entries.values()) {
         if (entry.isValid()) {
            ++count;
         }
      }
      return count;
   }

   public void resetAllStatuses() {
      for (BlockEntry entry : this.entries.values()) {
         entry.resetStatus();
      }
   }

   public @NotNull Iterator<BlockEntry> iterator() {
      return this.entries.values().iterator();
   }
}
