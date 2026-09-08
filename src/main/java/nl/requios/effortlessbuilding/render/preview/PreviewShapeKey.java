package nl.requios.effortlessbuilding.render.preview;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.BuildSettings;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipeline;
import org.jetbrains.annotations.Nullable;

/**
 * Cheap, immutable snapshot of everything that determines the preview SHAPE
 * (which blocks) and APPEARANCE (which ghost state, what opacity, what colors).
 *
 * <p>The render cache builds one of these every frame from raycasts and config
 * getters only — no block generation, no sorting, no world lookups. If it equals
 * the previous frame's key, the whole expensive pipeline (findCoordinates,
 * constraints, sorting, tessellation, border math) is skipped and the cached
 * GPU buffers are just re-drawn at the new camera position.</p>
 *
 * <p>What each field captures:</p>
 * <ul>
 *   <li>mode / inProgress / buildState — which tool and whether we are
 *       mid-sequence (placing vs breaking changes overlay colors).</li>
 *   <li>selectionOrigin / firstHitPos / firstHitFace — the anchored first click.
 *       Any new click changes these, so the next frame rebuilds.</li>
 *   <li>hoverPos / hoverFace / hoverPoint — the current crosshair target.
 *       {@code hoverPoint} is the resolved placement point (marker-snapped when
 *       hovering a Mesh vertex, offset to air otherwise), mirroring click logic.</li>
 *   <li>eyeBlock — block containing the camera eye. Look-vector modes
 *       (Plane/Wall/Floor) derive their second point from eye + look, snapped
 *       to blocks, so block-level eye movement is enough to catch changes.</li>
 *   <li>heldItem / trowel / replaceMode / blockAlpha — ghost appearance. A
 *       different held block (or opacity setting) re-tessellates with new states.</li>
 *   <li>maxBlocks / axisLimit / protectTiles / fill options — server-synced
 *       limits and shape options from the radial menu.</li>
 *   <li>creative — creative vs survival changes reach and limits.</li>
 *   <li>asyncBoundary — where the border bakes (worker vs main thread).
 *       Doesn't change WHAT renders, but flipping it must rebuild so the
 *       new path actually runs.</li>
 * </ul>
 *
 * <p>Deliberately NOT tracked: live world edits (a newly placed tile entity
 * won't flip a cached preview red until the next aim move — placement itself
 * stays server-authoritative). If that staleness ever matters, add a
 * {@code (frameNumber / 100)} field to force a periodic refresh.</p>
 */
public record PreviewShapeKey(
        BuildModeEnum mode,
        boolean inProgress,
        @Nullable BuildPipeline.BuildState buildState,
        @Nullable BlockPos selectionOrigin,
        @Nullable BlockPos firstHitPos,
        @Nullable Direction firstHitFace,
        @Nullable BlockPos hoverPos,
        @Nullable Direction hoverFace,
        @Nullable BlockPos hoverPoint,
        @Nullable BlockPos eyeBlock,
        @Nullable Item heldItem,
        boolean trowel,
        BuildSettings.ReplaceMode replaceMode,
        int blockAlpha,
        int maxBlocks,
        int axisLimit,
        boolean protectTiles,
        ModeOptions.ActionEnum fill,
        ModeOptions.ActionEnum cubeFill,
        ModeOptions.ActionEnum sides,
        ModeOptions.ActionEnum pointBuild,
        boolean creative,
        boolean asyncBoundary,
        String dimension
) {
}
