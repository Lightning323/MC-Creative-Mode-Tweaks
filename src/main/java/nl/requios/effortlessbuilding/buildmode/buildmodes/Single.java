package nl.requios.effortlessbuilding.buildmode.buildmodes;

import java.util.List;

import net.minecraft.world.phys.AABB;
import nl.requios.effortlessbuilding.buildmode.BaseBuildMode;
import nl.requios.effortlessbuilding.utilities.BlockEntry;
import nl.requios.effortlessbuilding.utilities.BlockSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public class Single extends BaseBuildMode {
    private BlockPos pos;

    public void initialize() {
        super.initialize();
        this.pos = null;
    }

    public boolean onClick(BlockSet blocks, BlockPos clickedPos, Player player) {
        this.pos = clickedPos;
        return true;
    }

    public void getClientBlocks(BlockSet blocks, Player player) {
        if (this.pos != null) {
            blocks.setStartPos(new BlockEntry(this.pos));
        }
    }

    public List<BlockPos> getServerBlocks(Player player, BlockPos firstPos, BlockPos secondPos, @Nullable BlockPos thirdPos, @Nullable BlockPos fourthPos) {
        return List.of(firstPos);
    }

    @Override
    public AABB getClientBoundary(Player player) {
        if (this.pos == null) {
            return null;
        }
        // Full-block box: AABB(BlockPos) spans pos -> pos + 1.
        return new AABB(this.pos);
    }
}
