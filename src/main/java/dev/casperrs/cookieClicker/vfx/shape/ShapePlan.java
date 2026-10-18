package dev.casperrs.cookieClicker.vfx.shape;

/**
 * A precomputed shape: for every tick, the points to emit as a flat array of
 * {@code [x, y, z, dx, dy, dz, x, y, z, ...]} (offset from the origin, then a unit direction or zeros).
 * Built once per preset at load, read-only afterwards, so playing an effect allocates nothing per point.
 */
public final class ShapePlan {

    /** Doubles per point: x, y, z, dx, dy, dz. */
    public static final int STRIDE = 6;

    private final double[][] ticks;
    private final boolean facing;
    private final int total;

    ShapePlan(double[][] ticks, boolean facing) {
        this.ticks = ticks;
        this.facing = facing;
        int sum = 0;
        for (double[] tick : ticks) {
            sum += tick.length / STRIDE;
        }
        this.total = sum;
    }

    /** Number of ticks the shape runs (at least 1). */
    public int ticks() {
        return ticks.length;
    }

    /** The points for one tick; never modify the returned array. */
    public double[] at(int tick) {
        return ticks[tick];
    }

    /** Points emitted at one tick. */
    public int pointsAt(int tick) {
        return ticks[tick].length / STRIDE;
    }

    /** Points over the whole shape (one particle each, unless the layer spawns {@code count} per point). */
    public int totalPoints() {
        return total;
    }

    /** True when the runtime must turn the shape (around y) to face the viewer. */
    public boolean facing() {
        return facing;
    }
}
