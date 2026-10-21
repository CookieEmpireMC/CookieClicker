package dev.casperrs.cookieClicker.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.format.NumberFormatter.Settings;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Short suffixes from 1,000 ({@code 1K}, {@code 2.3M}, … {@code DTg}), truncated and never rounded up, then
 * scientific. Most cases use the real suffix list from {@code messages.yml}; the small formatter checks the
 * settings themselves (decimals, separators, empty list).
 */
class NumberFormatterTest {

    private static final List<String> SMALL = List.of("K", "M", "B", "T");

    private static NumberFormatter small() {
        return new NumberFormatter(new Settings(SMALL, "{value}{suffix}", "{value}e{exponent}", 1, 1, ",", ".", "infinity"));
    }

    private static List<String> realSuffixes() {
        return RealMessages.yaml().getStringList("numbers.suffixes");
    }

    @Test
    void zeroNanNegativeShowZero() {
        NumberFormatter f = small();
        assertEquals("0", f.format(0));
        assertEquals("0", f.format(-0.0));
        assertEquals("0", f.format(-5));
        assertEquals("0", f.format(Double.NaN));
        assertEquals("0", f.format(Double.NEGATIVE_INFINITY));
        assertEquals("infinity", f.format(Double.POSITIVE_INFINITY));
    }

    @Test
    void belowThousandUsesSmallDecimalsAndTruncates() {
        NumberFormatter f = small();
        assertEquals("0.1", f.format(0.1));
        assertEquals("12.5", f.format(12.5));
        assertEquals("999", f.format(999));
        assertEquals("999.9", f.format(999.99));
        assertEquals("999.9", f.format(999.9999999999999)); // never rounds up to 1000
        assertEquals("0", f.format(0.04));
    }

    @Test
    void realListThousandsAndTheTruncationEdgesAroundThem() {
        NumberFormatter f = RealMessages.formatter();
        assertEquals("999", f.format(999));
        assertEquals("1K", f.format(1000));
        assertEquals("1.5K", f.format(1500));
        assertEquals("12.3K", f.format(12_345.9));
        assertEquals("999.9K", f.format(999_999));    // truncated, never "1M"
        assertEquals("999.9K", f.format(999_999.99));
        assertEquals("1M", f.format(1_000_000));
    }

    @Test
    void smallSettingsWriteTheSuffixDirectly() {
        NumberFormatter f = small();
        assertEquals("1M", f.format(1e6));
        assertEquals("1.2M", f.format(1.2e6));
        assertEquals("1.2M", f.format(1_299_999));
        assertEquals("999.9M", f.format(999_999_999));
        assertEquals("1B", f.format(1e9));
        assertEquals("3.4B", f.format(3.4e9));
        assertEquals("1T", f.format(1e12));
        assertEquals("999.9T", f.format(999_999_999_999_999.0));
    }

    @Test
    void everySuffixBoundaryShowsItsNameAndTheValueBelowRoundsDown() {
        NumberFormatter f = RealMessages.formatter();
        List<String> suffixes = realSuffixes();
        assertEquals(33, suffixes.size());
        for (int group = 1; group <= suffixes.size(); group++) {
            assertEquals("1" + suffixes.get(group - 1), f.format(Double.parseDouble("1e" + 3 * group)), "1 at group " + group);
            assertEquals("1.5" + suffixes.get(group - 1), f.format(Double.parseDouble("1.5e" + 3 * group)), "1.5 at group " + group);
            if (group >= 2) {
                // 999.99 × 10^(3·group − 3) is just under the next name: it shows 999.9 of the previous suffix
                assertEquals("999.9" + suffixes.get(group - 2), f.format(Double.parseDouble("999.99e" + (3 * group - 3))),
                        "just below group " + group);
            }
        }
    }

    @Test
    void pastTheLastSuffixGoesScientific() {
        NumberFormatter f = RealMessages.formatter();
        assertEquals("1e102", f.format(1e102));
        assertEquals("1.5e150", f.format(1.5e150));
        assertEquals("1.7e308", f.format(Double.MAX_VALUE));
        assertEquals("1DTg", f.format(1e99));
        assertEquals("9.9DTg", f.format(9.9e99));
    }

    @Test
    void zeroDecimalsAndCustomSeparators() {
        NumberFormatter f = new NumberFormatter(new Settings(SMALL, "{value} {suffix}", "{value}e{exponent}", 0, 2, ".", ",", "oneindig"));
        assertEquals("1 M", f.format(1.9e6));
        assertEquals("12 K", f.format(12_345));
        assertEquals("12,34", f.format(12.349));
        assertEquals("oneindig", f.format(Double.POSITIVE_INFINITY));
    }

    @Test
    void emptySuffixListFallsBackToScientific() {
        NumberFormatter f = new NumberFormatter(new Settings(List.of(), "{value}{suffix}", "{value}e{exponent}", 1, 1, ",", ".", "inf"));
        assertEquals("1e6", f.format(1e6));
        assertEquals("9.9e5", f.format(999_999));
        assertEquals("999.9", f.format(999.9));
    }

    @Test
    void decimalsAreClamped() {
        Settings s = new Settings(SMALL, "{value}{suffix}", "{value}e{exponent}", 99, -3, ",", ".", "inf");
        assertEquals(6, s.decimals());
        assertEquals(0, s.smallDecimals());
    }

    /**
     * Round tenths ("1.2M", "3.4B") are what players look at most. Truncation must not turn 3.4e9 into "3.3B"
     * because of a floating-point division (the old Math.pow path did that from 10^22 up).
     */
    @Test
    void roundTenthsStayRoundAcrossEveryMagnitude() {
        NumberFormatter f = RealMessages.formatter();
        List<String> suffixes = realSuffixes();
        StringBuilder wrong = new StringBuilder();
        for (int exp = 6; exp <= 99; exp++) {
            for (int tenths = 10; tenths <= 99; tenths++) {
                double value = Double.parseDouble(tenths / 10 + "." + tenths % 10 + "e" + exp);
                int group = exp / 3;
                BigDecimal mantissa = new BigDecimal(tenths).movePointLeft(1).movePointRight(exp - 3 * group);
                String want = mantissa.stripTrailingZeros().toPlainString() + suffixes.get(group - 1);
                String shown = f.format(value);
                if (!shown.equals(want)) {
                    wrong.append(value).append("->'").append(shown).append("' (want ").append(want).append("); ");
                }
            }
        }
        assertEquals("", wrong.toString());
    }

    /** A shown number must never exceed the real value (the formatter promises truncation). Exact, via BigDecimal. */
    @Test
    void neverShowsMoreThanTheRealAmountAcrossMagnitudes() {
        NumberFormatter f = RealMessages.formatter();
        List<String> suffixes = realSuffixes();
        for (int exp = 3; exp <= 99; exp++) {
            for (int tenths = 10; tenths <= 99; tenths += 7) {
                double value = Double.parseDouble(tenths / 10 + "." + tenths % 10 + "e" + exp);
                String shown = f.format(value);
                int group = exp / 3;
                String suffix = suffixes.get(group - 1);
                assertTrue(shown.endsWith(suffix), shown + " should end with " + suffix);
                BigDecimal shownValue = new BigDecimal(shown.substring(0, shown.length() - suffix.length()))
                        .movePointRight(3 * group);
                assertTrue(shownValue.compareTo(BigDecimal.valueOf(value)) <= 0, shown + " > " + value);
            }
        }
    }
}
