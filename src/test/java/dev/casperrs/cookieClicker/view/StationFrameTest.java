package dev.casperrs.cookieClicker.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.casperrs.cookieClicker.config.StationVisualSettings.Offset;
import dev.casperrs.cookieClicker.station.Station;
import org.junit.jupiter.api.Test;

class StationFrameTest {

    private static final double EPS = 1e-9;

    private static StationFrame at(float yaw) {
        return StationFrame.of(new Station("s", "minecraft:overworld", 10, 64, 20, yaw));
    }

    private static void assertPoint(double x, double y, double z, double[] p) {
        assertEquals(x, p[0], EPS, "x");
        assertEquals(y, p[1], EPS, "y");
        assertEquals(z, p[2], EPS, "z");
    }

    @Test
    void yawZeroFacesSouthAndRightIsWest() {
        assertPoint(10, 64, 22, at(0).point(new Offset(0, 0, 2)));      // forward = +Z
        assertPoint(9, 64, 20, at(0).point(new Offset(1, 0, 0)));       // right = -X when facing south
        assertPoint(10, 66.5, 20, at(0).point(new Offset(0, 2.5, 0)));  // up is world up
    }

    @Test
    void yaw90FacesWestAndRightIsNorth() {
        assertPoint(8, 64, 20, at(90).point(new Offset(0, 0, 2)));      // forward = -X
        assertPoint(10, 64, 19, at(90).point(new Offset(1, 0, 0)));     // right = -Z when facing west
    }

    @Test
    void aPlotWithoutAClicksSpotMirrorsTheShopSpotAcrossThePedestal() {
        Station station = new Station("s", "minecraft:overworld", 10, 64, 20, 0f, new Station.Anchor(12, 65, 18, 90f));
        Station.Anchor mirrored = StationAnchors.mirroredClicks(station);
        assertEquals(8, mirrored.x(), EPS);     // 2 * 10 - 12
        assertEquals(65, mirrored.y(), EPS);    // same height as the shop spot
        assertEquals(22, mirrored.z(), EPS);    // 2 * 20 - 18
        assertEquals(90f, mirrored.yaw(), EPS); // same yaw as the shop spot
    }

    @Test
    void localIsTheInverseOfPointAtAnyYaw() {
        Offset base = new Offset(0.5, -1.25, 2.0);
        Offset rel = new Offset(-0.75, 0.3, -3.1);
        for (float yaw : new float[] {0, 30, 90, -135, 200}) {
            StationFrame f = at(yaw);
            double[] p = f.point(base, rel);
            Offset back = f.local(p[0], p[1], p[2], base);
            assertEquals(rel.right(), back.right(), EPS, "right at yaw " + yaw);
            assertEquals(rel.up(), back.up(), EPS, "up at yaw " + yaw);
            assertEquals(rel.forward(), back.forward(), EPS, "forward at yaw " + yaw);
        }
    }
}
