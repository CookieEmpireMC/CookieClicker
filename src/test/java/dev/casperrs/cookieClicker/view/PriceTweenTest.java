package dev.casperrs.cookieClicker.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** upgrade-holograms: the panel price roll (start, exact end, monotonic, retarget without a jump). */
class PriceTweenTest {

    @Test
    void startsAtTheOldValueAndLandsOnTheExactNewOne() {
        PriceTween up = new PriceTween(1_000, 1_000_000, 100, 8);
        assertEquals(1_000, up.valueAt(100), 0.0);
        assertEquals(1_000_000, up.valueAt(108), 0.0, "the last tick is the exact value");
        assertEquals(1_000_000, up.valueAt(500), 0.0, "and stays there");
        assertTrue(up.done(108) && !up.done(107));

        PriceTween down = new PriceTween(12_345.678, 0.5, 0, 4);
        assertEquals(0.5, down.valueAt(4), 0.0);
    }

    @Test
    void isMonotonicInBothDirectionsAndStaysBetweenTheEnds() {
        for (PriceTween t : new PriceTween[] {
                new PriceTween(1e3, 1e6, 0, 8), new PriceTween(1e6, 1e3, 0, 8)}) {
            double prev = t.valueAt(0);
            for (long now = 1; now <= 8; now++) {
                double v = t.valueAt(now);
                assertTrue(t.from() < t.to() ? v >= prev : v <= prev, "tick " + now + ": " + v + " after " + prev);
                assertTrue(v >= Math.min(t.from(), t.to()) && v <= Math.max(t.from(), t.to()));
                prev = v;
            }
        }
    }

    @Test
    void rollsEvenlyInLogSpace() {
        // 1K → 1M: halfway in log space is 31.6K (10^4.5), not 500K as a linear roll would give
        assertEquals(Math.pow(10, 4.5), PriceTween.between(1e3, 1e6, 0.5), 1e-6);
        assertEquals(1e4, PriceTween.between(1e3, 1e6, 1.0 / 3), 1e-6);
    }

    @Test
    void retargetingMidRollStartsFromWhatIsShownAndNeverJumps() {
        PriceTween roll = new PriceTween(1e3, 1e6, 0, 8);
        double shown = roll.valueAt(4);
        PriceTween retargeted = roll.retarget(2e6, 4, 8);
        assertEquals(shown, retargeted.valueAt(4), 0.0, "the new roll begins exactly where the old one showed");
        assertEquals(2e6, retargeted.valueAt(12), 0.0);
        assertTrue(retargeted.valueAt(6) > shown, "and keeps moving toward the new target");
    }

    @Test
    void sameTargetLeavesARunningRollAlone() {
        PriceTween roll = new PriceTween(1e3, 1e6, 0, 8);
        assertEquals(roll, roll.retarget(1e6, 3, 8), "the 1 Hz refresh must not restart a running roll");
        assertEquals(roll.valueAt(5), roll.retarget(1e6, 5, 8).valueAt(5), 0.0);
        assertEquals(1e6, PriceTween.fixed(1e6).retarget(1e6, 9, 8).valueAt(9), 0.0);
    }
}
