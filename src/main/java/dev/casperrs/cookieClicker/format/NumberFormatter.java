package dev.casperrs.cookieClicker.format;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Formats cookie amounts for players: {@code 0.1}, {@code 999}, {@code 12,345}, {@code 1.2 million},
 * {@code 3.4 billion} … up to the last suffix word in {@code messages.yml}, then scientific ({@code 1.2e309}).
 * Values are truncated, never rounded up, so a shown amount is never more than the player really has.
 * NaN and negatives show as 0, infinity as the configured word. Pure Java; thread-safe (immutable).
 */
public final class NumberFormatter {

    /**
     * @param suffixes          words for 10^6, 10^9, 10^12, … in order ("million", "billion", …)
     * @param largePattern      template for big numbers, with {@code {value}} and {@code {suffix}}
     * @param scientificPattern template past the last suffix, with {@code {value}} and {@code {exponent}}
     * @param decimals          decimals shown for big numbers
     * @param smallDecimals     decimals shown below 1,000
     */
    public record Settings(List<String> suffixes, String largePattern, String scientificPattern,
                           int decimals, int smallDecimals, String groupingSeparator,
                           String decimalSeparator, String infinity) {
        public Settings {
            suffixes = List.copyOf(suffixes);
            Objects.requireNonNull(largePattern);
            Objects.requireNonNull(scientificPattern);
            Objects.requireNonNull(groupingSeparator);
            Objects.requireNonNull(decimalSeparator);
            Objects.requireNonNull(infinity);
            decimals = Math.clamp(decimals, 0, 6);
            smallDecimals = Math.clamp(smallDecimals, 0, 6);
        }
    }

    private static final double MILLION = 1e6;

    private final Settings settings;

    public NumberFormatter(Settings settings) {
        this.settings = Objects.requireNonNull(settings);
    }

    public Settings settings() {
        return settings;
    }

    public String format(double value) {
        if (value == Double.POSITIVE_INFINITY) {
            return settings.infinity();
        }
        if (Double.isNaN(value) || value <= 0) {
            return "0";
        }
        if (value < 1000) {
            return decimal(value, settings.smallDecimals());
        }
        if (value < MILLION) {
            return grouped((long) Math.floor(value));
        }
        // Exact decimal digits of the double (shortest repr), so the mantissa is never off by one step:
        // dividing by Math.pow(10, 3 * group) is inexact from 10^22 up and truncation then shows 99.9 for 100
        BigDecimal exact = BigDecimal.valueOf(value);
        int exponent = exact.precision() - exact.scale() - 1; // value >= 1e6, so this is floor(log10(value))
        int group = exponent / 3;
        int suffixIndex = group - 2;
        if (suffixIndex < settings.suffixes().size()) {
            return settings.largePattern()
                    .replace("{value}", decimal(exact.movePointLeft(3 * group), settings.decimals()))
                    .replace("{suffix}", settings.suffixes().get(suffixIndex));
        }
        return settings.scientificPattern()
                .replace("{value}", decimal(exact.movePointLeft(exponent), settings.decimals()))
                .replace("{exponent}", Integer.toString(exponent));
    }

    private String decimal(double v, int decimals) {
        return decimal(BigDecimal.valueOf(v), decimals);
    }

    /** Truncated to {@code decimals}, trailing zeros removed, configured decimal separator. */
    private String decimal(BigDecimal v, int decimals) {
        String plain = v.setScale(decimals, RoundingMode.DOWN).stripTrailingZeros().toPlainString();
        return plain.replace(".", settings.decimalSeparator());
    }

    private String grouped(long v) {
        String digits = Long.toString(v);
        StringBuilder out = new StringBuilder(digits.length() + digits.length() / 3);
        int first = digits.length() % 3 == 0 ? 3 : digits.length() % 3;
        out.append(digits, 0, first);
        for (int i = first; i < digits.length(); i += 3) {
            out.append(settings.groupingSeparator()).append(digits, i, i + 3);
        }
        return out.toString();
    }
}
