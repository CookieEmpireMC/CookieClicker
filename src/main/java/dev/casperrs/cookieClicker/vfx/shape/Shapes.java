package dev.casperrs.cookieClicker.vfx.shape;

import dev.casperrs.cookieClicker.vfx.shape.ShapeParams.Direction;
import dev.casperrs.cookieClicker.vfx.shape.ShapeParams.Plane;
import java.util.Arrays;

/**
 * Shape generators: turn {@link ShapeParams} into a {@link ShapePlan}. Deterministic (golden angle, even
 * spacing, no randomness), so the preview export shows exactly what the game spawns. Plain maths, no Bukkit types;
 * y is up, like Minecraft.
 */
public final class Shapes {

    /** The golden angle in radians, about 137.5 degrees: an even, non-repeating spread for any count. */
    public static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));

    private Shapes() {
    }

    public static ShapePlan build(ShapeParams p) {
        int duration = Math.max(1, p.durationTicks());
        int points = Math.max(1, p.points());
        return switch (p.kind()) {
            case POINT -> point(p, duration);
            case BURST -> burst(p, points, duration);
            case SPHERE -> sphere(p, points, duration);
            case RING -> ring(p, points, duration);
            case ARC -> arc(p, points, duration);
            case LINE -> line(p, points, duration);
            case HELIX -> helix(p, points, duration);
            case FOUNTAIN -> fountain(p, points, duration);
            case SPARKLES -> sparkles(p, points, duration);
        };
    }

    /** Point {@code i} of {@code n} evenly spread over a disc of radius {@code r} (sunflower pattern), as {x, z}. */
    public static double[] goldenDisc(int i, int n, double r) {
        double rr = r * Math.sqrt((i + 0.5) / Math.max(1, n));
        double a = i * GOLDEN_ANGLE;
        return new double[] {rr * Math.cos(a), rr * Math.sin(a)};
    }

    /**
     * Unit direction {@code i} of {@code n} on a sphere (golden spiral). {@code upBias} 0 covers the whole sphere,
     * 1 squeezes every direction to straight up.
     */
    public static double[] sphereDir(int i, int n, double upBias) {
        double y = 1 - 2 * (i + 0.5) / n;
        double b = Easing.clamp01(upBias);
        y = b + (1 - b) * y;
        double r = Math.sqrt(Math.max(0, 1 - y * y));
        double a = i * GOLDEN_ANGLE;
        return new double[] {r * Math.cos(a), y, r * Math.sin(a)};
    }

    /** Unit direction {@code i} of {@code n} in a cone around +y with half-angle {@code spreadDeg}. */
    public static double[] coneDir(int i, int n, double spreadDeg) {
        double polar = Math.toRadians(spreadDeg) * Math.sqrt((i + 0.5) / n);
        double a = i * GOLDEN_ANGLE;
        double s = Math.sin(polar);
        return new double[] {s * Math.cos(a), Math.cos(polar), s * Math.sin(a)};
    }

    /** Radius at tick {@code t} of {@code duration} when growing from {@code start} to {@code end} (ease-out). */
    public static double growRadius(double start, double end, int t, int duration) {
        if (duration <= 1) {
            return end;
        }
        return Easing.lerp(start, end, Easing.outQuad((double) t / (duration - 1)));
    }

    /** The tick a progressive shape emits point {@code i} of {@code n} at, spread evenly over {@code duration}. */
    public static int progressiveTick(int i, int n, int duration) {
        return Math.min(duration - 1, (int) ((long) i * duration / n));
    }

    private static ShapePlan point(ShapeParams p, int duration) {
        Builder b = new Builder(duration);
        for (int t = 0; t < duration; t++) {
            b.add(t, 0, 0, 0, dir(p.direction(), 0, 1, 0));
        }
        return b.build(false);
    }

    private static ShapePlan burst(ShapeParams p, int n, int duration) {
        Builder b = new Builder(duration);
        for (int i = 0; i < n; i++) {
            double[] d = sphereDir(i, n, p.upBias());
            b.add(progressiveTick(i, n, duration), d[0] * p.radius(), d[1] * p.radius(), d[2] * p.radius(),
                    dir(p.direction(), d[0], d[1], d[2]));
        }
        return b.build(false);
    }

    private static ShapePlan sphere(ShapeParams p, int n, int duration) {
        Builder b = new Builder(duration);
        for (int t = 0; t < duration; t++) {
            double r = growRadius(p.radiusStart(), p.radius(), t, duration);
            for (int i = 0; i < n; i++) {
                double[] d = sphereDir(i, n, 0);
                b.add(t, d[0] * r, d[1] * r, d[2] * r, dir(p.direction(), d[0], d[1], d[2]));
            }
        }
        return b.build(false);
    }

    private static ShapePlan ring(ShapeParams p, int n, int duration) {
        Builder b = new Builder(duration);
        double start = Math.toRadians(p.startAngleDeg());
        for (int t = 0; t < duration; t++) {
            double r = growRadius(p.radiusStart(), p.radius(), t, duration);
            for (int i = 0; i < n; i++) {
                double a = start + 2 * Math.PI * i / n;
                b.addPlanar(p, t, r * Math.cos(a), 0, r * Math.sin(a), Math.cos(a), 0, Math.sin(a));
            }
        }
        return b.build(p.plane() == Plane.FACING);
    }

    private static ShapePlan arc(ShapeParams p, int n, int duration) {
        Builder b = new Builder(duration);
        double start = Math.toRadians(p.startAngleDeg());
        double span = Math.toRadians(p.angleDeg());
        for (int i = 0; i < n; i++) {
            double a = start + (n == 1 ? 0 : span * i / (n - 1));
            b.addPlanar(p, progressiveTick(i, n, duration), p.radius() * Math.cos(a), 0, p.radius() * Math.sin(a),
                    Math.cos(a), 0, Math.sin(a));
        }
        return b.build(p.plane() == Plane.FACING);
    }

    private static ShapePlan line(ShapeParams p, int n, int duration) {
        Builder b = new Builder(duration);
        double len = Math.sqrt(p.toX() * p.toX() + p.toY() * p.toY() + p.toZ() * p.toZ());
        double ux = len == 0 ? 0 : p.toX() / len;
        double uy = len == 0 ? 1 : p.toY() / len;
        double uz = len == 0 ? 0 : p.toZ() / len;
        for (int i = 0; i < n; i++) {
            double f = n == 1 ? 0 : (double) i / (n - 1);
            b.add(progressiveTick(i, n, duration), p.toX() * f, p.toY() * f, p.toZ() * f,
                    dir(p.direction(), ux, uy, uz));
        }
        return b.build(false);
    }

    private static ShapePlan helix(ShapeParams p, int n, int duration) {
        Builder b = new Builder(duration);
        int strands = Math.max(1, p.strands());
        double start = Math.toRadians(p.startAngleDeg());
        for (int s = 0; s < strands; s++) {
            double phase = 2 * Math.PI * s / strands;
            for (int i = 0; i < n; i++) {
                double f = n == 1 ? 0 : (double) i / (n - 1);
                double a = start + phase + 2 * Math.PI * p.turns() * f;
                double cx = Math.cos(a);
                double cz = Math.sin(a);
                b.add(progressiveTick(i, n, duration), p.radius() * cx, p.height() * f, p.radius() * cz,
                        dir(p.direction(), cx, 0, cz));
            }
        }
        return b.build(false);
    }

    private static ShapePlan fountain(ShapeParams p, int n, int duration) {
        Builder b = new Builder(duration);
        for (int i = 0; i < n; i++) {
            double[] d = coneDir(i, n, p.spreadDeg());
            b.add(progressiveTick(i, n, duration), d[0] * p.radius(), d[1] * p.radius(), d[2] * p.radius(),
                    dir(p.direction(), d[0], d[1], d[2]));
        }
        return b.build(false);
    }

    private static ShapePlan sparkles(ShapeParams p, int n, int life) {
        int stagger = Math.max(0, p.staggerTicks());
        int every = Math.max(1, p.emitEvery());
        Builder b = new Builder(stagger + life);
        for (int i = 0; i < n; i++) {
            double[] xz = goldenDisc(i, n, p.radius());
            int start = n == 1 ? 0 : (int) Math.round((double) i * stagger / (n - 1));
            // age 0 sits on the origin plane; the last age reaches the full height (no gap at the bottom)
            for (int age = 0; age < life; age += every) {
                double f = life == 1 ? 0 : Easing.outQuad((double) age / (life - 1));
                b.add(start + age, xz[0], p.height() * f, xz[1], dir(p.direction(), 0, 1, 0));
            }
        }
        return b.build(false);
    }

    /** Direction vector for a mode: outward = the shape normal, up = +y, none = zero. */
    private static double[] dir(Direction mode, double nx, double ny, double nz) {
        return switch (mode) {
            case OUTWARD -> new double[] {nx, ny, nz};
            case UP -> new double[] {0, 1, 0};
            case NONE -> new double[] {0, 0, 0};
        };
    }

    /** Collects points per tick into flat arrays. Only used at load time. */
    private static final class Builder {
        private final double[][] data;
        private final int[] size;

        Builder(int ticks) {
            data = new double[ticks][];
            size = new int[ticks];
            for (int t = 0; t < ticks; t++) {
                data[t] = new double[ShapePlan.STRIDE * 8];
            }
        }

        void add(int t, double x, double y, double z, double[] d) {
            if (size[t] + ShapePlan.STRIDE > data[t].length) {
                data[t] = Arrays.copyOf(data[t], data[t].length * 2);
            }
            double[] a = data[t];
            int o = size[t];
            a[o] = x;
            a[o + 1] = y;
            a[o + 2] = z;
            a[o + 3] = d[0];
            a[o + 4] = d[1];
            a[o + 5] = d[2];
            size[t] = o + ShapePlan.STRIDE;
        }

        /** Adds a point generated in the x/z plane, mapped to x/y when the shape stands up. */
        void addPlanar(ShapeParams p, int t, double x, double y, double z, double nx, double ny, double nz) {
            double[] d = dir(p.direction(), nx, ny, nz);
            if (p.plane() == Plane.HORIZONTAL) {
                add(t, x, y, z, d);
            } else {
                // stand the shape up: its z axis becomes y, so its face points along +z
                add(t, x, z, y, new double[] {d[0], d[2], d[1]});
            }
        }

        ShapePlan build(boolean facing) {
            double[][] out = new double[data.length][];
            for (int t = 0; t < data.length; t++) {
                out[t] = Arrays.copyOf(data[t], size[t]);
            }
            return new ShapePlan(out, facing);
        }
    }
}
