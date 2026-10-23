package dev.casperrs.cookieClicker.config;

import dev.casperrs.cookieClicker.belt.BeltCurve;
import org.bukkit.configuration.ConfigurationSection;

import java.util.function.Consumer;

/**
 * The conveyor belt look and its CPS -&gt; speed/interval curve. Purely visual. soundEvery in ticks (0 = silent);
 * angles in degrees (surfaceYaw 0 = slats move toward the pedestal, cookiePitch 90 = lying flat).
 */
public record BeltSettings(boolean enabled, int maxCookies, double cookieScale, BeltCurve curve, int soundEvery,
                           String fallbackItem, double surfaceYaw, double cookiePitch) {

    public static final int MAX_COOKIES = 32;

    private static final double COOKIE_SCALE = 0.45;
    /** t = log(1 + cps) / log(1 + 1000): slow with a cookie now and then at first, full speed from 1000 CPS. */
    private static final BeltCurve CURVE = new BeltCurve(0.6, 2.5, 8, 80, 1000.0);
    private static final int SOUND_EVERY = 100;
    private static final String FALLBACK_ITEM = "BLACK_CARPET";
    private static final double SURFACE_YAW = 0.0;
    private static final double COOKIE_PITCH = 90.0;

    public static BeltSettings from(ConfigurationSection c, Consumer<String> problems) {
        return new BeltSettings(
                c.getBoolean("belt.enabled", true),
                Math.clamp(c.getInt("belt.max-cookies", 8), 0, MAX_COOKIES),
                COOKIE_SCALE, CURVE, SOUND_EVERY, FALLBACK_ITEM, SURFACE_YAW, COOKIE_PITCH);
    }
}
