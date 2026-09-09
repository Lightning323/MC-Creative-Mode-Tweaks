package nl.requios.effortlessbuilding.buildmode.buildmodes;

import java.util.ArrayList;
import java.util.List;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import nl.requios.effortlessbuilding.buildmode.BuildModes;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import nl.requios.effortlessbuilding.buildmode.TwoClicksBuildMode;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipeline;
import org.lightning323.creative_mode_tweaks.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class Wall extends TwoClicksBuildMode {
   public static BlockPos findWall(Player player, BlockPos firstPos, boolean skipRaytrace) {
      Vec3 look = BuildPipeline.getPlayerLookVec(player);
      Vec3 start = BuildPipeline.getPlayerEyePosition(player);
      List<Criteria> criteriaList = new ArrayList(3);
      Vec3 xBound = BuildModes.findXBound((double)firstPos.getX(), start, look);
      criteriaList.add(new Criteria(xBound, firstPos, start, look));
      Vec3 zBound = BuildModes.findZBound((double)firstPos.getZ(), start, look);
      criteriaList.add(new Criteria(zBound, firstPos, start, look));
      double reach = Config.getBuildingReach(player);
      criteriaList.removeIf((criteriax) -> !criteriax.isValid(start, look, reach, player, skipRaytrace));
      if (criteriaList.isEmpty()) {
         return null;
      } else {
         Criteria selected = (Criteria)criteriaList.get(0);
         if (criteriaList.size() > 1) {
            for(int i = 1; i < criteriaList.size(); ++i) {
               Criteria criteria = (Criteria)criteriaList.get(i);
               if (criteria.distToPlayerSq < selected.distToPlayerSq && Math.abs(criteria.angle) - Math.abs(selected.angle) < (double)3.0F) {
                  selected = criteria;
               }
            }
         }

         return BlockPos.containing(selected.planeBound);
      }
   }

   public static List<BlockPos> getWallBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2) {
      long dx = Math.abs((long) x2 - x1) + 1L;
      long dy = Math.abs((long) y2 - y1) + 1L;
      long dz = Math.abs((long) z2 - z1) + 1L;
      boolean full = ModeOptions.getFill() == ModeOptions.ActionEnum.FULL;
      List<BlockPos> list;
      if (x1 == x2) {
         // Full walls fill the face; hollow ones only trace its edge.
         long est = full ? dy * dz : 2L * (dy + dz);
         list = new ArrayList<>((int) Math.min(est, 131072));
         if (full) {
            addXWallBlocks(list, x1, y1, y2, z1, z2);
         } else {
            addXHollowWallBlocks(list, x1, y1, y2, z1, z2);
         }
      } else if (full) {
         list = new ArrayList<>((int) Math.min(dx * dy, 131072));
         addZWallBlocks(list, x1, x2, y1, y2, z1);
      } else {
         list = new ArrayList<>((int) Math.min(2L * (dx + dy), 131072));
         addZHollowWallBlocks(list, x1, x2, y1, y2, z1);
      }

      return list;
   }

   public static void addXWallBlocks(List<BlockPos> list, int x, int y1, int y2, int z1, int z2) {
      forEachXWallBlocks(x, y1, y2, z1, z2, packed -> list.add(BlockPos.of(packed)));
   }

   /** Bare-int twin of {@link #addXWallBlocks}: same order, packed longs, no allocation. */
   public static void forEachXWallBlocks(int x, int y1, int y2, int z1, int z2, LongConsumer out) {
      int zStep = z1 < z2 ? 1 : -1;
      int yStep = y1 < y2 ? 1 : -1;
      for (int z = z1; ; z += zStep) {
         for (int y = y1; ; y += yStep) {
            out.accept(BlockPos.asLong(x, y, z));
            if (y == y2) {
               break;
            }
         }
         if (z == z2) {
            break;
         }
      }
   }

   public static void addZWallBlocks(List<BlockPos> list, int x1, int x2, int y1, int y2, int z) {
      forEachZWallBlocks(x1, x2, y1, y2, z, packed -> list.add(BlockPos.of(packed)));
   }

   /** Bare-int twin of {@link #addZWallBlocks}: same order, packed longs, no allocation. */
   public static void forEachZWallBlocks(int x1, int x2, int y1, int y2, int z, LongConsumer out) {
      int xStep = x1 < x2 ? 1 : -1;
      int yStep = y1 < y2 ? 1 : -1;
      for (int x = x1; ; x += xStep) {
         for (int y = y1; ; y += yStep) {
            out.accept(BlockPos.asLong(x, y, z));
            if (y == y2) {
               break;
            }
         }
         if (x == x2) {
            break;
         }
      }
   }

   public static void addXHollowWallBlocks(List<BlockPos> list, int x, int y1, int y2, int z1, int z2) {
      Line.addZLineBlocks(list, z1, z2, x, y1);
      Line.addZLineBlocks(list, z1, z2, x, y2);
      Line.addYLineBlocks(list, y1, y2, x, z1);
      Line.addYLineBlocks(list, y1, y2, x, z2);
   }

   /** Bare-int twin of {@link #addXHollowWallBlocks}: same order, packed longs, no allocation. */
   public static void forEachXHollowWallBlocks(int x, int y1, int y2, int z1, int z2, LongConsumer out) {
      Line.forEachZLineBlocks(z1, z2, x, y1, out);
      Line.forEachZLineBlocks(z1, z2, x, y2, out);
      Line.forEachYLineBlocks(y1, y2, x, z1, out);
      Line.forEachYLineBlocks(y1, y2, x, z2, out);
   }

   public static void addZHollowWallBlocks(List<BlockPos> list, int x1, int x2, int y1, int y2, int z) {
      Line.addXLineBlocks(list, x1, x2, y1, z);
      Line.addXLineBlocks(list, x1, x2, y2, z);
      Line.addYLineBlocks(list, y1, y2, x1, z);
      Line.addYLineBlocks(list, y1, y2, x2, z);
   }

   /** Bare-int twin of {@link #addZHollowWallBlocks}: same order, packed longs, no allocation. */
   public static void forEachZHollowWallBlocks(int x1, int x2, int y1, int y2, int z, LongConsumer out) {
      Line.forEachXLineBlocks(x1, x2, y1, z, out);
      Line.forEachXLineBlocks(x1, x2, y2, z, out);
      Line.forEachYLineBlocks(y1, y2, x1, z, out);
      Line.forEachYLineBlocks(y1, y2, x2, z, out);
   }

   /** Streams {@link #getWallBlocks} as packed longs: no intermediate list. */
   public static void forEachWallBlocks(int x1, int y1, int z1, int x2, int y2, int z2, boolean full, LongConsumer out) {
      if (x1 == x2) {
         if (full) {
            forEachXWallBlocks(x1, y1, y2, z1, z2, out);
         } else {
            forEachXHollowWallBlocks(x1, y1, y2, z1, z2, out);
         }
      } else if (full) {
         forEachZWallBlocks(x1, x2, y1, y2, z1, out);
      } else {
         forEachZHollowWallBlocks(x1, x2, y1, y2, z1, out);
      }
   }

   protected BlockPos findSecondPos(Player player, BlockPos firstPos, boolean skipRaytrace) {
      return findWall(player, firstPos, skipRaytrace);
   }

   protected List<BlockPos> getAllBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2) {
      return getWallBlocks(player, x1, y1, z1, x2, y2, z2);
   }

   @Override
   protected void forEachAllBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, LongConsumer out) {
      forEachWallBlocks(x1, y1, z1, x2, y2, z2, ModeOptions.getFill() == ModeOptions.ActionEnum.FULL, out);
   }

   static class Criteria {
      Vec3 planeBound;
      double distToPlayerSq;
      double angle;

      Criteria(Vec3 planeBound, BlockPos firstPos, Vec3 start, Vec3 look) {
         this.planeBound = planeBound;
         this.distToPlayerSq = this.planeBound.subtract(start).lengthSqr();
         Vec3 wall = this.planeBound.subtract(Vec3.atLowerCornerOf(firstPos));
         this.angle = wall.x * look.x + wall.z * look.z;
      }

      public boolean isValid(Vec3 start, Vec3 look, double reach, Player player, boolean skipRaytrace) {
         return BuildModes.isCriteriaValid(start, look, reach, player, skipRaytrace, this.planeBound, this.planeBound, this.distToPlayerSq);
      }
   }
}
