package dev.casperrs.cookieClicker.vfx.shape;

/**
 * Everything a shape needs, in blocks, degrees and ticks. Fields a shape doesn't use are ignored.
 *
 * <p>{@code points}: per tick (ring, sphere) or in total (burst, arc, line, helix per strand, fountain, sparkles);
 * ignored by {@link ShapeKind#POINT}. {@code radiusStart} equal to {@code radius} = no growth. {@code upBias}
 * (burst): 0 = full sphere, 1 = straight up. {@code toX/Y/Z}: line end relative to the origin. {@code plane}
 * (ring/arc): horizontal, vertical or facing the viewer. {@code startAngleDeg}: 0 = +x. {@code direction}: none
 * (placed), outward (along the shape normal) or up. {@code durationTicks}: draw time (sparkles: one spark's life);
 * {@code staggerTicks}/{@code emitEvery}: sparkles only.
 */
public record ShapeParams(
        ShapeKind kind,
        int points,
        double radius,
        double radiusStart,
        double height,
        double turns,
        double angleDeg,
        double startAngleDeg,
        double spreadDeg,
        double upBias,
        double toX,
        double toY,
        double toZ,
        Plane plane,
        int durationTicks,
        int staggerTicks,
        int emitEvery,
        Direction direction,
        int strands) {

    /** Orientation of flat shapes. */
    public enum Plane {
        /** In the x/z plane (lying flat). */
        HORIZONTAL,
        /** In the x/y plane (standing, facing +z). */
        VERTICAL,
        /** Standing, turned at play time so its face points at the viewer (vertical when there is none). */
        FACING
    }

    /** How a particle moves after it spawns. */
    public enum Direction {
        /** Placed exactly on the point; uses the layer's {@code offset}/{@code speed} as vanilla randomness. */
        NONE,
        /** Launched along the shape normal at {@code speed} blocks/tick. */
        OUTWARD,
        /** Launched straight up at {@code speed}. */
        UP
    }

    /** Defaults for one shape: the starting point for the config loader and the tests. */
    public static ShapeParams defaults(ShapeKind kind) {
        Direction dir = switch (kind) {
            case BURST, FOUNTAIN -> Direction.OUTWARD;
            default -> Direction.NONE;
        };
        return new ShapeParams(kind, 8, 0.5, 0.5, 1.0, 1.0, 180, 0, 30, 0,
                0, 1, 0, Plane.HORIZONTAL, 1, 0, 1, dir, 1);
    }

    public ShapeParams withPoints(int newPoints) {
        return new ShapeParams(kind, newPoints, radius, radiusStart, height, turns, angleDeg, startAngleDeg,
                spreadDeg, upBias, toX, toY, toZ, plane, durationTicks, staggerTicks, emitEvery, direction, strands);
    }
}
