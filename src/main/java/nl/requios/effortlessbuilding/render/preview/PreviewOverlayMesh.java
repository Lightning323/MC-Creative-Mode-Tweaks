package nl.requios.effortlessbuilding.render.preview;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import nl.requios.effortlessbuilding.render.preview.SectionMeshDraw.Section;
import org.joml.Matrix4f;

/**
 * Cached overlay meshes: the tinted fill boxes plus the border outline.
 *
 * <p><b>Why two meshes:</b> the fill (checkerboard texture, per-face quads)
 * and the outline (thin edge strips on a blank texture) need different
 * {@link RenderType}s, so they live in separate section buffers and are drawn
 * in two passes — same as before, just uploaded once instead of re-emitted
 * every frame.</p>
 *
 * <p><b>When it rebuilds:</b> only from {@link PreviewRenderCache} when the
 * shape key changes. That moves three per-frame costs off the hot path:</p>
 * <ul>
 *   <li>6 neighbor lookups per block for fill culling (packed-long set,
 *       no per-block allocations),</li>
 *   <li>the 12 edge toggles per block into primitive long sets (no edge-key
 *       objects),</li>
 *   <li>the per-block {@code pushPose/translate} and immediate-mode quad
 *       emission (up to ~6 quads x 50k blocks per frame before).</li>
 * </ul>
 *
 * <p>Shapes past the detailed limit skip this entirely via
 * {@link #bakeBoundingBox}: one 6-quad fill plus 12 edge strips, constant
 * cost regardless of block count.</p>
 *
 * <p>Colors are baked into the vertices (white for placeable, red/grey for
 * rejected, red tint when breaking), so a breaking↔placing switch is part of
 * the shape key and rebakes — no per-frame color patching.</p>
 */
public final class PreviewOverlayMesh {
    private static final int SECTION_BUFFER_HINT = 65536;
    private static final float FILL_EPS = 0.002F;
    private static final float FILL_GROW = 0.004F;
    private static final float OUTLINE_HALF_WIDTH = 0.02F;

    private final ResourceLocation fillTexture;
    private final ResourceLocation outlineTexture;

    private final List<Section> fillSections = new ArrayList<>();
    private final List<Section> outlineSections = new ArrayList<>();
    private Level level;

    public PreviewOverlayMesh(ResourceLocation fillTexture, ResourceLocation outlineTexture) {
        this.fillTexture = fillTexture;
        this.outlineTexture = outlineTexture;
    }

    /** Worker-side result: baked fill + outline, still CPU-side (no GL). */
    public record Baked(List<SectionMeshDraw.PendingSection> fill,
                        List<SectionMeshDraw.PendingSection> outline) {
    }

    /**
     * Bakes fill + outline into CPU-side meshes. Runs on the preview worker
     * thread — pure math on the position lists, no level reads, no GL.
     * {@code breakable} draws white (red when breaking), {@code unbreakable}
     * draws red fill + grey outline.
     *
     * <p>Allocation discipline (hot path for multi-thousand-block shapes):
     * neighbor checks use packed {@code long} positions — no
     * {@code below()/above()/…} temporaries — and border edges toggle in
     * primitive {@code long} sets, so a bake allocates O(sections) buffers
     * plus the output meshes and nothing per block.</p>
     */
    public static Baked bake(List<BlockPos> breakable, List<BlockPos> unbreakable, boolean isBreaking) {
        List<SectionMeshDraw.PendingSection> fill = new ArrayList<>();
        List<SectionMeshDraw.PendingSection> outline = new ArrayList<>();
        if (breakable.isEmpty() && unbreakable.isEmpty()) {
            return new Baked(fill, outline);
        }

        // Fill colors match the old immediate path.
        int fillR = 255, fillG = isBreaking ? 0 : 255, fillB = isBreaking ? 0 : 255, fillA = 150;
        Map<Long, BufferBuilder> fillBuilders = new LinkedHashMap<>();
        Map<Long, ByteBufferBuilder> fillBacking = new LinkedHashMap<>();
        try {
            // Each list culls against itself only — same as the old per-list
            // box calls, so shared faces between the two lists still draw
            // (preserves old look, avoids cross-list logic).
            emitFill(fillBuilders, fillBacking, breakable, fillR, fillG, fillB, fillA);
            emitFill(fillBuilders, fillBacking, unbreakable, 255, 80, 80, 100);
            collectAll(fillBuilders, fillBacking, fill);
        } catch (RuntimeException failure) {
            // Bake failed partway: free everything built so far (both closes
            // are idempotent, so transferred + untransferred mix safely).
            SectionMeshDraw.discardAllPending(fill);
            closeAllBacking(fillBacking);
            throw failure;
        }

        // Outline: border edges only (interior edges cancel in pairs), one
        // thin quad per surviving edge. Same toggle math as before.
        int lineR = 255, lineG = isBreaking ? 0 : 255, lineB = isBreaking ? 0 : 255;
        Map<Long, BufferBuilder> lineBuilders = new LinkedHashMap<>();
        Map<Long, ByteBufferBuilder> lineBacking = new LinkedHashMap<>();
        try {
            emitOutlineList(lineBuilders, lineBacking, breakable, lineR, lineG, lineB, 255);
            emitOutlineList(lineBuilders, lineBacking, unbreakable, 100, 100, 100, 255);
            collectAll(lineBuilders, lineBacking, outline);
        } catch (RuntimeException failure) {
            SectionMeshDraw.discardAllPending(outline);
            closeAllBacking(lineBacking);
            throw failure;
        }
        return new Baked(fill, outline);
    }

    /**
     * Ultra-cheap overlay for huge shapes: one box around {@code [min, max]}
     * (inclusive blocks) instead of per-block faces and border edges.
     * Constant cost no matter how many blocks are inside — this is what keeps
     * multi-thousand-block drags interactive.
     *
     * <p>Vertices are relative to {@code min} (a real shape block, so
     * Sable/contraption translation still resolves correctly) rather than
     * section-local; a single offset that small keeps float precision exact.</p>
     */
    public static Baked bakeBoundingBox(BlockPos min, BlockPos max, boolean isBreaking) {
        List<SectionMeshDraw.PendingSection> fill = new ArrayList<>(1);
        List<SectionMeshDraw.PendingSection> outline = new ArrayList<>(1);
        int fillR = 255, fillG = isBreaking ? 0 : 255, fillB = isBreaking ? 0 : 255;
        int lineR = 255, lineG = isBreaking ? 0 : 255, lineB = isBreaking ? 0 : 255;

        float sx = (float) (max.getX() - min.getX() + 1);
        float sy = (float) (max.getY() - min.getY() + 1);
        float sz = (float) (max.getZ() - min.getZ() + 1);

        ByteBufferBuilder fillStore = new ByteBufferBuilder(4096);
        BufferBuilder fillBuilder = new BufferBuilder(fillStore, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        float x0 = -FILL_EPS;
        float x1 = sx + FILL_EPS + FILL_GROW;
        float y0 = -FILL_EPS;
        float y1 = sy + FILL_EPS + FILL_GROW;
        float z0 = -FILL_EPS;
        float z1 = sz + FILL_EPS + FILL_GROW;
        addQuad(fillBuilder, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, fillR, fillG, fillB, 150);
        addQuad(fillBuilder, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0, fillR, fillG, fillB, 150);
        addQuad(fillBuilder, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, 0, 0, -1, fillR, fillG, fillB, 150);
        addQuad(fillBuilder, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, 0, 0, 1, fillR, fillG, fillB, 150);
        addQuad(fillBuilder, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, -1, 0, 0, fillR, fillG, fillB, 150);
        addQuad(fillBuilder, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, 1, 0, 0, fillR, fillG, fillB, 150);
        MeshData fillMesh = fillBuilder.build();
        if (fillMesh != null) {
            fill.add(new SectionMeshDraw.PendingSection(min.immutable(), fillMesh, fillStore));
        } else {
            fillStore.close();
        }

        ByteBufferBuilder lineStore = new ByteBufferBuilder(8192);
        BufferBuilder lineBuilder = new BufferBuilder(lineStore, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        // 4 edges along each axis.
        addBoxEdge(lineBuilder, 0, 0, 0, 0, sx, lineR, lineG, lineB, 255);
        addBoxEdge(lineBuilder, 0, sy, 0, 0, sx, lineR, lineG, lineB, 255);
        addBoxEdge(lineBuilder, 0, 0, sz, 0, sx, lineR, lineG, lineB, 255);
        addBoxEdge(lineBuilder, 0, sy, sz, 0, sx, lineR, lineG, lineB, 255);
        addBoxEdge(lineBuilder, 0, 0, 0, 1, sy, lineR, lineG, lineB, 255);
        addBoxEdge(lineBuilder, sx, 0, 0, 1, sy, lineR, lineG, lineB, 255);
        addBoxEdge(lineBuilder, 0, 0, sz, 1, sy, lineR, lineG, lineB, 255);
        addBoxEdge(lineBuilder, sx, 0, sz, 1, sy, lineR, lineG, lineB, 255);
        addBoxEdge(lineBuilder, 0, 0, 0, 2, sz, lineR, lineG, lineB, 255);
        addBoxEdge(lineBuilder, sx, 0, 0, 2, sz, lineR, lineG, lineB, 255);
        addBoxEdge(lineBuilder, 0, sy, 0, 2, sz, lineR, lineG, lineB, 255);
        addBoxEdge(lineBuilder, sx, sy, 0, 2, sz, lineR, lineG, lineB, 255);
        MeshData lineMesh = lineBuilder.build();
        if (lineMesh != null) {
            outline.add(new SectionMeshDraw.PendingSection(min.immutable(), lineMesh, lineStore));
        } else {
            lineStore.close();
        }
        return new Baked(fill, outline);
    }

    /**
     * Uploads a baked result to GL. Must run on the render thread. Takes
     * ownership of every pending mesh; on mid-loop GL failure the failing
     * section frees itself inside upload() and the never-attempted tail is
     * freed here, so no path leaks native memory.
     */
    public void adopt(Level level, Baked baked) {
        SectionMeshDraw.closeAll(this.fillSections);
        SectionMeshDraw.closeAll(this.outlineSections);
        this.level = level;
        adoptAll(baked.fill(), this.fillSections);
        adoptAll(baked.outline(), this.outlineSections);
    }

    private void adoptAll(List<SectionMeshDraw.PendingSection> pending, List<Section> live) {
        int done = 0;
        try {
            for (; done < pending.size(); done++) {
                live.add(SectionMeshDraw.upload(pending.get(done)));
            }
        } catch (RuntimeException failure) {
            for (int j = done + 1; j < pending.size(); j++) {
                pending.get(j).discard();
            }
            throw failure;
        }
    }

    /** Draws the tinted fill boxes (checkerboard texture). */
    public void renderFill(Level level, double camX, double camY, double camZ,
                           Matrix4f baseModelView, Matrix4f projectionMatrix) {
        if (this.fillSections.isEmpty() || this.level != level) {
            return;
        }
        SectionMeshDraw.drawAll(this.fillSections, level, camX, camY, camZ,
                baseModelView, projectionMatrix, RenderType.entityTranslucentCull(this.fillTexture));
    }

    /** Draws the border outline (blank texture, thin strips). */
    public void renderOutline(Level level, double camX, double camY, double camZ,
                              Matrix4f baseModelView, Matrix4f projectionMatrix) {
        if (this.outlineSections.isEmpty() || this.level != level) {
            return;
        }
        SectionMeshDraw.drawAll(this.outlineSections, level, camX, camY, camZ,
                baseModelView, projectionMatrix, RenderType.entityTranslucent(this.outlineTexture));
    }

    public void clear() {
        SectionMeshDraw.closeAll(this.fillSections);
        SectionMeshDraw.closeAll(this.outlineSections);
        this.level = null;
    }

    public boolean isEmpty() {
        return this.fillSections.isEmpty() && this.outlineSections.isEmpty();
    }

    // -- fill baking -------------------------------------------------------

    // Emits one slightly-expanded box per block, skipping faces hidden by a
    // neighbor in the same list. Vertices are section-local; the origin goes
    // back on at draw time. Membership is tracked as packed longs: neighbor
    // checks are integer math, zero BlockPos temporaries, zero map nodes.
    private static void emitFill(Map<Long, BufferBuilder> builders, Map<Long, ByteBufferBuilder> backing,
                                 List<BlockPos> positions,
                                 int r, int g, int b, int a) {
        LongOpenHashSet self = new LongOpenHashSet(Math.max(16, positions.size() * 2));
        for (int i = 0, n = positions.size(); i < n; i++) {
            self.add(positions.get(i).asLong());
        }
        for (int i = 0, n = positions.size(); i < n; i++) {
            BlockPos pos = positions.get(i);
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            BufferBuilder builder = builderFor(builders, backing, SectionPos.asLong(pos));
            float x0 = -FILL_EPS;
            float x1 = x0 + 1.0F + FILL_GROW;
            float y0 = -FILL_EPS;
            float y1 = y0 + 1.0F + FILL_GROW;
            float z0 = -FILL_EPS;
            float z1 = z0 + 1.0F + FILL_GROW;
            // Local offset inside the 16³ section (origin re-applied on draw).
            float ox = SectionPos.sectionRelative(x);
            float oy = SectionPos.sectionRelative(y);
            float oz = SectionPos.sectionRelative(z);
            if (!self.contains(BlockPos.asLong(x, y - 1, z))) {
                addQuad(builder, ox + x0, oy + y0, oz + z0, ox + x1, oy + y0, oz + z0,
                        ox + x1, oy + y0, oz + z1, ox + x0, oy + y0, oz + z1,
                        0, -1, 0, r, g, b, a);
            }
            if (!self.contains(BlockPos.asLong(x, y + 1, z))) {
                addQuad(builder, ox + x0, oy + y1, oz + z0, ox + x0, oy + y1, oz + z1,
                        ox + x1, oy + y1, oz + z1, ox + x1, oy + y1, oz + z0,
                        0, 1, 0, r, g, b, a);
            }
            if (!self.contains(BlockPos.asLong(x, y, z - 1))) {
                addQuad(builder, ox + x0, oy + y0, oz + z0, ox + x0, oy + y1, oz + z0,
                        ox + x1, oy + y1, oz + z0, ox + x1, oy + y0, oz + z0,
                        0, 0, -1, r, g, b, a);
            }
            if (!self.contains(BlockPos.asLong(x, y, z + 1))) {
                addQuad(builder, ox + x1, oy + y0, oz + z1, ox + x1, oy + y1, oz + z1,
                        ox + x0, oy + y1, oz + z1, ox + x0, oy + y0, oz + z1,
                        0, 0, 1, r, g, b, a);
            }
            if (!self.contains(BlockPos.asLong(x - 1, y, z))) {
                addQuad(builder, ox + x0, oy + y0, oz + z1, ox + x0, oy + y1, oz + z1,
                        ox + x0, oy + y1, oz + z0, ox + x0, oy + y0, oz + z0,
                        -1, 0, 0, r, g, b, a);
            }
            if (!self.contains(BlockPos.asLong(x + 1, y, z))) {
                addQuad(builder, ox + x1, oy + y0, oz + z0, ox + x1, oy + y1, oz + z0,
                        ox + x1, oy + y1, oz + z1, ox + x1, oy + y0, oz + z1,
                        1, 0, 0, r, g, b, a);
            }
        }
    }

    // -- outline baking ----------------------------------------------------

    // Border edges for one position list, straight into section builders.
    // Interior edges cancel in pairs across the three per-axis packed sets,
    // leaving exactly the outer border — same toggle math as before, but one
    // thin quad per surviving edge and no EdgeKey objects at all.
    private static void emitOutlineList(Map<Long, BufferBuilder> builders, Map<Long, ByteBufferBuilder> backing,
                                        List<BlockPos> positions, int r, int g, int b, int a) {
        if (positions.isEmpty()) {
            return;
        }
        int cap = Math.max(64, positions.size() * 2);
        LongOpenHashSet xEdges = new LongOpenHashSet(cap);
        LongOpenHashSet yEdges = new LongOpenHashSet(cap);
        LongOpenHashSet zEdges = new LongOpenHashSet(cap);
        for (int i = 0, n = positions.size(); i < n; i++) {
            BlockPos pos = positions.get(i);
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            toggleEdge(xEdges, BlockPos.asLong(x, y, z));
            toggleEdge(xEdges, BlockPos.asLong(x, y + 1, z));
            toggleEdge(xEdges, BlockPos.asLong(x, y, z + 1));
            toggleEdge(xEdges, BlockPos.asLong(x, y + 1, z + 1));
            toggleEdge(yEdges, BlockPos.asLong(x, y, z));
            toggleEdge(yEdges, BlockPos.asLong(x + 1, y, z));
            toggleEdge(yEdges, BlockPos.asLong(x, y, z + 1));
            toggleEdge(yEdges, BlockPos.asLong(x + 1, y, z + 1));
            toggleEdge(zEdges, BlockPos.asLong(x, y, z));
            toggleEdge(zEdges, BlockPos.asLong(x + 1, y, z));
            toggleEdge(zEdges, BlockPos.asLong(x, y + 1, z));
            toggleEdge(zEdges, BlockPos.asLong(x + 1, y + 1, z));
        }
        emitAxisEdges(builders, backing, xEdges, 0, r, g, b, a);
        emitAxisEdges(builders, backing, yEdges, 1, r, g, b, a);
        emitAxisEdges(builders, backing, zEdges, 2, r, g, b, a);
    }

    private static void toggleEdge(LongOpenHashSet edges, long packed) {
        if (!edges.remove(packed)) {
            edges.add(packed);
        }
    }

    private static void emitAxisEdges(Map<Long, BufferBuilder> builders, Map<Long, ByteBufferBuilder> backing,
                                      LongOpenHashSet edges, int axis, int r, int g, int b, int a) {
        // for-each would box every packed pos; the primitive iterator does not.
        LongIterator it = edges.iterator();
        while (it.hasNext()) {
            long packed = it.nextLong();
            // Section of the edge's base block; strips never cross sections
            // because each edge spans exactly one block along its axis... except
            // edges on section borders. Those still bake fine: the strip is
            // stored in the base block's section and drawn with that section's
            // origin, so it lands in the right world spot either way.
            int x = BlockPos.getX(packed);
            int y = BlockPos.getY(packed);
            int z = BlockPos.getZ(packed);
            BufferBuilder builder = builderFor(builders, backing, SectionPos.asLong(
                    SectionPos.blockToSectionCoord(x),
                    SectionPos.blockToSectionCoord(y),
                    SectionPos.blockToSectionCoord(z)));
            float ox = SectionPos.sectionRelative(x);
            float oy = SectionPos.sectionRelative(y);
            float oz = SectionPos.sectionRelative(z);
            addEdgeStrip(builder, ox, oy, oz, axis, 1.0F, r, g, b, a);
        }
    }

    // One thin outline strip starting at (x0, y0, z0), running {@code len}
    // along {@code axis}. Identical look to the per-block border quads.
    private static void addEdgeStrip(VertexConsumer consumer,
                                     float x0, float y0, float z0, int axis, float len,
                                     int r, int g, int b, int a) {
        float x1 = axis == 0 ? len : 0;
        float y1 = axis == 1 ? len : 0;
        float z1 = axis == 2 ? len : 0;
        float sideX = axis == 1 ? OUTLINE_HALF_WIDTH : 0.0F;
        float sideY = axis == 1 ? 0.0F : OUTLINE_HALF_WIDTH;
        addQuad(consumer,
                x0 - sideX, y0 - sideY, z0, x0 + sideX, y0 + sideY, z0,
                x0 + x1 + sideX, y0 + y1 + sideY, z0 + z1, x0 + x1 - sideX, y0 + y1 - sideY, z0 + z1,
                0, 1, 0, r, g, b, a);
    }

    // One edge of the huge-shape bounding box (origin-relative coords).
    private static void addBoxEdge(VertexConsumer consumer,
                                   float x0, float y0, float z0, int axis, float len,
                                   int r, int g, int b, int a) {
        addEdgeStrip(consumer, x0, y0, z0, axis, len, r, g, b, a);
    }

    // -- section buffer plumbing -------------------------------------------

    private static BufferBuilder builderFor(Map<Long, BufferBuilder> builders,
                                            Map<Long, ByteBufferBuilder> backing, long sectionKey) {
        BufferBuilder existing = builders.get(sectionKey);
        if (existing != null) {
            return existing;
        }
        ByteBufferBuilder store = new ByteBufferBuilder(SECTION_BUFFER_HINT);
        backing.put(sectionKey, store);
        BufferBuilder created = new BufferBuilder(store, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        builders.put(sectionKey, created);
        return created;
    }

    // Collects finished builders into CPU-side sections (render thread
    // uploads them later). Backing stores travel with their meshes — see
    // PendingSection — and are freed on upload or discard, never here.
    // Null builds (empty builders) free their backing immediately.
    private static void collectAll(Map<Long, BufferBuilder> builders, Map<Long, ByteBufferBuilder> backing,
                                   List<SectionMeshDraw.PendingSection> out) {
        for (Map.Entry<Long, BufferBuilder> entry : builders.entrySet()) {
            ByteBufferBuilder store = backing.get(entry.getKey());
            MeshData mesh = entry.getValue().build();
            if (mesh == null) {
                if (store != null) {
                    store.close();
                }
                continue;
            }
            long key = entry.getKey();
            out.add(new SectionMeshDraw.PendingSection(new BlockPos(SectionPos.sectionToBlockCoord(SectionPos.x(key)),
                    SectionPos.sectionToBlockCoord(SectionPos.y(key)),
                    SectionPos.sectionToBlockCoord(SectionPos.z(key))), mesh, store));
        }
    }

    private static void closeAllBacking(Map<Long, ByteBufferBuilder> backing) {
        for (ByteBufferBuilder store : backing.values()) {
            store.close();
        }
    }

    // One NEW_ENTITY quad: baked color/uv/full-bright light, matching the old
    // immediate path so the look doesn't change, just the upload frequency.
    private static void addQuad(VertexConsumer consumer,
                                float x0, float y0, float z0, float x1, float y1, float z1,
                                float x2, float y2, float z2, float x3, float y3, float z3,
                                float nx, float ny, float nz, int r, int g, int b, int a) {
        // NOTE: no PoseStack here — vertices are baked section-local once.
        // The section origin is applied on the GPU at draw time.
        consumer.addVertex(x0, y0, z0).setColor(r, g, b, a).setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(nx, ny, nz);
        consumer.addVertex(x1, y1, z1).setColor(r, g, b, a).setUv(0, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(nx, ny, nz);
        consumer.addVertex(x2, y2, z2).setColor(r, g, b, a).setUv(1, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(nx, ny, nz);
        consumer.addVertex(x3, y3, z3).setColor(r, g, b, a).setUv(1, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(nx, ny, nz);
    }

}
