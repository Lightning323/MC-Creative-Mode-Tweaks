package nl.requios.effortlessbuilding.render.preview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.BuildModes;
import nl.requios.effortlessbuilding.buildmode.BuildSelectionGuard;
import nl.requios.effortlessbuilding.buildmode.BuildSettings;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipeline;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipelineClient;
import nl.requios.effortlessbuilding.buildpipeline.SableCompat;
import nl.requios.effortlessbuilding.buildpipeline.TrowelSystem;
import nl.requios.effortlessbuilding.mixin.BucketItemAccessor;
import nl.requios.effortlessbuilding.render.RenderHandler;
import nl.requios.effortlessbuilding.render.preview.PreviewBlockMesh.PreviewBlock;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import nl.requios.effortlessbuilding.utilities.BlockStatus;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lightning323.creative_mode_tweaks.Config;

/**
 * Central preview cache: cheap per-frame key compare on the render thread,
 * shape resolved synchronously on change, ghost-block tessellation on
 * {@link PreviewBuildWorker} and border baking on either worker.
 *
 * <p><b>Why the split is shaped this way:</b> ghost tessellation (baked
 * models, AO, thousands of quads) and the pure-math border bake (fill culling
 * + edge toggles + quad emission) are the parts that hitch, so both go async
 * — the previous meshes keep drawing until the new ones swap in: seamless, no
 * flicker. Per key change the render thread does raycast, coordinates,
 * constraints and state resolution only, so feedback stays live even while
 * meshes still bake.</p>
 *
 * <p>With {@code building.async_boundary} on (the default), the border rides
 * the ghost worker together with the blocks (one bake, one handoff — but the
 * border lags behind slow ghosts). With it off, the border bakes on its own
 * dedicated {@link PreviewBoundaryWorker} thread and the render loop picks the
 * finished bake up from there — still fully off the render thread, just
 * independent of ghost progress.</p>
 *
 * <p><b>Huge shapes</b> (boundary volume past {@link #DETAILED_PREVIEW_BLOCK_LIMIT}
 * blocks, or past the configured max-blocks cap) skip all of that: the client
 * boundary feeds a single bounding-box overlay directly, client blocks are
 * never enumerated, ghosts stay off, and hover-only reshapes are throttled to
 * ~12Hz. Dims come from the boundary and the count is its volume (an upper
 * bound, red past the cap); placement itself was always server-authoritative.</p>
 *
 * <p>Hover-only reshapes (cursor moves, nothing structural) are throttled —
 * ~12Hz for huge boxes, ~28Hz for detailed shapes — so dragging never
 * regenerates every frame; clicks and option changes rebuild immediately.</p>
 *
 * <p>Validity split is preserved: over-limit / protected / out-of-reach blocks
 * stay flagged as <i>rejected</i> for the overlay, which draws the <i>exact
 * tool shape</i> — white fill + outline for placeable, red/grey for rejected.
 * The block mesh itself stays strictly placeable-only, and the count/dims
 * line turns red whenever the count cap or the survival stock cuts blocks
 * (see isOverLimit).</p>
 */
public final class PreviewRenderCache {
    private static final PreviewRenderCache INSTANCE = new PreviewRenderCache();


    /**
     * Minimum time between shape rebuilds while a huge preview is live and
     * only the hover point moved. Dragging a 50k-block shape otherwise
     * regenerates tens of thousands of coordinates every frame; 80ms keeps
     * the box tracking the cursor at ~12Hz instead of hitching the game.
     * Clicks, mode/item/config changes always rebuild immediately.
     */
    private static final long THROTTLE_SHAPE_RESHAPE_MIN_NANOS = 100_000_000L;
    private static final long SHAPE_RESHAPE_MIN_NANOS = 35_000_000L;

    /**
     * Upper bound for transient presizing (block sets, generator lists).
     * Exact for filled boxes, an overestimate for hollow/sparse shapes; the
     * cap keeps one pathological drag from ballooning eden.
     */
    private static final int PRESIZE_CAP = 1 << 16;

    /**
     * Refresh period for the config snapshot (see config fields below).
     */
    private static final long CONFIG_CACHE_NANOS = 500_000_000L;

    public static PreviewRenderCache get() {
        return INSTANCE;
    }

    private final PreviewBlockMesh blockMesh = new PreviewBlockMesh();
    private final PreviewOverlayMesh overlayMesh;

    // Last SHAPED outputs (applied synchronously: lists, overlay, feedback).
    private PreviewShapeKey shapedKey;
    private Level shapedLevel;
    private List<BlockPos> breakable = List.of();
    private List<BlockPos> unbreakable = List.of();
    // Combined view for the action-bar feedback (count + dims). Built once per
    // shape so the per-frame feedback call allocates nothing.
    private List<BlockPos> all = List.of();
    private List<PreviewBlock> animated = List.of();
    private boolean hasOverlay;
    private boolean isBreaking;
    // True when survival stock cut blocks out of this shape (the only
    // remaining cutoff). Drives the red count/dims warning.
    private boolean overLimit;
    private boolean wantsBlocks;

    // Ghost-mesh tracking (async): meshKey = key whose mesh is uploaded,
    // submittedKey = key handed to the worker. HasPreview for ghosts means
    // meshKey caught up; the overlay/lists above are always live.
    private PreviewShapeKey meshKey;
    private PreviewShapeKey submittedKey;
    // Retained bake inputs for the stuck-build retry (rare, see update).
    private List<PreviewBlock> pendingMeshBlocks = List.of();
    private int pendingAlpha;
    private long submittedFrame;
    private long frame;

    // Border-mesh tracking (dedicated worker, async-boundary off):
    // boundaryKey = key whose overlay is uploaded, submittedBoundaryKey = key
    // handed to PreviewBoundaryWorker. The overlay lists above (breakable /
    // unbreakable) are always live; these only track the async handoff.
    private PreviewShapeKey boundaryKey;
    private PreviewShapeKey submittedBoundaryKey;
    // Retained border inputs for the stuck-build retry (rare, see update).
    private List<BlockPos> pendingBoundaryOk = List.of();
    private List<BlockPos> pendingBoundaryBad = List.of();
    private boolean pendingBoundaryBreaking;
    private long submittedBoundaryFrame;

    // True while the live shape uses the huge-shape box path (no ghosts,
    // box overlay). Gates the hover-only reshape throttle in update().
    private boolean simplePreview;
    // True while the live preview is over the throttle count: positions +
    // mesh only, every validation skipped (see shapeFast). False means the
    // preview runs the exact same logic as placement, replacement included.
    private boolean throttleMode;
    private long lastShapeNanos;

    // Raycast hit reused by the frame: computed once here, shared with the
    // selection-marker pass so the frame pays for exactly one raycast.
    private @Nullable BlockHitResult currentHit;

    // level.dimension().location().toString() allocates every call; the
    // dimension barely changes, so remember it per level instance.
    private @Nullable Level dimensionLevel;
    private String dimensionId = "";

    // 1-entry trowel cache: the held item rarely changes between frames, but
    // TrowelSystem.isTrowel does a registry lookup on every miss.
    private @Nullable Item trowelCacheItem;
    private boolean trowelCacheValue;

    // Config snapshot for the key path: the five reads below are volatile
    // config lookups (plus try/catch) that never change mid-drag. Refreshed
    // at most twice a second, or immediately when creative mode flips (which
    // swaps the limit set).
    private long configNanos;
    private boolean configCreative;
    private int configMaxBlocks;
    private int configAxisLimit;
    private double configReach;
    private boolean configProtectTiles;
    private int configBlockAlpha;
    private boolean configAsyncBoundary;
    private int configPreviewRenderThrottleBlocks;


    // Bumped on resource reload so pre-reload bakes can never go live.
    private int modelGeneration;

    private PreviewRenderCache() {
        this.overlayMesh = new PreviewOverlayMesh(
                ResourceLocation.fromNamespaceAndPath("creative_mode_tweaks", "textures/special/checkerboard.png"),
                ResourceLocation.fromNamespaceAndPath("creative_mode_tweaks", "textures/special/solid.png"),
                ResourceLocation.fromNamespaceAndPath("creative_mode_tweaks", "textures/special/blank.png"));
    }

    /**
     * Refreshes the cache if — and only if — the shape inputs changed.
     * Always call once per render frame before drawing.
     */
    public void update(Minecraft mc) {
        Player player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) {
            clear();
            return;
        }
        if (BuildModes.CLIENT.getBuildMode() == BuildModeEnum.DISABLED
                || !BuildPipelineClient.isBuildModesAllowed(player)) {
            if (this.shapedKey != null || this.submittedKey != null || this.submittedBoundaryKey != null
                    || this.hasPreview()) {
                clear();
            }
            RenderHandler.resetPreviewSize();
            return;
        }

        BuildModeEnum mode = BuildModes.CLIENT.getBuildMode();
        boolean inProgress = !mode.instance.isFirstClick();
        BuildPipeline.BuildState state = BuildPipelineClient.getBuildState();

        // Single-block cursor with no sequence: old code explicitly showed no
        // big preview here, so keep everything empty (markers still draw).
        if (!inProgress && state == null) {
            if (this.shapedKey != null || this.submittedKey != null || this.submittedBoundaryKey != null
                    || this.hasPreview()) {
                clear();
                RenderHandler.resetPreviewSize();
            }
            return;
        }

        refreshConfigCache(player);
        BlockHitResult hit = reuseVanillaHit(mc, player);
        this.currentHit = hit;
        PreviewShapeKey key = buildKey(player, level, mode, inProgress, state, hit);
        if (key == null) {
            // Hovered across a Sable boundary: nothing valid to show.
            clear();
            return;
        }

        // A finished bake lands on the shaped snapshot, not the drifting
        // cursor: while a freeze (e.g. oversized) pins shapedKey, the bake
        // submitted on entry still lands instead of being discarded as
        // stale — so the mesh updates one last time, then stays frozen.
        // Every other displayed output already follows shapedKey, so this
        // keeps ghosts consistent with the shown shape.
        PreviewShapeKey liveKey = this.shapedKey != null ? this.shapedKey : key;
        drainReady(liveKey, level);
        drainBoundaryReady(liveKey, level);
        this.frame++;

        if (!key.equals(this.shapedKey) || level != this.shapedLevel) {
            // Hover drags reshape constantly: hold the live shape briefly
            // instead of regenerating every frame. Huge boxes throttle harder
            // (~12Hz); detailed shapes refresh at ~28Hz — a frame or two of
            // border lag for much less hitch. Clicks, mode/item/config changes
            // always rebuild immediately via hoverOnlyChange.


            if (level == this.shapedLevel && hoverOnlyChange(this.shapedKey, key)
                    && System.nanoTime() - this.lastShapeNanos < (this.throttleMode ? THROTTLE_SHAPE_RESHAPE_MIN_NANOS : SHAPE_RESHAPE_MIN_NANOS)) {

                // Stale shape stays on screen; the next frame retries with a
                // newer key, so the preview converges within the window.
            } else {
                shapeOnRenderThread(mc, player, level, mode, state, hit, key);
            }
        } else if (this.wantsBlocks && !key.equals(this.meshKey)
                && this.frame - this.submittedFrame > 120) {
            // Worker bake vanished without a trace (dropped race + no
            // follow-up change). Re-hand the retained inputs, no shape redo.
            submitMeshTask(mc, level, key);
        } else if (!key.asyncBoundary() && !key.equals(this.boundaryKey)
                && this.submittedBoundaryKey != null
                && this.frame - this.submittedBoundaryFrame > 120) {
            // Same retry for the dedicated border worker: re-hand the retained
            // overlay inputs so a dropped border bake can't stick forever.
            submitBoundaryTask(level, key);
        }
    }

    /**
     * Ghost blocks (cached sections). No-op until the first bake swaps in.
     */
    public void renderBlocks(Level level, double camX, double camY, double camZ,
                             Matrix4f modelView, Matrix4f projection) {
        this.blockMesh.render(level, camX, camY, camZ, modelView, projection);
    }

    /**
     * Tinted fill boxes (cached sections, always live).
     */
    public void renderFill(Level level, double camX, double camY, double camZ,
                           Matrix4f modelView, Matrix4f projection) {
        if (!this.hasOverlay) {
            return;
        }
        this.overlayMesh.renderFill(level, camX, camY, camZ, modelView, projection);
    }

    /**
     * Border outline (cached sections, always live).
     */
    public void renderOutline(Level level, double camX, double camY, double camZ,
                              Matrix4f modelView, Matrix4f projection) {
        if (!this.hasOverlay) {
            return;
        }
        this.overlayMesh.renderOutline(level, camX, camY, camZ, modelView, projection);
    }

    /**
     * Animated block entities can't cache (models tick), so these draw via
     * the immediate path each frame. Resolved at shape time, so live too.
     */
    public List<PreviewBlock> animatedBlocks() {
        return this.animated;
    }

    public List<BlockPos> breakablePositions() {
        return this.breakable;
    }

    public List<BlockPos> unbreakablePositions() {
        return this.unbreakable;
    }

    /**
     * Breakable + unbreakable, for the count/dims action-bar line.
     */
    public List<BlockPos> allPositions() {
        return this.all;
    }

    public boolean isBreaking() {
        return this.isBreaking;
    }

    /**
     * True when the count cap cut blocks: the shape won't fully build.
     */
    public boolean isOverLimit() {
        return this.overLimit;
    }

    public boolean hasPreview() {
        return !this.breakable.isEmpty() || !this.unbreakable.isEmpty();
    }

    /**
     * True while the live preview is the huge-shape bounding box (no ghosts).
     */
    public boolean isSimplePreview() {
        return this.simplePreview;
    }

    /**
     * True while the live preview is in fast mode (over the throttle count:
     * shape + mesh only, no modifiers, no reach caps, no survival checks).
     */
    public boolean isThrottleMode() {
        return this.throttleMode;
    }

    /**
     * This frame's raycast hit, or null when there is no live preview.
     * Lets the marker pass reuse the hit instead of raycasting twice.
     */
    public @Nullable BlockHitResult getCurrentHit() {
        return this.currentHit;
    }

    /**
     * True when nothing is cached, submitted or shaped: clear() would be a no-op.
     */
    public boolean isIdle() {
        return this.shapedKey == null && this.submittedKey == null && this.submittedBoundaryKey == null
                && !hasPreview();
    }

    /**
     * Drops GPU buffers + snapshots and cancels in-flight work.
     */
    public void clear() {
        PreviewBuildWorker.cancel();
        PreviewBoundaryWorker.cancel();
        BuildSelectionGuard.CLIENT.reset();
        this.blockMesh.clear();
        this.overlayMesh.clear();
        this.shapedKey = null;
        this.meshKey = null;
        this.submittedKey = null;
        this.boundaryKey = null;
        this.submittedBoundaryKey = null;
        this.shapedLevel = null;
        this.breakable = List.of();
        this.unbreakable = List.of();
        this.all = List.of();
        this.animated = List.of();
        this.pendingMeshBlocks = List.of();
        this.pendingBoundaryOk = List.of();
        this.pendingBoundaryBad = List.of();
        this.hasOverlay = false;
        this.wantsBlocks = false;
        this.overLimit = false;
        this.simplePreview = false;
        this.throttleMode = false;
        this.lastShapeNanos = 0;
        this.currentHit = null;
        this.dimensionLevel = null;
        this.trowelCacheItem = null;
        this.configNanos = 0;
    }

    /**
     * Resource reload: same as clear, plus retire pre-reload bakes.
     */
    public void onModelsBaked() {
        clear();
        this.modelGeneration++;
    }

    // -- shape path (render thread, key-gated) --------------------------------

    // Everything except mesh baking, done the moment the key changes: hover
    // publish, coordinates, constraints, state resolution, feedback — then the
    // ghosts go to PreviewBuildWorker and (when async-boundary is off) the
    // border goes to PreviewBoundaryWorker. The previous meshes keep drawing
    // until each worker's bake swaps in.
    private void shapeOnRenderThread(Minecraft mc, Player player, Level level,
                                     BuildModeEnum mode, BuildPipeline.@Nullable BuildState state,
                                     @Nullable BlockHitResult hit, PreviewShapeKey key) {
        // A new anchor/mode/first-click starts a fresh selection session: the
        // oversize boundary from the previous session must not gate the new one.
        if (this.shapedKey == null
                || this.shapedKey.mode() != key.mode()
                || !Objects.equals(this.shapedKey.selectionOrigin(), key.selectionOrigin())
                || !Objects.equals(this.shapedKey.firstHitPos(), key.firstHitPos())) {
            BuildSelectionGuard.CLIENT.reset();
        }

        // Publish the hover point BEFORE generating coordinates —
        // findCoordinates reads it as the in-progress second/third point.
        if (mode.instance.usesDirectSecondPoint()) {
            mode.instance.setPreviewPoint(key.hoverPoint());
        }


        BlockPos anchor = key.selectionOrigin() != null ? key.selectionOrigin() : player.blockPosition();
        // Boundary first: pure coordinate math (first/second/third points +
        // clamps + mode-specific expansion), no block enumeration. Huge shapes
        // render from this alone and never pay for block enumeration.
        // Must run inside the selection scope: findSecondPos/findThirdPos read
        // the eye/look through SableCompat, which transforms them into the
        // anchor's selection space. Outside the scope the eye stays global
        // while the first point is plot-local on contraptions, so the reach
        // check always fails, the boundary is null and the preview vanishes.
        AABB previewBoundary;
        try (SableCompat.SelectionScope ignored = SableCompat.pushSelection(level, anchor)) {
            previewBoundary = mode.instance.getClientBoundary(player);
        }
        if (previewBoundary == null) {
            clear();
            RenderHandler.resetPreviewSize();
            this.shapedKey = key;
            this.shapedLevel = level;
            return;
        }

        int maxBlocks = key.maxBlocks();
        long boundaryVol = boundaryVolume(previewBoundary);

        // Oversized freeze without enumeration: while the guard is oversized,
        // a non-shrinking boundary is rejected on volume alone — no block
        // enumeration, no rebake, the frozen preview just keeps drawing.
        // This is what keeps the selection from growing too large. Note this
        // never cuts blocks: an oversized shape that does get through shapes
        // — and places — in full.
        if (BuildSelectionGuard.CLIENT.isOversized()) {
            AABB lastBoundary = BuildSelectionGuard.CLIENT.getLastBoundary();
            if (lastBoundary != null
                    && boundaryVol >= BuildSelectionGuard.boundaryVolume(lastBoundary)) {
                if (mode.instance.usesDirectSecondPoint()) {
                    mode.instance.setPreviewPoint(
                            this.shapedKey != null ? this.shapedKey.hoverPoint() : null);
                }
                return;
            }
        }


        // Presize from the boundary volume (exact for filled boxes, an upper
        // bound otherwise, capped): a fresh set at default capacity would
        // rehash repeatedly on the way to thousands of entries.
        int est = (int) Math.min(boundaryVol, (long) maxBlocks * 2L);
        BlockSet blocks = new BlockSet(Math.max(16, Math.min(est, PRESIZE_CAP)));
        String shapeInfo;
        try (SableCompat.SelectionScope ignored = SableCompat.pushSelection(level, anchor)) {
            // Preview path: bare positions only, never the exact placement list.
            mode.instance.getPlacementBlocks(blocks, player, false);
            // Same scope: the info readout raycasts through SableCompat too.
            shapeInfo = mode.instance.getShapeInfo(player);
        }
        this.throttleMode = blocks.size() > configPreviewRenderThrottleBlocks;


        if (blocks.isEmpty()) {
            clear();
            RenderHandler.resetPreviewSize();
            this.shapedKey = key;
            this.shapedLevel = level;
            return;
        }

        // Oversize gate: when the raw block count exceeds the configured
        // block-set limit the selection becomes oversized, after which only a
        // strictly smaller boundary box may proceed. Growing (or same-size)
        // updates are rejected so the player must shrink back down instead of
        // dragging an ever-larger selection. Rejected here means frozen, not
        // cut: no blocks are ever truncated for size.
        if (!BuildSelectionGuard.CLIENT.updateSelection(previewBoundary, blocks.size(), maxBlocks)) {
            if (mode.instance.usesDirectSecondPoint()) {
                mode.instance.setPreviewPoint(
                        this.shapedKey != null ? this.shapedKey.hoverPoint() : null);
            }
            return;
        }

        if (this.throttleMode) {
            shapeFast(mc, player, level, state, hit, key, blocks, shapeInfo);
        } else {
            shapeDetailed(mc, player, level, state, hit, key, blocks, anchor, shapeInfo);
        }
    }

    /**
     * Volume (block count upper bound) of a full-block boundary AABB. The
     * boundary uses block-aligned corners with an exclusive max, so the
     * inclusive block extents are floor(min)..ceil(max)-1 per axis.
     */
    private static long boundaryVolume(AABB boundary) {
        long dx = (long) Math.ceil(boundary.maxX) - (long) Math.floor(boundary.minX);
        long dy = (long) Math.ceil(boundary.maxY) - (long) Math.floor(boundary.minY);
        long dz = (long) Math.ceil(boundary.maxZ) - (long) Math.floor(boundary.minZ);
        if (dx <= 0 || dy <= 0 || dz <= 0) {
            return 0;
        }
        return dx * dy * dz;
    }

    /**
     * Inclusive block corners of a full-block boundary AABB. Uses floor for
     * the min corner and ceil-1 for the exclusive max corner, so negative
     * coordinates (truncation bugs with plain casts) stay correct.
     */
    private static BlockPos boundaryMin(AABB boundary) {
        return new BlockPos(
                (int) Math.floor(boundary.minX),
                (int) Math.floor(boundary.minY),
                (int) Math.floor(boundary.minZ));
    }

    private static BlockPos boundaryMax(AABB boundary) {
        return new BlockPos(
                (int) Math.ceil(boundary.maxX) - 1,
                (int) Math.ceil(boundary.maxY) - 1,
                (int) Math.ceil(boundary.maxZ) - 1);
    }

    /**
     * Huge-shape fast path: one bounding-box overlay from the client boundary,
     * no ghosts, no block enumeration. Skips coordinates, constraints,
     * sorting, state resolution and per-block validity — placement stays
     * server-authoritative, so a box + volume-based count/dims is the honest
     * cheap preview. Constant cost no matter how many blocks: this is what
     * keeps multi-thousand-block drags interactive without generating tens of
     * thousands of coordinates every frame.
     *
     * <p>The count is the boundary volume (an upper bound, exact for filled
     * boxes, an overestimate for hollow/sparse shapes); dims come from the
     * boundary itself. breakable/all hold only the box corners so
     * {@link #hasPreview()} stays true — exact per-block lists are
     * unavailable on this path by design.</p>
     */
    private void shapeSimple(Level level, BuildPipeline.@Nullable BuildState state,
                             PreviewShapeKey key, AABB previewBoundary, int maxBlocks) {
        BlockPos min = boundaryMin(previewBoundary);
        BlockPos max = boundaryMax(previewBoundary);
        long volume = boundaryVolume(previewBoundary);
        int estimatedCount = volume > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) volume;
        // No max-blocks cutoff: the box always represents the full shape.
        boolean overCap = false;
        this.isBreaking = (state != null ? state : BuildPipeline.BuildState.PLACING)
                == BuildPipeline.BuildState.BREAKING;
        // Corners only: keeps hasPreview() true without enumerating blocks.
        // Deduplicate the single-block case so the lists never hold the same
        // corner twice.
        List<BlockPos> corners = min.equals(max) ? List.of(min) : List.of(min, max);
        this.breakable = corners;
        this.unbreakable = List.of();
        this.all = this.breakable;
        this.animated = List.of();
        this.overLimit = overCap;
        this.wantsBlocks = false;
        this.shapedKey = key;
        this.shapedLevel = level;
        this.simplePreview = true;
        this.lastShapeNanos = System.nanoTime();

        // Box overlay bakes synchronously — it is microseconds, never a hitch.
        // Exempt from the boundary-worker path for the same reason.
        this.overlayMesh.adopt(level, PreviewOverlayMesh.bakeBoundingBox(min, max, this.isBreaking));
        this.overlayMesh.setIsSimple(true);
        this.hasOverlay = !this.overlayMesh.isEmpty();

        // No worker traffic at all: drop stale ghosts and borders, retire any bake.
        PreviewBuildWorker.cancel();
        PreviewBoundaryWorker.cancel();
        this.blockMesh.clear();
        this.meshKey = key;
        this.submittedKey = key;
        this.boundaryKey = key;
        this.submittedBoundaryKey = key;
        this.pendingMeshBlocks = List.of();
        this.pendingBoundaryOk = List.of();
        this.pendingBoundaryBad = List.of();

        // Count + dims from the already-known bounds: no list rescan.
        RenderHandler.updateFeedbackSimple(estimatedCount, min, max, state != null, state, overCap, null);
    }

    /**
     * Breakneck path for over-throttle shapes: selection shape + block mesh,
     * nothing else. No modifiers, no reach caps, no tile-entity scans, no
     * survival checks, no replacement handling — zero world reads per block,
     * every entry stays VALID. One linear pass builds positions, feedback
     * bounds and ghosts together; no ok/bad/entry side lists. Placement
     * (click path, server) still runs the full pipeline, so this preview is
     * allowed to over-promise.
     */
    private void shapeFast(Minecraft mc, Player player, Level level,
                           BuildPipeline.@Nullable BuildState state,
                           @Nullable BlockHitResult hit, PreviewShapeKey key,
                           BlockSet blocks, @Nullable String shapeInfo) {
        this.overlayMesh.setIsSimple(false);
        BuildPipeline.BuildState action = state != null ? state : BuildPipeline.BuildState.PLACING;
        // Appearance only: per-block trowel items. Hotbar scan + hash pick,
        // no level access.
        TrowelSystem.INSTANCE.processBlocks(blocks, player, action);

        this.isBreaking = action == BuildPipeline.BuildState.BREAKING;
        boolean wantBlocks = !this.isBreaking
                && BuildSettings.CLIENT.getReplaceMode() != BuildSettings.ReplaceMode.ONLY_BLOCKS;

        // Ghost base state resolves once per shape, exactly like the detailed
        // path (trowel rolls one random set per shape, no per-frame shimmer).
        BlockHitResult firstHit = BuildPipelineClient.getFirstClickHit();
        BlockState base = wantBlocks ? resolveBaseState(mc, player, firstHit, hit) : null;
        boolean trowel = base != null && TrowelSystem.isTrowel(player.getMainHandItem());
        Map<Item, BlockState> trowelCache = trowel ? new HashMap<>() : null;

        // The single pass: positions for the overlay, bounds for feedback,
        // ghosts for the mesh. No status checks, no side lists.
        List<BlockPos> ok = new ArrayList<>(blocks.size());
        List<PreviewBlock> meshBlocks = new ArrayList<>();
        List<PreviewBlock> animatedBlocks = new ArrayList<>();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockEntry entry : blocks.values()) {
            BlockPos pos = entry.blockPos;
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            if (x < minX) {
                minX = x;
            }
            if (x > maxX) {
                maxX = x;
            }
            if (y < minY) {
                minY = y;
            }
            if (y > maxY) {
                maxY = y;
            }
            if (z < minZ) {
                minZ = z;
            }
            if (z > maxZ) {
                maxZ = z;
            }
            ok.add(pos);
            if (base == null) {
                continue;
            }
            BlockState resolved = base;
            if (trowel && entry.item instanceof BlockItem randomBlock) {
                resolved = trowelCache.computeIfAbsent(entry.item,
                        item -> resolveBaseState(mc, player, randomBlock, new ItemStack(item), firstHit, hit));
            }
            if (resolved == null) {
                continue;
            }
            // No modifiers ran, so transforms are identity — skipped.
            if (resolved.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED) {
                animatedBlocks.add(new PreviewBlock(pos, resolved));
            } else {
                meshBlocks.add(new PreviewBlock(pos, resolved));
            }
        }

        // Wrap, don't copy: these lists are never mutated after publish.
        int alpha = key.blockAlpha();
        this.breakable = Collections.unmodifiableList(ok);
        this.unbreakable = List.of();
        this.all = this.breakable;
        this.animated = animatedBlocks.isEmpty() ? List.of() : Collections.unmodifiableList(animatedBlocks);
        // No max-blocks truncation: the count line never goes red for size —
        // every enumerated block is kept and placed.
        this.overLimit = false;
        this.wantsBlocks = wantBlocks && !meshBlocks.isEmpty();
        this.shapedKey = key;
        this.shapedLevel = level;
        this.simplePreview = false;
        this.lastShapeNanos = System.nanoTime();

        // Border routing (same rule as the detailed path): coupled when
        // async-boundary is on (rides the ghost worker, falls back to sync
        // when there are no ghosts to ride with), dedicated async worker when
        // off — never baked on the render thread. The old overlay keeps
        // drawing until the new bake swaps in.
        if (key.asyncBoundary()) {
            PreviewBoundaryWorker.cancel();
            this.submittedBoundaryKey = null;
            this.pendingBoundaryOk = List.of();
            this.pendingBoundaryBad = List.of();
            if (!this.wantsBlocks) {
                this.overlayMesh.adopt(level, PreviewOverlayMesh.bake(ok, List.of(), this.isBreaking));
                this.hasOverlay = !this.overlayMesh.isEmpty();
                this.boundaryKey = key;
            }
        } else {
            submitBoundaryTask(level, key);
        }

        // Sound + action-bar feedback, exactly like the detailed path.
        RenderHandler.updateFeedbackSimple(ok.size(),
                new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ),
                state != null, state, this.overLimit, shapeInfo);

        if (this.wantsBlocks) {
            this.pendingMeshBlocks = Collections.unmodifiableList(meshBlocks);
            this.pendingAlpha = alpha;
            submitMeshTask(mc, level, key);
        } else {
            // Nothing for the worker: drop stale ghosts now, cancel any bake.
            PreviewBuildWorker.cancel();
            this.blockMesh.clear();
            this.meshKey = key;
            this.submittedKey = key;
            this.pendingMeshBlocks = List.of();
        }
    }

    // Full-fidelity path for sensibly-sized shapes: constraints, ghosts,
    // exact per-block overlay. Bounded by DETAILED_PREVIEW_BLOCK_LIMIT, so
    // every O(N log N) step here has a hard ceiling.
    private void shapeDetailed(Minecraft mc, Player player, Level level,
                               BuildPipeline.@Nullable BuildState state,
                               @Nullable BlockHitResult hit, PreviewShapeKey key,
                               BlockSet blocks, BlockPos anchor, @Nullable String shapeInfo) {
        // NOTE: no sorting before processBlocks — and that is deliberate.
        // ConstraintSystem preserves GENERATION order, and the server pipeline
        // runs the exact same way. Sorting first would preview a different
        // subset than placed.
        this.overlayMesh.setIsSimple(false);
        BuildPipeline.BuildState action = state != null ? state : BuildPipeline.BuildState.PLACING;
        // Flood-fill seed is the cell a block would be placed into for the
        // live aim (replaceable hits in place, otherwise adjacent): open
        // space by construction, so the fill never starts buried inside a
        // solid block. Null (air aim, other modes) falls back to the shape
        // centerpoint in the flood runner.
        if (action == BuildPipeline.BuildState.PLACING
                && BuildSettings.CLIENT.getReplaceMode() == BuildSettings.ReplaceMode.FLOOD_FILL
                && hit != null) {
            BlockPos placeCell = resolvePlacePos(hit, action, level, player);
            blocks.floodSeed = SableCompat.isInSameSelection(level, anchor, placeCell) ? placeCell : null;
        } else {
            blocks.floodSeed = null;
        }
        // Full placement logic: modifiers, reach caps, tile-entity scans,
        // survival checks, replacement handling.
        try (SableCompat.SelectionScope ignored = SableCompat.pushSelection(level, anchor)) {
            BuildPipelineClient.CLIENT.processBlocks(blocks, player, action);
        }
        // Display ordering is generation order (already deterministic): the
        // old distance sort + full re-put cost O(n log n) per shape change
        // while nothing downstream needs it — the overlay bake is
        // order-independent, ghosts are sectioned, and feedback needs only
        // count + dims. Server placement keeps its own sort.
        // (sortByDistance retained on BlockSet for the server/legacy paths.)

        // Single pass: split + global flags + feedback bounds together, no
        // second hasEntriesWithStatus scan, no per-key map re-lookup, and no
        // separate min/max rescan in the feedback call. The outside-sublevel
        // rule (everything rejected) folds in afterwards by moving the whole
        // ok list at once — the common case stays one pass.
        List<BlockPos> ok = new ArrayList<>(blocks.size());
        List<BlockEntry> okEntries = new ArrayList<>(blocks.size());
        List<BlockPos> bad = new ArrayList<>();
        boolean outsideSublevel = false;
        boolean overCap = false;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockEntry weighed : blocks.values()) {
            BlockPos pos = weighed.blockPos;
            BlockEntry entry = weighed;
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            if (x < minX) {
                minX = x;
            }
            if (x > maxX) {
                maxX = x;
            }
            if (y < minY) {
                minY = y;
            }
            if (y > maxY) {
                maxY = y;
            }
            if (z < minZ) {
                minZ = z;
            }
            if (z > maxZ) {
                maxZ = z;
            }
            if (entry == null || entry.isValid()) {
                ok.add(pos);
                okEntries.add(entry);
            } else {
                if (entry.getStatus() == BlockStatus.OUTSIDE_REACH) {
                    outsideSublevel = true;
                }
                if (entry.getStatus() == BlockStatus.INSUFFICIENT_ITEMS) {
                    overCap = true;
                }
                bad.add(pos);
            }
        }
        if (outsideSublevel && !ok.isEmpty()) {
            bad.addAll(ok);
            ok.clear();
            okEntries.clear();
        }

        this.isBreaking = action == BuildPipeline.BuildState.BREAKING;
        boolean wantBlocks = !this.isBreaking
                && BuildSettings.CLIENT.getReplaceMode() != BuildSettings.ReplaceMode.ONLY_BLOCKS
                && !outsideSublevel;

        // Ghost states resolve here (was per-frame before caching). Trowel
        // rolls one random set per shape instead of shimmering every frame.
        // Mesh stays strictly placeable-only: survival-stock cuts show red in
        // the overlay + red count line instead of rendering as ghosts.
        // Iterates the retained entry refs — no map lookups.
        // Blocks that would pop without support are filtered here too (same
        // verdict as the server placement skip): no ghost, moved to the red
        // overlay list below.
        List<PreviewBlock> meshBlocks = new ArrayList<>();
        List<PreviewBlock> animatedBlocks = new ArrayList<>();
        List<BlockPos> unsupported = null;
        if (wantBlocks && !ok.isEmpty()) {
            BlockHitResult firstHit = BuildPipelineClient.getFirstClickHit();
            BlockState base = resolveBaseState(mc, player, firstHit, hit);
            boolean trowel = TrowelSystem.isTrowel(player.getMainHandItem());
            Map<Item, BlockState> trowelCache = trowel ? new HashMap<>() : null;
            for (int i = 0, n = ok.size(); i < n; i++) {
                BlockPos pos = ok.get(i);
                BlockEntry entry = okEntries.get(i);
                BlockState resolved = base;
                if (trowel && entry != null && entry.item instanceof BlockItem randomBlock) {
                    resolved = trowelCache.computeIfAbsent(entry.item,
                            item -> resolveBaseState(mc, player, randomBlock, new ItemStack(item), firstHit, hit));
                }
                if (resolved == null) {
                    continue;
                }
                if (entry != null) {
                    resolved = entry.applyTransforms(resolved);
                }
                if (!resolved.canSurvive(level, pos)) {
                    if (unsupported == null) {
                        unsupported = new ArrayList<>();
                    }
                    unsupported.add(pos);
                    continue;
                }
                // Animated entities tick — immediate path, never the baked mesh.
                if (resolved.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED) {
                    animatedBlocks.add(new PreviewBlock(pos, resolved));
                } else {
                    meshBlocks.add(new PreviewBlock(pos, resolved));
                }
            }
        }

        if (unsupported != null) {
            // Move unsupported positions from the placeable list to the
            // rejected overlay list, keeping the parallel entry list in sync.
            HashSet<BlockPos> unsupportedSet = new HashSet<>(unsupported);
            List<BlockPos> supported = new ArrayList<>(ok.size() - unsupported.size());
            List<BlockEntry> supportedEntries = new ArrayList<>(ok.size() - unsupported.size());
            for (int i = 0, n = ok.size(); i < n; i++) {
                if (unsupportedSet.contains(ok.get(i))) {
                    bad.add(ok.get(i));
                } else {
                    supported.add(ok.get(i));
                    supportedEntries.add(okEntries.get(i));
                }
            }
            ok = supported;
            okEntries = supportedEntries;
        }

        // Wrap, don't copy: these lists are never mutated after publish, so
        // unmodifiable views skip 4 full array copies per shape change.
        int alpha = key.blockAlpha();
        this.breakable = Collections.unmodifiableList(ok);
        this.unbreakable = bad.isEmpty() ? List.of() : Collections.unmodifiableList(bad);
        if (bad.isEmpty()) {
            this.all = this.breakable;
        } else {
            List<BlockPos> combined = new ArrayList<>(ok.size() + bad.size());
            combined.addAll(ok);
            combined.addAll(bad);
            this.all = Collections.unmodifiableList(combined);
        }
        this.animated = animatedBlocks.isEmpty() ? List.of() : Collections.unmodifiableList(animatedBlocks);
        this.overLimit = overCap;
        this.wantsBlocks = wantBlocks && !meshBlocks.isEmpty();
        this.shapedKey = key;
        this.shapedLevel = level;
        this.simplePreview = false;
        this.lastShapeNanos = System.nanoTime();

        // Border routing: coupled when async-boundary is on (rides the ghost
        // worker and swaps in later — but previews with no worker task
        // (breaking, ghost-less) bake it inline, or the box would never appear
        // at all). When off, the border goes to its own dedicated worker and
        // the render loop picks it up from there — never baked here.
        if (key.asyncBoundary()) {
            PreviewBoundaryWorker.cancel();
            this.submittedBoundaryKey = null;
            this.pendingBoundaryOk = List.of();
            this.pendingBoundaryBad = List.of();
            if (!this.wantsBlocks) {
                this.overlayMesh.adopt(level, PreviewOverlayMesh.bake(ok, bad, this.isBreaking));
                this.hasOverlay = !this.overlayMesh.isEmpty();
                this.boundaryKey = key;
            }
        } else {
            submitBoundaryTask(level, key);
        }

        // Sound + action-bar feedback, exactly like the old per-frame call —
        // internally it only dings when the size actually changed. Bounds come
        // from the split pass above: no second list rescan.
        RenderHandler.updateFeedbackSimple(ok.size() + bad.size(),
                new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ),
                state != null, state, this.overLimit, shapeInfo);

        if (this.wantsBlocks) {
            this.pendingMeshBlocks = Collections.unmodifiableList(meshBlocks);
            this.pendingAlpha = alpha;
            submitMeshTask(mc, level, key);
        } else {
            // Nothing for the worker: drop stale ghosts now, cancel any bake.
            PreviewBuildWorker.cancel();
            this.blockMesh.clear();
            this.meshKey = key;
            this.submittedKey = key;
            this.pendingMeshBlocks = List.of();
        }
    }

    // True when the new key differs from the shaped one only by aim: hover
    // block/face/point, eye block, or look direction. Anything structural (clicks,
    // mode, held item, options, limits, dimension…) must rebuild immediately.
    // Look is deliberately not compared: plane previews track it in air.
    private static boolean hoverOnlyChange(@Nullable PreviewShapeKey oldKey, PreviewShapeKey key) {
        if (oldKey == null) {
            return false;
        }
        return oldKey.mode() == key.mode()
                && oldKey.inProgress() == key.inProgress()
                && oldKey.buildState() == key.buildState()
                && Objects.equals(oldKey.selectionOrigin(), key.selectionOrigin())
                && Objects.equals(oldKey.firstHitPos(), key.firstHitPos())
                && oldKey.firstHitFace() == key.firstHitFace()
                && oldKey.heldItem() == key.heldItem()
                && oldKey.offhand() == key.offhand()
                && oldKey.trowel() == key.trowel()
                && oldKey.replaceMode() == key.replaceMode()
                && oldKey.blockAlpha() == key.blockAlpha()
                && oldKey.maxBlocks() == key.maxBlocks()
                && oldKey.axisLimit() == key.axisLimit()
                && oldKey.protectTiles() == key.protectTiles()
                && oldKey.fill() == key.fill()
                && oldKey.cubeFill() == key.cubeFill()
                && oldKey.sides() == key.sides()
                && oldKey.pointBuild() == key.pointBuild()
                && oldKey.planeAlign() == key.planeAlign()
                && oldKey.creative() == key.creative()
                && oldKey.asyncBoundary() == key.asyncBoundary()
                && oldKey.dimension().equals(key.dimension());
    }

    // Hands the retained bake inputs to the worker. Previous ghosts stay live
    // until the swap: seamless, no flicker.
    private void submitMeshTask(Minecraft mc, Level level, PreviewShapeKey key) {
        PreviewBuildWorker.submit(new PreviewBuildWorker.Task(
                PreviewBuildWorker.nextSeq(), this.modelGeneration, key,
                level, mc.getBlockRenderer(),
                this.pendingMeshBlocks, this.pendingAlpha,
                this.breakable, this.unbreakable, this.isBreaking));
        this.submittedKey = key;
        this.submittedFrame = this.frame;
    }

    // Hands the live overlay lists to the dedicated border worker. The lists
    // are unmodifiable snapshots that are never mutated after publish, so
    // sharing them with the worker is safe. The previous overlay keeps
    // drawing until the new bake swaps in: seamless, no flicker.
    private void submitBoundaryTask(Level level, PreviewShapeKey key) {
        this.pendingBoundaryOk = this.breakable;
        this.pendingBoundaryBad = this.unbreakable;
        this.pendingBoundaryBreaking = this.isBreaking;
        PreviewBoundaryWorker.submit(new PreviewBoundaryWorker.Task(
                PreviewBoundaryWorker.nextSeq(), this.modelGeneration, key,
                level,
                this.pendingBoundaryOk, this.pendingBoundaryBad, this.pendingBoundaryBreaking));
        this.submittedBoundaryKey = key;
        this.submittedBoundaryFrame = this.frame;
    }

    // -- swap-in (render thread: GL upload) -----------------------------------

    // Takes a finished bake if it is still the live shape; anything older
    // (superseded key, level hop, pre-reload bake) is freed un-drawn.
    private void drainReady(PreviewShapeKey liveKey, Level liveLevel) {
        BuiltPreview ready = PreviewBuildWorker.pollReady();
        if (ready == null) {
            return;
        }
        if (ready.modelGeneration() != this.modelGeneration
                || ready.level() != liveLevel
                || !ready.key().equals(liveKey)) {
            ready.discard();
            return;
        }
        try {
            this.blockMesh.adopt(liveLevel, ready.blockSections());
            if (liveKey.asyncBoundary() && ready.overlay() != null) {
                this.overlayMesh.adopt(liveLevel, ready.overlay());
                this.hasOverlay = !this.overlayMesh.isEmpty();
            }
        } catch (RuntimeException glFailure) {
            // GL upload all but guarantees success short of context loss (in
            // which case everything GL is gone anyway). Reset to empty; the
            // failing mesh was consumed by the upload itself.
            this.blockMesh.clear();
            this.hasOverlay = false;
        }
        this.meshKey = liveKey;
    }

    // Takes a finished dedicated-border bake if it is still the live shape;
    // anything older (superseded key, level hop, pre-reload bake) is freed
    // un-drawn. Render thread only (GL upload).
    private void drainBoundaryReady(PreviewShapeKey liveKey, Level liveLevel) {
        // Coupled path never produces boundary-worker output; polling would
        // only ever return stale discards, so skip it entirely.
        if (liveKey.asyncBoundary()) {
            return;
        }
        BuiltBoundary ready = PreviewBoundaryWorker.pollReady();
        if (ready == null) {
            return;
        }
        if (ready.modelGeneration() != this.modelGeneration
                || ready.level() != liveLevel
                || !ready.key().equals(liveKey)) {
            ready.discard();
            return;
        }
        try {
            this.overlayMesh.adopt(liveLevel, ready.overlay());
            this.hasOverlay = !this.overlayMesh.isEmpty();
        } catch (RuntimeException glFailure) {
            this.hasOverlay = false;
        }
        this.boundaryKey = liveKey;
    }

    // -- per-frame cost guards -------------------------------------------------

    // Vanilla already raycast this frame into mc.hitResult: reuse it when
    // Angel Placement is off instead of paying for a second clip. Falls back
    // to the full targeting path when vanilla missed (mod reach can exceed
    // vanilla's), when the hit is beyond mod reach, or while angel
    // air-targeting is active.
    private @Nullable BlockHitResult reuseVanillaHit(Minecraft mc, Player player) {
        if (!BuildPipelineClient.isAngelPlacementActive(player)
                && mc.hitResult instanceof BlockHitResult vanillaHit
                && vanillaHit.getType() == HitResult.Type.BLOCK) {
            double reach = this.configReach;
            if (vanillaHit.getLocation().distanceToSqr(player.getEyePosition()) <= reach * reach) {
                return vanillaHit;
            }
        }
        return BuildPipelineClient.getCurrentTargetHit(mc);
    }

    // Snapshot of the config reads on the key path. All of these are volatile
    // lookups that never change mid-drag; refresh at most twice a second, or
    // immediately when creative mode flips (which swaps the limit set).
    private void refreshConfigCache(Player player) {
        boolean creative = player.getAbilities().instabuild;
        long now = System.nanoTime();
        if (now - this.configNanos < CONFIG_CACHE_NANOS && creative == this.configCreative) {
            return;
        }
        this.configNanos = now;
        this.configCreative = creative;
        this.configPreviewRenderThrottleBlocks = Config.BUILDING_PREVIEW_RENDER_THROTTLE_BLOCKS.getAsInt();
        this.configMaxBlocks = Config.getBuildingMaxBlocksPlaced(player);
        this.configAxisLimit = Config.getBuildingMaxBlocksPerAxis(player);
        this.configReach = Config.getBuildingReach(player);
        this.configProtectTiles = readProtectTiles();
        this.configBlockAlpha = (int) (Config.getBuildingPreviewBlockTransparency() * 255.0F);
        this.configAsyncBoundary = readAsyncBoundary();
    }

    // -- key building (must stay cheap: raycast + getters only) -------------

    // Resolves the hovered block exactly like a click would land: reuse a
    // persistent Mesh vertex when hovering one, otherwise offset to air.
    // Returns null only when hovering across a Sable boundary (no preview).
    private @Nullable PreviewShapeKey buildKey(Player player, Level level,
                                               BuildModeEnum mode, boolean inProgress,
                                               BuildPipeline.@Nullable BuildState state,
                                               @Nullable BlockHitResult hit) {
        BlockPos hoverPos = hit != null ? hit.getBlockPos() : null;
        Direction hoverFace = hit != null ? hit.getDirection() : null;

        BlockPos hoverPoint = null;
        if (hit != null && mode.instance.usesDirectSecondPoint()) {
            BuildPipeline.BuildState action = state != null ? state : BuildPipeline.BuildState.PLACING;
            BlockPos marker = mode.instance.getSelectionMarker(hit.getBlockPos());
            hoverPoint = marker != null ? marker : resolvePlacePos(hit, action, level, player);
            BlockPos anchor = BuildPipelineClient.getSelectionOrigin() != null
                    ? BuildPipelineClient.getSelectionOrigin() : player.blockPosition();
            if (!SableCompat.isInSameSelection(level, anchor, hoverPoint)) {
                return null;
            }
        }

        BlockHitResult firstHit = BuildPipelineClient.getFirstClickHit();
        ItemStack held = player.getMainHandItem();
        Item heldItem = held.getItem();
        // 1-entry cache: the held item rarely changes between frames, but the
        // registry lookup behind isTrowel runs on every miss.
        boolean trowel;
        if (heldItem == this.trowelCacheItem) {
            trowel = this.trowelCacheValue;
        } else {
            trowel = TrowelSystem.isTrowel(held);
            this.trowelCacheItem = heldItem;
            this.trowelCacheValue = trowel;
        }
        Vec3 eye = player.getEyePosition();

        // Plane-derived previews (floor/wall/line/height) move with the look ray
        // even when the vanilla hit is null (mid-air). Without this the key is
        // identical frame-to-frame while rotating in air, so the preview freezes
        // instead of stopping at the imaginary plane. Direct-point modes ignore
        // look: their hover point already captures aim.
        float lookX = 0.0F;
        float lookY = 0.0F;
        float lookZ = 0.0F;
        if (inProgress && !mode.instance.usesDirectSecondPoint()) {
            Vec3 look = player.getLookAngle();
            lookX = (float) look.x;
            lookY = (float) look.y;
            lookZ = (float) look.z;
        }

        return new PreviewShapeKey(
                mode,
                inProgress,
                state,
                copyOf(BuildPipelineClient.getSelectionOrigin()),
                firstHit != null ? firstHit.getBlockPos() : null,
                firstHit != null ? firstHit.getDirection() : null,
                hoverPos != null ? hoverPos.immutable() : null,
                hoverFace,
                hoverPoint != null ? hoverPoint.immutable() : null,
                BlockPos.containing(eye),
                heldItem,
                player.getOffhandItem().getItem(),
                trowel,
                BuildSettings.CLIENT.getReplaceMode(),
                this.configBlockAlpha,
                this.configMaxBlocks,
                this.configAxisLimit,
                this.configProtectTiles,
                ModeOptions.getFill(),
                ModeOptions.getCubeFill(),
                ModeOptions.getSides(),
                ModeOptions.getPointBuild(),
                ModeOptions.getPlaneAlign(),
                player.getAbilities().instabuild,
                this.configAsyncBoundary,
                dimensionId(level),
                lookX,
                lookY,
                lookZ);
    }

    // The dimension registry id allocates a fresh String every call; cache it
    // per level instance (dimensions change rarely, instances never).
    private String dimensionId(Level level) {
        if (level != this.dimensionLevel) {
            this.dimensionLevel = level;
            this.dimensionId = level.dimension().location().toString();
        }
        return this.dimensionId;
    }

    // -- small helpers -------------------------------------------------------

    // Same placement-offset rule as clicks: solid blocks preview in the
    // adjacent air cell, replaceable ones (grass, water…) in place.
    private static BlockPos resolvePlacePos(BlockHitResult hit, BuildPipeline.BuildState action,
                                            Level level, Player player) {
        BlockPos hitPos = hit.getBlockPos();
        if (action == BuildPipeline.BuildState.BREAKING) {
            return hitPos;
        }
        if (BuildPipeline.isToolInteractionItem(player.getMainHandItem())) {
            return hitPos;
        }
        if (BuildSettings.CLIENT.shouldOffsetStartPosition()) {
            return hitPos;
        }
        return level.getBlockState(hitPos).canBeReplaced() ? hitPos : hitPos.relative(hit.getDirection());
    }

    private static @Nullable BlockState resolveBaseState(Minecraft mc, Player player,
                                                         @Nullable BlockHitResult firstHit,
                                                         @Nullable BlockHitResult hoverHit) {
        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof BlockItem blockItem) {
            return resolveBaseState(mc, player, blockItem, held, firstHit, hoverHit);
        }
        if (held.getItem() instanceof BucketItem bucket) {
            Fluid fluid = ((BucketItemAccessor) bucket).effortlessbuilding$getFluid();
            if (!fluid.isSame(Fluids.EMPTY)) {
                return fluid.defaultFluidState().createLegacyBlock();
            }
        }
        return null;
    }

    private static @Nullable BlockState resolveBaseState(Minecraft mc, Player player,
                                                         BlockItem blockItem, ItemStack stack,
                                                         @Nullable BlockHitResult firstHit,
                                                         @Nullable BlockHitResult hoverHit) {
        BlockHitResult hit = firstHit != null ? firstHit : hoverHit;
        if (hit == null) {
            return blockItem.getBlock().defaultBlockState();
        }
        BlockPlaceContext ctx = new OpenBlockPlaceContext(mc.level, player, InteractionHand.MAIN_HAND, stack, hit);
        BlockState state = blockItem.getBlock().getStateForPlacement(ctx);
        return state != null ? state : blockItem.getBlock().defaultBlockState();
    }

    private static boolean readProtectTiles() {
        try {
            return Config.BUILDING_PROTECT_TILE_ENTITIES.get();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean readAsyncBoundary() {
        try {
            return Config.BUILDING_ASYNC_BOUNDARY.get();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static @Nullable BlockPos copyOf(@Nullable BlockPos pos) {
        return pos != null ? pos.immutable() : null;
    }

    private static final class OpenBlockPlaceContext extends BlockPlaceContext {
        OpenBlockPlaceContext(Level level, Player player, InteractionHand hand, ItemStack stack, BlockHitResult hit) {
            super(level, player, hand, stack, hit);
        }
    }
}
