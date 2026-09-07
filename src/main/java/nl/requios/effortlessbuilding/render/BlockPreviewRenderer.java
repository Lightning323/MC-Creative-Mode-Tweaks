package nl.requios.effortlessbuilding.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.BuildModes;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipeline;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipelineClient;
import nl.requios.effortlessbuilding.buildpipeline.TrowelSystem;
import nl.requios.effortlessbuilding.buildpipeline.SableCompat;
import nl.requios.effortlessbuilding.mixin.BucketItemAccessor;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import nl.requios.effortlessbuilding.utilities.BlockStatus;
import org.lightning323.creative_mode_tweaks.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;

public class BlockPreviewRenderer {
   private static final ResourceLocation CHECKERBOARD_TEXTURE = ResourceLocation.fromNamespaceAndPath("creative_mode_tweaks", "textures/special/checkerboard.png");
   private static final ResourceLocation OUTLINE_TEXTURE = ResourceLocation.fromNamespaceAndPath("creative_mode_tweaks", "textures/special/blank.png");
   private static final double VANILLA_REACH_SQ = (double)20.25F;
   private static final int MAX_CACHED_BLOCK_MODELS = 256;
   private static final Map<BlockState, CachedBlockModel> CACHED_BLOCK_MODELS = new LinkedHashMap<>(MAX_CACHED_BLOCK_MODELS, 0.75F, true) {
      protected boolean removeEldestEntry(Map.Entry<BlockState, CachedBlockModel> eldest) {
         return this.size() > MAX_CACHED_BLOCK_MODELS;
      }
   };

   public static void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, double camX, double camY, double camZ) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null) {
         boolean emptyHand = !BuildPipeline.isBuildTriggerItem(mc.player.getMainHandItem());
         boolean sequenceActive = BuildPipelineClient.getBuildState() != null;
         boolean modeActive = BuildModes.CLIENT.getBuildMode() != BuildModeEnum.DISABLED;
         BlockSet blockSet = BuildPipelineClient.getPreviewBlocks(mc);

         if (blockSet != null && !blockSet.isEmpty()) {
            if (blockSet.size() <= 1 && !sequenceActive) {
               RenderHandler.resetPreviewSize();
            } else {
               List<BlockPos> positions = new ArrayList(blockSet.keySet());
               BuildPipeline.BuildState pendingAction = BuildPipelineClient.getBuildState();
               RenderHandler.updateFeedback(positions, sequenceActive, pendingAction);
               boolean isBreaking = pendingAction == BuildPipeline.BuildState.BREAKING;

               List<BlockPos> breakablePositions = new ArrayList();
               List<BlockPos> unbreakablePositions = new ArrayList();
               boolean selectionOutsideSublevel = blockSet.hasEntriesWithStatus(BlockStatus.OUTSIDE_REACH);

               for(BlockPos pos : positions) {
                  BlockEntry entry = (BlockEntry)blockSet.get(pos);
                  if (selectionOutsideSublevel || entry != null && !entry.isValid()) {
                     unbreakablePositions.add(pos);
                  } else {
                     breakablePositions.add(pos);
                  }
               }

               positions = breakablePositions;
               int maxPreviews = Config.BUILDING_MAX_BLOCK_PREVIEWS.get();
               if (!isBreaking) { //If we are building

                  int blockAlpha = (int)(Config.getBuildingPreviewBlockTransparency() * 255.0F);
                  ItemStack held = mc.player.getMainHandItem();
                  BlockState baseState = null;
                  Item heldItem = held.getItem();

                  if (heldItem instanceof BlockItem) {
                     BlockItem blockItem = (BlockItem) heldItem;
                     baseState = getPlacementState(blockItem, mc);
                  } else {
                     heldItem = held.getItem();
                     if (heldItem instanceof BucketItem) {
                        BucketItem bucketItem = (BucketItem)heldItem;
                        Fluid fluid = ((BucketItemAccessor)bucketItem).effortlessbuilding$getFluid();
                        if (!fluid.isSame(Fluids.EMPTY)) {
                           baseState = fluid.defaultFluidState().createLegacyBlock();
                        }
                     }
                  }

                  boolean randomized = TrowelSystem.isTrowel(held);
                  if (baseState != null || randomized) {
                     try {
                        AlphaMultiBufferSource wrappedSource = new AlphaMultiBufferSource(bufferSource, blockAlpha);
//                        TintedMultiBufferSource missingSource = new TintedMultiBufferSource(bufferSource, 255, 80, 80, 200);

                        Map<Item, BlockState> randomStates = new HashMap();
                        int rendered = 0;

                        for(BlockPos pos : positions) {
                           if (rendered >= maxPreviews) {
                              break;
                           }

                           BlockState state = baseState;
                           BlockEntry entry = (BlockEntry)blockSet.get(pos);
                           if (randomized && entry != null) { //If we have a trowel, set the block to a random block
                              Item var34 = entry.item;
                              if (var34 instanceof BlockItem) {
                                 BlockItem randomBlock = (BlockItem)var34;
                                 state = (BlockState)randomStates.computeIfAbsent(entry.item, (item) -> getPlacementState(randomBlock, mc, new ItemStack(item)));
                              }
                           }

                           if (state != null) {
                              if (entry != null) {
                                 state = entry.applyTransforms(state);
                              }

                              poseStack.pushPose();
                              SableCompat.translateToBlock(poseStack, mc.level, pos, camX, camY, camZ);
//                              poseStack.translate((double)0.5F, (double)0.5F, (double)0.5F);
//                              poseStack.scale(blockScale, blockScale, blockScale);
//                              poseStack.translate((double)-0.5F, (double)-0.5F, (double)-0.5F);

                              renderCachedBlock(mc, state, poseStack, wrappedSource);

                              poseStack.popPose();
                              ++rendered;
                           }
                        }

                        bufferSource.endBatch();
                     } catch (Exception var35) {
                     }
                  }
               }

               RenderSystem.depthMask(false);
               renderBoundingBoxAround(poseStack, bufferSource, mc.level, breakablePositions, camX, camY, camZ, isBreaking, false);
               if (!unbreakablePositions.isEmpty()) {
                  renderBoundingBoxAround(poseStack, bufferSource, mc.level, unbreakablePositions, camX, camY, camZ, isBreaking, true);
               }

               bufferSource.endBatch(RenderType.entityTranslucentCull(CHECKERBOARD_TEXTURE));
               RenderSystem.depthMask(true);
               float outlineWidth = 0.02F;
               int oR = 255;
               int oG = isBreaking ? 0 : 255;
               int oB = isBreaking ? 0 : 255;

               List<BlockPos> validPositions = new ArrayList<>(breakablePositions);
               if (!validPositions.isEmpty()) {
                  renderEdgeQuads(poseStack, bufferSource, mc.level, computeBorderEdges(validPositions), camX, camY, camZ, outlineWidth, oR, oG, oB, 255);
                  bufferSource.endBatch(RenderType.entityTranslucent(OUTLINE_TEXTURE));
               }

               if (!unbreakablePositions.isEmpty()) {
                  renderEdgeQuads(poseStack, bufferSource, mc.level, computeBorderEdges(unbreakablePositions), camX, camY, camZ, outlineWidth, 100, 100, 100, 255);
                  bufferSource.endBatch(RenderType.entityTranslucent(OUTLINE_TEXTURE));
               }

            }
         } else {
            RenderHandler.resetPreviewSize();
         }
      }
   }

   /**
    * The preview renderer always invokes vanilla's item-style block rendering: it has no
    * position-dependent model data and uses a fixed random seed.  Cache its emitted local
    * vertices so identical preview states do not re-tessellate their baked model every frame.
    */
   private static void renderCachedBlock(Minecraft mc, BlockState state, PoseStack poseStack, MultiBufferSource bufferSource) {
      if (state.getRenderShape() != RenderShape.MODEL) {
         // Animated block-entity renderers can change between frames, so they must stay live.
         mc.getBlockRenderer().renderSingleBlock(state, poseStack, bufferSource, 15728880, OverlayTexture.NO_OVERLAY);
         return;
      }

      CachedBlockModel cachedModel = CACHED_BLOCK_MODELS.get(state);
      if (cachedModel == null) {
         cachedModel = CachedBlockModel.capture(mc, state);
         CACHED_BLOCK_MODELS.put(state, cachedModel);
      }

      cachedModel.render(poseStack.last(), bufferSource);
   }

   /** Called after block models are rebaked so stale vertex data is never reused. */
   public static void clearCachedModels() {
      CACHED_BLOCK_MODELS.clear();
   }

   private static void renderBoundingBoxAround(PoseStack poseStack, MultiBufferSource bufferSource, Level level, List<BlockPos> positions,
                                               double camX, double camY, double camZ,
                                               boolean isBreaking, boolean isUnbreakable) {

      Set<BlockPos> posSet = new HashSet<>(positions);
      VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucentCull(CHECKERBOARD_TEXTURE));
      int r;
      int g;
      int b;
      if (isUnbreakable) {
         r = 255;
         g = 80;
         b = 80;
      } else {
         r = isBreaking ? 255 : 255;
         g = isBreaking ? 0 : 255;
         b = isBreaking ? 0 : 255;
      }

      int a = isUnbreakable ? 100 : 150;
      float eps = 0.002F;

      for(BlockPos pos : positions) {
         poseStack.pushPose();
         SableCompat.translateToBlock(poseStack, level, pos, camX, camY, camZ);
         PoseStack.Pose pose = poseStack.last();
         float x0 = -eps;
         float x1 = x0 + 1.0F + 0.004F;
         float y0 = -eps;
         float y1 = y0 + 1.0F + 0.004F;
         float z0 = -eps;
         float z1 = z0 + 1.0F + 0.004F;
         if (!posSet.contains(pos.below())) {
            addFace(consumer, pose, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0.0F, -1.0F, 0.0F, r, g, b, a);
         }

         if (!posSet.contains(pos.above())) {
            addFace(consumer, pose, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0.0F, 1.0F, 0.0F, r, g, b, a);
         }

         if (!posSet.contains(pos.north())) {
            addFace(consumer, pose, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, 0.0F, 0.0F, -1.0F, r, g, b, a);
         }

         if (!posSet.contains(pos.south())) {
            addFace(consumer, pose, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, 0.0F, 0.0F, 1.0F, r, g, b, a);
         }

         if (!posSet.contains(pos.west())) {
            addFace(consumer, pose, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, -1.0F, 0.0F, 0.0F, r, g, b, a);
         }

         if (!posSet.contains(pos.east())) {
            addFace(consumer, pose, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, 1.0F, 0.0F, 0.0F, r, g, b, a);
         }

         poseStack.popPose();
      }

   }

   private static void addFace(VertexConsumer consumer, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float nx, float ny, float nz, int r, int g, int b, int a) {
      consumer.addVertex(pose, x0, y0, z0).setColor(r, g, b, a).setUv(0.0F, 0.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, nx, ny, nz);
      consumer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setUv(0.0F, 1.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, nx, ny, nz);
      consumer.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setUv(1.0F, 1.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, nx, ny, nz);
      consumer.addVertex(pose, x3, y3, z3).setColor(r, g, b, a).setUv(1.0F, 0.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, nx, ny, nz);
   }

   private static void renderEdgeQuads(PoseStack poseStack, MultiBufferSource bufferSource, Level level, Set<EdgeKey> edges, double camX, double camY, double camZ, float halfWidth, int r, int g, int b, int a) {
      VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(OUTLINE_TEXTURE));

      for(EdgeKey edge : edges) {
         poseStack.pushPose();
         SableCompat.translateToBlock(poseStack, level, new BlockPos(edge.x(), edge.y(), edge.z()), camX, camY, camZ);
         PoseStack.Pose pose = poseStack.last();
         float x0 = 0.0F;
         float y0 = 0.0F;
         float z0 = 0.0F;
         float x1 = x0 + (float)(edge.axis() == 0 ? 1 : 0);
         float y1 = y0 + (float)(edge.axis() == 1 ? 1 : 0);
         float z1 = z0 + (float)(edge.axis() == 2 ? 1 : 0);
         float sideX = edge.axis() == 1 ? halfWidth : 0.0F;
         float sideY = edge.axis() == 1 ? 0.0F : halfWidth;
         float sideZ = 0.0F;
         addFace(consumer, pose, x0 - sideX, y0 - sideY, z0 - sideZ, x0 + sideX, y0 + sideY, z0 + sideZ, x1 + sideX, y1 + sideY, z1 + sideZ, x1 - sideX, y1 - sideY, z1 - sideZ, 0.0F, 1.0F, 0.0F, r, g, b, a);
         poseStack.popPose();
      }

   }

   private static BlockState getPlacementState(BlockItem blockItem, Minecraft mc) {
      return getPlacementState(blockItem, mc, mc.player.getMainHandItem());
   }

   private static BlockState getPlacementState(BlockItem blockItem, Minecraft mc, ItemStack placementStack) {
      Player player = mc.player;
      BlockHitResult hit = BuildPipelineClient.getFirstClickHit();
      if (hit == null) {
         hit = BuildPipelineClient.getCurrentTargetHit(mc);
         if (hit == null) {
            return blockItem.getBlock().defaultBlockState();
         }
      }

      BlockPlaceContext placeCtx = new OpenBlockPlaceContext(mc.level, player, InteractionHand.MAIN_HAND, placementStack, hit);
      BlockState state = blockItem.getBlock().getStateForPlacement(placeCtx);
      return state != null ? state : blockItem.getBlock().defaultBlockState();
   }

   private static Set<EdgeKey> computeBorderEdges(List<BlockPos> positions) {
      Set<EdgeKey> edges = new HashSet();

      for(BlockPos pos : positions) {
         int x = pos.getX();
         int y = pos.getY();
         int z = pos.getZ();
         toggleEdge(edges, 0, x, y, z);
         toggleEdge(edges, 0, x, y + 1, z);
         toggleEdge(edges, 0, x, y, z + 1);
         toggleEdge(edges, 0, x, y + 1, z + 1);
         toggleEdge(edges, 1, x, y, z);
         toggleEdge(edges, 1, x + 1, y, z);
         toggleEdge(edges, 1, x, y, z + 1);
         toggleEdge(edges, 1, x + 1, y, z + 1);
         toggleEdge(edges, 2, x, y, z);
         toggleEdge(edges, 2, x + 1, y, z);
         toggleEdge(edges, 2, x, y + 1, z);
         toggleEdge(edges, 2, x + 1, y + 1, z);
      }

      return edges;
   }

   private static void toggleEdge(Set<EdgeKey> edges, int axis, int x, int y, int z) {
      EdgeKey key = new EdgeKey(axis, x, y, z);
      if (!edges.remove(key)) {
         edges.add(key);
      }

   }

   private static record EdgeKey(int axis, int x, int y, int z) {
   }

   /** A state-specific copy of the vertices emitted by BlockRenderDispatcher.renderSingleBlock. */
   private static final class CachedBlockModel {
      private final List<CachedRenderLayer> layers;

      private CachedBlockModel(List<CachedRenderLayer> layers) {
         this.layers = layers;
      }

      static CachedBlockModel capture(Minecraft mc, BlockState state) {
         CapturingMultiBufferSource capture = new CapturingMultiBufferSource();
         mc.getBlockRenderer().renderSingleBlock(state, new PoseStack(), capture, 15728880, OverlayTexture.NO_OVERLAY);
         return capture.finish();
      }

      void render(PoseStack.Pose pose, MultiBufferSource bufferSource) {
         for(CachedRenderLayer layer : this.layers) {
            VertexConsumer consumer = bufferSource.getBuffer(layer.renderType());

            for(CachedVertex vertex : layer.vertices()) {
               vertex.write(consumer, pose);
            }
         }
      }
   }

   private static record CachedRenderLayer(RenderType renderType, List<CachedVertex> vertices) {
   }

   private static record CachedVertex(float x, float y, float z, int red, int green, int blue, int alpha, float u, float v, int overlayU, int overlayV, int lightU, int lightV, float normalX, float normalY, float normalZ) {
      void write(VertexConsumer consumer, PoseStack.Pose pose) {
         consumer.addVertex(pose, this.x, this.y, this.z)
                 .setColor(this.red, this.green, this.blue, this.alpha)
                 .setUv(this.u, this.v)
                 .setUv1(this.overlayU, this.overlayV)
                 .setUv2(this.lightU, this.lightV)
                 .setNormal(pose, this.normalX, this.normalY, this.normalZ);
      }
   }

   /** Captures dispatcher output once, before it is transformed to a preview block's position. */
   private static final class CapturingMultiBufferSource implements MultiBufferSource {
      private final Map<RenderType, CapturingVertexConsumer> consumers = new LinkedHashMap<>();

      public VertexConsumer getBuffer(RenderType renderType) {
         return this.consumers.computeIfAbsent(renderType, (unused) -> new CapturingVertexConsumer());
      }

      CachedBlockModel finish() {
         List<CachedRenderLayer> layers = new ArrayList<>(this.consumers.size());

         for(Map.Entry<RenderType, CapturingVertexConsumer> entry : this.consumers.entrySet()) {
            layers.add(new CachedRenderLayer(entry.getKey(), entry.getValue().finish()));
         }

         return new CachedBlockModel(layers);
      }
   }

   private static final class CapturingVertexConsumer implements VertexConsumer {
      private final List<CachedVertex> vertices = new ArrayList<>();
      private PendingVertex current;

      public VertexConsumer addVertex(float x, float y, float z) {
         this.completeCurrentVertex();
         this.current = new PendingVertex(x, y, z);
         return this;
      }

      public VertexConsumer setColor(int red, int green, int blue, int alpha) {
         if (this.current != null) {
            this.current.red = red;
            this.current.green = green;
            this.current.blue = blue;
            this.current.alpha = alpha;
         }

         return this;
      }

      public VertexConsumer setUv(float u, float v) {
         if (this.current != null) {
            this.current.u = u;
            this.current.v = v;
         }

         return this;
      }

      public VertexConsumer setUv1(int u, int v) {
         if (this.current != null) {
            this.current.overlayU = u;
            this.current.overlayV = v;
         }

         return this;
      }

      public VertexConsumer setUv2(int u, int v) {
         if (this.current != null) {
            this.current.lightU = u;
            this.current.lightV = v;
         }

         return this;
      }

      public VertexConsumer setNormal(float x, float y, float z) {
         if (this.current != null) {
            this.current.normalX = x;
            this.current.normalY = y;
            this.current.normalZ = z;
         }

         return this;
      }

      List<CachedVertex> finish() {
         this.completeCurrentVertex();
         return this.vertices;
      }

      private void completeCurrentVertex() {
         if (this.current != null) {
            this.vertices.add(this.current.toCachedVertex());
            this.current = null;
         }
      }
   }

   private static final class PendingVertex {
      private final float x;
      private final float y;
      private final float z;
      private int red = 255;
      private int green = 255;
      private int blue = 255;
      private int alpha = 255;
      private float u;
      private float v;
      private int overlayU;
      private int overlayV;
      private int lightU;
      private int lightV;
      private float normalX;
      private float normalY;
      private float normalZ;

      PendingVertex(float x, float y, float z) {
         this.x = x;
         this.y = y;
         this.z = z;
      }

      CachedVertex toCachedVertex() {
         return new CachedVertex(this.x, this.y, this.z, this.red, this.green, this.blue, this.alpha, this.u, this.v, this.overlayU, this.overlayV, this.lightU, this.lightV, this.normalX, this.normalY, this.normalZ);
      }
   }

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

   private static final class OpenBlockPlaceContext extends BlockPlaceContext {
      OpenBlockPlaceContext(Level level, Player player, InteractionHand hand, ItemStack stack, BlockHitResult hit) {
         super(level, player, hand, stack, hit);
      }
   }

   private static class TintedMultiBufferSource implements MultiBufferSource {
      private final MultiBufferSource.BufferSource delegate;
      private final int r;
      private final int g;
      private final int b;
      private final int a;

      TintedMultiBufferSource(MultiBufferSource.BufferSource delegate, int r, int g, int b, int a) {
         this.delegate = delegate;
         this.r = r;
         this.g = g;
         this.b = b;
         this.a = a;
      }

      public VertexConsumer getBuffer(RenderType renderType) {
         RenderType renderTypeToUse = renderType.toString().contains("entity_cutout") ? RenderType.translucent() : renderType;
         return new TintedVertexConsumer(this.delegate.getBuffer(renderTypeToUse), this.r, this.g, this.b, this.a);
      }
   }

   private static class TintedVertexConsumer implements VertexConsumer {
      private final VertexConsumer delegate;
      private final int r;
      private final int g;
      private final int b;
      private final int a;

      TintedVertexConsumer(VertexConsumer delegate, int r, int g, int b, int a) {
         this.delegate = delegate;
         this.r = r;
         this.g = g;
         this.b = b;
         this.a = a;
      }

      public VertexConsumer addVertex(float x, float y, float z) {
         this.delegate.addVertex(x, y, z);
         return this;
      }

      public VertexConsumer setColor(int cr, int cg, int cb, int ca) {
         this.delegate.setColor(this.r, this.g, this.b, this.a);
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
