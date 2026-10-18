package dev.casperrs.cookieClicker.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.format.NumberFormatter.Settings;
import java.util.List;
import org.junit.jupiter.api.Test;

class NumberFormatterTest {

    private static final List<String> NAMES = List.of("million", "billion", "trillion");

    private static NumberFormatter small() {
        return new NumberFormatter(new Settings(NAMES, "{value} {suffix}", "{value}e{exponent}", 1, 1, ",", ".", "infinity"));
    }

    @Test
    void zeroNanNegativeShowZero() {
        NumberFormatter f = small();
        assertEquals("0", f.format(0));
        assertEquals("0", f.format(-0.0));
        assertEquals("0", f.format(-5));
        assertEquals("0", f.format(Double.NaN));
        assertEquals("0", f.format(Double.NEGATIVE_INFINITY));
    }

    @Test
    void infinityUsesTheConfiguredWord() {
        assertEquals("infinity", small().format(Double.POSITIVE_INFINITY));
    }

    @Test
    void belowThousandUsesSmallDecimalsAndTruncates() {
        NumberFormatter f = small();
        assertEquals("0.1", f.format(0.1));
        assertEquals("12.5", f.format(12.5));
        assertEquals("999", f.format(999));
        assertEquals("999.9", f.format(999.99));
        assertEquals("1.9", f.format(1.99));
        assertEquals("0", f.format(0.04)); // truncated below the shown precision
    }

    @Test
    void thousandsAreGroupedWithoutSuffix() {
        NumberFormatter f = small();
        assertEquals("1,000", f.format(1000));
        assertEquals("12,345", f.format(12_345.9));
        assertEquals("999,999", f.format(999_999));
        assertEquals("999,999", f.format(999_999.99));
    }

    @Test
    void suffixBoundaries() {
        NumberFormatter f = small();
        assertEquals("1 million", f.format(1e6));
        assertEquals("1.2 million", f.format(1.2e6));
        assertEquals("1.2 million", f.format(1_299_999));
        assertEquals("999.9 million", f.format(999_999_999));
        assertEquals("1 billion", f.format(1e9));
        assertEquals("3.4 billion", f.format(3.4e9));
        assertEquals("1 trillion", f.format(1e12));
        assertEquals("999.9 trillion", f.format(999_999_999_999_999.0));
    }

    @Test
    void pastTheLastSuffixGoesScientific() {
        NumberFormatter f = small();
        assertEquals("1e15", f.format(1e15));
        assertEquals("1.2e20", f.format(1.2e20));
        assertEquals("1.7e308", f.format(1.7e308));
        assertEquals("1.7e308", f.format(Double.MAX_VALUE));
    }

    @Test
    void zeroDecimalsAndCustomSeparators() {
        NumberFormatter f = new NumberFormatter(new Settings(NAMES, "{value} {suffix}", "{value}e{exponent}", 0, 2, ".", ",", "oneindig"));
        assertEquals("1 million", f.format(1.9e6));
        assertEquals("12.345", f.format(12_345));
        assertEquals("12,34", f.format(12.349));
        assertEquals("oneindig", f.format(Double.POSITIVE_INFINITY));
    }

    @Test
    void emptySuffixListFallsBackToScientific() {
        NumberFormatter f = new NumberFormatter(new Settings(List.of(), "{value} {suffix}", "{value}e{exponent}", 1, 1, ",", ".", "inf"));
        assertEquals("1e6", f.format(1e6));
        assertEquals("999,999", f.format(999_999));
    }

    @Test
    void decimalsAreClamped() {
        Settings s = new Settings(NAMES, "{value} {suffix}", "{value}e{exponent}", 99, -3, ",", ".", "inf");
        assertEquals(6, s.decimals());
        assertEquals(0, s.smallDecimals());
    }

    @Test
    void realSuffixListRunsToTenToTheNinetyNine() {
        NumberFormatter f = RealMessages.formatter();
        assertEquals("1 duotrigintillion", f.format(1e99));
        assertEquals("9.9 duotrigintillion", f.format(9.9e99));
        assertEquals("1e102", f.format(1e102).replace("1.0e", "1e"));
        assertEquals("1.7e308", f.format(1.7e308));
        assertEquals("1.2 million", f.format(1_234_567));
        assertEquals("3.4 billion", f.format(3.4e9));
    }

    /** A shown number must never exceed the real value (the formatter promises truncation). */
    @Test
    void neverShowsMoreThanTheRealAmountAcrossMagnitudes() {
        NumberFormatter f = RealMessages.formatter();
        for (int exp = 6; exp <= 99; exp++) {
            for (int tenths = 10; tenths <= 99; tenths++) {
                double value = tenths / 10.0 * Math.pow(10, exp);
                String shown = f.format(value);
                double shownNumber = Double.parseDouble(shown.substring(0, shown.indexOf(' ')))
                        * Math.pow(10, 3 * (exp / 3));
                assertTrue(shownNumber <= value * (1 + 1e-12), shown + " > " + value);
            }
        }
    }

    /**
     * Round tenths ("1.2 million", "3.4 billion") are what players look at most. Truncation must not turn
     * 3.4e9 into "3.3 billion" because of a floating-point division.
     */
    @Test
    void roundTenthsStayRoundUpToTenToTheTwentyOne() {
        assertRoundTenths(21);
    }

    /** Regression: from 10^22 up, 1e23 used to show as "99.9 sextillion" (inexact Math.pow); fixed with BigDecimal. */
    @Test
    void roundTenthsStayRoundAcrossAllMagnitudes() {
        assertRoundTenths(99);
    }

    private static void assertRoundTenths(int maxExp) {
        NumberFormatter f = RealMessages.formatter();
        StringBuilder wrong = new StringBuilder();
        for (int exp = 6; exp <= maxExp; exp++) {
            for (int tenths = 10; tenths <= 99; tenths++) {
                double value = Double.parseDouble(tenths / 10 + "." + tenths % 10 + "e" + exp);
                String shown = f.format(value);
                String expectedNumber = (tenths / 10 + "." + tenths % 10).replace(".0", "");
                int group = exp / 3;
                int shift = exp - 3 * group;
                java.math.BigDecimal mantissa = new java.math.BigDecimal(tenths).movePointLeft(1).movePointRight(shift);
                String exact = mantissa.stripTrailingZeros().toPlainString();
                if (!shown.startsWith(exact + " ")) {
                    wrong.append(value).append("->'").append(shown).append("' (want ").append(exact).append("); ");
                }
            }
        }
        assertEquals("", wrong.toString(), "truncation off by one ulp");
    }
}
