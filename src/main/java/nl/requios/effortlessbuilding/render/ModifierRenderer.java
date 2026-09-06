package nl.requios.effortlessbuilding.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import nl.requios.effortlessbuilding.modifier.IModifier;
import nl.requios.effortlessbuilding.modifier.MirrorModifier;
import nl.requios.effortlessbuilding.modifier.ModifierSystem;
import nl.requios.effortlessbuilding.modifier.RadialMirrorModifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class ModifierRenderer {
   private static final ResourceLocation BLANK_TEXTURE = ResourceLocation.fromNamespaceAndPath("creative_mode_tweaks", "textures/special/blank.png");
   private static final int CIRCLE_SEGMENTS = 64;

   public static void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, double camX, double camY, double camZ) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         for(IModifier modifier : ModifierSystem.CLIENT.getModifiers()) {
            if (modifier.isEnabled()) {
               if (modifier instanceof MirrorModifier) {
                  MirrorModifier mirror = (MirrorModifier)modifier;
                  renderMirrorPlanes(poseStack, bufferSource, mirror, camX, camY, camZ);
               } else if (modifier instanceof RadialMirrorModifier) {
                  RadialMirrorModifier radial = (RadialMirrorModifier)modifier;
                  renderRadialBoundary(poseStack, bufferSource, radial, camX, camY, camZ);
               }
            }
         }

         RenderSystem.depthMask(false);
         bufferSource.endBatch(RenderType.entityTranslucent(BLANK_TEXTURE));
         RenderSystem.depthMask(true);
      }
   }

   private static void renderMirrorPlanes(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, MirrorModifier mirror, double camX, double camY, double camZ) {
      VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(BLANK_TEXTURE));
      PoseStack.Pose pose = poseStack.last();
      int radius = mirror.size / 2;
      float ox = (float)(mirror.originX - camX);
      float oy = (float)(mirror.originY - camY);
      float oz = (float)(mirror.originZ - camZ);
      float e = 0.005F;
      if (mirror.mirrorX) {
         addFace(consumer, pose, ox + e, oy - (float)radius, oz - (float)radius, ox + e, oy - (float)radius, oz + (float)radius, ox + e, oy + (float)radius, oz + (float)radius, ox + e, oy + (float)radius, oz - (float)radius, 1.0F, 0.0F, 0.0F, 255, 80, 80, 50);
      }

      if (mirror.mirrorY) {
         addFace(consumer, pose, ox - (float)radius, oy + e, oz - (float)radius, ox + (float)radius, oy + e, oz - (float)radius, ox + (float)radius, oy + e, oz + (float)radius, ox - (float)radius, oy + e, oz + (float)radius, 0.0F, 1.0F, 0.0F, 80, 255, 80, 50);
      }

      if (mirror.mirrorZ) {
         addFace(consumer, pose, ox - (float)radius, oy - (float)radius, oz + e, ox + (float)radius, oy - (float)radius, oz + e, ox + (float)radius, oy + (float)radius, oz + e, ox - (float)radius, oy + (float)radius, oz + e, 0.0F, 0.0F, 1.0F, 80, 80, 255, 50);
      }

   }

   private static void renderRadialBoundary(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, RadialMirrorModifier radial, double camX, double camY, double camZ) {
      VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(BLANK_TEXTURE));
      PoseStack.Pose pose = poseStack.last();
      float ox = (float)(radial.originX - camX);
      float oy = (float)(radial.originY - camY);
      float oz = (float)(radial.originZ - camZ);
      float r = (float)radial.size / 2.0F;
      float halfWidth = 0.04F;

      for(int i = 0; i < radial.slices; ++i) {
         double angle = (Math.PI * 2D) * (double)i / (double)radial.slices;
         float dx = (float)Math.cos(angle) * r;
         float dz = (float)Math.sin(angle) * r;
         addFace(consumer, pose, ox, oy - halfWidth, oz, ox, oy + halfWidth, oz, ox + dx, oy + halfWidth, oz + dz, ox + dx, oy - halfWidth, oz + dz, 0.0F, 1.0F, 0.0F, 200, 100, 255, 80);
      }

      for(int i = 0; i < 64; ++i) {
         double a0 = (Math.PI * 2D) * (double)i / (double)64.0F;
         double a1 = (Math.PI * 2D) * (double)(i + 1) / (double)64.0F;
         float x0 = ox + (float)Math.cos(a0) * r;
         float z0 = oz + (float)Math.sin(a0) * r;
         float x1 = ox + (float)Math.cos(a1) * r;
         float z1 = oz + (float)Math.sin(a1) * r;
         addFace(consumer, pose, x0, oy - halfWidth, z0, x0, oy + halfWidth, z0, x1, oy + halfWidth, z1, x1, oy - halfWidth, z1, 0.0F, 1.0F, 0.0F, 200, 100, 255, 80);
      }

   }

   private static void addFace(VertexConsumer consumer, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float nx, float ny, float nz, int r, int g, int b, int a) {
      consumer.addVertex(pose, x0, y0, z0).setColor(r, g, b, a).setUv(0.0F, 0.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, nx, ny, nz);
      consumer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setUv(0.0F, 1.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, nx, ny, nz);
      consumer.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setUv(1.0F, 1.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, nx, ny, nz);
      consumer.addVertex(pose, x3, y3, z3).setColor(r, g, b, a).setUv(1.0F, 0.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, nx, ny, nz);
   }
}
