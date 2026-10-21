package dev.casperrs.cookieClicker.view;

/**
 * A panel value rolling from {@code from} to {@code to} over {@code duration} ticks from {@code start}, eased
 * (ease-out cubic) and interpolated in log space, so 1K → 1M moves as evenly as 1 → 1K. Monotonic between the
 * two values, and {@link #valueAt} returns exactly {@code to} from the last tick on. When either end is not a
 * positive finite number the value switches straight to {@code to}. Pure Java.
 */
public record PriceTween(double from, double to, long start, int duration) {

    /** A finished tween that just shows {@code value}. */
    public static PriceTween fixed(double value) {
        return new PriceTween(value, value, Long.MIN_VALUE / 2, 0);
    }

    /**
     * Rolls to {@code newTo} starting at {@code now}, from what this tween shows at {@code now}, so a new target
     * (a purchase, a mode switch, the 1 Hz refresh) never makes the value jump. Same target = this tween unchanged,
     * so the refresh never cuts a running roll short.
     */
    public PriceTween retarget(double newTo, long now, int ticks) {
        if (Double.compare(newTo, to) == 0) {
            return this;
        }
        double shown = valueAt(now);
        if (Double.compare(shown, newTo) == 0 || ticks <= 0) {
            return fixed(newTo);
        }
        return new PriceTween(shown, newTo, now, ticks);
    }

    public boolean done(long now) {
        return now >= start + duration;
    }

    public double valueAt(long now) {
        if (done(now) || Double.compare(from, to) == 0) {
            return to;
        }
        double t = Math.clamp((double) (now - start) / duration, 0.0, 1.0);
        return between(from, to, ease(t));
    }

    /** Ease-out cubic: fast start, gentle landing. Monotonic on [0, 1], 0 → 0, 1 → 1. */
    public static double ease(double t) {
        double u = 1 - t;
        return 1 - u * u * u;
    }

    /** The value at fraction {@code f} (0..1) of the way, in log space when both ends are positive and finite. */
    public static double between(double a, double b, double f) {
        if (f >= 1) {
            return b;
        }
        if (f <= 0) {
            return a;
        }
        if (!(a > 0) || !(b > 0) || !Double.isFinite(a) || !Double.isFinite(b)) {
            return b;
        }
        double v = Math.exp(Math.log(a) + (Math.log(b) - Math.log(a)) * f);
        // keep it inside [min, max] whatever the rounding of exp/log does
        return Math.clamp(v, Math.min(a, b), Math.max(a, b));
    }
}
