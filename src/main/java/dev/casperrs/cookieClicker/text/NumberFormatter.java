package dev.casperrs.cookieClicker.text;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Formats cookie amounts for players: {@code 0.1}, {@code 999}, then short suffixes from 1,000:
 * {@code 1K}, {@code 1.5K}, {@code 2.3M}, {@code 4.1B} … up to the last suffix in {@code messages.yml},
 * then scientific ({@code 1.2e309}).
 * Values are truncated, never rounded up, so a shown amount is never more than the player really has.
 * NaN and negatives show as 0, infinity as the configured word. Pure Java; thread-safe (immutable).
 */
public final class NumberFormatter {

    /**
     * Suffixes for 10^3, 10^6, … in order; scientificPattern is used past the last suffix; smallDecimals below 1,000;
     * groupingSeparator is unused, read so old messages.yml files stay valid.
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
        // Exact decimal digits of the double (shortest repr), so the mantissa is never off by one step:
        // dividing by Math.pow(10, 3 * group) is inexact from 10^22 up and truncation then shows 99.9 for 100
        BigDecimal exact = BigDecimal.valueOf(value);
        int exponent = exact.precision() - exact.scale() - 1; // value >= 1000, so this is floor(log10(value))
        int group = exponent / 3;
        int suffixIndex = group - 1;
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
}
