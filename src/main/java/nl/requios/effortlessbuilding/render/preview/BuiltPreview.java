package nl.requios.effortlessbuilding.render.preview;

import java.util.List;
import net.minecraft.world.level.Level;
import nl.requios.effortlessbuilding.render.preview.SectionMeshDraw.PendingSection;
import org.jetbrains.annotations.Nullable;

/**
 * One finished ghost-block bake, worker thread → render thread.
 *
 * <p>Only CPU-side vertex data crosses the handoff ({@code MeshData} inside
 * the pending sections — safe to pass between threads, must never touch GL).
 * The render thread either {@linkplain PreviewRenderCache adopts} it (upload
 * consumes the meshes) or {@link #discard() discards} it (frees native
 * memory). Every path must do exactly one of those two — leaking a discard
 * leaks native vertex memory.</p>
 *
 * <p>Positions, states, overlay and feedback all live on the render thread
 * already; this carries nothing but the baked blocks plus the key needed to
 * verify it is still the live shape at swap time.</p>
 */
public final class BuiltPreview {
    private final long seq;
    private final int modelGeneration;
    private final Level level;
    private final PreviewShapeKey key;
    private final List<PendingSection> blockSections;
    // Non-null only when baked with async-boundary on; the render thread
    // adopts these at swap time instead of baking synchronously.
    private final @Nullable PreviewOverlayMesh.Baked overlay;

    BuiltPreview(long seq, int modelGeneration, Level level, PreviewShapeKey key,
                 List<PendingSection> blockSections,
                 @Nullable PreviewOverlayMesh.Baked overlay) {
        this.seq = seq;
        this.modelGeneration = modelGeneration;
        this.level = level;
        this.key = key;
        this.blockSections = blockSections;
        this.overlay = overlay;
    }

    long seq() {
        return this.seq;
    }

    int modelGeneration() {
        return this.modelGeneration;
    }

    Level level() {
        return this.level;
    }

    PreviewShapeKey key() {
        return this.key;
    }

    List<PendingSection> blockSections() {
        return this.blockSections;
    }

    /** Present only for async-boundary bakes; null means sync path. */
    @Nullable PreviewOverlayMesh.Baked overlay() {
        return this.overlay;
    }

    /** Frees all native mesh memory. Render thread calls this when a finished
     * bake arrives too late (superseded key, level hop, resource reload). */
    void discard() {
        SectionMeshDraw.discardAllPending(this.blockSections);
        if (this.overlay != null) {
            SectionMeshDraw.discardAllPending(this.overlay.fill());
            SectionMeshDraw.discardAllPending(this.overlay.outline());
        }
    }
}
