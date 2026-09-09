package nl.requios.effortlessbuilding.modifier;

import java.util.List;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipeline;
import org.lightning323.creative_mode_tweaks.Config;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Rotation;

public class RadialMirrorModifier extends AbstractModifier {
   public double originX;
   public double originY = (double)64.0F;
   public double originZ;
   public int slices = 4;
   public boolean mirrorSlices = false;
   public int size = 40;

   public Component getDisplayName() {
      return Component.literal("Radial Mirror");
   }

   public void processBlocks(BlockSet blocks, Player player, BuildPipeline.BuildState action) {
      if (this.slices > 1) {
         int effectiveSize = Math.min(this.size, Config.getBuildingMaxMirrorSize(player));
         List<BlockEntry> snapshot = blocks.snapshotEntries();

         for(int i = 1; i < this.slices; ++i) {
            double angle = (Math.PI * 2D) * (double)i / (double)this.slices;
            this.addRotated(blocks, snapshot, angle, false, effectiveSize);
         }

         if (this.mirrorSlices) {
            for(int i = 0; i < this.slices; ++i) {
               double angle = (Math.PI * 2D) * (double)i / (double)this.slices;
               this.addRotated(blocks, snapshot, angle, true, effectiveSize);
            }
         }

      }
   }

   private void addRotated(BlockSet blocks, List<BlockEntry> snapshot, double angle, boolean doMirrorZ, int effectiveSize) {
      double halfSize = (double)effectiveSize / (double)2.0F;
      double rSq = halfSize * halfSize;
      double cos = Math.cos(angle);
      double sin = Math.sin(angle);
      Rotation sliceRotation = angleToRotation(angle);

      for (BlockEntry original : snapshot) {
         BlockPos pos = original.blockPos;
         double dx = (double)pos.getX() + (double)0.5F - this.originX;
         double dz = (double)pos.getZ() + (double)0.5F - this.originZ;
         if (doMirrorZ) {
            dz = -dz;
         }

         double rx = this.originX + dx * cos - dz * sin - (double)0.5F;
         double rz = this.originZ + dx * sin + dz * cos - (double)0.5F;
         BlockPos rotated = BlockPos.containing(rx, (double)pos.getY(), rz);
         if (!rotated.equals(pos)) {
            double rdx = (double)rotated.getX() + (double)0.5F - this.originX;
            double rdz = (double)rotated.getZ() + (double)0.5F - this.originZ;
            if (!(rdx * rdx + rdz * rdz > rSq)) {
               BlockEntry entry = new BlockEntry(rotated);
               entry.copyRotationSettingsFrom(original);
               entry.rotation = composeRotations(entry.rotation, sliceRotation);
               if (doMirrorZ) {
                  entry.mirrorZ = !entry.mirrorZ;
               }

               blocks.add(entry);
            }
         }
      }

   }

   private static Rotation angleToRotation(double angle) {
      double normalized = (angle % (Math.PI * 2D) + (Math.PI * 2D)) % (Math.PI * 2D);
      int quarter = (int)Math.round(normalized / (Math.PI / 2D)) % 4;
      return Rotation.values()[quarter];
   }

   private static Rotation composeRotations(Rotation first, Rotation second) {
      return Rotation.values()[(first.ordinal() + second.ordinal()) % 4];
   }
}
