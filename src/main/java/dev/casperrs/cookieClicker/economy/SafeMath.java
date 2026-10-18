package dev.casperrs.cookieClicker.economy;

/** Cookie amounts are doubles that must never become NaN, infinite or negative. */
public final class SafeMath {

    private SafeMath() {
    }

    /** {@code a + b}, saturating at {@link Double#MAX_VALUE}; NaN keeps {@code a}. */
    public static double add(double a, double b) {
        double sum = a + b;
        if (Double.isNaN(sum)) {
            return sanitize(a);
        }
        return Math.min(sum, Double.MAX_VALUE);
    }

    /** {@code a * b}, saturating at {@link Double#MAX_VALUE}; NaN becomes 0. */
    public static double multiply(double a, double b) {
        return sanitize(a * b);
    }

    /** NaN and negatives become 0, +infinity becomes {@link Double#MAX_VALUE}. */
    public static double sanitize(double v) {
        if (Double.isNaN(v) || v <= 0) {
            return 0;
        }
        return Math.min(v, Double.MAX_VALUE);
    }
}
