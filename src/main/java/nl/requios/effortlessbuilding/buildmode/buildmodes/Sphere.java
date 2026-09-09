package nl.requios.effortlessbuilding.buildmode.buildmodes;

import java.util.ArrayList;
import java.util.List;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import nl.requios.effortlessbuilding.buildmode.ThreeClicksBuildMode;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import nl.requios.effortlessbuilding.buildmode.ThreeClicksBuildMode;

public class Sphere extends ThreeClicksBuildMode {
   public static List<BlockPos> getSphereBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, int x3, int y3, int z3) {
      List<BlockPos> list = new ArrayList();
      float centerX = (float)x1;
      float centerY = (float)y1;
      float centerZ = (float)z1;
      if (ModeOptions.getCircleStart() == ModeOptions.ActionEnum.CIRCLE_START_CORNER) {
         centerX = (float)x1 + (float)(x2 - x1) / 2.0F;
         centerY = (float)y1 + (float)(y3 - y1) / 2.0F;
         centerZ = (float)z1 + (float)(z2 - z1) / 2.0F;
      } else {
         x1 = (int)(centerX - ((float)x2 - centerX));
         y1 = (int)(centerY - ((float)y3 - centerY));
         z1 = (int)(centerZ - ((float)z2 - centerZ));
      }

      float radiusX = Mth.abs((float)x2 - centerX);
      float radiusY = Mth.abs((float)y3 - centerY);
      float radiusZ = Mth.abs((float)z2 - centerZ);
      if (ModeOptions.getFill() == ModeOptions.ActionEnum.FULL) {
         addSphereBlocks(list, x1, y1, z1, x3, y3, z3, centerX, centerY, centerZ, radiusX, radiusY, radiusZ);
      } else {
         addHollowSphereBlocks(list, x1, y1, z1, x3, y3, z3, centerX, centerY, centerZ, radiusX, radiusY, radiusZ);
      }

      return list;
   }

   public static void addSphereBlocks(List<BlockPos> list, int x1, int y1, int z1, int x2, int y2, int z2, float centerX, float centerY, float centerZ, float radiusX, float radiusY, float radiusZ) {
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

            int m = y1;

            while(true) {
               if (y1 < y2) {
                  if (m > y2) {
                     break;
                  }
               } else if (m < y2) {
                  break;
               }

               float distance = distance((float)l, (float)m, (float)n, centerX, centerY, centerZ);
               float radius = calculateSpheroidRadius(centerX, centerY, centerZ, radiusX, radiusY, radiusZ, l, m, n);
               if (distance < radius + 0.4F) {
                  list.add(new BlockPos(l, m, n));
               }

               m += y1 < y2 ? 1 : -1;
            }

            n += z1 < z2 ? 1 : -1;
         }

         l += x1 < x2 ? 1 : -1;
      }

   }

   public static void addHollowSphereBlocks(List<BlockPos> list, int x1, int y1, int z1, int x2, int y2, int z2, float centerX, float centerY, float centerZ, float radiusX, float radiusY, float radiusZ) {
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

            int m = y1;

            while(true) {
               if (y1 < y2) {
                  if (m > y2) {
                     break;
                  }
               } else if (m < y2) {
                  break;
               }

               float distance = distance((float)l, (float)m, (float)n, centerX, centerY, centerZ);
               float radius = calculateSpheroidRadius(centerX, centerY, centerZ, radiusX, radiusY, radiusZ, l, m, n);
               if (distance < radius + 0.4F && distance > radius - 0.6F) {
                  list.add(new BlockPos(l, m, n));
               }

               m += y1 < y2 ? 1 : -1;
            }

            n += z1 < z2 ? 1 : -1;
         }

         l += x1 < x2 ? 1 : -1;
      }

   }

   private static float distance(float x1, float y1, float z1, float x2, float y2, float z2) {
      return Mth.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1) + (z2 - z1) * (z2 - z1));
   }

   public static float calculateSpheroidRadius(float centerX, float centerY, float centerZ, float radiusX, float radiusY, float radiusZ, int x, int y, int z) {
      float radiusXZ = Circle.calculateEllipseRadius(centerX, centerZ, radiusX, radiusZ, x, z);
      return Circle.calculateEllipseRadius(centerX, centerY, radiusXZ, radiusY, x, y);
   }

   public BlockPos findSecondPos(Player player, BlockPos firstPos, boolean skipRaytrace) {
      return Floor.findFloor(player, firstPos, skipRaytrace);
   }

   public BlockPos findThirdPos(Player player, BlockPos firstPos, BlockPos secondPos, boolean skipRaytrace) {
      return findHeight(player, secondPos, skipRaytrace);
   }

    public List<BlockPos> getIntermediateBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2) {
       return Circle.getCircleBlocks(player, x1, y1, z1, x2, y2, z2);
    }

    public List<BlockPos> getFinalBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, int x3, int y3, int z3) {
       return getSphereBlocks(player, x1, y1, z1, x2, y2, z2, x3, y3, z3);
    }

    @Override
    protected AABB getIntermediateBoundary(BlockPos firstPos, BlockPos clampedSecond) {
       return circleBounds(firstPos, clampedSecond);
    }

    @Override
    protected AABB getFinalBoundary(BlockPos firstPos, BlockPos clampedSecond, BlockPos clampedThird) {
       if (ModeOptions.getCircleStart() == ModeOptions.ActionEnum.CIRCLE_START_CENTER) {
          int mirroredX = 2 * firstPos.getX() - clampedSecond.getX();
          int mirroredY = 2 * firstPos.getY() - clampedThird.getY();
          int mirroredZ = 2 * firstPos.getZ() - clampedSecond.getZ();
          return toFullBlockAABB(
                 Math.min(mirroredX, clampedSecond.getX()),
                 Math.min(mirroredY, clampedThird.getY()),
                 Math.min(mirroredZ, clampedSecond.getZ()),
                 Math.max(mirroredX, clampedSecond.getX()),
                 Math.max(mirroredY, clampedThird.getY()),
                 Math.max(mirroredZ, clampedSecond.getZ()));
       }
       return super.getFinalBoundary(firstPos, clampedSecond, clampedThird);
    }

    static AABB circleBounds(BlockPos firstPos, BlockPos clampedSecond) {
       int minX;
       int maxX;
       int minZ;
       int maxZ;
       if (ModeOptions.getCircleStart() == ModeOptions.ActionEnum.CIRCLE_START_CENTER) {
          int mirroredX = 2 * firstPos.getX() - clampedSecond.getX();
          int mirroredZ = 2 * firstPos.getZ() - clampedSecond.getZ();
          minX = Math.min(clampedSecond.getX(), mirroredX);
          maxX = Math.max(clampedSecond.getX(), mirroredX);
          minZ = Math.min(clampedSecond.getZ(), mirroredZ);
          maxZ = Math.max(clampedSecond.getZ(), mirroredZ);
       } else {
          minX = Math.min(firstPos.getX(), clampedSecond.getX());
          maxX = Math.max(firstPos.getX(), clampedSecond.getX());
          minZ = Math.min(firstPos.getZ(), clampedSecond.getZ());
          maxZ = Math.max(firstPos.getZ(), clampedSecond.getZ());
       }
       int y = firstPos.getY();
       return toFullBlockAABB(minX, y, minZ, maxX, y, maxZ);
    }
}
