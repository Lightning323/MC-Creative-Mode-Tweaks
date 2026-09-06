package nl.requios.effortlessbuilding.utilities;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class PlacedBlockTracker {
   private static final int MAX_POSITIONS_PER_PLAYER = 10000;
   public static final UUID CLIENT_ID = new UUID(64408460885L, 12656295L);
   private static final Map<UUID, Map<ResourceKey<Level>, LinkedHashSet<BlockPos>>> data = new HashMap();

   public static void clientTrackAll(ResourceKey<Level> dimension, Collection<BlockPos> positions) {
      trackAll(CLIENT_ID, dimension, positions);
   }

   public static boolean clientIsTracked(ResourceKey<Level> dimension, BlockPos pos) {
      return isTracked(CLIENT_ID, dimension, pos);
   }

   public static void clearClient() {
      clearPlayer(CLIENT_ID);
   }

   public static void trackAll(UUID playerId, ResourceKey<Level> dimension, Collection<BlockPos> positions) {
      if (!positions.isEmpty()) {
         LinkedHashSet<BlockPos> set = getOrCreate(playerId, dimension);

         for(BlockPos pos : positions) {
            set.add(pos.immutable());
         }

         evict(playerId, dimension);
      }
   }

   public static boolean isTracked(UUID playerId, ResourceKey<Level> dimension, BlockPos pos) {
      Map<ResourceKey<Level>, LinkedHashSet<BlockPos>> dimMap = (Map)data.get(playerId);
      if (dimMap == null) {
         return false;
      } else {
         LinkedHashSet<BlockPos> set = (LinkedHashSet)dimMap.get(dimension);
         return set != null && set.contains(pos);
      }
   }

   public static boolean isTrackedAnySide(Player player, Level level, BlockPos pos) {
      return level.isClientSide() ? clientIsTracked(level.dimension(), pos) : isTracked(player.getUUID(), level.dimension(), pos);
   }

   public static void clearPlayer(UUID playerId) {
      data.remove(playerId);
   }

   private static LinkedHashSet<BlockPos> getOrCreate(UUID playerId, ResourceKey<Level> dimension) {
      return (LinkedHashSet)((Map)data.computeIfAbsent(playerId, (k) -> new HashMap())).computeIfAbsent(dimension, (k) -> new LinkedHashSet());
   }

   private static void evict(UUID playerId, ResourceKey<Level> dimension) {
      Map<ResourceKey<Level>, LinkedHashSet<BlockPos>> dimMap = (Map)data.get(playerId);
      if (dimMap != null) {
         int total = 0;

         for(LinkedHashSet<BlockPos> s : dimMap.values()) {
            total += s.size();
         }

         if (total > 10000) {
            LinkedHashSet<BlockPos> set = (LinkedHashSet)dimMap.get(dimension);
            if (set != null) {
               for(Iterator<BlockPos> it = set.iterator(); total > 10000 && it.hasNext(); --total) {
                  it.next();
                  it.remove();
               }

            }
         }
      }
   }
}
