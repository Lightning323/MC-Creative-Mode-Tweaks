package nl.requios.effortlessbuilding.render.preview;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import nl.requios.effortlessbuilding.Constants;
import nl.requios.effortlessbuilding.render.preview.PreviewBlockMesh.PreviewBlock;
import org.jetbrains.annotations.Nullable;

/**
 * Single background thread that tessellates ghost blocks off the render thread.
 *
 * <p><b>Scope is deliberately narrow:</b> the worker takes already-resolved
 * ghost blocks (shape math, constraints and block-state resolution all happen
 * on the render thread, key-gated) and only runs them through vanilla's chunk
 * baker into CPU-side meshes. That keeps every world/model read here in the
 * same class vanilla's own chunk-meshing workers already perform off-thread,
 * while the border/overlay never waits on it — it bakes and draws
 * independently on the render thread (or here too if async-boundary is on).</p>
 *
 * <p><b>Why one thread, not a pool:</b> every build for a newer key makes
 * older builds worthless, so work is strictly latest-only — a second thread
 * would just burn CPU on results that get thrown away.</p>
 *
 * <p><b>Thread contract:</b></p>
 * <ul>
 *   <li>Render thread: raycast + key compare + shape + GL upload + swap.
 *       Never tessellates.</li>
 *   <li>Worker: {@link PreviewBlockMesh#bake} only. Never touches GL, never
 *       writes mode state, never raycasts.</li>
 * </ul>
 *
 * <p>Stale builds (superseded key, level hop, pre-reload bake) are freed
 * un-drawn. Any failure just drops the task — the shape/overlay stay live
 * and the next key change resubmits.</p>
 */
public final class PreviewBuildWorker {
    private PreviewBuildWorker() {
    }

    /** Immutable inputs for one bake, fully captured on the render thread. */
    public record Task(long seq, int modelGeneration, PreviewShapeKey key,
                       Level level, BlockRenderDispatcher dispatcher,
                       List<PreviewBlock> meshBlocks, int alpha,
                       // Overlay inputs ride along so async-boundary can bake
                       // the border here too; ignored (but still carried) when
                       // the border bakes synchronously instead.
                       List<BlockPos> overlayOk, List<BlockPos> overlayBad,
                       boolean overlayBreaking) {
    }

    private static final AtomicLong LATEST_SEQ = new AtomicLong();
    private static final AtomicReference<BuiltPreview> READY = new AtomicReference<>();

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable job) {
            // Daemon so a stuck bake can never pin the JVM on exit; the pool
            // lives for the whole game session (meshes are cleared per level).
            Thread thread = new Thread(job, "CreativeTweaks-Preview-Builder");
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
     * Render thread only (mode off, level hop, resource reload).
     */
    public static void cancel() {
        LATEST_SEQ.incrementAndGet();
        BuiltPreview stale = READY.getAndSet(null);
        if (stale != null) {
            stale.discard();
        }
    }

    /** Takes the newest finished build, if any. Render thread only. */
    public static @Nullable BuiltPreview pollReady() {
        return READY.getAndSet(null);
    }

    // -- worker thread below -------------------------------------------------

    private static void build(Task task) {
        try {
            if (task.seq() != LATEST_SEQ.get()) {
                return;
            }
            List<SectionMeshDraw.PendingSection> sections =
                    PreviewBlockMesh.bake(task.dispatcher(), task.level(), task.meshBlocks(), task.alpha(),
                            () -> task.seq() == LATEST_SEQ.get());
            if (sections == null) {
                // Aborted mid-bake: a newer shape already superseded us.
                return;
            }
            if (task.seq() != LATEST_SEQ.get()) {
                SectionMeshDraw.discardAllPending(sections);
                return;
            }
            // Optional async border: same pure-math bake the render thread
            // would run, just here so big shapes don't hitch the frame.
            PreviewOverlayMesh.Baked overlay = null;
            if (task.key().asyncBoundary()) {
                overlay = PreviewOverlayMesh.bake(task.overlayOk(), task.overlayBad(), task.overlayBreaking());
                if (task.seq() != LATEST_SEQ.get()) {
                    SectionMeshDraw.discardAllPending(sections);
                    SectionMeshDraw.discardAllPending(overlay.fill());
                    SectionMeshDraw.discardAllPending(overlay.outline());
                    return;
                }
            }
            BuiltPreview result = new BuiltPreview(task.seq(), task.modelGeneration(),
                    task.level(), task.key(), sections, overlay);
            BuiltPreview superseded = READY.getAndSet(result);
            if (superseded != null) {
                superseded.discard();
            }
        } catch (Throwable failure) {
            // Bake failed (chunk unloaded mid-bake, reload raced us, …).
            // Border/shape stay live; the next key change resubmits.
            Constants.LOG.warn("[EffortlessBuilding] Ghost-block bake dropped (will retry on next change)", failure);
        }
    }
}
