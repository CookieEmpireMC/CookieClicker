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
        int group = (int) Math.floor(Math.log10(value) / 3);
        double mantissa = value / Math.pow(10, 3.0 * group);
        // log10 can be off by one ulp right at a power of 1000
        if (mantissa >= 1000) {
            group++;
            mantissa /= 1000;
        } else if (mantissa < 1) {
            group--;
            mantissa *= 1000;
        }
        int suffixIndex = group - 2;
        if (suffixIndex < settings.suffixes().size()) {
            return settings.largePattern()
                    .replace("{value}", decimal(mantissa, settings.decimals()))
                    .replace("{suffix}", settings.suffixes().get(suffixIndex));
        }
        int exponent = (int) Math.floor(Math.log10(value));
        double m = value / Math.pow(10, exponent);
        if (m >= 10) {
            exponent++;
            m /= 10;
        } else if (m < 1) {
            exponent--;
            m *= 10;
        }
        return settings.scientificPattern()
                .replace("{value}", decimal(m, settings.decimals()))
                .replace("{exponent}", Integer.toString(exponent));
    }

    /** Truncated to {@code decimals}, trailing zeros removed, configured decimal separator. */
    private String decimal(double v, int decimals) {
        String plain = BigDecimal.valueOf(v).setScale(decimals, RoundingMode.DOWN).stripTrailingZeros().toPlainString();
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
