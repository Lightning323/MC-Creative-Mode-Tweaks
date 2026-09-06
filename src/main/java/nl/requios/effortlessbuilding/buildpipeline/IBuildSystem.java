package nl.requios.effortlessbuilding.buildpipeline;

import nl.requios.effortlessbuilding.utilities.BlockSet;
import net.minecraft.world.entity.player.Player;

public interface IBuildSystem {
   void processBlocks(BlockSet var1, Player var2, BuildPipeline.BuildState var3);
}
