package dev.casperrs.cookieClicker.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void yaw180And270() {
        assertPoint(10, 64, 18, at(180).point(new Offset(0, 0, 2)));
        assertPoint(11, 64, 20, at(180).point(new Offset(1, 0, 0)));
        assertPoint(12, 64, 20, at(270).point(new Offset(0, 0, 2)));    // forward = +X
        assertPoint(10, 64, 21, at(270).point(new Offset(1, 0, 0)));
    }

    @Test
    void offsetLengthIsPreservedForAnyYaw() {
        for (float yaw = -720; yaw <= 720; yaw += 17.5f) {
            double[] p = at(yaw).point(new Offset(1.9, 0, 2.5));
            double d = Math.hypot(p[0] - 10, p[2] - 20);
            assertEquals(Math.hypot(1.9, 2.5), d, 1e-9, "yaw " + yaw);
        }
    }

    @Test
    void relativePointAddsInTheFrame() {
        StationFrame f = at(90);
        double[] a = f.point(new Offset(1, 2, 3), new Offset(0.5, -1, 0.25));
        double[] b = f.point(new Offset(1.5, 1, 3.25));
        assertPoint(b[0], b[1], b[2], a);
    }

    @Test
    void facingViewersIsNormalisedAndOppositeTheStationYaw() {
        for (float yaw : new float[] {0, 90, 180, 270, -90, -270, 360, 450, 719.5f, -0.5f}) {
            float f = at(yaw).facingViewers(0);
            assertTrue(f >= -180 && f < 180, "range for yaw " + yaw + ": " + f);
            double diff = ((f - (yaw + 180)) % 360 + 360) % 360;
            assertTrue(diff < 1e-3 || diff > 360 - 1e-3, "opposite for yaw " + yaw + ": " + f);
        }
        assertEquals(-180f, at(0).facingViewers(0), 1e-4);
        assertEquals(0f, at(180).facingViewers(0), 1e-4);
        assertEquals(-170f, at(0).facingViewers(10), 1e-4);
    }
}
