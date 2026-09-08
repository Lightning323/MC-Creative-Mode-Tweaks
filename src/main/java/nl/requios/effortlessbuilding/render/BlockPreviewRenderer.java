package nl.requios.effortlessbuilding.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.BuildModes;
import nl.requios.effortlessbuilding.buildmode.buildmodes.Mesh;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipeline;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipelineClient;
import nl.requios.effortlessbuilding.buildpipeline.SableCompat;
import nl.requios.effortlessbuilding.render.preview.PreviewBlockMesh.PreviewBlock;
import nl.requios.effortlessbuilding.render.preview.PreviewRenderCache;
import org.lightning323.creative_mode_tweaks.Config;
import org.joml.Matrix4f;

/**
 * Thin per-frame orchestrator for build-mode previews.
 *
 * <p>All heavy work lives in {@link PreviewRenderCache}: shape generation,
 * constraints, sorting, ghost tessellation and border math run <b>only when
 * the shape key changes</b> (new hover block, new click, new held item, …).
 * This method therefore does, per frame:</p>
 * <ol>
 *   <li>refresh the cache (a raycast + key compare on a cache hit),</li>
 *   <li>re-draw the cached GPU sections at the new camera,</li>
 *   <li>draw the handful of animated block entities + Mesh vertices live.</li>
 * </ol>
 *
 * <p>The one remaining per-frame {@code O(N)} loop is the action-bar
 * count/dims line in {@code updateFeedback} while a sequence is active
 * (plain int compares, no allocation — the list itself is cached). Everything
 * that allocated, hashed, sorted or touched the world per frame before now
 * runs on shape change only.</p>
 */
public class BlockPreviewRenderer {
   private static final ResourceLocation SELECTION_TEXTURE = ResourceLocation.fromNamespaceAndPath("creative_mode_tweaks", "textures/special/selection.png");
   private static final RenderType MESH_VERTEX_RENDER_TYPE = RenderType.eyes(SELECTION_TEXTURE);

   public static void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, double camX, double camY, double camZ, Matrix4f modelViewMatrix, Matrix4f projectionMatrix) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null) {
         PreviewRenderCache.get().clear();
         return;
      }

      PreviewRenderCache cache = PreviewRenderCache.get();
      cache.update(mc);

      if (cache.hasPreview()) {
         // Count/dims line + placement sound. Reads the cached list (no
         // allocation); the min/max scan only runs mid-sequence. Red when the
         // count cap cut the shape (see PreviewRenderCache.isOverLimit).
         BuildPipeline.BuildState pendingAction = BuildPipelineClient.getBuildState();
         RenderHandler.updateFeedback(cache.allPositions(), pendingAction != null, pendingAction, cache.isOverLimit());

         // Ghost blocks: pure GPU re-draw of the cached sections.
         cache.renderBlocks(mc.level, camX, camY, camZ, modelViewMatrix, projectionMatrix);

         // Animated block entities tick every frame, so they bypass the cache
         // and draw immediately. Almost always zero or a handful.
         List<PreviewBlock> animated = cache.animatedBlocks();
         if (!animated.isEmpty()) {
            int blockAlpha = (int) (Config.getBuildingPreviewBlockTransparency() * 255.0F);
            AlphaMultiBufferSource wrappedSource = new AlphaMultiBufferSource(bufferSource, blockAlpha);
            for (PreviewBlock previewBlock : animated) {
               poseStack.pushPose();
               try {
                  SableCompat.translateToBlock(poseStack, mc.level, previewBlock.pos(), camX, camY, camZ);
                  mc.getBlockRenderer().renderSingleBlock(previewBlock.state(), poseStack, wrappedSource, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
               } finally {
                  poseStack.popPose();
               }
            }
            bufferSource.endBatch();
         }

         // Tinted fill, then border outline — both cached section draws.
         // Depth-mask toggling preserved from the old immediate path so the
         // ghost look is unchanged.
         RenderSystem.depthMask(false);
         cache.renderFill(mc.level, camX, camY, camZ, modelViewMatrix, projectionMatrix);
         RenderSystem.depthMask(true);
         cache.renderOutline(mc.level, camX, camY, camZ, modelViewMatrix, projectionMatrix);
      } else {
         RenderHandler.resetPreviewSize();
      }

      renderSelectionMarkers(poseStack, bufferSource, mc.level, camX, camY, camZ);
   }

   /** Renders Mesh's session-only vertices independently of the normal block preview. */
   private static void renderSelectionMarkers(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, Level level, double camX, double camY, double camZ) {
      if (BuildModes.CLIENT.getBuildMode() != BuildModeEnum.MESH || !(BuildModeEnum.MESH.instance instanceof Mesh mesh)) {
         return;
      }

      List<BlockPos> vertices = mesh.getVertexMarkers();
      if (vertices.isEmpty()) {
         return;
      }

      // Deliberately live: a handful of boxes, and they must track hover
      // highlights the same frame the crosshair moves onto them.
      RenderSystem.depthMask(false);
      VertexConsumer consumer = bufferSource.getBuffer(MESH_VERTEX_RENDER_TYPE);
      BlockHitResult hit = BuildPipelineClient.getCurrentTargetHit(Minecraft.getInstance());
      BlockPos hoveredMarker = hit != null ? mesh.getSelectionMarker(hit.getBlockPos()) : null;

      for (BlockPos vertex : vertices) {
         boolean selected = mesh.isVertexSelected(vertex)
//                 || vertex.equals(mesh.getPreviewPoint())
                 || vertex.equals(hoveredMarker);
         renderMeshVertexMarker(poseStack, consumer, level, vertex, camX, camY, camZ, selected ? 0 : 255, 255, selected ? 0 : 255);
      }

      bufferSource.endBatch(MESH_VERTEX_RENDER_TYPE);
      RenderSystem.depthMask(true);
   }

   /** A full box keeps each persistent vertex individually visible, including adjacent ones. */
   private static void renderMeshVertexMarker(PoseStack poseStack, VertexConsumer consumer, Level level, BlockPos pos, double camX, double camY, double camZ, int r, int g, int b) {
      float epsilon = 0.008F;
      float x0 = -epsilon;
      float x1 = 1.0F + epsilon;
      float y0 = -epsilon;
      float y1 = 1.0F + epsilon;
      float z0 = -epsilon;
      float z1 = 1.0F + epsilon;
      int alpha = 190;
      poseStack.pushPose();
      SableCompat.translateToBlock(poseStack, level, pos, camX, camY, camZ);
      PoseStack.Pose pose = poseStack.last();
      addFace(consumer, pose, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0.0F, -1.0F, 0.0F, r, g, b, alpha);
      addFace(consumer, pose, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0.0F, 1.0F, 0.0F, r, g, b, alpha);
      addFace(consumer, pose, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, 0.0F, 0.0F, -1.0F, r, g, b, alpha);
      addFace(consumer, pose, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, 0.0F, 0.0F, 1.0F, r, g, b, alpha);
      addFace(consumer, pose, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, -1.0F, 0.0F, 0.0F, r, g, b, alpha);
      addFace(consumer, pose, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, 1.0F, 0.0F, 0.0F, r, g, b, alpha);
      poseStack.popPose();
   }

   /** Called after models are rebaked so preview buffers never retain stale geometry. */
   public static void clearPreviewMesh() {
      PreviewRenderCache.get().onModelsBaked();
   }

   private static void addFace(VertexConsumer consumer, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float nx, float ny, float nz, int r, int g, int b, int a) {
      consumer.addVertex(pose, x0, y0, z0).setColor(r, g, b, a).setUv(0.0F, 0.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, nx, ny, nz);
      consumer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setUv(0.0F, 1.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, nx, ny, nz);
      consumer.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setUv(1.0F, 1.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, nx, ny, nz);
      consumer.addVertex(pose, x3, y3, z3).setColor(r, g, b, a).setUv(1.0F, 0.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, nx, ny, nz);
   }

   /** Applies the ghost opacity to animated-entity draws (same look as cached blocks). */
   private static class AlphaMultiBufferSource implements MultiBufferSource {
      private final MultiBufferSource.BufferSource delegate;
      private final int alpha;
      private final Map<RenderType, VertexConsumer> consumers = new HashMap<>();

      AlphaMultiBufferSource(MultiBufferSource.BufferSource delegate, int alpha) {
         this.delegate = delegate;
         this.alpha = alpha;
      }

      public VertexConsumer getBuffer(RenderType renderType) {
         RenderType renderTypeToUse = renderType.toString().contains("entity_cutout") ? RenderType.translucent() : renderType;
         return this.consumers.computeIfAbsent(renderTypeToUse, (type) -> new AlphaVertexConsumer(this.delegate.getBuffer(type), this.alpha));
      }
   }

   private static class AlphaVertexConsumer implements VertexConsumer {
      private final VertexConsumer delegate;
      private final int alpha;

      AlphaVertexConsumer(VertexConsumer delegate, int alpha) {
         this.delegate = delegate;
         this.alpha = alpha;
      }

      public VertexConsumer addVertex(float x, float y, float z) {
         this.delegate.addVertex(x, y, z);
         return this;
      }

      public VertexConsumer setColor(int r, int g, int b, int a) {
         this.delegate.setColor(r, g, b, this.alpha);
         return this;
      }

      public VertexConsumer setUv(float u, float v) {
         this.delegate.setUv(u, v);
         return this;
      }

      public VertexConsumer setUv1(int u, int v) {
         this.delegate.setUv1(u, v);
         return this;
      }

      public VertexConsumer setUv2(int u, int v) {
         this.delegate.setUv2(u, v);
         return this;
      }

      public VertexConsumer setNormal(float nx, float ny, float nz) {
         this.delegate.setNormal(nx, ny, nz);
         return this;
      }
   }
}
