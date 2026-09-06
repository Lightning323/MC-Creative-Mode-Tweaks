package nl.requios.effortlessbuilding.utilities;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

public final class BlockUtilities {
   private BlockUtilities() {
   }

   public static BlockState applyVerticalMirror(BlockState state) {
      if (state.hasProperty(BlockStateProperties.HALF)) {
         Half half = (Half)state.getValue(BlockStateProperties.HALF);
         state = (BlockState)state.setValue(BlockStateProperties.HALF, half == Half.TOP ? Half.BOTTOM : Half.TOP);
      }

      if (state.hasProperty(BlockStateProperties.FACING)) {
         Direction dir = (Direction)state.getValue(BlockStateProperties.FACING);
         if (dir == Direction.UP || dir == Direction.DOWN) {
            state = (BlockState)state.setValue(BlockStateProperties.FACING, dir.getOpposite());
         }
      }

      return state;
   }
}
