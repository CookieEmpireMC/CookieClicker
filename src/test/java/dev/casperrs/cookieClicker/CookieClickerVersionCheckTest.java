package dev.casperrs.cookieClicker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** {@code CookieClicker.olderThan}: the WorldGuard version warning (never blocks the start). */
class CookieClickerVersionCheckTest {

    private static final String MIN = "7.0.20";

    @Test
    void olderReleasesWarn() {
        assertTrue(CookieClicker.olderThan("7.0.19", MIN));
        assertTrue(CookieClicker.olderThan("7.0.9", MIN), "numeric, not lexicographic");
        assertTrue(CookieClicker.olderThan("6.2.2", MIN));
        assertTrue(CookieClicker.olderThan("7.0", MIN), "missing parts count as 0");
        assertTrue(CookieClicker.olderThan("7", MIN));
    }

    @Test
    void sameOrNewerReleasesDoNot() {
        assertFalse(CookieClicker.olderThan("7.0.20", MIN));
        assertFalse(CookieClicker.olderThan("7.0.21", MIN));
        assertFalse(CookieClicker.olderThan("7.1.0", MIN));
        assertFalse(CookieClicker.olderThan("8.0.0", MIN));
        assertFalse(CookieClicker.olderThan("7.0.100", MIN), "100 > 20 numerically");
    }

    @Test
    void buildSuffixesAreIgnored() {
        assertFalse(CookieClicker.olderThan("7.0.20-SNAPSHOT;abc123", MIN));
        assertTrue(CookieClicker.olderThan("7.0.19-SNAPSHOT", MIN));
        assertFalse(CookieClicker.olderThan("7.0.20+build.5", MIN));
    }

    @Test
    void unparseableVersionsAreNotOlder() {
        assertFalse(CookieClicker.olderThan("", MIN));
        assertFalse(CookieClicker.olderThan("abc", MIN));
        assertFalse(CookieClicker.olderThan("unknown", MIN));
    }
}
