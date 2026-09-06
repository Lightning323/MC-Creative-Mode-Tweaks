package nl.requios.effortlessbuilding.utilities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

public class BlockEntry {
   public final BlockPos blockPos;
   public boolean mirrorX;
   public boolean mirrorY;
   public boolean mirrorZ;
   public Rotation rotation;
   public BlockState blockState;
   public Item item;
   private BlockStatus status;

   public BlockEntry(BlockPos blockPos) {
      this.rotation = Rotation.NONE;
      this.status = BlockStatus.VALID;
      this.blockPos = blockPos;
   }

   public BlockEntry(BlockPos blockPos, BlockState blockState, Item item) {
      this.rotation = Rotation.NONE;
      this.status = BlockStatus.VALID;
      this.blockPos = blockPos;
      this.blockState = blockState;
      this.item = item;
   }

   public void copyRotationSettingsFrom(BlockEntry blockEntry) {
      this.mirrorX = blockEntry.mirrorX;
      this.mirrorY = blockEntry.mirrorY;
      this.mirrorZ = blockEntry.mirrorZ;
      this.rotation = blockEntry.rotation;
   }

   public BlockState applyTransforms(BlockState state) {
      if (this.mirrorX) {
         state = state.mirror(Mirror.FRONT_BACK);
      }

      if (this.mirrorZ) {
         state = state.mirror(Mirror.LEFT_RIGHT);
      }

      if (this.mirrorY) {
         state = BlockUtilities.applyVerticalMirror(state);
      }

      if (this.rotation != Rotation.NONE) {
         state = state.rotate(this.rotation);
      }

      return state;
   }

   public void markRejected(BlockStatus reason) {
      if (this.status == BlockStatus.VALID) {
         this.status = reason;
      }

   }

   public BlockStatus getStatus() {
      return this.status;
   }

   public boolean isValid() {
      return this.status.isValid();
   }

   public void resetStatus() {
      this.status = BlockStatus.VALID;
   }
}
