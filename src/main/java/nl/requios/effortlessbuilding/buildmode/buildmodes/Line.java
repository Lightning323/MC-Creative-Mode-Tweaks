package nl.requios.effortlessbuilding.buildmode.buildmodes;

import java.util.ArrayList;
import java.util.List;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import nl.requios.effortlessbuilding.buildmode.BuildModes;
import nl.requios.effortlessbuilding.buildmode.TwoClicksBuildMode;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipeline;
import org.lightning323.creative_mode_tweaks.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class Line extends TwoClicksBuildMode {
   public static BlockPos findLine(Player player, BlockPos firstPos, boolean skipRaytrace) {
      Vec3 look = BuildPipeline.getPlayerLookVec(player);
      Vec3 start = BuildPipeline.getPlayerEyePosition(player);
      List<Criteria> criteriaList = new ArrayList(3);
      Vec3 xBound = BuildModes.findXBound((double)firstPos.getX(), start, look);
      criteriaList.add(new Criteria(xBound, firstPos, start));
      Vec3 yBound = BuildModes.findYBound((double)firstPos.getY(), start, look);
      criteriaList.add(new Criteria(yBound, firstPos, start));
      Vec3 zBound = BuildModes.findZBound((double)firstPos.getZ(), start, look);
      criteriaList.add(new Criteria(zBound, firstPos, start));
      double reach = Config.getBuildingReach(player);
      criteriaList.removeIf((criteriax) -> !criteriax.isValid(start, look, reach, player, skipRaytrace));
      if (criteriaList.isEmpty()) {
         return null;
      } else {
         Criteria selected = (Criteria)criteriaList.get(0);
         if (criteriaList.size() > 1) {
            for(int i = 1; i < criteriaList.size(); ++i) {
               Criteria criteria = (Criteria)criteriaList.get(i);
               if (criteria.distToLineSq < (double)2.0F && selected.distToLineSq < (double)2.0F) {
                  if (criteria.distToPlayerSq < selected.distToPlayerSq) {
                     selected = criteria;
                  }
               } else if (criteria.distToLineSq < selected.distToLineSq) {
                  selected = criteria;
               }
            }
         }

         return BlockPos.containing(selected.lineBound);
      }
   }

   public static List<BlockPos> getLineBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2) {
      // Lines run along exactly one axis: length + 1 is the exact count.
      long len = x1 != x2 ? Math.abs((long) x2 - x1) : y1 != y2 ? Math.abs((long) y2 - y1) : Math.abs((long) z2 - z1);
      List<BlockPos> list = new ArrayList<>((int) Math.min(len + 1L, 131072));
      if (x1 != x2) {
         addXLineBlocks(list, x1, x2, y1, z1);
      } else if (y1 != y2) {
         addYLineBlocks(list, y1, y2, x1, z1);
      } else {
         addZLineBlocks(list, z1, z2, x1, y1);
      }

      return list;
   }

   public static void addXLineBlocks(List<BlockPos> list, int x1, int x2, int y, int z) {
      forEachXLineBlocks(x1, x2, y, z, packed -> list.add(BlockPos.of(packed)));
   }

   /** Bare-int twin of {@link #addXLineBlocks}: same order, packed longs, no allocation. */
   public static void forEachXLineBlocks(int x1, int x2, int y, int z, LongConsumer out) {
      int step = x1 < x2 ? 1 : -1;
      for (int x = x1; ; x += step) {
         out.accept(BlockPos.asLong(x, y, z));
         if (x == x2) {
            break;
         }
      }
   }

   public static void addYLineBlocks(List<BlockPos> list, int y1, int y2, int x, int z) {
      forEachYLineBlocks(y1, y2, x, z, packed -> list.add(BlockPos.of(packed)));
   }

   /** Bare-int twin of {@link #addYLineBlocks}: same order, packed longs, no allocation. */
   public static void forEachYLineBlocks(int y1, int y2, int x, int z, LongConsumer out) {
      int step = y1 < y2 ? 1 : -1;
      for (int y = y1; ; y += step) {
         out.accept(BlockPos.asLong(x, y, z));
         if (y == y2) {
            break;
         }
      }
   }

   public static void addZLineBlocks(List<BlockPos> list, int z1, int z2, int x, int y) {
      forEachZLineBlocks(z1, z2, x, y, packed -> list.add(BlockPos.of(packed)));
   }

   /** Bare-int twin of {@link #addZLineBlocks}: same order, packed longs, no allocation. */
   public static void forEachZLineBlocks(int z1, int z2, int x, int y, LongConsumer out) {
      int step = z1 < z2 ? 1 : -1;
      for (int z = z1; ; z += step) {
         out.accept(BlockPos.asLong(x, y, z));
         if (z == z2) {
            break;
         }
      }
   }

   protected BlockPos findSecondPos(Player player, BlockPos firstPos, boolean skipRaytrace) {
      return findLine(player, firstPos, skipRaytrace);
   }

   protected List<BlockPos> getAllBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2) {
      return getLineBlocks(player, x1, y1, z1, x2, y2, z2);
   }

   @Override
   protected void forEachAllBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, LongConsumer out) {
      if (x1 != x2) {
         forEachXLineBlocks(x1, x2, y1, z1, out);
      } else if (y1 != y2) {
         forEachYLineBlocks(y1, y2, x1, z1, out);
      } else {
         forEachZLineBlocks(z1, z2, x1, y1, out);
      }
   }

   static class Criteria {
      Vec3 planeBound;
      Vec3 lineBound;
      double distToLineSq;
      double distToPlayerSq;

      Criteria(Vec3 planeBound, BlockPos firstPos, Vec3 start) {
         this.planeBound = planeBound;
         this.lineBound = this.toLongestLine(this.planeBound, firstPos);
         this.distToLineSq = this.lineBound.subtract(this.planeBound).lengthSqr();
         this.distToPlayerSq = this.planeBound.subtract(start).lengthSqr();
      }

      private Vec3 toLongestLine(Vec3 boundVec, BlockPos firstPos) {
         BlockPos bound = BlockPos.containing(boundVec);
         BlockPos firstToSecond = bound.subtract(firstPos);
         firstToSecond = new BlockPos(Math.abs(firstToSecond.getX()), Math.abs(firstToSecond.getY()), Math.abs(firstToSecond.getZ()));
         int longest = Math.max(firstToSecond.getX(), Math.max(firstToSecond.getY(), firstToSecond.getZ()));
         if (longest == firstToSecond.getX()) {
            return new Vec3((double)bound.getX(), (double)firstPos.getY(), (double)firstPos.getZ());
         } else if (longest == firstToSecond.getY()) {
            return new Vec3((double)firstPos.getX(), (double)bound.getY(), (double)firstPos.getZ());
         } else {
            return longest == firstToSecond.getZ() ? new Vec3((double)firstPos.getX(), (double)firstPos.getY(), (double)bound.getZ()) : null;
         }
      }

      public boolean isValid(Vec3 start, Vec3 look, double reach, Player player, boolean skipRaytrace) {
         return BuildModes.isCriteriaValid(start, look, reach, player, skipRaytrace, this.lineBound, this.planeBound, this.distToPlayerSq);
      }
   }
}
