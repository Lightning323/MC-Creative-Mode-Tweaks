package nl.requios.effortlessbuilding.render.preview;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.model.data.ModelData;
import nl.requios.effortlessbuilding.render.preview.SectionMeshDraw.Section;
import org.joml.Matrix4f;

/**
 * Cached ghost-block mesh: the translucent preview cubes.
 *
 * <p><b>Threading split:</b> {@link #bake} tessellates into CPU-side
 * {@code MeshData} on the preview worker thread (never GL); {@link #adopt}
 * uploads those to GPU buffers on the render thread. Frames in between just
 * re-draw the uploaded sections at the new camera position.</p>
 *
 * <p><b>How it tessellates:</b> one pass per 16³ section through vanilla's
 * chunk baker ({@code renderBatched}). The {@link PreviewBlockView} below
 * reports the preview blocks with air everywhere else, so faces hidden
 * between two preview blocks are culled like real chunks. Coplanar quads are
 * <i>not</i> merged (no greedy meshing) — still one quad per visible face,
 * just uploaded once instead of re-emitted every frame.</p>
 *
 * <p>Animated block entities ({@code ENTITYBLOCK_ANIMATED}) are excluded here;
 * their models change per frame, so the cache hands them back for the
 * immediate path instead (see {@link PreviewRenderCache}).</p>
 */
public final class PreviewBlockMesh {
    private static final int SECTION_BUFFER_HINT = 262144;

    /** Ghost block + its resolved placement state. */
    public record PreviewBlock(BlockPos pos, BlockState state) {
    }

    private final List<Section> sections = new ArrayList<>();
    private Level level;

    /**
     * Tessellates ghost blocks into CPU-side meshes. Runs on the preview
     * worker thread — touches baked models and level reads (same class of
     * reads vanilla chunk builders do off-thread) but never GL.
     * {@code blocks} must already be the valid-only subset.
     *
     * @return per-section meshes the render thread uploads via {@link #adopt}.
     */
    public static List<SectionMeshDraw.PendingSection> bake(BlockRenderDispatcher dispatcher, Level level,
                                                            List<PreviewBlock> blocks, int alpha) {
        List<SectionMeshDraw.PendingSection> out = new ArrayList<>();
        if (blocks.isEmpty() || alpha == 0) {
            return out;
        }

        // Fast neighbor lookups for face culling: pos -> preview state.
        Long2ObjectOpenHashMap<BlockState> states = new Long2ObjectOpenHashMap<>(blocks.size());
        Map<Long, List<PreviewBlock>> bySection = new LinkedHashMap<>();
        for (PreviewBlock block : blocks) {
            states.put(block.pos().asLong(), block.state());
            bySection.computeIfAbsent(SectionPos.asLong(block.pos()), unused -> new ArrayList<>()).add(block);
        }

        PreviewBlockView view = new PreviewBlockView(level, states);
        RandomSource random = RandomSource.create();
        // ThreadLocal cache (vanilla uses the same pattern on its meshing
        // workers), so enable/clear stays paired on THIS thread.
        ModelBlockRenderer.enableCaching();
        try {
            for (Map.Entry<Long, List<PreviewBlock>> entry : bySection.entrySet()) {
                SectionMeshDraw.PendingSection section = buildSection(dispatcher, view, random, entry.getKey(), entry.getValue(), alpha);
                if (section != null) {
                    out.add(section);
                }
            }
        } catch (RuntimeException failure) {
            SectionMeshDraw.discardAllPending(out);
            throw failure;
        } finally {
            ModelBlockRenderer.clearCache();
        }
        return out;
    }

    /**
     * Uploads baked sections to GL. Must run on the render thread. Takes
     * ownership of every pending mesh: uploaded ones join the drawn sections,
     * and anything never attempted (after a mid-loop GL failure) is freed —
     * the failing section itself is freed inside upload().
     */
    public void adopt(Level level, List<SectionMeshDraw.PendingSection> pending) {
        SectionMeshDraw.closeAll(this.sections);
        this.level = level;
        int done = 0;
        try {
            for (; done < pending.size(); done++) {
                this.sections.add(SectionMeshDraw.upload(pending.get(done)));
            }
        } catch (RuntimeException failure) {
            for (int j = done + 1; j < pending.size(); j++) {
                pending.get(j).discard();
            }
            throw failure;
        }
    }

    /** Draws all cached sections. Pure GPU work — no tessellation here. */
    public void render(Level level, double camX, double camY, double camZ,
                       Matrix4f baseModelView, Matrix4f projectionMatrix) {
        if (this.sections.isEmpty() || this.level != level) {
            return;
        }
        SectionMeshDraw.drawAll(this.sections, level, camX, camY, camZ,
                baseModelView, projectionMatrix, RenderType.translucent());
    }

    /** Drops all GPU buffers (level unload, mode disable, resource reload). */
    public void clear() {
        SectionMeshDraw.closeAll(this.sections);
        this.level = null;
    }

    public boolean isEmpty() {
        return this.sections.isEmpty();
    }

    // Bakes one 16³ section into CPU-side vertex data (no GL — safe on the
    // worker). Vertices are section-local; the origin goes back on at draw.
    // The backing store travels WITH the mesh (see PendingSection): closing
    // it here would invalidate the mesh before upload (crash), never closing
    // it would leak native memory until OOM (also crash).
    private static SectionMeshDraw.PendingSection buildSection(BlockRenderDispatcher dispatcher, PreviewBlockView view,
                                                              RandomSource random, long sectionKey,
                                                              List<PreviewBlock> blocks, int alpha) {
        ByteBufferBuilder backing = new ByteBufferBuilder(SECTION_BUFFER_HINT);
        boolean transferred = false;
        try {
            BufferBuilder builder = new BufferBuilder(backing, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            VertexConsumer consumer = new AlphaFullBrightVertexConsumer(builder, alpha);
            PoseStack sectionPose = new PoseStack();

            for (PreviewBlock block : blocks) {
                BlockPos pos = block.pos();
                BlockState state = block.state();
                FluidState fluid = state.getFluidState();
                if (!fluid.isEmpty()) {
                    dispatcher.renderLiquid(pos, view, consumer, state, fluid);
                }
                if (state.getRenderShape() == RenderShape.MODEL) {
                    BakedModel model = dispatcher.getBlockModel(state);
                    ModelData data = model.getModelData(view, pos, state, ModelData.EMPTY);
                    random.setSeed(state.getSeed(pos));
                    sectionPose.pushPose();
                    sectionPose.translate(SectionPos.sectionRelative(pos.getX()),
                            SectionPos.sectionRelative(pos.getY()),
                            SectionPos.sectionRelative(pos.getZ()));
                    try {
                        for (RenderType layer : model.getRenderTypes(state, random, data)) {
                            dispatcher.renderBatched(state, pos, view, sectionPose, consumer, true, random, data, layer);
                        }
                    } finally {
                        sectionPose.popPose();
                    }
                }
            }

            MeshData mesh = builder.build();
            if (mesh == null) {
                return null;
            }
            // Ownership of mesh AND backing moves to the caller: the render
            // thread uploads (then frees the backing) or discards both.
            transferred = true;
            BlockPos origin = new BlockPos(SectionPos.sectionToBlockCoord(SectionPos.x(sectionKey)),
                    SectionPos.sectionToBlockCoord(SectionPos.y(sectionKey)),
                    SectionPos.sectionToBlockCoord(SectionPos.z(sectionKey)));
            return new SectionMeshDraw.PendingSection(origin, mesh, backing);
        } finally {
            if (!transferred) {
                backing.close();
            }
        }
    }

    /**
     * Fake world for the chunk baker: preview blocks where selected, air
     * everywhere else. That air is what lets vanilla face-culling drop the
     * hidden faces between adjacent preview blocks.
     */
    private static final class PreviewBlockView implements BlockAndTintGetter {
        private final Level delegate;
        private final Long2ObjectOpenHashMap<BlockState> previewStates;

        private PreviewBlockView(Level delegate, Long2ObjectOpenHashMap<BlockState> previewStates) {
            this.delegate = delegate;
            this.previewStates = previewStates;
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            BlockState state = this.previewStates.get(pos.asLong());
            return state != null ? state : Blocks.AIR.defaultBlockState();
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public int getHeight() {
            return this.delegate.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return this.delegate.getMinBuildHeight();
        }

        @Override
        public float getShade(Direction direction, boolean shade) {
            return this.delegate.getShade(direction, shade);
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return this.delegate.getLightEngine();
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver resolver) {
            return this.delegate.getBlockTint(pos, resolver);
        }
    }

    /**
     * Forces the ghost look onto chunk-format vertices: configured opacity
     * instead of the model's alpha, full-bright light so previews glow in
     * the dark like before.
     */
    private static final class AlphaFullBrightVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final int alpha;

        private AlphaFullBrightVertexConsumer(VertexConsumer delegate, int alpha) {
            this.delegate = delegate;
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int ignoredAlpha) {
            this.delegate.setColor(red, green, blue, this.alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int ignoredU, int ignoredV) {
            this.delegate.setUv2(LightTexture.FULL_BRIGHT & 65535, LightTexture.FULL_BRIGHT >>> 16);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            this.delegate.setNormal(x, y, z);
            return this;
        }
    }
}
