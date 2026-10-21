package dev.casperrs.cookieClicker.belt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BeltCurveTest {

    private static final BeltCurve C = new BeltCurve(0.6, 2.5, 8, 80, 1000);

    @Test
    void idleBeltAtZeroNegativeAndNaNCps() {
        for (double cps : new double[] {0, -1, -1e9, Double.NaN, Double.NEGATIVE_INFINITY}) {
            assertFalse(C.running(cps), "cps " + cps);
            assertEquals(0, C.progress(cps), 0);
        }
        assertTrue(C.running(1e-9));
    }

    @Test
    void speedAndIntervalStayInsideTheirRangesAndNeverExceedTheCap() {
        for (double cps = 1e-6; cps < 1e300; cps *= 3.7) {
            assertTrue(C.speed(cps) >= 0.6 && C.speed(cps) <= 2.5, "speed at " + cps);
            assertTrue(C.interval(cps) >= 8 && C.interval(cps) <= 80, "interval at " + cps);
            assertTrue(C.progress(cps) >= 0 && C.progress(cps) <= 1);
        }
        assertEquals(2.5, C.speed(Double.POSITIVE_INFINITY), 0);
        assertEquals(8, C.interval(Double.MAX_VALUE));
    }

    @Test
    void monotonicFasterAndTighterWithMoreCps() {
        double lastSpeed = 0;
        int lastInterval = Integer.MAX_VALUE;
        for (double cps = 0.01; cps < 5000; cps *= 1.3) {
            assertTrue(C.speed(cps) >= lastSpeed, "speed dropped at " + cps);
            assertTrue(C.interval(cps) <= lastInterval, "interval grew at " + cps);
            lastSpeed = C.speed(cps);
            lastInterval = C.interval(cps);
        }
    }

    @Test
    void fullSpeedAtTheScaleAndAbove() {
        assertEquals(2.5, C.speed(1000), 1e-12);
        assertEquals(8, C.interval(1000));
        assertEquals(2.5, C.speed(1e12), 1e-12);
        assertEquals(1.0, C.progress(1000), 0);
    }

    @Test
    void lowCpsIsSlowWithSparseCookies() {
        assertTrue(C.speed(0.1) < 0.9);
        assertTrue(C.interval(0.1) > 70);
    }

    @Test
    void intervalIsAtLeastOneTick() {
        BeltCurve tight = new BeltCurve(1, 1, 1, 1, 10);
        assertEquals(1, tight.interval(1e6));
        assertEquals(1, tight.interval(0.5));
    }

    @Test
    void rejectsBadCurves() {
        assertThrows(IllegalArgumentException.class, () -> new BeltCurve(0, 1, 1, 2, 10));
        assertThrows(IllegalArgumentException.class, () -> new BeltCurve(2, 1, 1, 2, 10));
        assertThrows(IllegalArgumentException.class, () -> new BeltCurve(1, 2, 0, 2, 10));
        assertThrows(IllegalArgumentException.class, () -> new BeltCurve(1, 2, 5, 2, 10));
        assertThrows(IllegalArgumentException.class, () -> new BeltCurve(1, 2, 1, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> new BeltCurve(1, 2, 1, 2, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new BeltCurve(Double.NaN, 2, 1, 2, 5));
        assertThrows(IllegalArgumentException.class, () -> new BeltCurve(1, Double.POSITIVE_INFINITY, 1, 2, 5));
    }
}
