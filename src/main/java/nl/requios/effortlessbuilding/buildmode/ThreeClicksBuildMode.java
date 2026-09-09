package nl.requios.effortlessbuilding.buildmode;

import java.util.ArrayList;
import java.util.List;

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
            return getFinalBoundary(firstPos, boundedSecond, boundedSecond);
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

    public void getClientBlocks(BlockSet blocks, Player player) {
        if (this.twoPointBuild) {
            this.findTwoPointCoordinates(blocks, player);
            return;
        }

        if (this.clicks == 0 || this.firstBlockEntry == null) {
            return;
        }

        int axisLimit = Config.getBuildingMaxBlocksPerAxis(player);
        BlockPos firstPos = this.firstBlockEntry.blockPos;

        if (this.clicks == 1) {
            BlockPos secondPos = this.findSecondPos(player, firstPos, true);
            if (secondPos == null) {
                return;
            }

            BlockPos clampedSecond = clampPos(firstPos, secondPos, axisLimit);

            blocks.clear();
            blocks.addAllPositions(this.getIntermediateBlocks(
                    player,
                    firstPos.getX(), firstPos.getY(), firstPos.getZ(),
                    clampedSecond.getX(), clampedSecond.getY(), clampedSecond.getZ()));

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

            BlockPos clampedSecond = clampPos(firstPos, secondPos, axisLimit);
            BlockPos clampedThird = clampPos(firstPos, thirdPos, axisLimit);

            blocks.clear();
            blocks.addAllPositions(this.getFinalBlocks(
                    player,
                    firstPos.getX(), firstPos.getY(), firstPos.getZ(),
                    clampedSecond.getX(), clampedSecond.getY(), clampedSecond.getZ(),
                    clampedThird.getX(), clampedThird.getY(), clampedThird.getZ()));

            blocks.firstPos = firstPos;
            blocks.lastPos = thirdPos;
        }
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

    public List<BlockPos> getServerBlocks(Player player, BlockPos firstPos, BlockPos secondPos, @Nullable BlockPos thirdPos, @Nullable BlockPos fourthPos) {
        if (this.supportsTwoPointBuild() && ModeOptions.isTwoPointBuild()) {
            return this.getTwoPointBlocks(player, firstPos, secondPos);
        }

        if (thirdPos == null) {
            return List.of();
        } else {
            int axisLimit = Config.getBuildingMaxBlocksPerAxis(player);
            int x1 = firstPos.getX();
            int x2 = secondPos.getX();
            int x3 = thirdPos.getX();
            int y1 = firstPos.getY();
            int y2 = secondPos.getY();
            int y3 = thirdPos.getY();
            int z1 = firstPos.getZ();
            int z2 = secondPos.getZ();
            int z3 = thirdPos.getZ();
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

            if (x3 - x1 >= axisLimit) {
                x3 = x1 + axisLimit - 1;
            }

            if (x1 - x3 >= axisLimit) {
                x3 = x1 - axisLimit + 1;
            }

            if (y3 - y1 >= axisLimit) {
                y3 = y1 + axisLimit - 1;
            }

            if (y1 - y3 >= axisLimit) {
                y3 = y1 - axisLimit + 1;
            }

            if (z3 - z1 >= axisLimit) {
                z3 = z1 + axisLimit - 1;
            }

            if (z1 - z3 >= axisLimit) {
                z3 = z1 - axisLimit + 1;
            }

            return this.getFinalBlocks(player, x1, y1, z1, x2, y2, z2, x3, y3, z3);
        }
    }

    public static BlockPos findHeight(Player player, BlockPos secondPos, boolean skipRaytrace) {
        Vec3 look = BuildPipeline.getPlayerLookVec(player);
        Vec3 start = BuildPipeline.getPlayerEyePosition(player);
        List<HeightCriteria> criteriaList = new ArrayList(2);
        Vec3 xBound = BuildModes.findXBound((double) secondPos.getX(), start, look);
        criteriaList.add(new HeightCriteria(xBound, secondPos, start));
        Vec3 zBound = BuildModes.findZBound((double) secondPos.getZ(), start, look);
        criteriaList.add(new HeightCriteria(zBound, secondPos, start));
        double reach = Config.getBuildingReach(player);
        criteriaList.removeIf((criteriax) -> !criteriax.isValid(start, look, reach, player, skipRaytrace));
        if (criteriaList.isEmpty()) {
            return null;
        } else {
            HeightCriteria selected = (HeightCriteria) criteriaList.get(0);
            if (criteriaList.size() > 1) {
                for (int i = 1; i < criteriaList.size(); ++i) {
                    HeightCriteria criteria = (HeightCriteria) criteriaList.get(i);
                    if (criteria.distToLineSq < (double) 2.0F && selected.distToLineSq < (double) 2.0F) {
                        if (criteria.distToPlayerSq < selected.distToPlayerSq) {
                            selected = criteria;
                        }
                    } else if (criteria.distToLineSq < selected.distToLineSq) {
                        selected = criteria;
                    }
                }
            }

            return BlockPos.containing(selected.lineBound);
        }
    }

    protected abstract BlockPos findSecondPos(Player var1, BlockPos var2, boolean var3);

    protected boolean supportsTwoPointBuild() {
        return false;
    }

    protected abstract BlockPos findThirdPos(Player var1, BlockPos var2, BlockPos var3, boolean var4);

    protected abstract List<BlockPos> getIntermediateBlocks(Player var1, int var2, int var3, int var4, int var5, int var6, int var7);

    protected abstract List<BlockPos> getFinalBlocks(Player var1, int var2, int var3, int var4, int var5, int var6, int var7, int var8, int var9, int var10);

    public void setPreviewPoint(@Nullable BlockPos pos) {
        this.previewSecondPoint = pos;
    }

    public boolean usesDirectSecondPoint() {
        return this.supportsTwoPointBuild() && (this.clicks == 0 ? ModeOptions.isTwoPointBuild() : this.twoPointBuild);
    }

    public ModeOptions.ActionEnum getPointBuildAction() {
        return this.twoPointBuild ? ModeOptions.ActionEnum.TWO_POINT_BUILD : ModeOptions.ActionEnum.THREE_POINT_BUILD;
    }

    private void findTwoPointCoordinates(BlockSet blocks, Player player) {
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
        blocks.addAllPositions(this.getTwoPointBlocks(player, firstPos, boundedSecondPos));

        blocks.firstPos = firstPos;
        blocks.lastPos = boundedSecondPos;
    }

    private List<BlockPos> getTwoPointBlocks(Player player, BlockPos firstPos, BlockPos secondPos) {
        BlockPos boundedSecondPos = this.limitToBuildRange(player, firstPos, secondPos);
        return this.getFinalBlocks(player, firstPos.getX(), firstPos.getY(), firstPos.getZ(), boundedSecondPos.getX(), boundedSecondPos.getY(), boundedSecondPos.getZ(), boundedSecondPos.getX(), boundedSecondPos.getY(), boundedSecondPos.getZ());
    }

    private BlockPos limitToBuildRange(Player player, BlockPos firstPos, BlockPos secondPos) {
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

    static class HeightCriteria {
        Vec3 planeBound;
        Vec3 lineBound;
        double distToLineSq;
        double distToPlayerSq;

        HeightCriteria(Vec3 planeBound, BlockPos secondPos, Vec3 start) {
            this.planeBound = planeBound;
            this.lineBound = this.toLongestLine(this.planeBound, secondPos);
            this.distToLineSq = this.lineBound.subtract(this.planeBound).lengthSqr();
            this.distToPlayerSq = this.planeBound.subtract(start).lengthSqr();
        }

        private Vec3 toLongestLine(Vec3 boundVec, BlockPos secondPos) {
            BlockPos bound = BlockPos.containing(boundVec);
            return new Vec3((double) secondPos.getX(), (double) bound.getY(), (double) secondPos.getZ());
        }

        public boolean isValid(Vec3 start, Vec3 look, double reach, Player player, boolean skipRaytrace) {
            return BuildModes.isCriteriaValid(start, look, reach, player, skipRaytrace, this.lineBound, this.planeBound, this.distToPlayerSq);
        }
    }
}
