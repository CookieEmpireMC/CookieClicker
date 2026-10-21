package dev.casperrs.cookieClicker.belt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BeltLaneTest {

    private static final BeltCurve CURVE = new BeltCurve(0.6, 2.5, 8, 80, 1000);
    private static final BeltPath PATH = new BeltPath(0.5, 12, 1, 0, -1, 5);

    @Test
    void noCookiesAtZeroCps() {
        BeltLane lane = new BeltLane();
        for (long t = 0; t < 1000; t++) {
            assertEquals(-1, lane.trySpawn(t, 0, CURVE, PATH, 8));
        }
        assertEquals(0, lane.active());
    }

    @Test
    void firstCookieComesOutAtOnceThenWaitsTheInterval() {
        BeltLane lane = new BeltLane();
        double cps = 1000; // interval 8, 2.5 b/s -> 40 ticks
        assertEquals(40, lane.trySpawn(100, cps, CURVE, PATH, 8));
        for (long t = 101; t < 108; t++) {
            assertEquals(-1, lane.trySpawn(t, cps, CURVE, PATH, 8), "tick " + t);
        }
        assertEquals(40, lane.trySpawn(108, cps, CURVE, PATH, 8));
    }

    @Test
    void neverMoreThanMaxCookiesOnTheBelt() {
        BeltLane lane = new BeltLane();
        int max = 3;
        for (long t = 0; t < 500; t++) {
            lane.trySpawn(t, 1e6, CURVE, PATH, max);
            assertTrue(lane.active() <= max);
        }
        assertEquals(max, lane.active());
        lane.arrived();
        assertEquals(max - 1, lane.active());
        assertTrue(lane.trySpawn(1000, 1e6, CURVE, PATH, max) > 0);
        assertEquals(max, lane.active());
    }

    @Test
    void zeroCapMeansNothingEver() {
        BeltLane lane = new BeltLane();
        assertEquals(-1, lane.trySpawn(0, 1e6, CURVE, PATH, 0));
    }

    @Test
    void arrivedNeverGoesNegative() {
        BeltLane lane = new BeltLane();
        lane.arrived();
        lane.arrived();
        assertEquals(0, lane.active());
    }

    @Test
    void aFasterCookieNeverOvertakesTheOneBeforeIt() {
        BeltLane lane = new BeltLane();
        long lastArrive = Long.MIN_VALUE;
        // CPS ramps up over time (a purchase spree): the belt speeds up, cookies must still arrive in order
        for (long t = 0; t < 4000; t++) {
            double cps = t < 500 ? 0.5 : t < 600 ? 1000 : 1e7;
            int ride = lane.trySpawn(t, cps, CURVE, PATH, 1000);
            if (ride > 0) {
                long arrive = t + ride;
                assertTrue(arrive > lastArrive, "overtook at tick " + t);
                lastArrive = arrive;
            }
        }
        // and with a sudden jump right after a slow cookie
        BeltLane l2 = new BeltLane();
        int slow = l2.trySpawn(0, 0.01, CURVE, PATH, 8);
        int fast = l2.trySpawn(200, 1e9, CURVE, PATH, 8);
        assertTrue(fast > 0 && 200 + fast > slow);
    }

    @Test
    void aPurchaseShortensTheGapRightAway() {
        BeltLane lane = new BeltLane();
        assertTrue(lane.trySpawn(0, 0.1, CURVE, PATH, 8) > 0); // gap ~ 80 ticks
        assertEquals(-1, lane.trySpawn(20, 0.1, CURVE, PATH, 8));
        assertTrue(lane.trySpawn(20, 1000, CURVE, PATH, 8) > 0); // gap 8: 20 ticks since the last is enough
    }
}
