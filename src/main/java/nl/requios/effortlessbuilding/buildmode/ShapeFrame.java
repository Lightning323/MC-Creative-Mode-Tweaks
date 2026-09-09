package nl.requios.effortlessbuilding.buildmode;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * O(1) shape descriptor: the resolved anchor points of a build-mode selection.
 *
 * <p><b>Contract:</b> {@link IBuildMode#describeShape} resolves at most the
 * second/third anchor (look-vector math + axis clamping) and snapshots the
 * shape options — no block enumeration, no world scans, no allocation
 * proportional to the shape volume. ALWAYS O(1).</p>
 *
 * <p>The O(N) work lives separately in {@link IBuildMode#expandShape}: turning
 * a frame into the full {@code List<BlockPos>}. The preview cache calls
 * describe on the render thread every shape change (cheap box overlay +
 * routing decision), and only expands when the O(1) estimate says the shape
 * fits the detailed path.</p>
 */
public record ShapeFrame(
        /** Which expansion rule to use. */
        Kind kind,
        /** First click anchor. Never null for a non-empty frame. */
        BlockPos first,
        /** Bounded second anchor (or bounded single/second point). May be null. */
        @Nullable BlockPos second,
        /** Bounded third anchor. Only set for {@link Kind#FINAL}. May be null. */
        @Nullable BlockPos third,
        /** Mesh vertices (bounded, at most 4). Only set for {@link Kind#MESH}. */
        @Nullable java.util.List<BlockPos> meshPoints,
        /** Axis clamp that was applied. */
        int axisLimit,
        /** Shape options snapshot taken at describe time (worker-safe). */
        ModeOptions.ActionEnum fill,
        ModeOptions.ActionEnum cubeFill,
        ModeOptions.ActionEnum sides,
        ModeOptions.ActionEnum circleStart,
        boolean twoPointBuild) {

    public enum Kind {
        /** Two-click mode: first + second. */
        PAIR,
        /** Three-click mode, one click so far: intermediate (floor/circle) plane. */
        INTERMEDIATE,
        /** Three-click mode, two clicks: final volume. */
        FINAL,
        /** Two-point shortcut of a three-click mode. */
        TWO_POINT,
        /** Mesh face / line from a handful of vertices. */
        MESH,
        /** Single block. */
        SINGLE
    }

    /** True when there is nothing to expand (no anchors resolved). */
    public boolean isEmpty() {
        return this.first == null;
    }

    /** Inclusive min corner of the anchor bounding box. O(1). */
    public BlockPos aabbMin() {
        int minX = this.first.getX();
        int minY = this.first.getY();
        int minZ = this.first.getZ();
        if (this.second != null) {
            minX = Math.min(minX, this.second.getX());
            minY = Math.min(minY, this.second.getY());
            minZ = Math.min(minZ, this.second.getZ());
        }
        if (this.third != null) {
            minX = Math.min(minX, this.third.getX());
            minY = Math.min(minY, this.third.getY());
            minZ = Math.min(minZ, this.third.getZ());
        }
        if (this.meshPoints != null) {
            for (BlockPos p : this.meshPoints) {
                minX = Math.min(minX, p.getX());
                minY = Math.min(minY, p.getY());
                minZ = Math.min(minZ, p.getZ());
            }
        }
        return new BlockPos(minX, minY, minZ);
    }

    /** Inclusive max corner of the anchor bounding box. O(1). */
    public BlockPos aabbMax() {
        int maxX = this.first.getX();
        int maxY = this.first.getY();
        int maxZ = this.first.getZ();
        if (this.second != null) {
            maxX = Math.max(maxX, this.second.getX());
            maxY = Math.max(maxY, this.second.getY());
            maxZ = Math.max(maxZ, this.second.getZ());
        }
        if (this.third != null) {
            maxX = Math.max(maxX, this.third.getX());
            maxY = Math.max(maxY, this.third.getY());
            maxZ = Math.max(maxZ, this.third.getZ());
        }
        if (this.meshPoints != null) {
            for (BlockPos p : this.meshPoints) {
                maxX = Math.max(maxX, p.getX());
                maxY = Math.max(maxY, p.getY());
                maxZ = Math.max(maxZ, p.getZ());
            }
        }
        return new BlockPos(maxX, maxY, maxZ);
    }

    /**
     * O(1) upper bound on the block count: the anchor bounding-box volume.
     * Hollow/skeleton shapes expand to fewer blocks, never more — so routing
     * on this value is conservative (may take the detailed path, never skips
     * detail that would fit).
     */
    public long aabbVolume() {
        BlockPos min = aabbMin();
        BlockPos max = aabbMax();
        return (long) (max.getX() - min.getX() + 1)
                * (max.getY() - min.getY() + 1)
                * (max.getZ() - min.getZ() + 1);
    }

    public static ShapeFrame pair(BlockPos first, BlockPos second, int axisLimit,
                                  ModeOptions.ActionEnum fill, ModeOptions.ActionEnum cubeFill,
                                  ModeOptions.ActionEnum sides, ModeOptions.ActionEnum circleStart,
                                  boolean twoPointBuild) {
        return new ShapeFrame(Kind.PAIR, first, second, null, null,
                axisLimit, fill, cubeFill, sides, circleStart, twoPointBuild);
    }

    public static ShapeFrame intermediate(BlockPos first, BlockPos second, int axisLimit,
                                          ModeOptions.ActionEnum fill, ModeOptions.ActionEnum cubeFill,
                                          ModeOptions.ActionEnum sides, ModeOptions.ActionEnum circleStart,
                                          boolean twoPointBuild) {
        return new ShapeFrame(Kind.INTERMEDIATE, first, second, null, null,
                axisLimit, fill, cubeFill, sides, circleStart, twoPointBuild);
    }

    public static ShapeFrame finall(BlockPos first, BlockPos second, BlockPos third, int axisLimit,
                                    ModeOptions.ActionEnum fill, ModeOptions.ActionEnum cubeFill,
                                    ModeOptions.ActionEnum sides, ModeOptions.ActionEnum circleStart,
                                    boolean twoPointBuild) {
        return new ShapeFrame(Kind.FINAL, first, second, third, null,
                axisLimit, fill, cubeFill, sides, circleStart, twoPointBuild);
    }

    public static ShapeFrame twoPoint(BlockPos first, BlockPos second, int axisLimit,
                                      ModeOptions.ActionEnum fill, ModeOptions.ActionEnum cubeFill,
                                      ModeOptions.ActionEnum sides, ModeOptions.ActionEnum circleStart) {
        return new ShapeFrame(Kind.TWO_POINT, first, second, null, null,
                axisLimit, fill, cubeFill, sides, circleStart, true);
    }

    public static ShapeFrame mesh(java.util.List<BlockPos> boundedPoints, int axisLimit,
                                  ModeOptions.ActionEnum fill, ModeOptions.ActionEnum cubeFill,
                                  ModeOptions.ActionEnum sides, ModeOptions.ActionEnum circleStart) {
        return new ShapeFrame(Kind.MESH, boundedPoints.getFirst(), boundedPoints.size() > 1 ? boundedPoints.getLast() : null,
                null, java.util.List.copyOf(boundedPoints),
                axisLimit, fill, cubeFill, sides, circleStart, false);
    }

    public static ShapeFrame single(BlockPos pos, int axisLimit,
                                    ModeOptions.ActionEnum fill, ModeOptions.ActionEnum cubeFill,
                                    ModeOptions.ActionEnum sides, ModeOptions.ActionEnum circleStart) {
        return new ShapeFrame(Kind.SINGLE, pos, null, null, null,
                axisLimit, fill, cubeFill, sides, circleStart, false);
    }
}
