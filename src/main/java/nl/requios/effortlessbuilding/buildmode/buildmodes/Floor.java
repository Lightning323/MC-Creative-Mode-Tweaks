package nl.requios.effortlessbuilding.buildmode.buildmodes;

import java.util.ArrayList;
import java.util.List;
import nl.requios.effortlessbuilding.buildmode.BuildModes;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import nl.requios.effortlessbuilding.buildmode.TwoClicksBuildMode;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipeline;
import org.lightning323.creative_mode_tweaks.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class Floor extends TwoClicksBuildMode {
   public static BlockPos findFloor(Player player, BlockPos firstPos, boolean skipRaytrace) {
      Vec3 look = BuildPipeline.getPlayerLookVec(player);
      Vec3 start = BuildPipeline.getPlayerEyePosition(player);
      List<Criteria> criteriaList = new ArrayList(3);
      Vec3 yBound = BuildModes.findYBound((double)firstPos.getY(), start, look);
      criteriaList.add(new Criteria(yBound, start));
      double reach = Config.getBuildingReach(player);
      criteriaList.removeIf((criteria) -> !criteria.isValid(start, look, reach, player, skipRaytrace));
      if (criteriaList.isEmpty()) {
         return null;
      } else {
         Criteria selected = (Criteria)criteriaList.get(0);
         return BlockPos.containing(selected.planeBound);
      }
   }

   public static List<BlockPos> getFloorBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2) {
      List<BlockPos> list = new ArrayList();
      if (ModeOptions.getFill() == ModeOptions.ActionEnum.FULL) {
         addFloorBlocks(list, x1, x2, y1, z1, z2);
      } else {
         addHollowFloorBlocks(list, x1, x2, y1, z1, z2);
      }

      return list;
   }

   public static void addFloorBlocks(List<BlockPos> list, int x1, int x2, int y, int z1, int z2) {
      int l = x1;

      while(true) {
         if (x1 < x2) {
            if (l > x2) {
               break;
            }
         } else if (l < x2) {
            break;
         }

         int n = z1;

         while(true) {
            if (z1 < z2) {
               if (n > z2) {
                  break;
               }
            } else if (n < z2) {
               break;
            }

            list.add(new BlockPos(l, y, n));
            n += z1 < z2 ? 1 : -1;
         }

         l += x1 < x2 ? 1 : -1;
      }

   }

   public static void addHollowFloorBlocks(List<BlockPos> list, int x1, int x2, int y, int z1, int z2) {
      Line.addXLineBlocks(list, x1, x2, y, z1);
      Line.addXLineBlocks(list, x1, x2, y, z2);
      Line.addZLineBlocks(list, z1, z2, x1, y);
      Line.addZLineBlocks(list, z1, z2, x2, y);
   }

   protected BlockPos findSecondPos(Player player, BlockPos firstPos, boolean skipRaytrace) {
      return findFloor(player, firstPos, skipRaytrace);
   }

   protected List<BlockPos> getAllBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2) {
      return getFloorBlocks(player, x1, y1, z1, x2, y2, z2);
   }

   static class Criteria {
      Vec3 planeBound;
      double distToPlayerSq;

      Criteria(Vec3 planeBound, Vec3 start) {
         this.planeBound = planeBound;
         this.distToPlayerSq = this.planeBound.subtract(start).lengthSqr();
      }

      public boolean isValid(Vec3 start, Vec3 look, double reach, Player player, boolean skipRaytrace) {
         return BuildModes.isCriteriaValid(start, look, reach, player, skipRaytrace, this.planeBound, this.planeBound, this.distToPlayerSq);
      }
   }
}
