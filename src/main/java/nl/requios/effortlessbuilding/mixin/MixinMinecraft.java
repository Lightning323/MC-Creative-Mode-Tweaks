package nl.requios.effortlessbuilding.mixin;

import nl.requios.effortlessbuilding.buildpipeline.BuildPipeline;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipelineClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult.Type;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Minecraft.class})
public class MixinMinecraft {
   @Inject(
      method = {"startUseItem"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onStartUseItem(CallbackInfo ci) {
      Minecraft mc = (Minecraft) (Object)this;
      if (mc.player != null && mc.level != null) {
         if (BuildPipelineClient.shouldInterceptPlacing()) {
            boolean sequenceActive = BuildPipelineClient.getBuildState() != null;
            if (BuildPipeline.isBuildTriggerItem(mc.player.getMainHandItem()) || sequenceActive) {
               ci.cancel();
            }
         }
      }
   }

   @Inject(
      method = {"startAttack"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onStartAttack(CallbackInfoReturnable<Boolean> cir) {
      Minecraft mc = (Minecraft)(Object)this;
      if (mc.player != null && mc.level != null) {
         if (BuildPipelineClient.shouldInterceptBreaking()) {
            boolean sequenceActive = BuildPipelineClient.getBuildState() != null;
            if (sequenceActive || BuildPipeline.isBuildTriggerItem(mc.player.getMainHandItem())) {
               if (mc.hitResult != null && mc.hitResult.getType() == Type.BLOCK) {
                  cir.setReturnValue(false);
                  cir.cancel();
               }

            }
         }
      }
   }

   @Inject(
      method = {"continueAttack"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onContinueAttack(boolean leftClick, CallbackInfo ci) {
      if (leftClick) {
         Minecraft mc = (Minecraft)(Object)this;
         if (mc.player != null && mc.level != null) {
            if (BuildPipelineClient.shouldInterceptBreaking()) {
               boolean sequenceActive = BuildPipelineClient.getBuildState() != null;
               if (sequenceActive || BuildPipeline.isBuildTriggerItem(mc.player.getMainHandItem())) {
                  if (mc.hitResult != null && mc.hitResult.getType() == Type.BLOCK) {
                     ci.cancel();
                  }

               }
            }
         }
      }
   }
}
