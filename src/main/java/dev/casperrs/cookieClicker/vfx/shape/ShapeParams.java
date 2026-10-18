package dev.casperrs.cookieClicker.vfx.shape;

/**
 * Everything a shape needs, in blocks, degrees and ticks. Fields a shape doesn't use are ignored.
 *
 * @param points        points per tick (ring, sphere) or in total (burst, arc, line, helix per strand, fountain,
 *                      sparkles = number of sparks); ignored by {@link ShapeKind#POINT}
 * @param radius        radius in blocks (final radius when growing)
 * @param radiusStart   start radius for ring/sphere growth (equal to {@code radius} = no growth)
 * @param height        helix height or sparkle rise, blocks
 * @param turns         helix revolutions
 * @param angleDeg      arc length in degrees
 * @param startAngleDeg first angle for ring/arc/helix, degrees (0 = +x)
 * @param spreadDeg     fountain cone half-angle, degrees
 * @param upBias        burst only: 0 = full sphere, 1 = only straight up
 * @param toX           line end, relative to the origin
 * @param plane         ring/arc only: horizontal, vertical, or facing the viewer
 * @param durationTicks how many ticks the shape is drawn over (sparkles: life of one spark)
 * @param staggerTicks  sparkles only: start times spread over this many ticks
 * @param emitEvery     sparkles only: emit each spark every n ticks
 * @param direction     none (placed), outward (launched along the shape normal) or up
 * @param strands       helix strands
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
