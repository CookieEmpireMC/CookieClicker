package dev.casperrs.cookieClicker.config;

import java.util.List;
import java.util.Map;

/**
 * The cookie basket on the pedestal. Visual thresholds only (not economy values): which look
 * ({@code <tier>_<fill>}) the pouch shows, how high each look is, where the cookie floats and when a tier-up switches
 * the look. tierStarts are pouch cookies (each tier's 5 fills split on a log scale); hysteresis is a fraction past the
 * boundary; heights in blocks (above the basket bottom, per fill 0..4; cookieMaxHeight 1.62 + 0.6 keeps it
 * clickable); times in ticks (moveTicks 0..59).
 */
public record BasketSettings(List<Double> tierStarts, double hysteresis, Map<String, List<Double>> heights,
                             double cookieGap, double cookieMaxHeight, int moveTicks, int tierUpSwitchTicks) {

    /** Tier names from the contract ({@code cookieclicker:cookie_basket}), lowest first. */
    public static final List<String> TIERS = List.of("wicker", "bushel", "golden");
    /** Fill steps per tier (0..4). */
    public static final int FILLS = 5;

    private static final List<Double> DEFAULT_STARTS = List.of(1.0, 1.0e6, 1.0e9, 1.0e12);
    private static final Map<String, List<Double>> DEFAULT_HEIGHTS = Map.of(
            // design/basket.md top heights; golden never below its corner posts (0.688)
            "wicker", List.of(0.281, 0.313, 0.406, 0.5, 0.594),
            "bushel", List.of(0.484, 0.5, 0.594, 0.688, 0.781),
            "golden", List.of(0.69, 0.69, 0.781, 0.969, 1.188));
    private static final BasketSettings DEFAULTS =
            new BasketSettings(DEFAULT_STARTS, 0.05, DEFAULT_HEIGHTS, 0.1, 2.2, 10, 8);

    public static BasketSettings defaults() {
        return DEFAULTS;
    }

    /** Top height of look {@code tier}/{@code fill} above the basket bottom. */
    public double height(int tier, int fill) {
        return heights.get(TIERS.get(tier)).get(fill);
    }
}
