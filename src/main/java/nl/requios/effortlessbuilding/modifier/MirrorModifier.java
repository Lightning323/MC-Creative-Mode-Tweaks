package nl.requios.effortlessbuilding.modifier;

import java.util.ArrayList;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipeline;
import org.lightning323.creative_mode_tweaks.Config;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public class MirrorModifier extends AbstractModifier {
   public double originX;
   public double originY = (double)64.0F;
   public double originZ;
   public boolean mirrorX = true;
   public boolean mirrorY = false;
   public boolean mirrorZ = false;
   public int size = 40;

   public Component getDisplayName() {
      return Component.literal("Mirror");
   }

   public void processBlocks(BlockSet blocks, Player player, BuildPipeline.BuildState action) {
      int effectiveSize = Math.min(this.size, Config.getBuildingMaxMirrorSize(player));
      if (this.mirrorX) {
         this.applyAxisMirror(blocks, 0, effectiveSize);
      }

      if (this.mirrorY) {
         this.applyAxisMirror(blocks, 1, effectiveSize);
      }

      if (this.mirrorZ) {
         this.applyAxisMirror(blocks, 2, effectiveSize);
      }

   }

   private void applyAxisMirror(BlockSet blocks, int axis, int effectiveSize) {
      double halfSize = (double)effectiveSize / (double)2.0F;

      for(BlockPos  pos : new ArrayList(blocks.keySet())) {
         double mx = (double)pos.getX();
         double my = (double)pos.getY();
         double mz = (double)pos.getZ();
         switch (axis) {
            case 0 -> mx = (double)2.0F * this.originX - (double)pos.getX() - (double)1.0F;
            case 1 -> my = (double)2.0F * this.originY - (double)pos.getY() - (double)1.0F;
            case 2 -> mz = (double)2.0F * this.originZ - (double)pos.getZ() - (double)1.0F;
         }

         BlockPos mirrored = BlockPos.containing(mx, my, mz);
         if (!mirrored.equals(pos)) {
            double dx = Math.abs((double)mirrored.getX() + (double)0.5F - this.originX);
            double dy = Math.abs((double)mirrored.getY() + (double)0.5F - this.originY);
            double dz = Math.abs((double)mirrored.getZ() + (double)0.5F - this.originZ);
            if (!(dx > halfSize) && !(dy > halfSize) && !(dz > halfSize)) {
               BlockEntry entry = new BlockEntry(mirrored);
               BlockEntry original = (BlockEntry)blocks.get(pos);
               if (original != null) {
                  entry.copyRotationSettingsFrom(original);
                  if (axis == 0) {
                     entry.mirrorX = !entry.mirrorX;
                  } else if (axis == 1) {
                     entry.mirrorY = !entry.mirrorY;
                  } else {
                     entry.mirrorZ = !entry.mirrorZ;
                  }
               }

               blocks.add(entry);
            }
         }
      }

   }
}
