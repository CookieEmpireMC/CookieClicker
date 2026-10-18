package dev.casperrs.cookieClicker.vfx.shape;

/** Easing curves on {@code p} in [0, 1]. Plain maths, no Bukkit types. */
public final class Easing {

    private Easing() {
    }

    /** Fast start, settles at the end: {@code 1 - (1 - p)^2}. The house default for motion. */
    public static double outQuad(double p) {
        double c = clamp01(p);
        return 1 - (1 - c) * (1 - c);
    }

    public static double clamp01(double p) {
        return p < 0 ? 0 : (p > 1 ? 1 : p);
    }

    public static double lerp(double a, double b, double p) {
        return a + (b - a) * p;
    }
}
