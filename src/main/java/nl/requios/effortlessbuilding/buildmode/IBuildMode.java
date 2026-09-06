package nl.requios.effortlessbuilding.buildmode;

import java.util.List;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public interface IBuildMode {
   void initialize();

   boolean onClick(BlockSet var1, BlockPos var2, Player var3);

   void findCoordinates(BlockSet var1, Player var2);

   default void setFirstClickFace(Direction face) {
   }

   default List<BlockPos> getServerBlocks(Player player, BlockPos firstPos, BlockPos secondPos, @Nullable BlockPos thirdPos) {
      return List.of();
   }

   default @Nullable BlockPos getIntermediatePos() {
      return null;
   }

   default boolean isFirstClick() {
      return true;
   }
}
