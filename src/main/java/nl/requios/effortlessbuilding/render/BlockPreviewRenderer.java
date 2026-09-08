package nl.requios.effortlessbuilding.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.BuildModes;
import nl.requios.effortlessbuilding.buildmode.BuildSettings;
import nl.requios.effortlessbuilding.buildmode.buildmodes.Mesh;
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
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;

public class BlockPreviewRenderer {
   private static final ResourceLocation CHECKERBOARD_TEXTURE = ResourceLocation.fromNamespaceAndPath("creative_mode_tweaks", "textures/special/checkerboard.png");
   private static final ResourceLocation SELECTION_TEXTURE = ResourceLocation.fromNamespaceAndPath("creative_mode_tweaks", "textures/special/selection.png");
   private static final RenderType MESH_VERTEX_RENDER_TYPE = RenderType.eyes(SELECTION_TEXTURE);
   private static final ResourceLocation OUTLINE_TEXTURE = ResourceLocation.fromNamespaceAndPath("creative_mode_tweaks", "textures/special/blank.png");
   private static final PreviewBlockMesh PREVIEW_BLOCK_MESH = new PreviewBlockMesh();

    public static void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, double camX, double camY, double camZ, Matrix4f modelViewMatrix, Matrix4f projectionMatrix) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null) {
         boolean emptyHand = !BuildPipeline.isBuildTriggerItem(mc.player.getMainHandItem());
         boolean sequenceActive = BuildPipelineClient.getBuildState() != null;
         boolean modeActive = BuildModes.CLIENT.getBuildMode() != BuildModeEnum.DISABLED;
         BlockSet blockSet = BuildPipelineClient.getPreviewBlocks(mc);

         if (blockSet != null && !blockSet.isEmpty()) {
            if (blockSet.size() <= 1 && !sequenceActive) {
               PREVIEW_BLOCK_MESH.clear();
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
               boolean renderPlacementBlocks = !isBreaking
                       && BuildSettings.CLIENT.getReplaceMode() != BuildSettings.ReplaceMode.ONLY_BLOCKS;
               if (renderPlacementBlocks) {

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
                        Map<Item, BlockState> randomStates = new HashMap();
                        List<PreviewBlock> previewBlocks = new ArrayList<>(positions.size());
                        List<PreviewBlock> animatedBlocks = new ArrayList<>();

                        for(BlockPos pos : positions) {
                           BlockState state = baseState;
                           BlockEntry entry = (BlockEntry)blockSet.get(pos);
                           if (randomized && entry != null) {
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

                              PreviewBlock previewBlock = new PreviewBlock(pos, state);
                              previewBlocks.add(previewBlock);
                              if (state.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED) {
                                 animatedBlocks.add(previewBlock);
                              }
                           }
                        }

                        PREVIEW_BLOCK_MESH.update(mc, mc.level, previewBlocks, blockAlpha);
                        PREVIEW_BLOCK_MESH.render(mc.level, camX, camY, camZ, modelViewMatrix, projectionMatrix);

                        for (PreviewBlock previewBlock : animatedBlocks) {
                           poseStack.pushPose();
                           try {
                              SableCompat.translateToBlock(poseStack, mc.level, previewBlock.pos(), camX, camY, camZ);
                              mc.getBlockRenderer().renderSingleBlock(previewBlock.state(), poseStack, wrappedSource, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                           } finally {
                              poseStack.popPose();
                           }
                        }
                        if (!animatedBlocks.isEmpty()) {
                           bufferSource.endBatch();
                        }
                     } catch (Exception var35) {
                     }
                  } else {
                     PREVIEW_BLOCK_MESH.clear();
                  }
               } else {
                  PREVIEW_BLOCK_MESH.clear();
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

               if (!breakablePositions.isEmpty()) {
                  renderOutlineAround(poseStack, bufferSource, mc.level, computeBorderEdges(breakablePositions), camX, camY, camZ, outlineWidth, oR, oG, oB, 255);
                  bufferSource.endBatch(RenderType.entityTranslucent(OUTLINE_TEXTURE));
               }
               if (!unbreakablePositions.isEmpty()) {
                  renderOutlineAround(poseStack, bufferSource, mc.level, computeBorderEdges(unbreakablePositions), camX, camY, camZ, outlineWidth, 100, 100, 100, 255);
                  bufferSource.endBatch(RenderType.entityTranslucent(OUTLINE_TEXTURE));
               }

            }
         } else {
            PREVIEW_BLOCK_MESH.clear();
            RenderHandler.resetPreviewSize();
         }

         renderSelectionMarkers(poseStack, bufferSource, mc.level, camX, camY, camZ);
      } else {
         PREVIEW_BLOCK_MESH.clear();
      }
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

      RenderSystem.depthMask(false);
      VertexConsumer consumer = bufferSource.getBuffer(MESH_VERTEX_RENDER_TYPE);

      for (BlockPos vertex : vertices) {
         boolean selected = mesh.isVertexSelected(vertex);
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

   /** Called after models are rebaked so preview vertex buffers never retain stale geometry. */
   public static void clearPreviewMesh() {
      PREVIEW_BLOCK_MESH.clear();
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

   private static void renderOutlineAround(PoseStack poseStack, MultiBufferSource bufferSource, Level level, Set<EdgeKey> edges, double camX, double camY, double camZ, float halfWidth, int r, int g, int b, int a) {
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

   private static record PreviewBlock(BlockPos pos, BlockState state) {
   }

   /**
    * A chunk-style preview mesh. It is rebuilt only when the selected positions, resolved states,
    * or preview opacity change; frames in between issue only static vertex-buffer draws.
    */
   private static final class PreviewBlockMesh {
      private static final int INITIAL_SECTION_BUFFER_SIZE = 262144;
      private final List<SectionMesh> sections = new ArrayList<>();
      private Level level;
      private long fingerprint = Long.MIN_VALUE;
      private int blockCount;

      void update(Minecraft mc, Level level, List<PreviewBlock> blocks, int alpha) {
         long fingerprint = this.fingerprint(blocks, alpha);
         if (this.level == level && this.fingerprint == fingerprint && this.blockCount == blocks.size()) {
            return;
         }

         this.clearBuffers();
         this.level = level;
         this.fingerprint = fingerprint;
         this.blockCount = blocks.size();
         if (blocks.isEmpty() || alpha == 0) {
            return;
         }

         Long2ObjectOpenHashMap<BlockState> states = new Long2ObjectOpenHashMap<>(blocks.size());
         Map<Long, List<PreviewBlock>> blocksBySection = new LinkedHashMap<>();
         for (PreviewBlock block : blocks) {
            states.put(block.pos().asLong(), block.state());
            blocksBySection.computeIfAbsent(SectionPos.asLong(block.pos()), unused -> new ArrayList<>()).add(block);
         }

         PreviewBlockView previewLevel = new PreviewBlockView(level, states);
         BlockRenderDispatcher blockRenderer = mc.getBlockRenderer();
         RandomSource random = RandomSource.create();
         ModelBlockRenderer.enableCaching();

         try {
            for (Map.Entry<Long, List<PreviewBlock>> entry : blocksBySection.entrySet()) {
               SectionMesh section = this.buildSection(blockRenderer, previewLevel, random, entry.getKey(), entry.getValue(), alpha);
               if (section != null) {
                  this.sections.add(section);
               }
            }
         } catch (RuntimeException exception) {
            this.clear();
            throw exception;
         } finally {
            ModelBlockRenderer.clearCache();
         }
      }

      private SectionMesh buildSection(BlockRenderDispatcher blockRenderer, PreviewBlockView previewLevel, RandomSource random,
                                       long sectionKey, List<PreviewBlock> blocks, int alpha) {
         try (ByteBufferBuilder backingBuffer = new ByteBufferBuilder(INITIAL_SECTION_BUFFER_SIZE)) {
            BufferBuilder builder = new BufferBuilder(backingBuffer, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            VertexConsumer consumer = new AlphaFullBrightVertexConsumer(builder, alpha);
            PoseStack sectionPose = new PoseStack();

            for (PreviewBlock block : blocks) {
               BlockPos pos = block.pos();
               BlockState state = block.state();
               FluidState fluidState = state.getFluidState();
               if (!fluidState.isEmpty()) {
                  blockRenderer.renderLiquid(pos, previewLevel, consumer, state, fluidState);
               }

               if (state.getRenderShape() == RenderShape.MODEL) {
                  BakedModel model = blockRenderer.getBlockModel(state);
                  ModelData modelData = model.getModelData(previewLevel, pos, state, ModelData.EMPTY);
                  random.setSeed(state.getSeed(pos));
                  sectionPose.pushPose();
                  sectionPose.translate(SectionPos.sectionRelative(pos.getX()), SectionPos.sectionRelative(pos.getY()), SectionPos.sectionRelative(pos.getZ()));
                  for (RenderType renderType : model.getRenderTypes(state, random, modelData)) {
                     blockRenderer.renderBatched(state, pos, previewLevel, sectionPose, consumer, true, random, modelData, renderType);
                  }
                  sectionPose.popPose();
               }
            }

            MeshData mesh = builder.build();
            if (mesh == null) {
               return null;
            }

            VertexBuffer vertexBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vertexBuffer.bind();
            try {
               vertexBuffer.upload(mesh);
            } catch (RuntimeException exception) {
               vertexBuffer.close();
               throw exception;
            } finally {
               VertexBuffer.unbind();
            }

            BlockPos origin = new BlockPos(SectionPos.sectionToBlockCoord(SectionPos.x(sectionKey)), SectionPos.sectionToBlockCoord(SectionPos.y(sectionKey)), SectionPos.sectionToBlockCoord(SectionPos.z(sectionKey)));
            return new SectionMesh(origin, vertexBuffer);
         }
      }

      void render(Level level, double camX, double camY, double camZ, Matrix4f baseModelView, Matrix4f projectionMatrix) {
         if (this.sections.isEmpty() || this.level != level) {
            return;
         }

         RenderType renderType = RenderType.translucent();
         renderType.setupRenderState();
         ShaderInstance shader = RenderSystem.getShader();
         try {
            // Chunk shaders add CHUNK_OFFSET to every vertex. We bake the section
            // origin (and any Sable sublevel transform) into the model-view instead,
            // so make sure no stale offset leaks in from vanilla chunk rendering.
            if (shader != null && shader.CHUNK_OFFSET != null) {
               shader.CHUNK_OFFSET.set(0.0F, 0.0F, 0.0F);
               shader.CHUNK_OFFSET.upload();
            }
            PoseStack sectionPose = new PoseStack();
            for (SectionMesh section : this.sections) {
               sectionPose.pushPose();
               try {
                  // Reuse the same world/sublevel logic as the immediate-mode overlays:
                  // vanilla -> translate(origin - cam), sublevel -> globalOrigin - cam + rotation + scale.
                  SableCompat.translateToBlock(sectionPose, level, section.origin(), camX, camY, camZ);
                  // Event model-view carries the camera rotation; the pose stack carries
                  // the camera-relative translation (plus sublevel rotation/scale).
                  Matrix4f sectionModelView = new Matrix4f(baseModelView).mul(sectionPose.last().pose());
                  section.buffer().bind();
                  section.buffer().drawWithShader(sectionModelView, projectionMatrix, shader);
               } finally {
                  sectionPose.popPose();
               }
            }
         } finally {
            VertexBuffer.unbind();
            renderType.clearRenderState();
         }
      }

      void clear() {
         this.clearBuffers();
         this.level = null;
         this.fingerprint = Long.MIN_VALUE;
         this.blockCount = 0;
      }

      private void clearBuffers() {
         for (SectionMesh section : this.sections) {
            section.buffer().close();
         }
         this.sections.clear();
      }

      private long fingerprint(List<PreviewBlock> blocks, int alpha) {
         long sum = 0L;
         long xor = 0L;
         for (PreviewBlock block : blocks) {
            long entryHash = block.pos().asLong() ^ (long)block.state().hashCode() * -7046029254386353131L;
            entryHash ^= entryHash >>> 33;
            entryHash *= -49064778989728563L;
            entryHash ^= entryHash >>> 33;
            sum += entryHash;
            xor ^= Long.rotateLeft(entryHash, (int)entryHash & 63);
         }
         long hash = 7640891576956012809L ^ (long)alpha << 32 ^ blocks.size();
         hash ^= sum;
         hash = Long.rotateLeft(hash, 27) * -4658895280553007687L;
         return hash ^ xor;
      }
   }

   private static record SectionMesh(BlockPos origin, VertexBuffer buffer) {
   }

   /** Presents just the selected blocks to vanilla's chunk tessellator, with air around them. */
   private static final class PreviewBlockView implements BlockAndTintGetter {
      private final Level delegate;
      private final Long2ObjectOpenHashMap<BlockState> previewStates;

      private PreviewBlockView(Level delegate, Long2ObjectOpenHashMap<BlockState> previewStates) {
         this.delegate = delegate;
         this.previewStates = previewStates;
      }

      public BlockEntity getBlockEntity(BlockPos pos) {
         return null;
      }

      public BlockState getBlockState(BlockPos pos) {
         BlockState state = this.previewStates.get(pos.asLong());
         return state != null ? state : Blocks.AIR.defaultBlockState();
      }

      public FluidState getFluidState(BlockPos pos) {
         return this.getBlockState(pos).getFluidState();
      }

      public int getHeight() {
         return this.delegate.getHeight();
      }

      public int getMinBuildHeight() {
         return this.delegate.getMinBuildHeight();
      }

      public float getShade(Direction direction, boolean shade) {
         return this.delegate.getShade(direction, shade);
      }

      public LevelLightEngine getLightEngine() {
         return this.delegate.getLightEngine();
      }

      public int getBlockTint(BlockPos pos, ColorResolver resolver) {
         return this.delegate.getBlockTint(pos, resolver);
      }
   }

   /** Preserves the existing ghost-preview alpha and full-bright lighting in chunk-format vertices. */
   private static final class AlphaFullBrightVertexConsumer implements VertexConsumer {
      private final VertexConsumer delegate;
      private final int alpha;

      private AlphaFullBrightVertexConsumer(VertexConsumer delegate, int alpha) {
         this.delegate = delegate;
         this.alpha = alpha;
      }

      public VertexConsumer addVertex(float x, float y, float z) {
         this.delegate.addVertex(x, y, z);
         return this;
      }

      public VertexConsumer setColor(int red, int green, int blue, int ignoredAlpha) {
         this.delegate.setColor(red, green, blue, this.alpha);
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

      public VertexConsumer setUv2(int ignoredU, int ignoredV) {
         this.delegate.setUv2(LightTexture.FULL_BRIGHT & 65535, LightTexture.FULL_BRIGHT >>> 16);
         return this;
      }

      public VertexConsumer setNormal(float x, float y, float z) {
         this.delegate.setNormal(x, y, z);
         return this;
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
