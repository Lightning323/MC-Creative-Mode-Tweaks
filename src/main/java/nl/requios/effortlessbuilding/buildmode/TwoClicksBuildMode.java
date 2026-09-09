package nl.requios.effortlessbuilding.buildmode;

import java.util.List;

import net.minecraft.world.phys.AABB;
import org.lightning323.creative_mode_tweaks.Config;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public abstract class TwoClicksBuildMode extends BaseBuildMode {
    protected BlockEntry firstBlockEntry;

    public void initialize() {
        super.initialize();
        this.firstBlockEntry = null;
    }

    public boolean onClick(BlockSet blocks, BlockPos clickedPos, Player player) {
        super.onClick(blocks, clickedPos, player);
        if (this.clicks == 1) {
            this.firstBlockEntry = new BlockEntry(clickedPos);
            return false;
        } else {
            return true;
        }
    }

    @Override
    public AABB getClientBoundary(Player player) {
        if (this.clicks == 0 || this.firstBlockEntry == null || this.firstBlockEntry.blockPos == null) {
            return null;
        }
        BlockPos firstPos = this.firstBlockEntry.blockPos;
        BlockPos secondPos = this.findSecondPos(player, firstPos, true);
        if (secondPos == null) {
            return null;
        }
        BlockPos clampedSecond = clampPos(firstPos, secondPos, Config.getBuildingMaxBlocksPerAxis(player));
        return boundaryForPoints(firstPos, clampedSecond);
    }

    /**
     * Full-block boundary for the clamped selection endpoints. The returned
     * AABB uses block-aligned corners with an exclusive max (+1), so it fully
     * contains both endpoint blocks. Modes whose blocks can extend beyond the
     * raw endpoints (e.g. center-started circles) override this.
     */
    protected AABB boundaryForPoints(BlockPos firstPos, BlockPos clampedSecond) {
        return toFullBlockAABB(
                Math.min(firstPos.getX(), clampedSecond.getX()),
                Math.min(firstPos.getY(), clampedSecond.getY()),
                Math.min(firstPos.getZ(), clampedSecond.getZ()),
                Math.max(firstPos.getX(), clampedSecond.getX()),
                Math.max(firstPos.getY(), clampedSecond.getY()),
                Math.max(firstPos.getZ(), clampedSecond.getZ()));
    }

    /** Builds a block-aligned AABB from inclusive block min/max corners. */
    protected static AABB toFullBlockAABB(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    protected static BlockPos clampPos(BlockPos origin, BlockPos target, int limit) {
        return new BlockPos(
                clampAxis(origin.getX(), target.getX(), limit),
                clampAxis(origin.getY(), target.getY(), limit),
                clampAxis(origin.getZ(), target.getZ(), limit));
    }

    protected static int clampAxis(int origin, int target, int limit) {
        if (target - origin >= limit) {
            return origin + limit - 1;
        }
        if (origin - target >= limit) {
            return origin - limit + 1;
        }
        return target;
    }

    public void getClientBlocks(BlockSet blocks, Player player) {
        if (this.clicks == 0 || this.firstBlockEntry == null || this.firstBlockEntry.blockPos == null) {
            return;
        }
        BlockPos firstPos = this.firstBlockEntry.blockPos;
        BlockPos secondPos = this.findSecondPos(player, firstPos, true);
        if (secondPos != null) {
            BlockPos clampedSecond = clampPos(firstPos, secondPos, Config.getBuildingMaxBlocksPerAxis(player));

            blocks.clear();

            // Preserve endpoint order: circle starting modes treat the first
            // point as the center/corner anchor, so min/max reordering here
            // would mirror around the wrong point.
            for (BlockPos pos : this.getAllBlocks(player,
                    firstPos.getX(), firstPos.getY(), firstPos.getZ(),
                    clampedSecond.getX(), clampedSecond.getY(), clampedSecond.getZ())) {
                if (!blocks.containsKey(pos)) {
                    blocks.add(new BlockEntry(pos));
                }
            }

            blocks.firstPos = firstPos;
            blocks.lastPos = secondPos;
        }
    }

    public List<BlockPos> getServerBlocks(Player player, BlockPos firstPos, BlockPos secondPos, @Nullable BlockPos thirdPos, @Nullable BlockPos fourthPos) {
        int axisLimit = Config.getBuildingMaxBlocksPerAxis(player);
        int x1 = firstPos.getX();
        int x2 = secondPos.getX();
        int y1 = firstPos.getY();
        int y2 = secondPos.getY();
        int z1 = firstPos.getZ();
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

        return this.getAllBlocks(player, x1, y1, z1, x2, y2, z2);
    }

    protected abstract BlockPos findSecondPos(Player var1, BlockPos var2, boolean var3);

    protected abstract List<BlockPos> getAllBlocks(Player var1, int var2, int var3, int var4, int var5, int var6, int var7);
}
