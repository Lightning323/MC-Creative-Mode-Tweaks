package nl.requios.effortlessbuilding.render.preview;

import net.minecraft.world.level.Level;

/**
 * One finished border bake, boundary worker thread → render thread.
 *
 * <p>Only CPU-side vertex data crosses the handoff ({@code MeshData} inside
 * the pending sections — safe to pass between threads, must never touch GL).
 * The render thread either {@linkplain PreviewRenderCache adopts} it (upload
 * consumes the meshes) or {@link #discard() discards} it (frees native
 * memory). Every path must do exactly one of those two.</p>
 */
public final class BuiltBoundary {
    private final long seq;
    private final int modelGeneration;
    private final Level level;
    private final PreviewShapeKey key;
    private final PreviewOverlayMesh.Baked overlay;

    BuiltBoundary(long seq, int modelGeneration, Level level, PreviewShapeKey key,
                  PreviewOverlayMesh.Baked overlay) {
        this.seq = seq;
        this.modelGeneration = modelGeneration;
        this.level = level;
        this.key = key;
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

    PreviewOverlayMesh.Baked overlay() {
        return this.overlay;
    }

    /** Frees all native mesh memory. Render thread calls this when a finished
     * bake arrives too late (superseded key, level hop, resource reload). */
    void discard() {
        SectionMeshDraw.discardAllPending(this.overlay.fill());
        SectionMeshDraw.discardAllPending(this.overlay.outline());
    }
}
