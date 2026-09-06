package nl.requios.effortlessbuilding.buildmode.buildmodes;

import java.util.ArrayList;
import java.util.List;
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
      int reach = Config.getBuildingReach(player);
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
      List<BlockPos> list = new ArrayList();
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
      int x = x1;

      while(true) {
         if (x1 < x2) {
            if (x > x2) {
               break;
            }
         } else if (x < x2) {
            break;
         }

         list.add(new BlockPos(x, y, z));
         x += x1 < x2 ? 1 : -1;
      }

   }

   public static void addYLineBlocks(List<BlockPos> list, int y1, int y2, int x, int z) {
      int y = y1;

      while(true) {
         if (y1 < y2) {
            if (y > y2) {
               break;
            }
         } else if (y < y2) {
            break;
         }

         list.add(new BlockPos(x, y, z));
         y += y1 < y2 ? 1 : -1;
      }

   }

   public static void addZLineBlocks(List<BlockPos> list, int z1, int z2, int x, int y) {
      int z = z1;

      while(true) {
         if (z1 < z2) {
            if (z > z2) {
               break;
            }
         } else if (z < z2) {
            break;
         }

         list.add(new BlockPos(x, y, z));
         z += z1 < z2 ? 1 : -1;
      }

   }

   protected BlockPos findSecondPos(Player player, BlockPos firstPos, boolean skipRaytrace) {
      return findLine(player, firstPos, skipRaytrace);
   }

   protected List<BlockPos> getAllBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2) {
      return getLineBlocks(player, x1, y1, z1, x2, y2, z2);
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

      public boolean isValid(Vec3 start, Vec3 look, int reach, Player player, boolean skipRaytrace) {
         return BuildModes.isCriteriaValid(start, look, reach, player, skipRaytrace, this.lineBound, this.planeBound, this.distToPlayerSq);
      }
   }
}
