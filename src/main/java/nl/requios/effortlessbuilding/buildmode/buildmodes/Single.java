package nl.requios.effortlessbuilding.buildmode.buildmodes;

import java.util.List;
import nl.requios.effortlessbuilding.buildmode.BaseBuildMode;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import nl.requios.effortlessbuilding.buildmode.ShapeFrame;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public class Single extends BaseBuildMode {
   private BlockPos pos;

   public void initialize() {
      super.initialize();
      this.pos = null;
   }

   public boolean onClick(BlockSet blocks, BlockPos clickedPos, Player player) {
      this.pos = clickedPos;
      return true;
   }

   public void findCoordinates(BlockSet blocks, Player player) {
      if (this.pos != null) {
         blocks.setStartPos(new BlockEntry(this.pos));
      }

   }

   @Override
   public @Nullable ShapeFrame describeShape(Player player) {
      if (this.pos == null) {
         return null;
      }
      return ShapeFrame.single(this.pos, 1,
            ModeOptions.getFill(), ModeOptions.getCubeFill(), ModeOptions.getSides(),
            ModeOptions.getCircleStart());
   }

   @Override
   public List<BlockPos> expandShape(Player player, ShapeFrame frame) {
      if (frame.kind() != ShapeFrame.Kind.SINGLE) {
         return List.of();
      }
      return List.of(frame.first());
   }

   public List<BlockPos> getServerBlocks(Player player, BlockPos firstPos, BlockPos secondPos, @Nullable BlockPos thirdPos, @Nullable BlockPos fourthPos) {
      return List.of(firstPos);
   }
}
