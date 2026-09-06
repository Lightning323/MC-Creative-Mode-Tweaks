package nl.requios.effortlessbuilding.mixin;

import nl.requios.effortlessbuilding.buildpipeline.BuildPipelineClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

/** Renders the standard block-selection outline for Angel Placement's air target. */
@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {
   @Shadow
   @Final
   private Minecraft minecraft;

   @Redirect(
      method = {"renderLevel"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/multiplayer/ClientLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
      ),
      slice = @Slice(
         from = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;hitResult:Lnet/minecraft/world/phys/HitResult;"),
         to = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/debug/DebugRenderer;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;DDD)V"
         )
      )
   )
   private BlockState renderAngelPlacementCursor(ClientLevel level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      Player player = this.minecraft.player;
      if (player == null || !BuildPipelineClient.isAngelPlacementActive(player)) {
         return state;
      }

      if (this.minecraft.hitResult instanceof BlockHitResult target && target.getBlockPos().equals(pos) && state.isAir()) {
         return Blocks.STONE.defaultBlockState();
      }

      return state;
   }
}
