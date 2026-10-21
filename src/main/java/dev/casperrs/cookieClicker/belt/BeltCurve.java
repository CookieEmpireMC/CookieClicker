package dev.casperrs.cookieClicker.belt;

/**
 * CPS -&gt; belt speed and cookie interval (brief conveyor-belt). Purely visual; no economy value. Pure Java.
 *
 * <p>The curve is logarithmic, because CPS spans many orders of magnitude:
 * <pre>
 * t        = clamp( log(1 + cps) / log(1 + cpsScale), 0, 1 )     (cps &lt;= 0: the belt is idle)
 * speed    = speedMin + (speedMax - speedMin) * t                 blocks per second
 * interval = intervalMax - (intervalMax - intervalMin) * t        ticks between two cookies
 * </pre>
 * So the belt starts slow with a cookie now and then, and reaches full speed and the shortest gap at
 * {@code cpsScale} CPS (and stays there above it).
 *
 * @param speedMin    blocks/second at the lowest CPS (&gt; 0)
 * @param speedMax    blocks/second at {@code cpsScale} CPS and above (&gt;= speedMin)
 * @param intervalMin ticks between cookies at {@code cpsScale} CPS and above (&gt;= 1)
 * @param intervalMax ticks between cookies at the lowest CPS (&gt;= intervalMin)
 * @param cpsScale    the CPS at which the belt is at full speed (&gt; 0)
 */
public record BeltCurve(double speedMin, double speedMax, int intervalMin, int intervalMax, double cpsScale) {

    public BeltCurve {
        if (!(speedMin > 0) || !(speedMax >= speedMin) || intervalMin < 1 || intervalMax < intervalMin
                || !(cpsScale > 0) || Double.isInfinite(speedMax) || Double.isInfinite(cpsScale)) {
            throw new IllegalArgumentException("bad belt curve " + speedMin + ".." + speedMax + " b/s, "
                    + intervalMin + ".." + intervalMax + " ticks, scale " + cpsScale);
        }
    }

    /** Whether cookies come out at all at this CPS. */
    public boolean running(double cps) {
        return cps > 0; // false for NaN
    }

    /** 0 at (almost) no CPS, 1 at {@code cpsScale} and above. */
    public double progress(double cps) {
        if (!running(cps)) {
            return 0;
        }
        if (cps >= cpsScale) {
            return 1;
        }
        return Math.clamp(Math.log1p(cps) / Math.log1p(cpsScale), 0.0, 1.0);
    }

    /** Belt speed in blocks per second. */
    public double speed(double cps) {
        return speedMin + (speedMax - speedMin) * progress(cps);
    }

    /** Ticks between two cookies (at least 1). */
    public int interval(double cps) {
        return Math.max(1, (int) Math.round(intervalMax - (intervalMax - intervalMin) * progress(cps)));
    }
}
