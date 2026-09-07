package nl.requios.effortlessbuilding.buildmode.buildmodes;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import nl.requios.effortlessbuilding.buildmode.BaseBuildMode;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import org.lightning323.creative_mode_tweaks.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Builds a filled triangular face or a quad split into two triangular faces. */
public class Mesh extends BaseBuildMode {
   private static final double SAMPLES_PER_BLOCK = 4.0D;
   private final List<BlockPos> points = new ArrayList();
   private final Set<BlockPos> vertexMarkers = new LinkedHashSet();
   private ModeOptions.ActionEnum faceShape = ModeOptions.ActionEnum.MESH_TRIANGLE;
   private @Nullable BlockPos previewPoint;

   public void initialize() {
      super.initialize();
      this.points.clear();
      this.faceShape = ModeOptions.getMeshFace();
      this.previewPoint = null;
   }

   /** Markers belong to the current Mesh session, not an individual face. */
   @Override
   public void onBuildModeDeselected() {
      this.points.clear();
      this.vertexMarkers.clear();
      this.previewPoint = null;
   }

   public boolean onClick(BlockSet blocks, BlockPos clickedPos, Player player) {
      if (this.clicks == 0) {
         this.faceShape = ModeOptions.getMeshFace();
      }

      if (this.points.contains(clickedPos)) {
         return false;
      }

      ++this.clicks;
      this.points.add(clickedPos);
      this.vertexMarkers.add(clickedPos);
      return this.points.size() == this.requiredPointCount();
   }

   public void findCoordinates(BlockSet blocks, Player player) {
      List<BlockPos> selectedPoints = new ArrayList(this.points);
      if (selectedPoints.size() < this.requiredPointCount() && this.previewPoint != null) {
         selectedPoints.add(this.previewPoint);
      }

      if (selectedPoints.isEmpty()) {
         return;
      }

      List<BlockPos> boundedPoints = this.limitToBuildRange(player, selectedPoints);
      List<BlockPos> meshBlocks = this.getMeshBlocks(boundedPoints);
      blocks.clear();

      for(BlockPos pos : meshBlocks) {
         if (!blocks.containsKey(pos)) {
            blocks.add(new BlockEntry(pos));
         }
      }

      blocks.firstPos = (BlockPos)boundedPoints.getFirst();
      blocks.lastPos = (BlockPos)boundedPoints.getLast();
   }

   public List<BlockPos> getServerBlocks(Player player, BlockPos firstPos, BlockPos secondPos, @Nullable BlockPos thirdPos, @Nullable BlockPos fourthPos) {
      if (thirdPos == null) {
         return List.of();
      }

      List<BlockPos> selectedPoints = new ArrayList();
      selectedPoints.add(firstPos);
      selectedPoints.add(secondPos);
      selectedPoints.add(thirdPos);
      if (fourthPos != null) {
         selectedPoints.add(fourthPos);
      }

      return this.getMeshBlocks(this.limitToBuildRange(player, selectedPoints));
   }

   public void setPreviewSecondPoint(@Nullable BlockPos pos) {
      this.previewPoint = pos;
   }

   /** Persistent vertices can be clicked again to connect a new face. */
   @Override
   public @Nullable BlockPos getSelectionMarker(BlockPos clickedPos) {
      return this.vertexMarkers.contains(clickedPos) ? clickedPos : null;
   }

   public List<BlockPos> getVertexMarkers() {
      return List.copyOf(this.vertexMarkers);
   }

   public boolean isVertexSelected(BlockPos pos) {
      return this.points.contains(pos);
   }

   public boolean usesDirectSecondPoint() {
      return true;
   }

   public @Nullable BlockPos getIntermediatePos() {
      return this.getSelectedPoint(1);
   }

   public @Nullable BlockPos getThirdSelectionPos() {
      return this.getSelectedPoint(2);
   }

   public @Nullable BlockPos getFourthSelectionPos() {
      return this.getSelectedPoint(3);
   }

   private int requiredPointCount() {
      return this.isQuad() ? 4 : 3;
   }

   private boolean isQuad() {
      return this.faceShape == ModeOptions.ActionEnum.MESH_QUAD;
   }

   private @Nullable BlockPos getSelectedPoint(int index) {
      if (this.points.size() <= index) {
         return null;
      }

      return (BlockPos)this.points.get(index);
   }

   private List<BlockPos> limitToBuildRange(Player player, List<BlockPos> selectedPoints) {
      return this.limitToBuildRange(selectedPoints, Config.getBuildingMaxBlocksPerAxis(player));
   }

   private List<BlockPos> limitToBuildRange(List<BlockPos> selectedPoints, int axisLimit) {
      BlockPos firstPos = (BlockPos)selectedPoints.getFirst();
      List<BlockPos> boundedPoints = new ArrayList();
      boundedPoints.add(firstPos);

      for(int i = 1; i < selectedPoints.size(); ++i) {
         BlockPos point = (BlockPos)selectedPoints.get(i);
         boundedPoints.add(new BlockPos(this.limitAxis(point.getX(), firstPos.getX(), axisLimit), this.limitAxis(point.getY(), firstPos.getY(), axisLimit), this.limitAxis(point.getZ(), firstPos.getZ(), axisLimit)));
      }

      return boundedPoints;
   }

   private int limitAxis(int point, int origin, int axisLimit) {
      if (point - origin >= axisLimit) {
         return origin + axisLimit - 1;
      } else {
         return origin - point >= axisLimit ? origin - axisLimit + 1 : point;
      }
   }

   private List<BlockPos> getMeshBlocks(List<BlockPos> selectedPoints) {
      if (selectedPoints.size() == 1) {
         return List.of((BlockPos)selectedPoints.getFirst());
      } else {
         Set<BlockPos> meshBlocks = new LinkedHashSet();
         if (selectedPoints.size() == 2) {
            this.addLine(meshBlocks, (BlockPos)selectedPoints.get(0), (BlockPos)selectedPoints.get(1));
         } else {
            this.addTriangle(meshBlocks, (BlockPos)selectedPoints.get(0), (BlockPos)selectedPoints.get(1), (BlockPos)selectedPoints.get(2));
            if (selectedPoints.size() == 4) {
               this.addTriangle(meshBlocks, (BlockPos)selectedPoints.get(0), (BlockPos)selectedPoints.get(2), (BlockPos)selectedPoints.get(3));
            }
         }

         return new ArrayList(meshBlocks);
      }
   }

   private void addLine(Set<BlockPos> meshBlocks, BlockPos firstPos, BlockPos secondPos) {
      Vec3 first = Vec3.atCenterOf(firstPos);
      Vec3 second = Vec3.atCenterOf(secondPos);
      int samples = Math.max(1, (int)Math.ceil(first.distanceTo(second) * SAMPLES_PER_BLOCK));

      for(int i = 0; i <= samples; ++i) {
         meshBlocks.add(BlockPos.containing(first.lerp(second, (double)i / (double)samples)));
      }
   }

   private void addTriangle(Set<BlockPos> meshBlocks, BlockPos firstPos, BlockPos secondPos, BlockPos thirdPos) {
      Vec3 first = Vec3.atCenterOf(firstPos);
      Vec3 second = Vec3.atCenterOf(secondPos);
      Vec3 third = Vec3.atCenterOf(thirdPos);
      Vec3 firstToSecond = second.subtract(first);
      Vec3 firstToThird = third.subtract(first);
      if (firstToSecond.cross(firstToThird).lengthSqr() < 1.0E-6D) {
         this.addLine(meshBlocks, firstPos, secondPos);
         this.addLine(meshBlocks, secondPos, thirdPos);
         return;
      }

      int samples = Math.max(1, (int)Math.ceil(Math.max(first.distanceTo(second), Math.max(first.distanceTo(third), second.distanceTo(third))) * SAMPLES_PER_BLOCK));

      for(int firstWeight = 0; firstWeight <= samples; ++firstWeight) {
         for(int secondWeight = 0; firstWeight + secondWeight <= samples; ++secondWeight) {
            double secondFraction = (double)firstWeight / (double)samples;
            double thirdFraction = (double)secondWeight / (double)samples;
            Vec3 point = first.add(firstToSecond.scale(secondFraction)).add(firstToThird.scale(thirdFraction));
            meshBlocks.add(BlockPos.containing(point));
         }
      }
   }
}
