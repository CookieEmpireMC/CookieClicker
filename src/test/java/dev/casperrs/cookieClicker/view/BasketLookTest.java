package dev.casperrs.cookieClicker.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.casperrs.cookieClicker.config.BasketSettings;
import org.junit.jupiter.api.Test;

/** The basket look maths: tier/fill boundaries, hysteresis, edge values, the model id and the cookie height cap. */
class BasketLookTest {

    private static final BasketSettings B = BasketSettings.defaults();

    private static String lookFor(double cookies) {
        return BasketLook.ofLevel(BasketLook.level(cookies, B)).id();
    }

    @Test
    void tierStartsAndLogFillStepsPickTheLook() {
        assertEquals("wicker_0", lookFor(1));
        assertEquals("wicker_4", lookFor(999_999));
        assertEquals("bushel_0", lookFor(1e6));
        assertEquals("golden_0", lookFor(1e9));
        assertEquals("golden_4", lookFor(1e12));
        // wicker fill steps start at 10^(0, 1.2, 2.4, ...): fill 1 at 15.85
        assertEquals("wicker_0", lookFor(15));
        assertEquals("wicker_1", lookFor(16));
        // bushel fill 3 starts at 10^7.8 (about 6.31e7); golden fill 1 at 10^9.6 (about 3.98e9), fill 2 at 10^10.2
        assertEquals("bushel_2", lookFor(6.2e7));
        assertEquals("bushel_3", lookFor(6.4e7));
        assertEquals("golden_0", lookFor(3.9e9));
        assertEquals("golden_1", lookFor(4.0e9));
        assertEquals("golden_2", lookFor(1.6e10));
    }

    @Test
    void aLookChangesOnlyPastTheHysteresisBandAtEitherBoundary() {
        BasketLook wicker4 = new BasketLook(0, 4);
        BasketLook bushel0 = new BasketLook(1, 0);
        // up: the pouch must be 5% past 1M before bushel shows
        assertEquals(wicker4, BasketLook.pick(1.02e6, wicker4, B));
        assertEquals(bushel0, BasketLook.pick(1.06e6, wicker4, B));
        // down: it must fall 5% under 1M before wicker shows again
        assertEquals(bushel0, BasketLook.pick(0.96e6, bushel0, B));
        assertEquals(wicker4, BasketLook.pick(0.94e6, bushel0, B));
    }

    @Test
    void aBigJumpShowsTheLookItReachesInOneStep() {
        // up: wicker_0 straight to bushel_3 (no tier-by-tier stepping)
        assertEquals(new BasketLook(1, 3), BasketLook.pick(2e8, new BasketLook(0, 0), B));
        // down: golden_2 falls four looks to bushel_3 in one refresh
        assertEquals(new BasketLook(1, 3), BasketLook.pick(1e8, new BasketLook(2, 2), B));
    }

    @Test
    void zeroNegativeNaNAndHugeValuesStayInRange() {
        assertEquals(0, BasketLook.level(0, B));
        assertEquals(0, BasketLook.level(-5, B));
        assertEquals(0, BasketLook.level(Double.NaN, B));
        assertEquals(BasketLook.LEVELS - 1, BasketLook.level(Double.POSITIVE_INFINITY, B));
        assertEquals(new BasketLook(2, 4), BasketLook.pick(1e300, null, B));
        assertEquals(new BasketLook(0, 0), BasketLook.pick(0, new BasketLook(0, 0), B));
        assertEquals(new BasketLook(0, 0), BasketLook.ofLevel(-3));
        assertEquals(new BasketLook(2, 4), BasketLook.ofLevel(99));
    }

    @Test
    void idIsTheModelStringAndEveryLevelRoundTrips() {
        assertEquals("golden_4", new BasketLook(2, 4).id());
        assertEquals("bushel_0", new BasketLook(1, 0).id());
        for (int level = 0; level < BasketLook.LEVELS; level++) {
            assertEquals(level, BasketLook.ofLevel(level).level());
        }
    }

    @Test
    void cookieFloatsAboveTheLookAndIsCappedToStayClickable() {
        // wicker_0 on the pedestal: top 0.281 + gap 0.1 + half the 1.4 cookie + bob 0.1
        assertEquals(1.181, new BasketLook(0, 0).cookieCentre(B, 0, 1.4, 0.1), 1e-9);
        // golden_4 (top 1.188) still clears the default 2.2 cap
        assertEquals(2.088, new BasketLook(2, 4).cookieCentre(B, 0, 1.4, 0.1), 1e-9);
        // a raised pedestal would float the cookie out of reach: capped at cookie-max-height
        assertEquals(B.cookieMaxHeight(), new BasketLook(2, 4).cookieCentre(B, 1.5, 1.4, 0.1), 1e-9);
    }
}
