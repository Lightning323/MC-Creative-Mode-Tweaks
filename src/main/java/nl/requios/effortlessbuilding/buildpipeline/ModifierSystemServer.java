package nl.requios.effortlessbuilding.buildpipeline;

import nl.requios.effortlessbuilding.modifier.ModifierServerStorage;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import net.minecraft.world.entity.player.Player;

public class ModifierSystemServer implements IBuildSystem {
   public static final ModifierSystemServer INSTANCE = new ModifierSystemServer();

   public void processBlocks(BlockSet blocks, Player player, BuildPipeline.BuildState action) {
      ModifierServerStorage.getModifiers(player.getUUID()).processBlocks(blocks, player, action);
   }
}
