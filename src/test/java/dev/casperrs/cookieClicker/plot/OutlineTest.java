package dev.casperrs.cookieClicker.plot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class OutlineTest {

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-9;
    }

    @Test
    void cuboidOutlineStaysOnTheOuterFacesOfTheBox() {
        RegionShape r = RegionShape.cuboid("c", 0, 0, 0, 3, 1, 5); // blocks 0..3, 0..1, 0..5
        List<double[]> pts = Outline.points(r, 1.0, 10_000);
        assertFalse(pts.isEmpty());
        for (double[] p : pts) {
            assertTrue(p[0] >= 0 && p[0] <= 4, "x " + p[0]); // max + 1
            assertTrue(p[1] >= 0 && p[1] <= 2, "y " + p[1]);
            assertTrue(p[2] >= 0 && p[2] <= 6, "z " + p[2]);
            // every point lies on an edge: at least two coordinates on a boundary
            int onBoundary = 0;
            onBoundary += near(p[0], 0) || near(p[0], 4) ? 1 : 0;
            onBoundary += near(p[1], 0) || near(p[1], 2) ? 1 : 0;
            onBoundary += near(p[2], 0) || near(p[2], 6) ? 1 : 0;
            assertTrue(onBoundary >= 2, "not on an edge: " + java.util.Arrays.toString(p));
        }
    }

    @Test
    void stepControlsDensityAndMaxPointsCapsTheOutline() {
        RegionShape r = RegionShape.cuboid("c", 0, 0, 0, 99, 9, 99);
        int dense = Outline.points(r, 1.0, 100_000).size();
        int sparse = Outline.points(r, 4.0, 100_000).size();
        assertTrue(dense > sparse * 2, dense + " vs " + sparse);
        int capped = Outline.points(r, 1.0, 120).size();
        assertTrue(capped <= 120 && capped > 0, "capped to " + capped);
    }

    @Test
    void polygonOutlineFollowsTheVertexRingAndVerticalVertexLines() {
        RegionShape tri = new RegionShape("tri", true, 0, 5, 0, 4, 7, 4,
                List.of(new int[] {0, 0}, new int[] {4, 0}, new int[] {0, 4}), (x, y, z) -> true);
        List<double[]> pts = Outline.points(tri, 1.0, 10_000);
        double bottom = 5;
        double top = 8;
        // vertices are block centres at bottom and top
        for (double[] v : new double[][] {{0.5, 0.5}, {4.5, 0.5}, {0.5, 4.5}}) {
            assertTrue(pts.stream().anyMatch(p -> near(p[0], v[0]) && near(p[1], bottom) && near(p[2], v[1])), "bottom " + v[0] + "," + v[1]);
            assertTrue(pts.stream().anyMatch(p -> near(p[0], v[0]) && near(p[2], v[1]) && p[1] > bottom && p[1] < top),
                    "vertical line at " + v[0] + "," + v[1]);
        }
        // nothing outside the polygon's bounding box (+1 for centre offset) and y range
        for (double[] p : pts) {
            assertTrue(p[0] >= 0 && p[0] <= 5 && p[2] >= 0 && p[2] <= 5 && p[1] >= bottom && p[1] <= top);
        }
        // the hypotenuse from (4.5,0.5) to (0.5,4.5) is traced: a point with x + z == 5 at the bottom ring
        assertTrue(pts.stream().anyMatch(p -> near(p[1], bottom) && near(p[0] + p[2], 5.0) && p[0] > 0.6 && p[0] < 4.4));
    }

    /**
     * BUG (production): {@code plot.show.max-points} is clamped to 4..2000 by PlotSettings, but a cuboid outline has 12
     * edges and each edge yields at least one point, so a limit below 12 can never be met and
     * {@link Outline#points} loops forever on the main thread (step grows, point count stops shrinking at 12).
     */
}
