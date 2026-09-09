package nl.requios.effortlessbuilding.modifier;

import java.util.List;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipeline;
import org.lightning323.creative_mode_tweaks.Config;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public class ArrayModifier extends AbstractModifier {
   public int count = 1;
   public int offsetX = 1;
   public int offsetY = 0;
   public int offsetZ = 0;

   public Component getDisplayName() {
      return Component.literal("Array");
   }

   public void processBlocks(BlockSet blocks, Player player, BuildPipeline.BuildState action) {
      if (this.count > 0) {
         int maxCount = Config.getBuildingMaxArrayCount(player);
         int maxOffset = Config.getBuildingMaxArrayOffset(player);
         int effectiveCount = Math.min(this.count, maxCount);
         int effOffsetX = Math.clamp((long)this.offsetX, -maxOffset, maxOffset);
         int effOffsetY = Math.clamp((long)this.offsetY, -maxOffset, maxOffset);
         int effOffsetZ = Math.clamp((long)this.offsetZ, -maxOffset, maxOffset);
         List<BlockEntry> snapshot = blocks.snapshotEntries();

         for(int i = 1; i <= effectiveCount; ++i) {
            int dx = effOffsetX * i;
            int dy = effOffsetY * i;
            int dz = effOffsetZ * i;

            for (BlockEntry original : snapshot) {
               BlockPos copy = original.blockPos.offset(dx, dy, dz);
               BlockEntry entry = new BlockEntry(copy);
               entry.copyRotationSettingsFrom(original);

               blocks.add(entry);
            }
         }

      }
   }
}
