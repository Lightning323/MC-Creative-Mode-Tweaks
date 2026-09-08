package nl.requios.effortlessbuilding.render.preview;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 *   <li>the {@code HashSet} of all positions + 6 neighbor lookups per block
 *       for fill culling,</li>
 *   <li>the 12 edge toggles per block ({@code computeBorderEdges}) plus all
 *       those short-lived edge-key objects,</li>
 *   <li>the per-block {@code pushPose/translate} and immediate-mode quad
 *       emission (up to ~6 quads x 50k blocks per frame before).</li>
 * </ul>
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
            emitFill(fillBuilders, fillBacking, breakable, new HashSet<>(breakable), fillR, fillG, fillB, fillA);
            emitFill(fillBuilders, fillBacking, unbreakable, new HashSet<>(unbreakable), 255, 80, 80, 100);
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
            emitOutline(lineBuilders, lineBacking, computeBorderEdges(breakable), lineR, lineG, lineB, 255);
            emitOutline(lineBuilders, lineBacking, computeBorderEdges(unbreakable), 100, 100, 100, 255);
            collectAll(lineBuilders, lineBacking, outline);
        } catch (RuntimeException failure) {
            SectionMeshDraw.discardAllPending(outline);
            closeAllBacking(lineBacking);
            throw failure;
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
    // back on at draw time.
    private static void emitFill(Map<Long, BufferBuilder> builders, Map<Long, ByteBufferBuilder> backing,
                                 List<BlockPos> positions, Set<BlockPos> self,
                                 int r, int g, int b, int a) {
        for (BlockPos pos : positions) {
            BufferBuilder builder = builderFor(builders, backing, SectionPos.asLong(pos));
            float x0 = -FILL_EPS;
            float x1 = x0 + 1.0F + FILL_GROW;
            float y0 = -FILL_EPS;
            float y1 = y0 + 1.0F + FILL_GROW;
            float z0 = -FILL_EPS;
            float z1 = z0 + 1.0F + FILL_GROW;
            // Local offset inside the 16³ section (origin re-applied on draw).
            float ox = SectionPos.sectionRelative(pos.getX());
            float oy = SectionPos.sectionRelative(pos.getY());
            float oz = SectionPos.sectionRelative(pos.getZ());
            if (!self.contains(pos.below())) {
                addQuad(builder, ox + x0, oy + y0, oz + z0, ox + x1, oy + y0, oz + z0,
                        ox + x1, oy + y0, oz + z1, ox + x0, oy + y0, oz + z1,
                        0, -1, 0, r, g, b, a);
            }
            if (!self.contains(pos.above())) {
                addQuad(builder, ox + x0, oy + y1, oz + z0, ox + x0, oy + y1, oz + z1,
                        ox + x1, oy + y1, oz + z1, ox + x1, oy + y1, oz + z0,
                        0, 1, 0, r, g, b, a);
            }
            if (!self.contains(pos.north())) {
                addQuad(builder, ox + x0, oy + y0, oz + z0, ox + x0, oy + y1, oz + z0,
                        ox + x1, oy + y1, oz + z0, ox + x1, oy + y0, oz + z0,
                        0, 0, -1, r, g, b, a);
            }
            if (!self.contains(pos.south())) {
                addQuad(builder, ox + x1, oy + y0, oz + z1, ox + x1, oy + y1, oz + z1,
                        ox + x0, oy + y1, oz + z1, ox + x0, oy + y0, oz + z1,
                        0, 0, 1, r, g, b, a);
            }
            if (!self.contains(pos.west())) {
                addQuad(builder, ox + x0, oy + y0, oz + z1, ox + x0, oy + y1, oz + z1,
                        ox + x0, oy + y1, oz + z0, ox + x0, oy + y0, oz + z0,
                        -1, 0, 0, r, g, b, a);
            }
            if (!self.contains(pos.east())) {
                addQuad(builder, ox + x1, oy + y0, oz + z0, ox + x1, oy + y1, oz + z0,
                        ox + x1, oy + y1, oz + z1, ox + x1, oy + y0, oz + z1,
                        1, 0, 0, r, g, b, a);
            }
        }
    }

    // -- outline baking ----------------------------------------------------

    private static void emitOutline(Map<Long, BufferBuilder> builders, Map<Long, ByteBufferBuilder> backing,
                                    Set<EdgeKey> edges, int r, int g, int b, int a) {
        for (EdgeKey edge : edges) {
            // Section of the edge's base block; strips never cross sections
            // because each edge spans exactly one block along its axis... except
            // edges on section borders. Those still bake fine: the strip is
            // stored in the base block's section and drawn with that section's
            // origin, so it lands in the right world spot either way.
            BlockPos base = new BlockPos(edge.x(), edge.y(), edge.z());
            BufferBuilder builder = builderFor(builders, backing, SectionPos.asLong(base));
            float ox = SectionPos.sectionRelative(base.getX());
            float oy = SectionPos.sectionRelative(base.getY());
            float oz = SectionPos.sectionRelative(base.getZ());
            float x1 = edge.axis() == 0 ? 1 : 0;
            float y1 = edge.axis() == 1 ? 1 : 0;
            float z1 = edge.axis() == 2 ? 1 : 0;
            float sideX = edge.axis() == 1 ? OUTLINE_HALF_WIDTH : 0.0F;
            float sideY = edge.axis() == 1 ? 0.0F : OUTLINE_HALF_WIDTH;
            addQuad(builder,
                    ox - sideX, oy - sideY, oz, ox + sideX, oy + sideY, oz,
                    ox + x1 + sideX, oy + y1 + sideY, oz + z1, ox + x1 - sideX, oy + y1 - sideY, oz + z1,
                    0, 1, 0, r, g, b, a);
        }
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

    // -- border math (shape-change only now, was per-frame) ------------------

    // Every block contributes its 12 edges; shared edges toggle off in pairs,
    // leaving exactly the outer border of the shape.
    private static Set<EdgeKey> computeBorderEdges(List<BlockPos> positions) {
        Set<EdgeKey> edges = new HashSet<>();
        for (BlockPos pos : positions) {
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

    private record EdgeKey(int axis, int x, int y, int z) {
    }
}
