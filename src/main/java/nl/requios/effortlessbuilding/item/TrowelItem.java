package nl.requios.effortlessbuilding.item;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.BuildModes;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipelineClient;
import nl.requios.effortlessbuilding.buildpipeline.TrowelSystem;

/**
 * A placement tool whose block palette is the player's hotbar.
 * Placement is handled by {@code TrowelSystem}, so this item intentionally has no use action or UI.
 *
 * <p>Hold-place repeat lives here too: vanilla repeats single-block placement
 * while use is held, but trowel placements run through the mod pipeline on
 * discrete presses only. The client tick therefore drives repeats through
 * {@link #handleHoldPlaceTick} instead of inlining the logic in the tick
 * handler.
 */
public final class TrowelItem extends Item {
    /** Ticks between hold-place repeats (vanilla rightClickDelay). */
    private static final int HOLD_REPEAT_TICKS = 4;
    private static int holdDelay = 0;

    public TrowelItem(Properties properties) {
        super(properties);
    }

    /**
     * Vanilla-style hold-place repeat for single trowel placements. Call every
     * client tick while no screen is open, with the current use-key state.
     * Only single placements repeat: multi-click sequences need discrete
     * clicks (buildState != null while one is active).
     */
    public static void handleHoldPlaceTick(Minecraft mc, boolean rightDown, boolean rightJustPressed) {
        if (rightDown && !rightJustPressed
                && BuildPipelineClient.getBuildState() == null
                && BuildModes.CLIENT.getBuildMode() == BuildModeEnum.DISABLED
                && mc.player != null
                && TrowelSystem.isTrowel(mc.player.getMainHandItem())) {
            if (holdDelay > 0) {
                holdDelay--;
            } else {
                BuildPipelineClient.handleRightClick(mc);
                holdDelay = HOLD_REPEAT_TICKS;
            }
        } else {
            holdDelay = rightJustPressed ? HOLD_REPEAT_TICKS : 0;
        }
    }

    /** Clears hold-place state (screen opened, key released, mode changed). */
    public static void resetHoldPlace() {
        holdDelay = 0;
    }
}
