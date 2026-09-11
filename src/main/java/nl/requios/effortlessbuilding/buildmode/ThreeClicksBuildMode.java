package nl.requios.effortlessbuilding.buildmode;

import java.util.ArrayList;
import java.util.List;

import it.unimi.dsi.fastutil.longs.LongConsumer;
import net.minecraft.world.phys.AABB;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipeline;
import org.lightning323.creative_mode_tweaks.Config;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public abstract class ThreeClicksBuildMode extends BaseBuildMode {
    protected BlockEntry firstBlockEntry;
    protected BlockEntry secondBlockEntry;
    private boolean twoPointBuild;
    private @Nullable BlockPos previewSecondPoint;

    public void initialize() {
        super.initialize();
        this.firstBlockEntry = null;
        this.secondBlockEntry = null;
        this.twoPointBuild = false;
        this.previewSecondPoint = null;
    }

    public boolean onClick(BlockSet blocks, BlockPos clickedPos, Player player) {
        if (this.clicks == 0) {
            this.twoPointBuild = this.supportsTwoPointBuild() && ModeOptions.isTwoPointBuild();
        }

        if (this.twoPointBuild) {
            ++this.clicks;
            if (this.clicks == 1) {
                this.firstBlockEntry = new BlockEntry(clickedPos);
                return false;
            } else {
                this.secondBlockEntry = new BlockEntry(clickedPos);
                return true;
            }
        }

        super.onClick(blocks, clickedPos, player);
        if (this.clicks == 1) {
            this.firstBlockEntry = new BlockEntry(clickedPos);
        } else {
            if (this.clicks != 2) {
                return true;
            }

            BlockPos secondPos = this.findSecondPos(player, this.firstBlockEntry.blockPos, true);
            if (secondPos == null) {
                this.clicks = 1;
                return false;
            }

            this.secondBlockEntry = new BlockEntry(secondPos);
        }

        return false;
    }

    @Override
    public AABB getClientBoundary(Player player) {
        if (this.clicks == 0 || this.firstBlockEntry == null || this.firstBlockEntry.blockPos == null) {
            return null;
        }
        int axisLimit = Config.getBuildingMaxBlocksPerAxis(player);
        BlockPos firstPos = this.firstBlockEntry.blockPos;

        if (this.twoPointBuild) {
            BlockPos secondPos = this.clicks == 1 ? this.previewSecondPoint
                    : (this.secondBlockEntry != null ? this.secondBlockEntry.blockPos : null);
            if (secondPos == null) {
                return null;
            }
            BlockPos boundedSecond = limitToBuildRange(player, firstPos, secondPos);
            // Two-point builds place getFinalBlocks(first, second, second).
            return getTwoPointBoundary(firstPos, boundedSecond);
        }

        if (this.clicks == 1) {
            BlockPos secondPos = this.findSecondPos(player, firstPos, true);
            if (secondPos == null) {
                return null;
            }
            BlockPos clampedSecond = clampPos(firstPos, secondPos, axisLimit);
            return getIntermediateBoundary(firstPos, clampedSecond);
        } else {
            if (this.secondBlockEntry == null || this.secondBlockEntry.blockPos == null) {
                return null;
            }
            BlockPos secondPos = this.secondBlockEntry.blockPos;
            BlockPos thirdPos = this.findThirdPos(player, firstPos, secondPos, true);
            if (thirdPos == null) {
                return null;
            }
            BlockPos clampedSecond = clampPos(firstPos, secondPos, axisLimit);
            BlockPos clampedThird = clampPos(firstPos, thirdPos, axisLimit);
            return getFinalBoundary(firstPos, clampedSecond, clampedThird);
        }
    }

    /**
     * Boundary of the intermediate (first-click) preview. Defaults to the
     * full-block box around both clamped points; modes with mirrored previews
     * (center-started circles) override this.
     */
    protected AABB getIntermediateBoundary(BlockPos firstPos, BlockPos clampedSecond) {
        return toFullBlockAABB(
                Math.min(firstPos.getX(), clampedSecond.getX()),
                Math.min(firstPos.getY(), clampedSecond.getY()),
                Math.min(firstPos.getZ(), clampedSecond.getZ()),
                Math.max(firstPos.getX(), clampedSecond.getX()),
                Math.max(firstPos.getY(), clampedSecond.getY()),
                Math.max(firstPos.getZ(), clampedSecond.getZ()));
    }

    /**
     * Boundary of a two-point selection. Defaults to the final boundary with
     * the second point standing in for the third; modes whose two-point shape
     * extends beyond the raw endpoints (e.g. spheres through both clicks)
     * override this.
     */
    protected AABB getTwoPointBoundary(BlockPos firstPos, BlockPos boundedSecond) {
        return getFinalBoundary(firstPos, boundedSecond, boundedSecond);
    }

    /**
     * Boundary of the final (second-click) preview. Defaults to the full-block
     * box around all three clamped points. The hovered third point shares the
     * second point's plane coordinates (findHeight keeps second XZ), so this
     * already covers the second point for wall/cylinder/slope-style shapes;
     * modes that expand beyond the raw points (mirrored spheres, squared
     * pyramids) override this.
     */
    protected AABB getFinalBoundary(BlockPos firstPos, BlockPos clampedSecond, BlockPos clampedThird) {
        return toFullBlockAABB(
                Math.min(firstPos.getX(), Math.min(clampedSecond.getX(), clampedThird.getX())),
                Math.min(firstPos.getY(), Math.min(clampedSecond.getY(), clampedThird.getY())),
                Math.min(firstPos.getZ(), Math.min(clampedSecond.getZ(), clampedThird.getZ())),
                Math.max(firstPos.getX(), Math.max(clampedSecond.getX(), clampedThird.getX())),
                Math.max(firstPos.getY(), Math.max(clampedSecond.getY(), clampedThird.getY())),
                Math.max(firstPos.getZ(), Math.max(clampedSecond.getZ(), clampedThird.getZ())));
    }

    /** Builds a block-aligned AABB from inclusive block min/max corners. */
    protected static AABB toFullBlockAABB(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    @Override
    public void getPlacementBlocks(BlockSet blocks, Player player, boolean fast) {
        if (this.twoPointBuild) {
            this.findTwoPointCoordinates(blocks, player, fast);
            return;
        }

        if (this.clicks == 0 || this.firstBlockEntry == null) {
            return;
        }

        BlockPos firstPos = this.firstBlockEntry.blockPos;

        if (this.clicks == 1) {
            BlockPos secondPos = this.findSecondPos(player, firstPos, true);
            if (secondPos == null) {
                return;
            }

            blocks.clear();
            if (fast) {
                // Bare positions only: streams packed longs, no intermediate list.
                this.forEachCommon(player, firstPos, secondPos, null, false, blocks::addPacked);
            } else {
                // Detailed path: exact list first, then insert.
                blocks.addAllPositions(this.commonBlocks(player, firstPos, secondPos, null, false));
            }

            blocks.firstPos = firstPos;
            blocks.lastPos = secondPos;
        } else {
            if (this.secondBlockEntry == null) {
                return;
            }
            BlockPos secondPos = this.secondBlockEntry.blockPos;
            BlockPos thirdPos = this.findThirdPos(player, firstPos, secondPos, true);
            if (thirdPos == null) {
                return;
            }

            blocks.clear();
            if (fast) {
                this.forEachCommon(player, firstPos, secondPos, thirdPos, false, blocks::addPacked);
            } else {
                blocks.addAllPositions(this.commonBlocks(player, firstPos, secondPos, thirdPos, false));
            }

            blocks.firstPos = firstPos;
            blocks.lastPos = thirdPos;
        }
    }

    /**
     * Canonical exact shape from explicit points, shared by the client click
     * path and the server placement path. A null third point previews the
     * intermediate shape; the server override keeps its legacy empty result
     * for that case (see {@link #getServerBlocks}).
     */
    @Override
    public List<BlockPos> getPlacementBlocks(Player player, BlockPos firstPos, BlockPos secondPos, @Nullable BlockPos thirdPos, @Nullable BlockPos fourthPos) {
        return this.commonBlocks(player, firstPos, secondPos, thirdPos,
                this.supportsTwoPointBuild() && ModeOptions.isTwoPointBuild());
    }

    @Override
    public void forEachCommonBlock(Player player, BlockPos firstPos, BlockPos secondPos, @Nullable BlockPos thirdPos, @Nullable BlockPos fourthPos, LongConsumer out) {
        this.forEachCommon(player, firstPos, secondPos, thirdPos,
                this.supportsTwoPointBuild() && ModeOptions.isTwoPointBuild(), out);
    }

    /**
     * Shared clamp + dispatch core behind both {@link #getPlacementBlocks}
     * overloads. The two-point flag is explicit so in-progress selections
     * keep the mode captured at first click instead of the live option.
     */
    private List<BlockPos> commonBlocks(Player player, BlockPos firstPos, BlockPos secondPos, @Nullable BlockPos thirdPos, boolean twoPoint) {
        int axisLimit = Config.getBuildingMaxBlocksPerAxis(player);
        if (twoPoint && this.supportsTwoPointBuild()) {
            return this.getTwoPointBlocks(player, firstPos, secondPos);
        }
        if (thirdPos == null) {
            BlockPos clampedSecond = clampPos(firstPos, secondPos, axisLimit);
            return this.getIntermediateBlocks(
                    player,
                    firstPos.getX(), firstPos.getY(), firstPos.getZ(),
                    clampedSecond.getX(), clampedSecond.getY(), clampedSecond.getZ());
        }
        BlockPos clampedSecond = clampPos(firstPos, secondPos, axisLimit);
        BlockPos clampedThird = clampPos(firstPos, thirdPos, axisLimit);
        return this.getFinalBlocks(
                player,
                firstPos.getX(), firstPos.getY(), firstPos.getZ(),
                clampedSecond.getX(), clampedSecond.getY(), clampedSecond.getZ(),
                clampedThird.getX(), clampedThird.getY(), clampedThird.getZ());
    }

    /** Primitive twin of {@link #commonBlocks}: same clamp + dispatch, bare packed longs. */
    private void forEachCommon(Player player, BlockPos firstPos, BlockPos secondPos, @Nullable BlockPos thirdPos, boolean twoPoint, LongConsumer out) {
        int axisLimit = Config.getBuildingMaxBlocksPerAxis(player);
        if (twoPoint && this.supportsTwoPointBuild()) {
            this.forEachTwoPointBlocks(player, firstPos, secondPos, out);
            return;
        }
        if (thirdPos == null) {
            BlockPos clampedSecond = clampPos(firstPos, secondPos, axisLimit);
            this.forEachIntermediateBlocks(
                    player,
                    firstPos.getX(), firstPos.getY(), firstPos.getZ(),
                    clampedSecond.getX(), clampedSecond.getY(), clampedSecond.getZ(), out);
            return;
        }
        BlockPos clampedSecond = clampPos(firstPos, secondPos, axisLimit);
        BlockPos clampedThird = clampPos(firstPos, thirdPos, axisLimit);
        this.forEachFinalBlocks(
                player,
                firstPos.getX(), firstPos.getY(), firstPos.getZ(),
                clampedSecond.getX(), clampedSecond.getY(), clampedSecond.getZ(),
                clampedThird.getX(), clampedThird.getY(), clampedThird.getZ(), out);
    }

    /**
     * Clamps a target BlockPos relative to an origin BlockPos within an axis limit.
     */
    private BlockPos clampPos(BlockPos origin, BlockPos target, int limit) {
        int x = clampAxis(origin.getX(), target.getX(), limit);
        int y = clampAxis(origin.getY(), target.getY(), limit);
        int z = clampAxis(origin.getZ(), target.getZ(), limit);
        return new BlockPos(x, y, z);
    }

    /**
     * Ensures target coordinate stays within [origin - limit + 1, origin + limit - 1].
     */
    private int clampAxis(int origin, int target, int limit) {
        if (target - origin >= limit) {
            return origin + limit - 1;
        }
        if (origin - target >= limit) {
            return origin - limit + 1;
        }
        return target;
    }

    public @Nullable BlockPos getIntermediatePos() {
        return !this.twoPointBuild && this.secondBlockEntry != null ? this.secondBlockEntry.blockPos : null;
    }

    @Override
    public List<BlockPos> getServerBlocks(Player player, BlockPos firstPos, BlockPos secondPos, @Nullable BlockPos thirdPos, @Nullable BlockPos fourthPos) {
        // Legacy placement semantics: no third point means nothing to place.
        // (getCommonBlocks resolves the intermediate preview shape instead.)
        if (thirdPos == null && !(this.supportsTwoPointBuild() && ModeOptions.isTwoPointBuild())) {
            return List.of();
        }
        return this.getPlacementBlocks(player, firstPos, secondPos, thirdPos, fourthPos);
    }

    public static BlockPos findHeight(Player player, BlockPos secondPos, boolean skipRaytrace) {
        // Plane-only hit: registers even when the vanilla raycast hits no block.
        // skipRaytrace is kept for signature compatibility and ignored.
        return RaycastToPlane.findHeight(player, secondPos);
    }

    protected abstract BlockPos findSecondPos(Player var1, BlockPos var2, boolean var3);

    protected boolean supportsTwoPointBuild() {
        return false;
    }

    protected abstract BlockPos findThirdPos(Player var1, BlockPos var2, BlockPos var3, boolean var4);

    protected abstract List<BlockPos> getIntermediateBlocks(Player var1, int var2, int var3, int var4, int var5, int var6, int var7);

    protected abstract List<BlockPos> getFinalBlocks(Player var1, int var2, int var3, int var4, int var5, int var6, int var7, int var8, int var9, int var10);

    /**
     * Primitive twins of the exact generators: emit the same shapes as packed
     * longs. Defaults pack the exact lists; hot shapes override with bare-int
     * loops.
     */
    protected void forEachIntermediateBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, LongConsumer out) {
        List<BlockPos> intermediate = this.getIntermediateBlocks(player, x1, y1, z1, x2, y2, z2);
        for (int i = 0, n = intermediate.size(); i < n; i++) {
            out.accept(intermediate.get(i).asLong());
        }
    }

    protected void forEachFinalBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, int x3, int y3, int z3, LongConsumer out) {
        List<BlockPos> fin = this.getFinalBlocks(player, x1, y1, z1, x2, y2, z2, x3, y3, z3);
        for (int i = 0, n = fin.size(); i < n; i++) {
            out.accept(fin.get(i).asLong());
        }
    }

    protected void forEachTwoPointBlocks(Player player, BlockPos firstPos, BlockPos secondPos, LongConsumer out) {
        List<BlockPos> twoPoint = this.getTwoPointBlocks(player, firstPos, secondPos);
        for (int i = 0, n = twoPoint.size(); i < n; i++) {
            out.accept(twoPoint.get(i).asLong());
        }
    }

    public void setPreviewPoint(@Nullable BlockPos pos) {
        this.previewSecondPoint = pos;
    }

    public boolean usesDirectSecondPoint() {
        return this.supportsTwoPointBuild() && (this.clicks == 0 ? ModeOptions.isTwoPointBuild() : this.twoPointBuild);
    }

    public ModeOptions.ActionEnum getPointBuildAction() {
        return this.twoPointBuild ? ModeOptions.ActionEnum.TWO_POINT_BUILD : ModeOptions.ActionEnum.THREE_POINT_BUILD;
    }

    private void findTwoPointCoordinates(BlockSet blocks, Player player, boolean fast) {
        if (this.clicks == 0 || this.firstBlockEntry == null) {
            return;
        }

        BlockPos secondPos = this.clicks == 1 ? this.previewSecondPoint : this.secondBlockEntry.blockPos;
        if (secondPos == null) {
            return;
        }

        BlockPos firstPos = this.firstBlockEntry.blockPos;
        BlockPos boundedSecondPos = this.limitToBuildRange(player, firstPos, secondPos);
        blocks.clear();
        if (fast) {
            this.forEachTwoPointBlocks(player, firstPos, boundedSecondPos, blocks::addPacked);
        } else {
            blocks.addAllPositions(this.getTwoPointBlocks(player, firstPos, boundedSecondPos));
        }

        blocks.firstPos = firstPos;
        blocks.lastPos = boundedSecondPos;
    }

    /**
     * Blocks for a two-point selection. Defaults to the final shape with the
     * second point standing in for the third; modes with dedicated two-point
     * geometry (e.g. spheres through both clicks) override this. The second
     * point is already clamped to the build range by the callers.
     */
    protected List<BlockPos> getTwoPointBlocks(Player player, BlockPos firstPos, BlockPos secondPos) {
        BlockPos boundedSecondPos = this.limitToBuildRange(player, firstPos, secondPos);
        return this.getFinalBlocks(player, firstPos.getX(), firstPos.getY(), firstPos.getZ(), boundedSecondPos.getX(), boundedSecondPos.getY(), boundedSecondPos.getZ(), boundedSecondPos.getX(), boundedSecondPos.getY(), boundedSecondPos.getZ());
    }

    protected BlockPos limitToBuildRange(Player player, BlockPos firstPos, BlockPos secondPos) {
        int axisLimit = Config.getBuildingMaxBlocksPerAxis(player);
        int x1 = firstPos.getX();
        int y1 = firstPos.getY();
        int z1 = firstPos.getZ();
        int x2 = secondPos.getX();
        int y2 = secondPos.getY();
        int z2 = secondPos.getZ();
        if (x2 - x1 >= axisLimit) {
            x2 = x1 + axisLimit - 1;
        }

        if (x1 - x2 >= axisLimit) {
            x2 = x1 - axisLimit + 1;
        }

        if (y2 - y1 >= axisLimit) {
            y2 = y1 + axisLimit - 1;
        }

        if (y1 - y2 >= axisLimit) {
            y2 = y1 - axisLimit + 1;
        }

        if (z2 - z1 >= axisLimit) {
            z2 = z1 + axisLimit - 1;
        }

        if (z1 - z2 >= axisLimit) {
            z2 = z1 - axisLimit + 1;
        }

        return new BlockPos(x2, y2, z2);
    }
}
