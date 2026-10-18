package dev.casperrs.cookieClicker.view;

import dev.casperrs.cookieClicker.config.StationVisualSettings;

/**
 * Idle spin + bob of the giant cookie as a function of the server tick. The controller samples it once per
 * segment and lets the client interpolate in between (no teleports). Pure maths.
 *
 * @param revolutionTicks ticks per full turn (0 = no spin)
 * @param bobPeriodTicks  ticks per bob up-and-down
 */
public record CookieMotion(double revolutionTicks, double bobAmplitude, int bobPeriodTicks, int segmentTicks) {

    public static CookieMotion of(StationVisualSettings.Cookie c) {
        return new CookieMotion(c.spinSecondsPerRevolution() * 20, c.bobAmplitude(), c.bobPeriodTicks(), c.segmentTicks());
    }

    /** Yaw of the spin at {@code tick}, radians in [0, 2π). Consecutive segments differ by less than 120°. */
    public double angle(long tick) {
        if (revolutionTicks <= 0) {
            return 0;
        }
        double turns = tick / revolutionTicks; // server ticks stay far below 2^53, so this is precise
        double phase = turns - Math.floor(turns);
        return 2 * Math.PI * phase;
    }

    /** Vertical offset of the bob at {@code tick}, blocks. */
    public double bob(long tick) {
        if (bobAmplitude == 0 || bobPeriodTicks <= 0) {
            return 0;
        }
        return bobAmplitude * Math.sin(2 * Math.PI * (tick % bobPeriodTicks) / bobPeriodTicks);
    }
}
