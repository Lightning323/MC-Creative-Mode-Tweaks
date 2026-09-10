package nl.requios.effortlessbuilding.render.preview;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import nl.requios.effortlessbuilding.Constants;
import org.jetbrains.annotations.Nullable;

/**
 * Dedicated background thread for the preview border when
 * {@code building.async_boundary} is off.
 *
 * <p><b>Why a second worker:</b> with async-boundary on the border rides
 * {@link PreviewBuildWorker} together with the ghost blocks (one bake, one
 * handoff — but the border lags behind slow ghost tessellation). With
 * async-boundary off the border must still stay off the render thread, so it
 * gets its own latest-only thread here: pure-math
 * {@link PreviewOverlayMesh#bake} only, no level reads, no GL. The render
 * thread polls the finished bake and uploads it, keeping the previous overlay
 * on screen until the swap — seamless, no flicker.</p>
 *
 * <p><b>Why one thread, not a pool:</b> every build for a newer key makes
 * older builds worthless, so work is strictly latest-only — a second thread
 * would just burn CPU on results that get thrown away.</p>
 *
 * <p><b>Thread contract:</b></p>
 * <ul>
 *   <li>Render thread: shape + submit + GL upload + swap. Never bakes.</li>
 *   <li>Worker: {@link PreviewOverlayMesh#bake} only. Never touches GL, never
 *       writes mode state, never raycasts.</li>
 * </ul>
 *
 * <p>Stale builds (superseded key, level hop, pre-reload bake) are freed
 * un-drawn. Any failure just drops the task — the old border stays live
 * and the next key change resubmits.</p>
 */
public final class PreviewBoundaryWorker {
    private PreviewBoundaryWorker() {
    }

    /** Immutable inputs for one bake, fully captured on the render thread. */
    public record Task(long seq, int modelGeneration, PreviewShapeKey key,
                       Level level,
                       List<BlockPos> overlayOk, List<BlockPos> overlayBad,
                       boolean overlayBreaking) {
    }

    private static final AtomicLong LATEST_SEQ = new AtomicLong();
    private static final AtomicReference<BuiltBoundary> READY = new AtomicReference<>();

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable job) {
            // Daemon so a stuck bake can never pin the JVM on exit; the pool
            // lives for the whole game session (meshes are cleared per level).
            Thread thread = new Thread(job, "CreativeTweaks-Preview-Boundary");
            thread.setDaemon(true);
            return thread;
        }
    });

    /** Next sequence number. Render thread only. */
    public static long nextSeq() {
        return LATEST_SEQ.incrementAndGet();
    }

    /** Hands a task to the worker. Render thread only. */
    public static void submit(Task task) {
        EXECUTOR.execute(() -> build(task));
    }

    /**
     * Cancels in-flight work and frees any finished-but-uncollected result.
     * Render thread only (mode off, level hop, resource reload, path switch).
     */
    public static void cancel() {
        LATEST_SEQ.incrementAndGet();
        BuiltBoundary stale = READY.getAndSet(null);
        if (stale != null) {
            stale.discard();
        }
    }

    /** Takes the newest finished build, if any. Render thread only. */
    public static @Nullable BuiltBoundary pollReady() {
        return READY.getAndSet(null);
    }

    // -- worker thread below -------------------------------------------------

    private static void build(Task task) {
        try {
            if (task.seq() != LATEST_SEQ.get()) {
                return;
            }
            PreviewOverlayMesh.Baked overlay =
                    PreviewOverlayMesh.bake(task.overlayOk(), task.overlayBad(), task.overlayBreaking());
            if (task.seq() != LATEST_SEQ.get()) {
                SectionMeshDraw.discardAllPending(overlay.fill());
                SectionMeshDraw.discardAllPending(overlay.outline());
                return;
            }
            BuiltBoundary result = new BuiltBoundary(task.seq(), task.modelGeneration(),
                    task.level(), task.key(), overlay);
            BuiltBoundary superseded = READY.getAndSet(result);
            if (superseded != null) {
                superseded.discard();
            }
        } catch (Throwable failure) {
            // Bake failed partway. Old border stays live; the next key change
            // resubmits (plus the stuck-build retry in PreviewRenderCache).
            Constants.LOG.warn("[EffortlessBuilding] Border bake dropped (will retry on next change)", failure);
        }
    }
}
