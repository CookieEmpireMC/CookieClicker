package dev.casperrs.cookieClicker.belt;

import dev.casperrs.cookieClicker.plot.Plot;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometry of a straight conveyor belt. Pure Java. A belt runs from its first cell (at the factory hatch) to its last
 * cell (right before the pedestal); cookies ride from the hatch edge of the first cell to the pedestal edge of the
 * last cell, on the belt surface. World coordinates: (startX, startZ) is where a cookie appears (hatch edge, block
 * centre line), y the belt surface; dirX/dirZ are -1, 0 or 1; length is in blocks (= cells).
 */
public record BeltPath(double startX, double startZ, double y, int dirX, int dirZ, int length) {

    /**
     * The cells from {@code from} to {@code to}, both inclusive, in order. Null unless the two share y and x or z.
     * One cell when they are equal.
     */
    public static @Nullable List<Plot.Cell> cells(int[] from, int[] to) {
        if (from[1] != to[1] || (from[0] != to[0] && from[2] != to[2])) {
            return null;
        }
        int dx = Integer.signum(to[0] - from[0]);
        int dz = Integer.signum(to[2] - from[2]);
        int n = Math.abs(to[0] - from[0]) + Math.abs(to[2] - from[2]) + 1;
        List<Plot.Cell> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new Plot.Cell(from[0] + dx * i, from[1], from[2] + dz * i));
        }
        return out;
    }

    /**
     * The ride of a stored belt. A single-cell belt has no direction of its own and runs toward {@code pedestal}
     * (or north when there is none). Null when the cells don't form a straight line.
     */
    public static @Nullable BeltPath of(Plot.Belt belt, Plot.@Nullable Pedestal pedestal) {
        List<Plot.Cell> cells = belt.cells();
        Plot.Cell first = cells.getFirst();
        Plot.Cell last = cells.getLast();
        int dx = Integer.signum(last.x() - first.x());
        int dz = Integer.signum(last.z() - first.z());
        if (dx != 0 && dz != 0) {
            return null;
        }
        if (dx == 0 && dz == 0) {
            double px = pedestal == null ? 0 : pedestal.x() - (first.x() + 0.5);
            double pz = pedestal == null ? -1 : pedestal.z() - (first.z() + 0.5);
            if (Math.abs(px) > Math.abs(pz)) {
                dx = (int) Math.signum(px);
            } else {
                dz = pz == 0 ? -1 : (int) Math.signum(pz);
            }
        }
        for (int i = 0; i < cells.size(); i++) {
            Plot.Cell c = cells.get(i);
            if (c.y() != first.y() || c.x() != first.x() + dx * i || c.z() != first.z() + dz * i) {
                return null;
            }
        }
        return new BeltPath(first.x() + 0.5 - dx * 0.5, first.z() + 0.5 - dz * 0.5, first.y() + belt.height(), dx,
                dz, cells.size());
    }

    /** Middle of the belt at surface height: x, y, z. */
    public double[] middle() {
        return new double[] {startX + dirX * length / 2.0, y, startZ + dirZ * length / 2.0};
    }

    /** Where a cookie leaves the belt (pedestal edge of the last cell): x, y, z. */
    public double[] end() {
        return new double[] {startX + dirX * length, y, startZ + dirZ * length};
    }

    /** Centre of cell {@code i} at surface height: x, y, z. */
    public double[] cellCentre(int i) {
        return new double[] {startX + dirX * (i + 0.5), y, startZ + dirZ * (i + 0.5)};
    }

    /** The yaw (radians, about +y) that turns +z into the travel direction: atan2(dirX, dirZ). */
    public double travelAngle() {
        return Math.atan2(dirX, dirZ);
    }

    /** Ticks a cookie needs at {@code blocksPerSecond}: at least 1. */
    public int rideTicks(double blocksPerSecond) {
        return Math.max(1, (int) Math.round(length / blocksPerSecond * 20.0));
    }
}
