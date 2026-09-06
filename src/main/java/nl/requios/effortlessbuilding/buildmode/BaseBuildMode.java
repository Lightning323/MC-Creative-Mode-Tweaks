package nl.requios.effortlessbuilding.buildmode;

import nl.requios.effortlessbuilding.utilities.BlockSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

public abstract class BaseBuildMode implements IBuildMode {
   protected int clicks;

   public void initialize() {
      this.clicks = 0;
   }

   public boolean onClick(BlockSet blocks, BlockPos clickedPos, Player player) {
      ++this.clicks;
      return false;
   }

   public void findCoordinates(BlockSet blocks, Player player) {
   }

   public boolean isFirstClick() {
      return this.clicks == 0;
   }
}
