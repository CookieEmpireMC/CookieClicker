package dev.casperrs.cookieClicker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.worldguard.WorldGuardHook;
import org.junit.jupiter.api.Test;

/** {@code WorldGuardHook.olderThan}: the WorldGuard version warning (never blocks the start). */
class CookieClickerVersionCheckTest {

    private static final String MIN = "7.0.20";

    @Test
    void olderReleasesWarn() {
        assertTrue(WorldGuardHook.olderThan("7.0.19", MIN));
        assertTrue(WorldGuardHook.olderThan("7.0.9", MIN), "numeric, not lexicographic");
        assertTrue(WorldGuardHook.olderThan("6.2.2", MIN));
        assertTrue(WorldGuardHook.olderThan("7.0", MIN), "missing parts count as 0");
        assertTrue(WorldGuardHook.olderThan("7", MIN));
    }

    @Test
    void sameOrNewerReleasesDoNot() {
        assertFalse(WorldGuardHook.olderThan("7.0.20", MIN));
        assertFalse(WorldGuardHook.olderThan("7.0.21", MIN));
        assertFalse(WorldGuardHook.olderThan("7.1.0", MIN));
        assertFalse(WorldGuardHook.olderThan("8.0.0", MIN));
        assertFalse(WorldGuardHook.olderThan("7.0.100", MIN), "100 > 20 numerically");
    }

}
