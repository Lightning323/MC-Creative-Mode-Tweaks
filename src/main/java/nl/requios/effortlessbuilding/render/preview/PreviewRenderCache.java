package nl.requios.effortlessbuilding.render.preview;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * <p>Validity split is preserved: over-limit / protected / out-of-reach blocks
 * stay flagged as <i>rejected</i> for the overlay, which draws the <i>exact
 * tool shape</i> — white fill + outline for placeable, red/grey for rejected.
 * The block mesh itself stays strictly placeable-only, and the count/dims
 * line turns red whenever the count cap cuts blocks (see isOverLimit).</p>
 */
public final class PreviewRenderCache {
    private static final PreviewRenderCache INSTANCE = new PreviewRenderCache();

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

    // Bumped on resource reload so pre-reload bakes can never go live.
    private int modelGeneration;

    private PreviewRenderCache() {
        this.overlayMesh = new PreviewOverlayMesh(
                ResourceLocation.fromNamespaceAndPath("creative_mode_tweaks", "textures/special/checkerboard.png"),
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
            shapeOnRenderThread(mc, player, level, mode, state, hit, key);
        } else if (this.wantsBlocks && !key.equals(this.meshKey)
                && this.frame - this.submittedFrame > 120) {
            // Worker bake vanished without a trace (dropped race + no
            // follow-up change). Re-hand the retained inputs, no shape redo.
            submitMeshTask(mc, level, key);
        }
    }

    /** Ghost blocks (cached sections). No-op until the first bake swaps in. */
    public void renderBlocks(Level level, double camX, double camY, double camZ,
                             Matrix4f modelView, Matrix4f projection) {
        this.blockMesh.render(level, camX, camY, camZ, modelView, projection);
    }

    /** Tinted fill boxes (cached sections, always live). */
    public void renderFill(Level level, double camX, double camY, double camZ,
                           Matrix4f modelView, Matrix4f projection) {
        if (!this.hasOverlay) {
            return;
        }
        this.overlayMesh.renderFill(level, camX, camY, camZ, modelView, projection);
    }

    /** Border outline (cached sections, always live). */
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

    /** Breakable + unbreakable, for the count/dims action-bar line. */
    public List<BlockPos> allPositions() {
        return this.all;
    }

    public boolean isBreaking() {
        return this.isBreaking;
    }

    /** True when the count cap cut blocks: the shape won't fully build. */
    public boolean isOverLimit() {
        return this.overLimit;
    }

    public boolean hasPreview() {
        return !this.breakable.isEmpty() || !this.unbreakable.isEmpty();
    }

    /** Drops GPU buffers + snapshots and cancels in-flight work. */
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
    }

    /** Resource reload: same as clear, plus retire pre-reload bakes. */
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
        BlockSet blocks = new BlockSet();
        try (SableCompat.SelectionScope ignored = SableCompat.pushSelection(level, anchor)) {
            mode.instance.findCoordinates(blocks, player);
        }
        if (blocks.isEmpty()) {
            clear();
            RenderHandler.resetPreviewSize();
            this.shapedKey = key;
            this.shapedLevel = level;
            return;
        }

        // NOTE: no sorting before processBlocks — and that is deliberate.
        // ConstraintSystem keeps the first N blocks in GENERATION order, and
        // the server pipeline caps the exact same way. Sorting first would
        // preview a different subset (closest-first blob) than placed.
        BuildPipeline.BuildState action = state != null ? state : BuildPipeline.BuildState.PLACING;
        try (SableCompat.SelectionScope ignored = SableCompat.pushSelection(level, anchor)) {
            BuildPipelineClient.CLIENT.processBlocks(blocks, player, action);
        }
        // Display ordering only (deterministic lists); never affects which
        // blocks were kept valid above.
        blocks.sortByDistance();

        List<BlockPos> ok = new ArrayList<>();
        List<BlockPos> bad = new ArrayList<>();
        boolean outsideSublevel = blocks.hasEntriesWithStatus(BlockStatus.OUTSIDE_REACH);
        boolean overCap = false;
        for (BlockPos pos : blocks.keySet()) {
            BlockEntry entry = blocks.get(pos);
            if (outsideSublevel || (entry != null && !entry.isValid())) {
                bad.add(pos);
            } else {
                ok.add(pos);
            }
            if (entry != null && entry.getStatus() == BlockStatus.MAX_BLOCKS_EXCEEDED) {
                overCap = true;
            }
        }

        this.isBreaking = action == BuildPipeline.BuildState.BREAKING;
        boolean wantBlocks = !this.isBreaking
                && BuildSettings.CLIENT.getReplaceMode() != BuildSettings.ReplaceMode.ONLY_BLOCKS
                && !outsideSublevel;

        // Ghost states resolve here (was per-frame before caching). Trowel
        // rolls one random set per shape instead of shimmering every frame.
        // Mesh stays strictly placeable-only: over-cap blocks show red in the
        // overlay + red count line instead of rendering as ghosts.
        List<PreviewBlock> meshBlocks = new ArrayList<>();
        List<PreviewBlock> animatedBlocks = new ArrayList<>();
        if (wantBlocks) {
            BlockHitResult firstHit = BuildPipelineClient.getFirstClickHit();
            BlockState base = resolveBaseState(mc, player, firstHit, hit);
            Map<Item, BlockState> trowelCache = new HashMap<>();
            boolean trowel = TrowelSystem.isTrowel(player.getMainHandItem());
            for (BlockPos pos : ok) {
                BlockState resolved = base;
                BlockEntry entry = blocks.get(pos);
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

        int alpha = key.blockAlpha();
        this.breakable = List.copyOf(ok);
        this.unbreakable = List.copyOf(bad);
        List<BlockPos> combined = new ArrayList<>(ok.size() + bad.size());
        combined.addAll(ok);
        combined.addAll(bad);
        this.all = List.copyOf(combined);
        this.animated = List.copyOf(animatedBlocks);
        this.overLimit = overCap;
        this.wantsBlocks = wantBlocks && !meshBlocks.isEmpty();
        this.shapedKey = key;
        this.shapedLevel = level;

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
            this.pendingMeshBlocks = List.copyOf(meshBlocks);
            this.pendingAlpha = alpha;
            submitMeshTask(mc, level, key);
        } else {
            // Nothing for the worker: drop stale ghosts now, cancel any bake.
            PreviewBuildWorker.cancel();
            this.blockMesh.clear();
            this.meshKey = key;
            this.submittedKey = key;
        }
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
    private static @Nullable PreviewShapeKey buildKey(Player player, Level level,
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
                level.dimension().location().toString());
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
