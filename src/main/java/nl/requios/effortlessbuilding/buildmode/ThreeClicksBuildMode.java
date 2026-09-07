package nl.requios.effortlessbuilding.buildmode;

import java.util.ArrayList;
import java.util.List;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipeline;
import org.lightning323.creative_mode_tweaks.Config;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public abstract class ThreeClicksBuildMode extends BaseBuildMode {
   protected BlockEntry firstBlockEntry;
   protected BlockEntry secondBlockEntry;
   private boolean twoPointBuild;
   private @Nullable BlockPos previewSecondPoint;

   public void initialize() {
      super.initialize();
      this.firstBlockEntry = null;
      this.secondBlockEntry = null;
      this.twoPointBuild = false;
      this.previewSecondPoint = null;
   }

   public boolean onClick(BlockSet blocks, BlockPos clickedPos, Player player) {
      if (this.clicks == 0) {
         this.twoPointBuild = this.supportsTwoPointBuild() && ModeOptions.isTwoPointBuild();
      }

      if (this.twoPointBuild) {
         ++this.clicks;
         if (this.clicks == 1) {
            this.firstBlockEntry = new BlockEntry(clickedPos);
            return false;
         } else {
            this.secondBlockEntry = new BlockEntry(clickedPos);
            return true;
         }
      }

      super.onClick(blocks, clickedPos, player);
      if (this.clicks == 1) {
         this.firstBlockEntry = new BlockEntry(clickedPos);
      } else {
         if (this.clicks != 2) {
            return true;
         }

         BlockPos secondPos = this.findSecondPos(player, this.firstBlockEntry.blockPos, true);
         if (secondPos == null) {
            this.clicks = 1;
            return false;
         }

         this.secondBlockEntry = new BlockEntry(secondPos);
      }

      return false;
   }

   public void findCoordinates(BlockSet blocks, Player player) {
      if (this.twoPointBuild) {
         this.findTwoPointCoordinates(blocks, player);
         return;
      }

      if (this.clicks != 0) {
         int axisLimit = Config.getBuildingMaxBlocksPerAxis(player);
         if (this.clicks == 1) {
            BlockPos firstPos = this.firstBlockEntry.blockPos;
            BlockPos secondPos = this.findSecondPos(player, this.firstBlockEntry.blockPos, true);
            if (secondPos == null) {
               return;
            }

            int x1 = firstPos.getX();
            int x2 = secondPos.getX();
            int y1 = firstPos.getY();
            int y2 = secondPos.getY();
            int z1 = firstPos.getZ();
            int z2 = secondPos.getZ();
            if (x2 - x1 >= axisLimit) {
               x2 = x1 + axisLimit - 1;
            }

            if (x1 - x2 >= axisLimit) {
               x2 = x1 - axisLimit + 1;
            }

            if (y2 - y1 >= axisLimit) {
               y2 = y1 + axisLimit - 1;
            }

            if (y1 - y2 >= axisLimit) {
               y2 = y1 - axisLimit + 1;
            }

            if (z2 - z1 >= axisLimit) {
               z2 = z1 + axisLimit - 1;
            }

            if (z1 - z2 >= axisLimit) {
               z2 = z1 - axisLimit + 1;
            }

            blocks.clear();

            for(BlockPos pos : this.getIntermediateBlocks(player, x1, y1, z1, x2, y2, z2)) {
               if (!blocks.containsKey(pos)) {
                  blocks.add(new BlockEntry(pos));
               }
            }

            blocks.firstPos = firstPos;
            blocks.lastPos = secondPos;
         } else {
            BlockPos firstPos = this.firstBlockEntry.blockPos;
            BlockPos secondPos = this.secondBlockEntry.blockPos;
            BlockPos thirdPos = this.findThirdPos(player, firstPos, secondPos, true);
            if (thirdPos == null) {
               return;
            }

            int x1 = firstPos.getX();
            int x2 = secondPos.getX();
            int x3 = thirdPos.getX();
            int y1 = firstPos.getY();
            int y2 = secondPos.getY();
            int y3 = thirdPos.getY();
            int z1 = firstPos.getZ();
            int z2 = secondPos.getZ();
            int z3 = thirdPos.getZ();
            if (x2 - x1 >= axisLimit) {
               x2 = x1 + axisLimit - 1;
            }

            if (x1 - x2 >= axisLimit) {
               x2 = x1 - axisLimit + 1;
            }

            if (y2 - y1 >= axisLimit) {
               y2 = y1 + axisLimit - 1;
            }

            if (y1 - y2 >= axisLimit) {
               y2 = y1 - axisLimit + 1;
            }

            if (z2 - z1 >= axisLimit) {
               z2 = z1 + axisLimit - 1;
            }

            if (z1 - z2 >= axisLimit) {
               z2 = z1 - axisLimit + 1;
            }

            if (x3 - x1 >= axisLimit) {
               x3 = x1 + axisLimit - 1;
            }

            if (x1 - x3 >= axisLimit) {
               x3 = x1 - axisLimit + 1;
            }

            if (y3 - y1 >= axisLimit) {
               y3 = y1 + axisLimit - 1;
            }

            if (y1 - y3 >= axisLimit) {
               y3 = y1 - axisLimit + 1;
            }

            if (z3 - z1 >= axisLimit) {
               z3 = z1 + axisLimit - 1;
            }

            if (z1 - z3 >= axisLimit) {
               z3 = z1 - axisLimit + 1;
            }

            blocks.clear();

            for(BlockPos pos : this.getFinalBlocks(player, x1, y1, z1, x2, y2, z2, x3, y3, z3)) {
               if (!blocks.containsKey(pos)) {
                  blocks.add(new BlockEntry(pos));
               }
            }

            blocks.firstPos = firstPos;
            blocks.lastPos = thirdPos;
         }

      }
   }

   public @Nullable BlockPos getIntermediatePos() {
      return !this.twoPointBuild && this.secondBlockEntry != null ? this.secondBlockEntry.blockPos : null;
   }

   public List<BlockPos> getServerBlocks(Player player, BlockPos firstPos, BlockPos secondPos, @Nullable BlockPos thirdPos) {
      if (this.supportsTwoPointBuild() && ModeOptions.isTwoPointBuild()) {
         return this.getTwoPointBlocks(player, firstPos, secondPos);
      }

      if (thirdPos == null) {
         return List.of();
      } else {
         int axisLimit = Config.getBuildingMaxBlocksPerAxis(player);
         int x1 = firstPos.getX();
         int x2 = secondPos.getX();
         int x3 = thirdPos.getX();
         int y1 = firstPos.getY();
         int y2 = secondPos.getY();
         int y3 = thirdPos.getY();
         int z1 = firstPos.getZ();
         int z2 = secondPos.getZ();
         int z3 = thirdPos.getZ();
         if (x2 - x1 >= axisLimit) {
            x2 = x1 + axisLimit - 1;
         }

         if (x1 - x2 >= axisLimit) {
            x2 = x1 - axisLimit + 1;
         }

         if (y2 - y1 >= axisLimit) {
            y2 = y1 + axisLimit - 1;
         }

         if (y1 - y2 >= axisLimit) {
            y2 = y1 - axisLimit + 1;
         }

         if (z2 - z1 >= axisLimit) {
            z2 = z1 + axisLimit - 1;
         }

         if (z1 - z2 >= axisLimit) {
            z2 = z1 - axisLimit + 1;
         }

         if (x3 - x1 >= axisLimit) {
            x3 = x1 + axisLimit - 1;
         }

         if (x1 - x3 >= axisLimit) {
            x3 = x1 - axisLimit + 1;
         }

         if (y3 - y1 >= axisLimit) {
            y3 = y1 + axisLimit - 1;
         }

         if (y1 - y3 >= axisLimit) {
            y3 = y1 - axisLimit + 1;
         }

         if (z3 - z1 >= axisLimit) {
            z3 = z1 + axisLimit - 1;
         }

         if (z1 - z3 >= axisLimit) {
            z3 = z1 - axisLimit + 1;
         }

         return this.getFinalBlocks(player, x1, y1, z1, x2, y2, z2, x3, y3, z3);
      }
   }

   public static BlockPos findHeight(Player player, BlockPos secondPos, boolean skipRaytrace) {
      Vec3 look = BuildPipeline.getPlayerLookVec(player);
      Vec3 start = BuildPipeline.getPlayerEyePosition(player);
      List<HeightCriteria> criteriaList = new ArrayList(2);
      Vec3 xBound = BuildModes.findXBound((double)secondPos.getX(), start, look);
      criteriaList.add(new HeightCriteria(xBound, secondPos, start));
      Vec3 zBound = BuildModes.findZBound((double)secondPos.getZ(), start, look);
      criteriaList.add(new HeightCriteria(zBound, secondPos, start));
      double reach = Config.getReach(player);
      criteriaList.removeIf((criteriax) -> !criteriax.isValid(start, look, reach, player, skipRaytrace));
      if (criteriaList.isEmpty()) {
         return null;
      } else {
         HeightCriteria selected = (HeightCriteria)criteriaList.get(0);
         if (criteriaList.size() > 1) {
            for(int i = 1; i < criteriaList.size(); ++i) {
               HeightCriteria criteria = (HeightCriteria)criteriaList.get(i);
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

   protected abstract BlockPos findSecondPos(Player var1, BlockPos var2, boolean var3);

   protected boolean supportsTwoPointBuild() {
      return false;
   }

   protected abstract BlockPos findThirdPos(Player var1, BlockPos var2, BlockPos var3, boolean var4);

   protected abstract List<BlockPos> getIntermediateBlocks(Player var1, int var2, int var3, int var4, int var5, int var6, int var7);

   protected abstract List<BlockPos> getFinalBlocks(Player var1, int var2, int var3, int var4, int var5, int var6, int var7, int var8, int var9, int var10);

   public void setPreviewSecondPoint(@Nullable BlockPos pos) {
      this.previewSecondPoint = pos;
   }

   public boolean usesDirectSecondPoint() {
      return this.supportsTwoPointBuild() && (this.clicks == 0 ? ModeOptions.isTwoPointBuild() : this.twoPointBuild);
   }

   private void findTwoPointCoordinates(BlockSet blocks, Player player) {
      if (this.clicks == 0 || this.firstBlockEntry == null) {
         return;
      }

      BlockPos secondPos = this.clicks == 1 ? this.previewSecondPoint : this.secondBlockEntry.blockPos;
      if (secondPos == null) {
         return;
      }

      BlockPos firstPos = this.firstBlockEntry.blockPos;
      BlockPos boundedSecondPos = this.limitToBuildRange(player, firstPos, secondPos);
      blocks.clear();

      for(BlockPos pos : this.getTwoPointBlocks(player, firstPos, boundedSecondPos)) {
         if (!blocks.containsKey(pos)) {
            blocks.add(new BlockEntry(pos));
         }
      }

      blocks.firstPos = firstPos;
      blocks.lastPos = boundedSecondPos;
   }

   private List<BlockPos> getTwoPointBlocks(Player player, BlockPos firstPos, BlockPos secondPos) {
      BlockPos boundedSecondPos = this.limitToBuildRange(player, firstPos, secondPos);
      return this.getFinalBlocks(player, firstPos.getX(), firstPos.getY(), firstPos.getZ(), boundedSecondPos.getX(), boundedSecondPos.getY(), boundedSecondPos.getZ(), boundedSecondPos.getX(), boundedSecondPos.getY(), boundedSecondPos.getZ());
   }

   private BlockPos limitToBuildRange(Player player, BlockPos firstPos, BlockPos secondPos) {
      int axisLimit = Config.getBuildingMaxBlocksPerAxis(player);
      int x1 = firstPos.getX();
      int y1 = firstPos.getY();
      int z1 = firstPos.getZ();
      int x2 = secondPos.getX();
      int y2 = secondPos.getY();
      int z2 = secondPos.getZ();
      if (x2 - x1 >= axisLimit) {
         x2 = x1 + axisLimit - 1;
      }

      if (x1 - x2 >= axisLimit) {
         x2 = x1 - axisLimit + 1;
      }

      if (y2 - y1 >= axisLimit) {
         y2 = y1 + axisLimit - 1;
      }

      if (y1 - y2 >= axisLimit) {
         y2 = y1 - axisLimit + 1;
      }

      if (z2 - z1 >= axisLimit) {
         z2 = z1 + axisLimit - 1;
      }

      if (z1 - z2 >= axisLimit) {
         z2 = z1 - axisLimit + 1;
      }

      return new BlockPos(x2, y2, z2);
   }

   static class HeightCriteria {
      Vec3 planeBound;
      Vec3 lineBound;
      double distToLineSq;
      double distToPlayerSq;

      HeightCriteria(Vec3 planeBound, BlockPos secondPos, Vec3 start) {
         this.planeBound = planeBound;
         this.lineBound = this.toLongestLine(this.planeBound, secondPos);
         this.distToLineSq = this.lineBound.subtract(this.planeBound).lengthSqr();
         this.distToPlayerSq = this.planeBound.subtract(start).lengthSqr();
      }

      private Vec3 toLongestLine(Vec3 boundVec, BlockPos secondPos) {
         BlockPos bound = BlockPos.containing(boundVec);
         return new Vec3((double)secondPos.getX(), (double)bound.getY(), (double)secondPos.getZ());
      }

      public boolean isValid(Vec3 start, Vec3 look, double reach, Player player, boolean skipRaytrace) {
         return BuildModes.isCriteriaValid(start, look, reach, player, skipRaytrace, this.lineBound, this.planeBound, this.distToPlayerSq);
      }
   }
}
