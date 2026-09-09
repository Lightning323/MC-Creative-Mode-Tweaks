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

   void getClientBlocks(BlockSet var1, Player var2);

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
