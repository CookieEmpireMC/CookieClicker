package dev.casperrs.cookieClicker.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CookieMotionTest {

    /** The shipped defaults: 6 s per turn, 0.1 bob over 80 ticks, 10-tick segments. */
    private static final CookieMotion DEFAULT = new CookieMotion(6 * 20, 0.1, 80, 10);

    @Test
    void angleStaysInRangeAndWrapsOncePerRevolution() {
        for (long t = 0; t < 10_000; t++) {
            double a = DEFAULT.angle(t);
            assertTrue(a >= 0 && a < 2 * Math.PI, "tick " + t + " -> " + a);
        }
        assertEquals(0, DEFAULT.angle(0), 1e-12);
        assertEquals(Math.PI, DEFAULT.angle(60), 1e-9);
        assertEquals(0, DEFAULT.angle(120), 1e-9);
        assertEquals(DEFAULT.angle(7), DEFAULT.angle(7 + 120 * 1000), 1e-6);
    }

    @Test
    void bobIsASineBoundedByTheAmplitude() {
        assertEquals(0, DEFAULT.bob(0), 1e-12);
        assertEquals(0.1, DEFAULT.bob(20), 1e-9);
        assertEquals(0, DEFAULT.bob(40), 1e-9);
        assertEquals(-0.1, DEFAULT.bob(60), 1e-9);
        for (long t = 0; t < 1_000; t++) {
            assertTrue(Math.abs(DEFAULT.bob(t)) <= 0.1 + 1e-12);
            assertEquals(DEFAULT.bob(t), DEFAULT.bob(t + 80), 1e-12);
        }
    }

}
