package dev.casperrs.cookieClicker.plot;

import java.util.ArrayList;
import java.util.List;

/**
 * Points along a region's edges, for {@code /bakery plot show} (pure). Cuboids: the 12 edges of the box, on the
 * outer block faces ({@code min} to {@code max + 1}). Polygons: the ring through the vertex blocks' centres at the
 * bottom and top face, plus a vertical line at every vertex.
 */
public final class Outline {

    private Outline() {
    }

    /**
     * @param step      distance between points along an edge (blocks)
     * @param maxPoints the step grows until the outline fits (keeps huge regions cheap). Every edge keeps at least
     *                  one point, so an outline never gets below its edge count (12 for a cuboid, 3 per polygon
     *                  vertex): then the coarsest outline is returned
     */
    public static List<double[]> points(RegionShape shape, double step, int maxPoints) {
        double s = Math.max(0.25, step);
        List<double[]> pts = trace(shape, s);
        int limit = Math.max(1, maxPoints);
        while (pts.size() > limit) {
            s *= 1.5;
            List<double[]> coarser = trace(shape, s);
            if (coarser.size() >= pts.size() && s > longestEdge(shape)) {
                return coarser; // one point per edge already: it can't shrink any further
            }
            pts = coarser;
        }
        return pts;
    }

    /** Upper bound of any edge length; once the step exceeds it, every edge is a single point. */
    private static double longestEdge(RegionShape r) {
        double dx = r.maxX() - r.minX() + 1.0;
        double dy = r.maxY() - r.minY() + 1.0;
        double dz = r.maxZ() - r.minZ() + 1.0;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static List<double[]> trace(RegionShape r, double step) {
        List<double[]> out = new ArrayList<>();
        double bottom = r.minY();
        double top = r.maxY() + 1.0;
        if (!r.polygon()) {
            double x0 = r.minX();
            double x1 = r.maxX() + 1.0;
            double z0 = r.minZ();
            double z1 = r.maxZ() + 1.0;
            for (double y : new double[] {bottom, top}) {
                line(out, x0, y, z0, x1, y, z0, step);
                line(out, x1, y, z0, x1, y, z1, step);
                line(out, x1, y, z1, x0, y, z1, step);
                line(out, x0, y, z1, x0, y, z0, step);
            }
            for (double[] c : new double[][] {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}}) {
                line(out, c[0], bottom, c[1], c[0], top, c[1], step);
            }
            return out;
        }
        List<int[]> pts = r.points();
        for (int i = 0; i < pts.size(); i++) {
            int[] a = pts.get(i);
            int[] b = pts.get((i + 1) % pts.size());
            double ax = a[0] + 0.5;
            double az = a[1] + 0.5;
            double bx = b[0] + 0.5;
            double bz = b[1] + 0.5;
            line(out, ax, bottom, az, bx, bottom, bz, step);
            line(out, ax, top, az, bx, top, bz, step);
            line(out, ax, bottom, az, ax, top, az, step);
        }
        return out;
    }

    /** Points from a (inclusive) toward b (exclusive), evenly spaced at most {@code step} apart. */
    private static void line(List<double[]> out, double ax, double ay, double az, double bx, double by, double bz,
                             double step) {
        double dx = bx - ax;
        double dy = by - ay;
        double dz = bz - az;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int n = Math.max(1, (int) Math.ceil(len / step));
        for (int i = 0; i < n; i++) {
            double t = (double) i / n;
            out.add(new double[] {ax + dx * t, ay + dy * t, az + dz * t});
        }
    }
}
