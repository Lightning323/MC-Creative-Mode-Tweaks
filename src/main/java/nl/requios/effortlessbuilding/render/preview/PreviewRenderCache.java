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
 * Central preview cache: turns per-frame rendering into "hash cheap inputs,
 * reuse expensive outputs".
 *
 * <p><b>Per frame (cheap):</b> one crosshair raycast, ~15 config getters,
 * one {@link PreviewShapeKey} compare. On a key hit nothing else runs except
 * GPU draws — no shape generation, no sorting, no world lookups, no
 * tessellation, no border math.</p>
 *
 * <p><b>On shape change only:</b> snap the hover point (same marker logic as
 * clicks), {@code findCoordinates}, {@code processBlocks} (constraints),
 * distance sort, state resolution, then rebuild {@link PreviewBlockMesh} and
 * {@link PreviewOverlayMesh}. Animated block entities are collected here but
 * drawn immediately each frame — their models tick, so they can never cache.</p>
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

    // Last built outputs. Lists are plain snapshots — safe to read on later
    // frames because findCoordinates hands us a fresh BlockSet each rebuild.
    private PreviewShapeKey lastKey;
    private Level lastLevel;
    private List<BlockPos> breakable = List.of();
    private List<BlockPos> unbreakable = List.of();
    // Combined view for the action-bar feedback (count + dims). Built once per
    // shape so the per-frame feedback call allocates nothing.
    private List<BlockPos> all = List.of();
    private List<PreviewBlock> animated = List.of();
    private boolean hasBlockMesh;
    private boolean hasOverlay;
    // True when the count cap (getBuildingMaxBlocksPlaced) cut blocks out of
    // this shape. Drives the red count/dims warning; part of the rebuild,
    // not the per-frame key, since it derives from the constraint results.
    private boolean overLimit;
    private boolean isBreaking;

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
            clear();
            RenderHandler.resetPreviewSize();
            return;
        }

        BuildModeEnum mode = BuildModes.CLIENT.getBuildMode();
        boolean inProgress = !mode.instance.isFirstClick();
        BuildPipeline.BuildState state = BuildPipelineClient.getBuildState();

        // Single-block cursor with no sequence: old code explicitly showed no
        // big preview here, so keep the meshes empty (markers still draw).
        if (!inProgress && state == null) {
            if (this.lastKey != null || !this.breakable.isEmpty() || !this.unbreakable.isEmpty()) {
                clear();
                RenderHandler.resetPreviewSize();
            }
            this.lastLevel = level;
            return;
        }

        PreviewShapeKey key = buildKey(mc, player, level, mode, inProgress, state);
        if (key == null) {
            // Hovered across a Sable boundary: nothing valid to show.
            clear();
            return;
        }
        if (key.equals(this.lastKey) && level == this.lastLevel) {
            return;
        }

        rebuild(mc, player, level, mode, state, key);
        this.lastKey = key;
        this.lastLevel = level;
    }

    /** Ghost blocks (cached sections). No-op when the mesh is empty. */
    public void renderBlocks(Level level, double camX, double camY, double camZ,
                             Matrix4f modelView, Matrix4f projection) {
        if (!this.hasBlockMesh) {
            return;
        }
        this.blockMesh.render(level, camX, camY, camZ, modelView, projection);
    }

    /** Tinted fill boxes (cached sections). */
    public void renderFill(Level level, double camX, double camY, double camZ,
                           Matrix4f modelView, Matrix4f projection) {
        if (!this.hasOverlay) {
            return;
        }
        this.overlayMesh.renderFill(level, camX, camY, camZ, modelView, projection);
    }

    /** Border outline (cached sections). */
    public void renderOutline(Level level, double camX, double camY, double camZ,
                              Matrix4f modelView, Matrix4f projection) {
        if (!this.hasOverlay) {
            return;
        }
        this.overlayMesh.renderOutline(level, camX, camY, camZ, modelView, projection);
    }

    /**
     * Animated block entities can't cache (models tick), so these draw via
     * the immediate path each frame. Normally zero or a handful.
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

    /** Drops GPU buffers + snapshots (dimension hop, mode disable, reload). */
    public void clear() {
        this.blockMesh.clear();
        this.overlayMesh.clear();
        this.lastKey = null;
        this.lastLevel = null;
        this.breakable = List.of();
        this.unbreakable = List.of();
        this.all = List.of();
        this.animated = List.of();
        this.hasBlockMesh = false;
        this.hasOverlay = false;
        this.overLimit = false;
    }

    // -- key building (must stay cheap: raycast + getters only) -------------

    // Resolves the hovered block exactly like a click would land: reuse a
    // persistent Mesh vertex when hovering one, otherwise offset to air.
    // Returns null only when hovering across a Sable boundary (no preview).
    private static @Nullable PreviewShapeKey buildKey(Minecraft mc, Player player, Level level,
                                                      BuildModeEnum mode, boolean inProgress,
                                                      BuildPipeline.@Nullable BuildState state) {
        BlockHitResult hit = BuildPipelineClient.getCurrentTargetHit(mc);
        BlockPos hoverPos = hit != null ? hit.getBlockPos() : null;
        Direction hoverFace = hit != null ? hit.getDirection() : null;

        BlockPos hoverPoint = null;
        if (hit != null && mode.instance.usesDirectSecondPoint()) {
            BuildPipeline.BuildState action = state != null ? state : BuildPipeline.BuildState.PLACING;
            BlockPos marker = mode.instance.getSelectionMarker(hit.getBlockPos());
            hoverPoint = marker != null ? marker : resolvePlacePos(hit, action, level);
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
                level.dimension().location().toString());
    }

    // -- rebuild (expensive: runs only when the key changed) ----------------

    private void rebuild(Minecraft mc, Player player, Level level,
                         BuildModeEnum mode, BuildPipeline.@Nullable BuildState state,
                         PreviewShapeKey key) {
        // Publish the hover point to the mode BEFORE generating coordinates —
        // findCoordinates reads it as the in-progress second/third point.
        if (mode.instance.usesDirectSecondPoint()) {
            mode.instance.setPreviewSecondPoint(key.hoverPoint());
        }

        BlockPos anchor = key.selectionOrigin() != null ? key.selectionOrigin() : player.blockPosition();
        BlockSet blocks = new BlockSet();
        try (SableCompat.SelectionScope ignored = SableCompat.pushSelection(level, anchor)) {
            mode.instance.findCoordinates(blocks, player);
        }
        if (blocks.isEmpty()) {
            clearKeepKey();
            RenderHandler.resetPreviewSize();
            return;
        }

        // NOTE: no sorting before processBlocks — and that is deliberate.
        // ConstraintSystem keeps the first N blocks in GENERATION order, and the
        // server pipeline (BuildModeSystem -> ... -> ConstraintSystem, no sort)
        // caps the exact same way. Sorting first would keep the N closest to
        // the first click instead, carving a rounded blob out of big shapes
        // while the server places a flat generation-ordered slice. The preview
        // must show what will actually be placed, so membership matches.
        BuildPipeline.BuildState action = state != null ? state : BuildPipeline.BuildState.PLACING;
        try (SableCompat.SelectionScope ignored = SableCompat.pushSelection(level, anchor)) {
            BuildPipelineClient.CLIENT.processBlocks(blocks, player, action);
        }
        // Display ordering only (deterministic lists); must not affect which
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
        this.overLimit = overCap;

        this.isBreaking = action == BuildPipeline.BuildState.BREAKING;
        boolean wantBlocks = !this.isBreaking
                && BuildSettings.CLIENT.getReplaceMode() != BuildSettings.ReplaceMode.ONLY_BLOCKS;

        // Resolve ghost states once per shape (was per frame). Trowel rolls
        // one random set per shape instead of shimmering every frame.
        //
        // The mesh renders strictly what will be placed: valid blocks only.
        // Anything cut by getBuildingMaxBlocksPlaced stays out of the mesh and
        // shows red in the overlay instead, with the count/dims line turned
        // red (see isOverLimit) so it reads as "this won't fully build".
        List<PreviewBlock> meshBlocks = new ArrayList<>();
        List<PreviewBlock> animatedBlocks = new ArrayList<>();
        if (wantBlocks && !outsideSublevel) {
            BlockState base = resolveBaseState(mc, player);
            Map<Item, BlockState> trowelCache = new HashMap<>();
            boolean trowel = TrowelSystem.isTrowel(player.getMainHandItem());
            for (BlockPos pos : ok) {
                BlockState s = base;
                BlockEntry entry = blocks.get(pos);
                if (trowel && entry != null && entry.item instanceof BlockItem randomBlock) {
                    s = trowelCache.computeIfAbsent(entry.item,
                            item -> resolveBaseState(mc, player, randomBlock, new ItemStack(item)));
                }
                if (s == null) {
                    continue;
                }
                if (entry != null) {
                    s = entry.applyTransforms(s);
                }
                // Animated entities tick — immediate path, never the static mesh.
                if (s.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED) {
                    animatedBlocks.add(new PreviewBlock(pos, s));
                } else {
                    meshBlocks.add(new PreviewBlock(pos, s));
                }
            }
        }

        int alpha = key.blockAlpha();
        if (wantBlocks && !meshBlocks.isEmpty()) {
            this.blockMesh.update(mc, level, meshBlocks, alpha);
            this.hasBlockMesh = !this.blockMesh.isEmpty();
        } else {
            this.blockMesh.clear();
            this.hasBlockMesh = false;
        }

        // Overlay always draws the EXACT tool shape: white fill + outline for
        // placeable, red/grey for rejected (over-limit, protected, …).
        if (!ok.isEmpty() || !bad.isEmpty()) {
            this.overlayMesh.update(level, ok, bad, this.isBreaking);
            this.hasOverlay = !this.overlayMesh.isEmpty();
        } else {
            this.overlayMesh.clear();
            this.hasOverlay = false;
        }

        this.breakable = List.copyOf(ok);
        this.unbreakable = List.copyOf(bad);
        this.animated = List.copyOf(animatedBlocks);

        // Sound + action-bar feedback, exactly like the old per-frame call —
        // internally it only dings when the size actually changed.
        List<BlockPos> combined = new ArrayList<>(ok.size() + bad.size());
        combined.addAll(ok);
        combined.addAll(bad);
        this.all = List.copyOf(combined);
        RenderHandler.updateFeedback(this.all, state != null, state, this.overLimit);
    }

    private void clearKeepKey() {
        this.blockMesh.clear();
        this.overlayMesh.clear();
        this.breakable = List.of();
        this.unbreakable = List.of();
        this.all = List.of();
        this.animated = List.of();
        this.hasBlockMesh = false;
        this.hasOverlay = false;
        this.overLimit = false;
    }

    // -- small helpers (mirrors of click-time logic) -------------------------

    // Same placement-offset rule as clicks: solid blocks preview in the
    // adjacent air cell, replaceable ones (grass, water…) in place.
    private static BlockPos resolvePlacePos(BlockHitResult hit, BuildPipeline.BuildState action, Level level) {
        BlockPos hitPos = hit.getBlockPos();
        if (action == BuildPipeline.BuildState.BREAKING) {
            return hitPos;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && BuildPipeline.isToolInteractionItem(mc.player.getMainHandItem())) {
            return hitPos;
        }
        if (BuildSettings.CLIENT.shouldOffsetStartPosition()) {
            return hitPos;
        }
        return level.getBlockState(hitPos).canBeReplaced() ? hitPos : hitPos.relative(hit.getDirection());
    }

    private static BlockState resolveBaseState(Minecraft mc, Player player) {
        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof BlockItem blockItem) {
            return resolveBaseState(mc, player, blockItem, held);
        }
        if (held.getItem() instanceof BucketItem bucket) {
            Fluid fluid = ((BucketItemAccessor) bucket).effortlessbuilding$getFluid();
            if (!fluid.isSame(Fluids.EMPTY)) {
                return fluid.defaultFluidState().createLegacyBlock();
            }
        }
        return null;
    }

    private static BlockState resolveBaseState(Minecraft mc, Player player,
                                               BlockItem blockItem, ItemStack stack) {
        BlockHitResult hit = BuildPipelineClient.getFirstClickHit();
        if (hit == null) {
            hit = BuildPipelineClient.getCurrentTargetHit(mc);
            if (hit == null) {
                return blockItem.getBlock().defaultBlockState();
            }
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

    private static @Nullable BlockPos copyOf(@Nullable BlockPos pos) {
        return pos != null ? pos.immutable() : null;
    }

    private static final class OpenBlockPlaceContext extends BlockPlaceContext {
        OpenBlockPlaceContext(Level level, Player player, InteractionHand hand, ItemStack stack, BlockHitResult hit) {
            super(level, player, hand, stack, hit);
        }
    }
}
