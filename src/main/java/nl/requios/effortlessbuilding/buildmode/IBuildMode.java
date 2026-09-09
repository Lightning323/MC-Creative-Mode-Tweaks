package nl.requios.effortlessbuilding.buildmode;

import java.util.List;

import it.unimi.dsi.fastutil.longs.LongConsumer;
import net.minecraft.world.phys.AABB;
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

   /**
    * Resolves the in-progress click-state points and streams the shape as
    * bare packed positions ({@link BlockPos#asLong()}) straight into the
    * set — no intermediate {@code List<BlockPos>}, no per-block object in
    * the generators. Serves both the per-frame render preview and the
    * one-shot click path.
    */
   void getCommonBlocks(BlockSet blocks, Player player);

   /**
    * Canonical exact shape from explicit points: the single implementation
    * shared by the client click path and the server placement path (which
    * used to duplicate this logic between the old {@code getClientBlocks}
    * and {@code getServerBlocks}). Points are already resolved — clamping to
    * the per-axis build limit happens in here.
    */
   List<BlockPos> getCommonBlocks(Player player, BlockPos firstPos, BlockPos secondPos,
                                  @Nullable BlockPos thirdPos, @Nullable BlockPos fourthPos);

   /**
    * Primitive twin of {@link #getCommonBlocks}: emits the same shape as
    * packed {@code long}s into {@code out} with bare-int loops. The default
    * packs the exact list; hot shapes override with allocation-free emitters.
    */
   default void forEachCommonBlock(Player player, BlockPos firstPos, BlockPos secondPos,
                                   @Nullable BlockPos thirdPos, @Nullable BlockPos fourthPos,
                                   LongConsumer out) {
      List<BlockPos> common = getCommonBlocks(player, firstPos, secondPos, thirdPos, fourthPos);
      for (int i = 0, n = common.size(); i < n; i++) {
         out.accept(common.get(i).asLong());
      }
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
      return getCommonBlocks(player, firstPos, secondPos, thirdPos, fourthPos);
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

   public AABB getClientBoundary(Player player);
}
