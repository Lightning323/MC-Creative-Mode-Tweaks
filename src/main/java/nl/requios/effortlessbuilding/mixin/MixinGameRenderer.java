package nl.requios.effortlessbuilding.mixin;

import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.BuildModes;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipeline;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipelineClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lightning323.creative_mode_tweaks.Config;

@Mixin({GameRenderer.class})
public class MixinGameRenderer {
   @Shadow
   @Final
   Minecraft minecraft;

   @Inject(
      method = {"pick(F)V"},
      at = {@At("TAIL")}
   )
   private void onPick(float partialTicks, CallbackInfo ci) {
      if (this.minecraft.player != null && this.minecraft.level != null) {
         if (BuildModes.CLIENT.getBuildMode() != BuildModeEnum.DISABLED || BuildPipelineClient.isAngelPlacementActive(this.minecraft.player)) {
            if (this.minecraft.player.getMainHandItem().isEmpty() || BuildPipeline.isBuildTriggerItem(this.minecraft.player.getMainHandItem()) || BuildPipelineClient.getBuildState() != null) {
               if (BuildPipelineClient.isAngelPlacementActive(this.minecraft.player)) {
                  BlockHitResult hit = BuildPipelineClient.getCurrentTargetHit(this.minecraft);
                  if (hit != null) {
                     this.minecraft.hitResult = hit;
                     this.minecraft.crosshairPickEntity = null;
                  }

                  return;
               }

               if (this.minecraft.hitResult == null || this.minecraft.hitResult.getType() != Type.BLOCK) {
                  Vec3 start = this.minecraft.player.getEyePosition(partialTicks);
                  Vec3 end = start.add(this.minecraft.player.getViewVector(partialTicks).scale((double)Config.getBuildingReach(this.minecraft.player)));
                  ClipContext ctx = new ClipContext(start, end, Block.OUTLINE, Fluid.NONE, this.minecraft.player);
                  BlockHitResult hit = this.minecraft.level.clip(ctx);
                  if (hit.getType() == Type.BLOCK) {
                     this.minecraft.hitResult = hit;
                     this.minecraft.crosshairPickEntity = null;
                  }

               }
            }
         }
      }
   }
}
