package nl.requios.effortlessbuilding.render.preview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
import net.minecraft.world.phys.Vec3;
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.BuildModes;
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
 * shape + border resolved synchronously on change, ghost-block tessellation
 * on {@link PreviewBuildWorker}.
 *
 * <p><b>Why the split is shaped this way:</b> the border must react the same
 * frame the crosshair moves onto a new block — no worker round-trip allowed —
 * while ghost tessellation (baked models, AO, thousands of quads) is the part
 * that hitches. So per key change the render thread does coordinates,
 * constraints, state resolution and the pure-math border bake immediately,
 * and only the model tessellation goes async. The previous ghost mesh keeps
 * drawing until the new one swaps in: seamless, no flicker.</p>
 *
 * <p>Set {@code building.async_boundary} to bake the border on the worker too
 * (smoother shape-change frames, but the border then lags with the ghosts).
 * Off by default.</p>
 *
 * <p><b>Huge shapes</b> (boundary volume past {@link #DETAILED_PREVIEW_BLOCK_LIMIT}
 * blocks, or past the configured max-blocks cap) skip all of that: the client
 * boundary feeds a single bounding-box overlay directly, client blocks are
 * never enumerated, ghosts stay off, and hover-only reshapes are throttled to
 * ~12Hz. Dims come from the boundary and the count is its volume (an upper
 * bound, red past the cap); placement itself was always server-authoritative.</p>
 *
 * <p>Validity split is preserved: over-limit / protected / out-of-reach blocks
 * stay flagged as <i>rejected</i> for the overlay, which draws the <i>exact
 * tool shape</i> — white fill + outline for placeable, red/grey for rejected.
 * The block mesh itself stays strictly placeable-only, and the count/dims
 * line turns red whenever the count cap cuts blocks (see isOverLimit).</p>
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
    private static final long HUGE_SHAPE_RESHAPE_MIN_NANOS = 80_000_000L;

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
    // True when the count cap (getBuildingMaxBlocksPlaced) cut blocks out of
    // this shape. Drives the red count/dims warning.
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

    // True while the live shape uses the huge-shape box path (no ghosts,
    // box overlay). Gates the hover-only reshape throttle in update().
    private boolean simplePreview;
    private long lastShapeNanos;

    // Raycast hit reused by the frame: computed once here, shared with the
    // selection-marker pass so the frame pays for exactly one raycast.
    private @Nullable BlockHitResult currentHit;

    // level.dimension().location().toString() allocates every call; the
    // dimension barely changes, so remember it per level instance.
    private @Nullable Level dimensionLevel;
    private String dimensionId = "";

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
        if (BuildModes.CLIENT.getBuildMode() == BuildModeEnum.DISABLED) {
            if (this.shapedKey != null || this.submittedKey != null || this.hasPreview()) {
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
            if (this.shapedKey != null || this.submittedKey != null || this.hasPreview()) {
                clear();
                RenderHandler.resetPreviewSize();
            }
            return;
        }

        BlockHitResult hit = BuildPipelineClient.getCurrentTargetHit(mc);
        this.currentHit = hit;
        PreviewShapeKey key = buildKey(player, level, mode, inProgress, state, hit);
        if (key == null) {
            // Hovered across a Sable boundary: nothing valid to show.
            clear();
            return;
        }

        // A finished bake only lands while it is still the live shape.
        drainReady(key, level);
        this.frame++;

        if (!key.equals(this.shapedKey) || level != this.shapedLevel) {
            if (this.simplePreview && hoverOnlyChange(this.shapedKey, key)
                    && System.nanoTime() - this.lastShapeNanos < HUGE_SHAPE_RESHAPE_MIN_NANOS) {
                // Huge box already on screen and only the cursor moved a
                // moment ago: hold the stale box briefly instead of
                // regenerating tens of thousands of blocks this frame.
            } else {
                shapeOnRenderThread(mc, player, level, mode, state, hit, key);
            }
        } else if (this.wantsBlocks && !key.equals(this.meshKey)
                && this.frame - this.submittedFrame > 120) {
            // Worker bake vanished without a trace (dropped race + no
            // follow-up change). Re-hand the retained inputs, no shape redo.
            submitMeshTask(mc, level, key);
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
        return this.shapedKey == null && this.submittedKey == null && !hasPreview();
    }

    /**
     * Drops GPU buffers + snapshots and cancels in-flight work.
     */
    public void clear() {
        PreviewBuildWorker.cancel();
        this.blockMesh.clear();
        this.overlayMesh.clear();
        this.shapedKey = null;
        this.meshKey = null;
        this.submittedKey = null;
        this.shapedLevel = null;
        this.breakable = List.of();
        this.unbreakable = List.of();
        this.all = List.of();
        this.animated = List.of();
        this.pendingMeshBlocks = List.of();
        this.hasOverlay = false;
        this.wantsBlocks = false;
        this.overLimit = false;
        this.simplePreview = false;
        this.lastShapeNanos = 0;
        this.currentHit = null;
        this.dimensionLevel = null;
    }

    /**
     * Resource reload: same as clear, plus retire pre-reload bakes.
     */
    public void onModelsBaked() {
        clear();
        this.modelGeneration++;
    }

    // -- shape path (render thread, key-gated) --------------------------------

    // Everything except ghost tessellation, done the moment the key changes:
    // hover publish, coordinates, constraints, state resolution, overlay bake,
    // feedback — then the ghosts go to the worker. The overlay/message are
    // therefore instantaneous even while ghosts still bake.
    private void shapeOnRenderThread(Minecraft mc, Player player, Level level,
                                     BuildModeEnum mode, BuildPipeline.@Nullable BuildState state,
                                     @Nullable BlockHitResult hit, PreviewShapeKey key) {
        // Publish the hover point BEFORE generating coordinates —
        // findCoordinates reads it as the in-progress second/third point.
        if (mode.instance.usesDirectSecondPoint()) {
            mode.instance.setPreviewPoint(key.hoverPoint());
        }


        BlockPos anchor = key.selectionOrigin() != null ? key.selectionOrigin() : player.blockPosition();
        // Boundary first: pure coordinate math (first/second/third points +
        // clamps + mode-specific expansion), no block enumeration. Huge shapes
        // render from this alone and never pay for getClientBlocks.
        AABB previewBoundary = mode.instance.getClientBoundary(player);
        if (previewBoundary == null) {
            clear();
            RenderHandler.resetPreviewSize();
            this.shapedKey = key;
            this.shapedLevel = level;
            return;
        }

        int maxBlocks = key.maxBlocks();
        BlockSet blocks = new BlockSet();
        try (SableCompat.SelectionScope ignored = SableCompat.pushSelection(level, anchor)) {
            mode.instance.getClientBlocks(blocks, player); //Get the blocks from the selected anchor points, first pos, second pos, etc...
        }
        if (blocks.isEmpty()) {
            clear();
            RenderHandler.resetPreviewSize();
            this.shapedKey = key;
            this.shapedLevel = level;
            return;
        }

        if (blocks.size() > maxBlocks * 2) {
            // Safety net: boundary underestimated (should not happen now that
            // every mode expands mirrors/squares), fall back to the box.
            shapeSimple(level, state, key, previewBoundary, maxBlocks);
            return;
        }
        shapeDetailed(mc, player, level, state, hit, key, blocks, anchor);
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
        boolean overCap = volume > maxBlocks;
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
        this.overlayMesh.adopt(level, PreviewOverlayMesh.bakeBoundingBox(min, max, this.isBreaking));
        this.overlayMesh.setIsSimple(true);
        this.hasOverlay = !this.overlayMesh.isEmpty();

        // No worker traffic at all: drop stale ghosts, retire any bake.
        PreviewBuildWorker.cancel();
        this.blockMesh.clear();
        this.meshKey = key;
        this.submittedKey = key;
        this.pendingMeshBlocks = List.of();

        // Count + dims from the already-known bounds: no list rescan.
        RenderHandler.updateFeedbackSimple(estimatedCount, min, max, state != null, state, overCap);
    }

    // Full-fidelity path for sensibly-sized shapes: constraints, ghosts,
    // exact per-block overlay. Bounded by DETAILED_PREVIEW_BLOCK_LIMIT, so
    // every O(N log N) step here has a hard ceiling.
    private void shapeDetailed(Minecraft mc, Player player, Level level,
                               BuildPipeline.@Nullable BuildState state,
                               @Nullable BlockHitResult hit, PreviewShapeKey key,
                               BlockSet blocks, BlockPos anchor) {
        // NOTE: no sorting before processBlocks — and that is deliberate.
        // ConstraintSystem keeps the first N blocks in GENERATION order, and
        // the server pipeline caps the exact same way. Sorting first would
        // preview a different subset (closest-first blob) than placed.
        this.overlayMesh.setIsSimple(false);
        BuildPipeline.BuildState action = state != null ? state : BuildPipeline.BuildState.PLACING;
        try (SableCompat.SelectionScope ignored = SableCompat.pushSelection(level, anchor)) {
            BuildPipelineClient.CLIENT.processBlocks(blocks, player, action);
        }
        // Display ordering only (deterministic lists); never affects which
        // blocks were kept valid above.
        blocks.sortByDistance();

        // Single entry-set pass: split + global flags together, no second
        // hasEntriesWithStatus scan and no per-key map re-lookup. The
        // outside-sublevel rule (everything rejected) folds in afterwards by
        // moving the whole ok list at once — the common case stays one pass.
        List<BlockPos> ok = new ArrayList<>(blocks.size());
        List<BlockEntry> okEntries = new ArrayList<>(blocks.size());
        List<BlockPos> bad = new ArrayList<>();
        boolean outsideSublevel = false;
        boolean overCap = false;
        for (BlockEntry weighed : blocks.values()) {
            BlockPos pos = weighed.blockPos;
            BlockEntry entry = weighed;
            if (entry == null || entry.isValid()) {
                ok.add(pos);
                okEntries.add(entry);
            } else {
                if (entry.getStatus() == BlockStatus.OUTSIDE_REACH) {
                    outsideSublevel = true;
                }
                if (entry.getStatus() == BlockStatus.MAX_BLOCKS_EXCEEDED) {
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
        // Mesh stays strictly placeable-only: over-cap blocks show red in the
        // overlay + red count line instead of rendering as ghosts. Iterates
        // the retained entry refs — no map lookups.
        List<PreviewBlock> meshBlocks = new ArrayList<>();
        List<PreviewBlock> animatedBlocks = new ArrayList<>();
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
                // Animated entities tick — immediate path, never the baked mesh.
                if (resolved.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED) {
                    animatedBlocks.add(new PreviewBlock(pos, resolved));
                } else {
                    meshBlocks.add(new PreviewBlock(pos, resolved));
                }
            }
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

        // Border: synchronous by default so it lands the same frame as the
        // shape. With async-boundary on, it rides the worker with the ghosts.
        if (!key.asyncBoundary()) {
            this.overlayMesh.adopt(level, PreviewOverlayMesh.bake(ok, bad, this.isBreaking));
            this.hasOverlay = !this.overlayMesh.isEmpty();
        }

        // Sound + action-bar feedback, exactly like the old per-frame call —
        // internally it only dings when the size actually changed.
        RenderHandler.updateFeedback(this.all, state != null, state, this.overLimit);

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
    // block/face/point or eye block. Anything structural (clicks, mode, held
    // item, options, limits, dimension…) must rebuild immediately.
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
        Vec3 eye = player.getEyePosition();

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
                held.getItem(),
                TrowelSystem.isTrowel(held),
                BuildSettings.CLIENT.getReplaceMode(),
                (int) (Config.getBuildingPreviewBlockTransparency() * 255.0F),
                Config.getBuildingMaxBlocksPlaced(player),
                Config.getBuildingMaxBlocksPerAxis(player),
                readProtectTiles(),
                ModeOptions.getFill(),
                ModeOptions.getCubeFill(),
                ModeOptions.getSides(),
                ModeOptions.getPointBuild(),
                player.getAbilities().instabuild,
                readAsyncBoundary(),
                dimensionId(level));
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
