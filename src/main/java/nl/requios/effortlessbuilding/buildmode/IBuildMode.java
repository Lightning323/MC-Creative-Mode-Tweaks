package nl.requios.effortlessbuilding.buildmode;

import java.util.List;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public interface IBuildMode {
   void initialize();

   /**
    * Discards the in-progress selection. Build modes with persistent markers
    * can override this to remove any markers created by the cancelled input.
    */
   default void onCancel() {
      this.initialize();
   }

   boolean onClick(BlockSet var1, BlockPos var2, Player var3);

   void findCoordinates(BlockSet var1, Player var2);

   /**
    * Resolves the shape anchors only (first/second/third click points +
    * axis clamping + option snapshot). MUST be O(1): no block enumeration,
    * no world scans, no allocation proportional to the shape volume.
    * Returns null when no selection is in progress.
    */
   default @Nullable ShapeFrame describeShape(Player player) {
      return null;
   }

   /**
    * Expands a frame from {@link #describeShape} into the full block list.
    * This is the O(N) block calculation, kept separate so callers can run
    * the O(1) coordinate part on the render thread and defer/skip this.
    */
   default List<BlockPos> expandShape(Player player, ShapeFrame frame) {
      return List.of();
   }

   /**
    * O(1) upper bound on the expanded block count, used for detailed-vs-box
    * routing without enumerating. Defaults to the anchor bounding-box volume
    * (conservative: hollow shapes only overestimate, never underestimate).
    */
   default long countBlocks(ShapeFrame frame) {
      return frame.aabbVolume();
   }

   default void setFirstClickFace(Direction face) {
   }

   /**
    * Supplies the block currently under the cursor while a direct-point
    * selection is being previewed. Most modes determine later points from the
    * player's look vector, so they deliberately ignore this value.
    */
   default void setPreviewPoint(@Nullable BlockPos pos) {
   }

   /**
    * Returns a persistent selection marker at the clicked block, when this
    * mode has one. This lets modes reuse an existing selection point even if
    * normal placement would offset the click to an adjacent block.
    */
   default @Nullable BlockPos getSelectionMarker(BlockPos clickedPos) {
      return null;
   }

   /** Called once when the player changes away from this build mode. */
   default void onBuildModeDeselected() {
   }

   /**
    * Whether the next click should use the block under the cursor as a selected
    * point instead of the player's look-vector based point selection.
    */
   default boolean usesDirectSecondPoint() {
      return false;
   }

   default List<BlockPos> getServerBlocks(Player player, BlockPos firstPos, BlockPos secondPos, @Nullable BlockPos thirdPos, @Nullable BlockPos fourthPos) {
      return List.of();
   }

   default @Nullable BlockPos getIntermediatePos() {
      return null;
   }

   /** Additional selection vertices used by modes that need more than three clicks. */
   default @Nullable BlockPos getThirdSelectionPos() {
      return null;
   }

   default @Nullable BlockPos getFourthSelectionPos() {
      return null;
   }

   /** Returns the point-count option captured when this selection was started. */
   default ModeOptions.ActionEnum getPointBuildAction() {
      return ModeOptions.getPointBuild();
   }

   default boolean isFirstClick() {
      return true;
   }
}
