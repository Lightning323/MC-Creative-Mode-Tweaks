package nl.requios.effortlessbuilding.buildmode;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import nl.requios.effortlessbuilding.buildpipeline.BuildPipeline;
import org.jetbrains.annotations.Nullable;
import org.lightning323.creative_mode_tweaks.Config;

/**
 * Central raycast-to-plane math for every build mode.
 *
 * <p>Each {@code findSecondPos}/{@code findThirdPos} implementation intersects the
 * player's look ray with an imaginary axis-aligned plane through a known anchor
 * ({@code firstPos} for the footprint, {@code secondPos} for the height). The hit
 * is accepted purely on plane geometry — in front of the eye, beyond a small
 * dead-zone, inside building reach — and never requires the vanilla block
 * raycast to hit anything. That is what lets the cursor stop at the plane in
 * mid-air and register a selection while pointing at the sky.</p>
 *
 * <p>Previously this math lived scattered across {@link BuildModes},
 * {@code Floor}/{@code Wall}/{@code Line} and {@code ThreeClicksBuildMode},
 * with an additional {@code ClipContext} coincidence check that only accepted
 * plane hits near a real block hit. That check defeated the purpose of the
 * imaginary plane, so it is gone: {@code skipRaytrace}-style block gating is
 * no longer consulted anywhere.</p>
 */
public final class RaycastToPlane {
    /** Squared dead-zone around the eye: avoids self-intersection jitter. */
    public static final double MIN_DIST_SQ = 2.8D;//Set this higher to allow angles that are more perpendicular to the raycast plane
    /** Look component below this counts as parallel: no intersection. */
    private static final double PARALLEL_EPS = 1.0E-9D;

    private RaycastToPlane() {
    }

    // -- low-level intersections (pure math, no player/config) ----------------

    /** Intersects the ray with the plane {@code x = planeX}. Null when parallel, behind, or non-finite. */
    public static @Nullable Vec3 intersectXPlane(double planeX, Vec3 eye, Vec3 look) {
        if (Math.abs(look.x) < PARALLEL_EPS) {
            return null;
        }
        double t = (planeX - eye.x) / look.x;
        if (t <= 0.0D || !Double.isFinite(t)) {
            return null;
        }
        Vec3 hit = eye.add(look.scale(t));
        return isFinite(hit) ? hit : null;
    }

    /** Intersects the ray with the plane {@code y = planeY}. Null when parallel, behind, or non-finite. */
    public static @Nullable Vec3 intersectYPlane(double planeY, Vec3 eye, Vec3 look) {
        if (Math.abs(look.y) < PARALLEL_EPS) {
            return null;
        }
        double t = (planeY - eye.y) / look.y;
        if (t <= 0.0D || !Double.isFinite(t)) {
            return null;
        }
        Vec3 hit = eye.add(look.scale(t));
        return isFinite(hit) ? hit : null;
    }

    /** Intersects the ray with the plane {@code z = planeZ}. Null when parallel, behind, or non-finite. */
    public static @Nullable Vec3 intersectZPlane(double planeZ, Vec3 eye, Vec3 look) {
        if (Math.abs(look.z) < PARALLEL_EPS) {
            return null;
        }
        double t = (planeZ - eye.z) / look.z;
        if (t <= 0.0D || !Double.isFinite(t)) {
            return null;
        }
        Vec3 hit = eye.add(look.scale(t));
        return isFinite(hit) ? hit : null;
    }

    /** Axis-dispatched intersection. Null when parallel, behind, or non-finite. */
    public static @Nullable Vec3 intersectAxisPlane(Direction.Axis axis, double planeCoord, Vec3 eye, Vec3 look) {
        return switch (axis) {
            case X -> intersectXPlane(planeCoord, eye, look);
            case Y -> intersectYPlane(planeCoord, eye, look);
            case Z -> intersectZPlane(planeCoord, eye, look);
        };
    }

    /**
     * Plane-only validity: in front of the eye, outside the dead-zone, inside reach.
     * Deliberately no block {@code clip()} — the plane hit registers even when the
     * vanilla raycast flies off into the air.
     */
    public static boolean isValidPlaneHit(Vec3 planeHit, Vec3 eye, Vec3 look, double reach) {
        double distSq = planeHit.subtract(eye).lengthSqr();
        if (!Double.isFinite(distSq)) {
            return false;
        }
        return planeHit.subtract(eye).dot(look) > 0.0D
                && distSq > MIN_DIST_SQ
                && distSq < reach * reach;
    }

    /** Intersects one axis plane and validates it in one step. Null when no usable hit. */
    public static @Nullable Vec3 raycastToPlane(Direction.Axis axis, double planeCoord, Vec3 eye, Vec3 look, double reach) {
        Vec3 hit = intersectAxisPlane(axis, planeCoord, eye, look);
        return hit != null && isValidPlaneHit(hit, eye, look, reach) ? hit : null;
    }

    /** Same as {@link #raycastToPlane} but snapped to a block. Null when no usable hit. */
    public static @Nullable BlockPos raycastToBlock(Direction.Axis axis, int planeCoord, Vec3 eye, Vec3 look, double reach) {
        Vec3 hit = raycastToPlane(axis, planeCoord, eye, look, reach);
        return hit != null ? BlockPos.containing(hit) : null;
    }

    /** Player convenience: eye/look come from {@link BuildPipeline}, reach from {@link Config}. */
    public static @Nullable BlockPos raycastToBlock(Direction.Axis axis, int planeCoord, Player player) {
        Vec3 eye = BuildPipeline.getPlayerEyePosition(player);
        Vec3 look = BuildPipeline.getPlayerLookVec(player);
        double reach = Config.getBuildingReach(player);
        return raycastToBlock(axis, planeCoord, eye, look, reach);
    }

    // -- high-level footprint/height resolvers (one per selection pattern) ----

    /** Floor footprint: {@code y = firstPos.y} plane. */
    public static @Nullable BlockPos findFloor(Vec3 eye, Vec3 look, double reach, BlockPos firstPos) {
        return raycastToBlock(Direction.Axis.Y, firstPos.getY(), eye, look, reach);
    }

    /** Floor footprint, player convenience. */
    public static @Nullable BlockPos findFloor(Player player, BlockPos firstPos) {
        Vec3 eye = BuildPipeline.getPlayerEyePosition(player);
        Vec3 look = BuildPipeline.getPlayerLookVec(player);
        return findFloor(eye, look, Config.getBuildingReach(player), firstPos);
    }

    /** Single wall plane through {@code firstPos} on the given horizontal axis. */
    public static @Nullable BlockPos findWallOnAxis(Vec3 eye, Vec3 look, double reach, BlockPos firstPos, Direction.Axis axis) {
        int coord = axis == Direction.Axis.X ? firstPos.getX() : firstPos.getZ();
        return raycastToBlock(axis, coord, eye, look, reach);
    }

    /** Single wall plane, player convenience. */
    public static @Nullable BlockPos findWallOnAxis(Player player, BlockPos firstPos, Direction.Axis axis) {
        Vec3 eye = BuildPipeline.getPlayerEyePosition(player);
        Vec3 look = BuildPipeline.getPlayerLookVec(player);
        return findWallOnAxis(eye, look, Config.getBuildingReach(player), firstPos, axis);
    }

    /** Wall footprint: best of the X/Z planes through {@code firstPos}. */
    public static @Nullable BlockPos findWall(Vec3 eye, Vec3 look, double reach, BlockPos firstPos) {
        Vec3 xHit = raycastToPlane(Direction.Axis.X, firstPos.getX(), eye, look, reach);
        Vec3 zHit = raycastToPlane(Direction.Axis.Z, firstPos.getZ(), eye, look, reach);
        if (xHit == null) {
            return zHit != null ? BlockPos.containing(zHit) : null;
        }
        if (zHit == null) {
            return BlockPos.containing(xHit);
        }
        double xDistSq = xHit.subtract(eye).lengthSqr();
        double zDistSq = zHit.subtract(eye).lengthSqr();
        double xAngle = angleOnGround(xHit, firstPos, look);
        double zAngle = angleOnGround(zHit, firstPos, look);
        // Preserve legacy tie-break: prefer the nearer wall unless its angle is much worse.
        Vec3 selected = (zDistSq < xDistSq && Math.abs(zAngle) - Math.abs(xAngle) < 3.0D) ? zHit : xHit;
        return BlockPos.containing(selected);
    }

    /** Wall footprint, player convenience. */
    public static @Nullable BlockPos findWall(Player player, BlockPos firstPos) {
        Vec3 eye = BuildPipeline.getPlayerEyePosition(player);
        Vec3 look = BuildPipeline.getPlayerLookVec(player);
        return findWall(eye, look, Config.getBuildingReach(player), firstPos);
    }

    /** Line endpoint: best of X/Y/Z planes projected onto the longest axis through {@code firstPos}. */
    public static @Nullable BlockPos findLine(Vec3 eye, Vec3 look, double reach, BlockPos firstPos) {
        Candidate best = null;
        for (Direction.Axis axis : Direction.Axis.values()) {
            Vec3 planeHit = raycastToPlane(axis, coordinate(firstPos, axis), eye, look, reach);
            if (planeHit == null) {
                continue;
            }
            Candidate candidate = new Candidate(planeHit, projectToLongestAxis(planeHit, firstPos), eye);
            best = pickLineCandidate(best, candidate);
        }
        return best != null ? BlockPos.containing(best.lineBound) : null;
    }

    /** Line endpoint, player convenience. */
    public static @Nullable BlockPos findLine(Player player, BlockPos firstPos) {
        Vec3 eye = BuildPipeline.getPlayerEyePosition(player);
        Vec3 look = BuildPipeline.getPlayerLookVec(player);
        return findLine(eye, look, Config.getBuildingReach(player), firstPos);
    }

    /**
     * Height for cube/cylinder/sphere-style shapes: X/Z planes through
     * {@code secondPos} projected onto the vertical line
     * {@code (secondX, hitY, secondZ)}.
     */
    public static @Nullable BlockPos findHeight(Vec3 eye, Vec3 look, double reach, BlockPos secondPos) {
        Candidate best = null;
        for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
            Vec3 planeHit = raycastToPlane(axis, coordinate(secondPos, axis), eye, look, reach);
            if (planeHit == null) {
                continue;
            }
            BlockPos bound = BlockPos.containing(planeHit);
            Vec3 lineBound = new Vec3(secondPos.getX(), bound.getY(), secondPos.getZ());
            best = pickLineCandidate(best, new Candidate(planeHit, lineBound, eye));
        }
        return best != null ? BlockPos.containing(best.lineBound) : null;
    }

    /** Height, player convenience. */
    public static @Nullable BlockPos findHeight(Player player, BlockPos secondPos) {
        Vec3 eye = BuildPipeline.getPlayerEyePosition(player);
        Vec3 look = BuildPipeline.getPlayerLookVec(player);
        return findHeight(eye, look, Config.getBuildingReach(player), secondPos);
    }

    /** Base plane for pyramid/dome-style shapes: the normal axis through {@code firstPos}. */
    public static @Nullable BlockPos findNormalPlane(Direction.Axis normalAxis, Vec3 eye, Vec3 look, double reach, BlockPos firstPos) {
        return raycastToBlock(normalAxis, coordinate(firstPos, normalAxis), eye, look, reach);
    }

    /** Base plane, player convenience. */
    public static @Nullable BlockPos findNormalPlane(Direction.Axis normalAxis, Player player, BlockPos firstPos) {
        Vec3 eye = BuildPipeline.getPlayerEyePosition(player);
        Vec3 look = BuildPipeline.getPlayerLookVec(player);
        return findNormalPlane(normalAxis, eye, look, Config.getBuildingReach(player), firstPos);
    }

    /**
     * Tip height for pyramid/dome-style shapes: planes perpendicular to the normal
     * axis through {@code secondPos}, projected back onto the normal axis so only
     * the tip coordinate moves.
     */
    public static @Nullable BlockPos findPerpendicularHeight(Direction.Axis normalAxis, Vec3 eye, Vec3 look, double reach, BlockPos secondPos) {
        Candidate best = null;
        for (Direction.Axis axis : perpendicularAxes(normalAxis)) {
            Vec3 planeHit = raycastToPlane(axis, coordinate(secondPos, axis), eye, look, reach);
            if (planeHit == null) {
                continue;
            }
            Vec3 lineBound = pointOnAxis(planeHit, secondPos, normalAxis);
            Candidate candidate = new Candidate(planeHit, lineBound, eye);
            if (best == null
                    || candidate.distToLineSq < best.distToLineSq
                    || (candidate.distToLineSq < MIN_DIST_SQ && best.distToLineSq < MIN_DIST_SQ
                        && candidate.distToPlayerSq < best.distToPlayerSq)) {
                best = candidate;
            }
        }
        return best != null ? BlockPos.containing(best.lineBound) : null;
    }

    /** Tip height, player convenience. */
    public static @Nullable BlockPos findPerpendicularHeight(Direction.Axis normalAxis, Player player, BlockPos secondPos) {
        Vec3 eye = BuildPipeline.getPlayerEyePosition(player);
        Vec3 look = BuildPipeline.getPlayerLookVec(player);
        return findPerpendicularHeight(normalAxis, eye, look, Config.getBuildingReach(player), secondPos);
    }

    // -- shared projection/selection helpers ----------------------------------

    /** Snaps a plane hit onto the longest axis through {@code anchor} (line modes). */
    public static Vec3 projectToLongestAxis(Vec3 planeHit, BlockPos anchor) {
        BlockPos bound = BlockPos.containing(planeHit);
        int dx = Math.abs(bound.getX() - anchor.getX());
        int dy = Math.abs(bound.getY() - anchor.getY());
        int dz = Math.abs(bound.getZ() - anchor.getZ());
        int longest = Math.max(dx, Math.max(dy, dz));
        if (longest == dx) {
            return new Vec3(bound.getX(), anchor.getY(), anchor.getZ());
        }
        if (longest == dy) {
            return new Vec3(anchor.getX(), bound.getY(), anchor.getZ());
        }
        return new Vec3(anchor.getX(), anchor.getY(), bound.getZ());
    }

    /** Keeps only the normal-axis coordinate of {@code point}, restoring the base on the rest. */
    public static Vec3 pointOnAxis(Vec3 point, BlockPos base, Direction.Axis axis) {
        return switch (axis) {
            case X -> new Vec3(point.x, base.getY(), base.getZ());
            case Y -> new Vec3(base.getX(), point.y, base.getZ());
            case Z -> new Vec3(base.getX(), base.getY(), point.z);
        };
    }

    /** Block coordinate on the given axis. */
    public static int coordinate(BlockPos pos, Direction.Axis axis) {
        return switch (axis) {
            case X -> pos.getX();
            case Y -> pos.getY();
            case Z -> pos.getZ();
        };
    }

    /** Axes perpendicular to the given normal axis. */
    public static Direction.Axis[] perpendicularAxes(Direction.Axis normalAxis) {
        return switch (normalAxis) {
            case X -> new Direction.Axis[]{Direction.Axis.Y, Direction.Axis.Z};
            case Y -> new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z};
            case Z -> new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Y};
        };
    }

    private static double angleOnGround(Vec3 planeHit, BlockPos firstPos, Vec3 look) {
        Vec3 wall = planeHit.subtract(Vec3.atLowerCornerOf(firstPos));
        return wall.x * look.x + wall.z * look.z;
    }

    private static boolean isFinite(Vec3 v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    /** Plane hit plus its line projection and cached distances. */
    private record Candidate(Vec3 planeBound, Vec3 lineBound, double distToLineSq, double distToPlayerSq) {
        Candidate(Vec3 planeBound, Vec3 lineBound, Vec3 eye) {
            this(planeBound, lineBound,
                    lineBound.subtract(planeBound).lengthSqr(),
                    planeBound.subtract(eye).lengthSqr());
        }
    }

    /** Legacy line/height pick: nearest line wins, nearest player breaks near-ties. */
    private static Candidate pickLineCandidate(@Nullable Candidate selected, Candidate candidate) {
        if (selected == null) {
            return candidate;
        }
        if (candidate.distToLineSq < MIN_DIST_SQ && selected.distToLineSq < MIN_DIST_SQ) {
            return candidate.distToPlayerSq < selected.distToPlayerSq ? candidate : selected;
        }
        return candidate.distToLineSq < selected.distToLineSq ? candidate : selected;
    }
}
