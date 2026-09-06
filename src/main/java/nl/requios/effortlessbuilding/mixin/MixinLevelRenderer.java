package nl.requios.effortlessbuilding.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipelineClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Renders a direct block-sized outline for Angel Placement's air target. */
@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {
   @Shadow
   @Final
   private Minecraft minecraft;

   @Redirect(
      method = {"renderLevel"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/debug/DebugRenderer;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;DDD)V"
      )
   )
   private void renderAngelPlacementCursor(DebugRenderer debugRenderer, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, double cameraX, double cameraY, double cameraZ) {
      Player player = this.minecraft.player;
      if (player != null && BuildPipelineClient.shouldRenderAngelPlacementCursor(player) && this.minecraft.hitResult instanceof BlockHitResult target && target.getType() == HitResult.Type.MISS) {
         BlockPos pos = target.getBlockPos();
         if (this.minecraft.level != null && this.minecraft.level.getBlockState(pos).isAir() && this.minecraft.level.getWorldBorder().isWithinBounds(pos)) {
            VertexConsumer buffer = bufferSource.getBuffer(RenderType.lines());
            LevelRenderer.renderLineBox(poseStack, buffer, (double)pos.getX() - cameraX, (double)pos.getY() - cameraY, (double)pos.getZ() - cameraZ, (double)(pos.getX() + 1) - cameraX, (double)(pos.getY() + 1) - cameraY, (double)(pos.getZ() + 1) - cameraZ, 0.0F, 0.0F, 0.0F, 0.4F);
         }
      }

      debugRenderer.render(poseStack, bufferSource, cameraX, cameraY, cameraZ);
   }
}
