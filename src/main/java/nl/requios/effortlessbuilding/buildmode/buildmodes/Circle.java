package nl.requios.effortlessbuilding.buildmode.buildmodes;

import java.util.ArrayList;
import java.util.List;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import nl.requios.effortlessbuilding.buildmode.TwoClicksBuildMode;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public class Circle extends TwoClicksBuildMode {
   public static List<BlockPos> getCircleBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2) {
      List<BlockPos> list = new ArrayList();
      float centerX = (float)x1;
      float centerZ = (float)z1;
      if (ModeOptions.getCircleStart() == ModeOptions.ActionEnum.CIRCLE_START_CORNER) {
         centerX = (float)x1 + (float)(x2 - x1) / 2.0F;
         centerZ = (float)z1 + (float)(z2 - z1) / 2.0F;
      } else {
         x1 = (int)(centerX - ((float)x2 - centerX));
         z1 = (int)(centerZ - ((float)z2 - centerZ));
      }

      float radiusX = Mth.abs((float)x2 - centerX);
      float radiusZ = Mth.abs((float)z2 - centerZ);
      if (ModeOptions.getFill() == ModeOptions.ActionEnum.FULL) {
         addCircleBlocks(list, x1, y1, z1, x2, y2, z2, centerX, centerZ, radiusX, radiusZ);
      } else {
         addHollowCircleBlocks(list, x1, y1, z1, x2, y2, z2, centerX, centerZ, radiusX, radiusZ);
      }

      return list;
   }

   public static void addCircleBlocks(List<BlockPos> list, int x1, int y1, int z1, int x2, int y2, int z2, float centerX, float centerZ, float radiusX, float radiusZ) {
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

            float distance = distance((float)l, (float)n, centerX, centerZ);
            float radius = calculateEllipseRadius(centerX, centerZ, radiusX, radiusZ, l, n);
            if (distance < radius + 0.4F) {
               list.add(new BlockPos(l, y1, n));
            }

            n += z1 < z2 ? 1 : -1;
         }

         l += x1 < x2 ? 1 : -1;
      }

   }

   public static void addHollowCircleBlocks(List<BlockPos> list, int x1, int y1, int z1, int x2, int y2, int z2, float centerX, float centerZ, float radiusX, float radiusZ) {
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

            float distance = distance((float)l, (float)n, centerX, centerZ);
            float radius = calculateEllipseRadius(centerX, centerZ, radiusX, radiusZ, l, n);
            if (distance < radius + 0.4F && distance > radius - 0.6F) {
               list.add(new BlockPos(l, y1, n));
            }

            n += z1 < z2 ? 1 : -1;
         }

         l += x1 < x2 ? 1 : -1;
      }

   }

   private static float distance(float x1, float z1, float x2, float z2) {
      return Mth.sqrt((x2 - x1) * (x2 - x1) + (z2 - z1) * (z2 - z1));
   }

   public static float calculateEllipseRadius(float centerX, float centerZ, float radiusX, float radiusZ, int x, int z) {
      float theta = (float)Mth.atan2((double)((float)z - centerZ), (double)((float)x - centerX));
      float part1 = radiusX * radiusX * Mth.sin(theta) * Mth.sin(theta);
      float part2 = radiusZ * radiusZ * Mth.cos(theta) * Mth.cos(theta);
      return radiusX * radiusZ / Mth.sqrt(part1 + part2);
   }

   protected BlockPos findSecondPos(Player player, BlockPos firstPos, boolean skipRaytrace) {
      return Floor.findFloor(player, firstPos, skipRaytrace);
   }

   protected List<BlockPos> getAllBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2) {
      return getCircleBlocks(player, x1, y1, z1, x2, y2, z2);
   }
}
