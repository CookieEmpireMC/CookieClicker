package dev.casperrs.cookieClicker.view.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** the panel price roll (start, exact end, monotonic, retarget without a jump). */
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
    void retargetingMidRollStartsFromWhatIsShownAndNeverJumps() {
        PriceTween roll = new PriceTween(1e3, 1e6, 0, 8);
        double shown = roll.valueAt(4);
        PriceTween retargeted = roll.retarget(2e6, 4, 8);
        assertEquals(shown, retargeted.valueAt(4), 0.0, "the new roll begins exactly where the old one showed");
        assertEquals(2e6, retargeted.valueAt(12), 0.0);
        assertTrue(retargeted.valueAt(6) > shown, "and keeps moving toward the new target");
    }

}
