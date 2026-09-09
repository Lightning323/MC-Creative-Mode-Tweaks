package nl.requios.effortlessbuilding.buildmode;

import java.util.List;

import it.unimi.dsi.fastutil.longs.LongConsumer;
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

    @Override
    public void getCommonBlocks(BlockSet blocks, Player player) {
        if (this.clicks == 0 || this.firstBlockEntry == null || this.firstBlockEntry.blockPos == null) {
            return;
        }
        BlockPos firstPos = this.firstBlockEntry.blockPos;
        BlockPos secondPos = this.findSecondPos(player, firstPos, true);
        if (secondPos != null) {
            blocks.clear();

            // Streams bare packed longs straight into the set — no intermediate list.
            this.forEachCommonBlock(player, firstPos, secondPos, null, null, blocks::addPacked);

            blocks.firstPos = firstPos;
            blocks.lastPos = secondPos;
        }
    }

    @Override
    public List<BlockPos> getCommonBlocks(Player player, BlockPos firstPos, BlockPos secondPos, @Nullable BlockPos thirdPos, @Nullable BlockPos fourthPos) {
        BlockPos clampedSecond = clampPos(firstPos, secondPos, Config.getBuildingMaxBlocksPerAxis(player));
        // Preserve endpoint order: circle starting modes treat the first
        // point as the center/corner anchor, so min/max reordering here
        // would mirror around the wrong point.
        return this.getAllBlocks(player,
                firstPos.getX(), firstPos.getY(), firstPos.getZ(),
                clampedSecond.getX(), clampedSecond.getY(), clampedSecond.getZ());
    }

    @Override
    public void forEachCommonBlock(Player player, BlockPos firstPos, BlockPos secondPos, @Nullable BlockPos thirdPos, @Nullable BlockPos fourthPos, LongConsumer out) {
        BlockPos clampedSecond = clampPos(firstPos, secondPos, Config.getBuildingMaxBlocksPerAxis(player));
        this.forEachAllBlocks(player,
                firstPos.getX(), firstPos.getY(), firstPos.getZ(),
                clampedSecond.getX(), clampedSecond.getY(), clampedSecond.getZ(), out);
    }

    protected abstract BlockPos findSecondPos(Player var1, BlockPos var2, boolean var3);

    protected abstract List<BlockPos> getAllBlocks(Player var1, int var2, int var3, int var4, int var5, int var6, int var7);

    /**
     * Primitive twin of {@link #getAllBlocks}: emits the same shape as packed
     * longs. Defaults to packing the exact list; hot shapes override with
     * bare-int loops.
     */
    protected void forEachAllBlocks(Player player, int x1, int y1, int z1, int x2, int y2, int z2, LongConsumer out) {
        List<BlockPos> all = this.getAllBlocks(player, x1, y1, z1, x2, y2, z2);
        for (int i = 0, n = all.size(); i < n; i++) {
            out.accept(all.get(i).asLong());
        }
    }
}
