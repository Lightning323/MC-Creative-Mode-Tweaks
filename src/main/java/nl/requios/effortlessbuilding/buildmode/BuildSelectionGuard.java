package nl.requios.effortlessbuilding.buildmode;

import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Guards in-progress build-mode selections against exceeding the configured
 * block-set limit ({@code building.*.max_blocks_placed}).
 *
 * <p>Protocol, exactly as requested:</p>
 * <ol>
 *   <li>On every selection update the caller reports the candidate boundary
 *       from {@code getClientBoundary} together with the candidate block count
 *       from {@code getClientBlocks}.</li>
 *   <li>If the count exceeds the limit, the selection mode becomes
 *       {@link SelectionMode#OVERSIZED}.</li>
 *   <li>While oversized, only a candidate whose boundary box is
 *       <em>smaller</em> (strictly smaller volume) than the stored boundary is
 *       accepted. Anything that would keep or increase the size is rejected and
 *       the caller must keep showing the previous selection.</li>
 *   <li>A smaller boundary is accepted as a normal selection update; when its
 *       block count is back within the limit the mode returns to
 *       {@link SelectionMode#NORMAL} (otherwise it stays oversized on the new,
 *       smaller boundary so the player can keep shrinking out).</li>
 * </ol>
 *
 * <p>The guard is session-agnostic: callers must {@link #reset()} when the
 * selection session ends (cancel, finish, mode change, no active preview) so a
 * stale boundary never gates a fresh selection.</p>
 */
public final class BuildSelectionGuard {
    /** Client-wide singleton (the client has a single player/selection). */
    public static final BuildSelectionGuard CLIENT = new BuildSelectionGuard();

    /** Selection mode: normal shaping vs. locked-down oversized shaping. */
    public enum SelectionMode {
        NORMAL,
        OVERSIZED
    }

    private SelectionMode selectionMode = SelectionMode.NORMAL;
    private @Nullable AABB lastBoundary;
    private long lastVolume;

    public synchronized void reset() {
        this.selectionMode = SelectionMode.NORMAL;
        this.lastBoundary = null;
        this.lastVolume = 0L;
    }

    public synchronized SelectionMode getSelectionMode() {
        return this.selectionMode;
    }

    public synchronized boolean isOversized() {
        return this.selectionMode == SelectionMode.OVERSIZED;
    }

    public synchronized @Nullable AABB getLastBoundary() {
        return this.lastBoundary;
    }

    /**
     * Attempts to accept a candidate selection update.
     *
     * @param candidateBoundary boundary from {@code getClientBoundary}, or null
     *                          when there is no active selection.
     * @param candidateBlockCount raw size from {@code getClientBlocks}, before
     *                          constraint capping.
     * @param maxBlocks block-set limit from configs
     *                  ({@code Config.getBuildingMaxBlocksPlaced}).
     * @return true when the caller should use the candidate (normal selection
     *         path); false when oversized and the candidate does not shrink the
     *         boundary, in which case the caller must keep the previous
     *         selection and revert any tentative hover-point publish.
     */
    public synchronized boolean updateSelection(@Nullable AABB candidateBoundary, int candidateBlockCount, int maxBlocks) {
        if (candidateBoundary == null) {
            return true;
        }
        long candidateVolume = boundaryVolume(candidateBoundary);
        if (this.selectionMode == SelectionMode.NORMAL) {
            this.lastBoundary = candidateBoundary;
            this.lastVolume = candidateVolume;
            if (candidateBlockCount > maxBlocks) {
                this.selectionMode = SelectionMode.OVERSIZED;
            }
            return true;
        }
        // OVERSIZED: only a strictly smaller boundary box may proceed.
        if (candidateVolume < this.lastVolume) {
            this.lastBoundary = candidateBoundary;
            this.lastVolume = candidateVolume;
            if (candidateBlockCount <= maxBlocks) {
                this.selectionMode = SelectionMode.NORMAL;
            }
            return true;
        }
        return false;
    }

    /**
     * Volume (block-count upper bound) of a full-block boundary AABB. The
     * boundary uses block-aligned corners with an exclusive max, so the
     * inclusive block extents are floor(min)..ceil(max)-1 per axis.
     */
    public static long boundaryVolume(AABB boundary) {
        long dx = (long) Math.ceil(boundary.maxX) - (long) Math.floor(boundary.minX);
        long dy = (long) Math.ceil(boundary.maxY) - (long) Math.floor(boundary.minY);
        long dz = (long) Math.ceil(boundary.maxZ) - (long) Math.floor(boundary.minZ);
        if (dx <= 0 || dy <= 0 || dz <= 0) {
            return 0L;
        }
        try {
            return Math.multiplyExact(Math.multiplyExact(dx, dy), dz);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}
