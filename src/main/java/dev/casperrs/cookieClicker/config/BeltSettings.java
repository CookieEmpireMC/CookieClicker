package dev.casperrs.cookieClicker.config;

import dev.casperrs.cookieClicker.belt.BeltCurve;
import org.bukkit.configuration.ConfigurationSection;

import java.util.function.Consumer;

/**
 * {@code config.yml} {@code belt.*}: the conveyor belt look and its CPS -&gt; speed/interval curve. Purely visual (no
 * economy value).
 *
 * @param enabled      belt surfaces and cookies on/off
 * @param maxCookies   cookies per player on the belt at once
 * @param cookieScale  size of a belt cookie (1.0 = one block)
 * @param curve        CPS -&gt; speed (blocks/second) and interval (ticks)
 * @param soundEvery   ticks between {@code belt_loop} plays (0 = silent)
 * @param fallbackItem vanilla item (a {@code Material} name) of the belt surface for players without our pack
 * @param surfaceYaw   degrees added to the belt surface's turn (180 flips it, if the slats run the wrong way)
 */
public record BeltSettings(boolean enabled, int maxCookies, double cookieScale, BeltCurve curve, int soundEvery,
                           String fallbackItem, double surfaceYaw) {

    public static final int MAX_COOKIES = 32;

    public static BeltSettings from(ConfigurationSection c, Consumer<String> problems) {
        double speedMin = Math.clamp(c.getDouble("belt.speed.min", 0.6), 0.05, 20.0);
        double speedMax = Math.clamp(c.getDouble("belt.speed.max", 2.5), 0.05, 20.0);
        if (speedMax < speedMin) {
            problems.accept("config.yml: belt.speed.max is below belt.speed.min; using min for both");
            speedMax = speedMin;
        }
        int intervalMin = Math.clamp(c.getInt("belt.interval.min", 8), 1, 1200);
        int intervalMax = Math.clamp(c.getInt("belt.interval.max", 80), 1, 1200);
        if (intervalMax < intervalMin) {
            problems.accept("config.yml: belt.interval.max is below belt.interval.min; using min for both");
            intervalMax = intervalMin;
        }
        double scale = c.getDouble("belt.cps-scale", 1000.0);
        if (!(scale > 0) || Double.isInfinite(scale)) {
            problems.accept("config.yml: belt.cps-scale must be > 0; using 1000");
            scale = 1000.0;
        }
        return new BeltSettings(
                c.getBoolean("belt.enabled", true),
                Math.clamp(c.getInt("belt.max-cookies", 8), 0, MAX_COOKIES),
                Math.clamp(c.getDouble("belt.cookie-scale", 0.45), 0.05, 2.0),
                new BeltCurve(speedMin, speedMax, intervalMin, intervalMax, scale),
                Math.clamp(c.getInt("belt.sound-every", 100), 0, 6000),
                c.getString("belt.fallback-item", "BLACK_CARPET").trim(),
                c.getDouble("belt.surface-yaw", 0.0) % 360.0);
    }
}
