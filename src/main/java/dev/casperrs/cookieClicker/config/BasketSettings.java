package dev.casperrs.cookieClicker.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The cookie basket on the pedestal ({@code config.yml: station.basket.*}). Visual thresholds only (not economy
 * values): which look ({@code <tier>_<fill>}) the pouch shows, how high each look is, where the cookie floats and
 * when a tier-up switches the look.
 *
 * @param tierStarts        cookies in the pouch where wicker, bushel and golden start, then where golden is full
 *                          (4 increasing values &gt; 0); each tier's 5 fill steps split its range on a log scale
 * @param hysteresis        a look changes only once the pouch is this fraction past the boundary (no flicker)
 * @param heights           tier → top height of the look per fill 0..4 (blocks above the basket bottom; the highest
 *                          point the cookie must clear, e.g. golden's corner posts)
 * @param cookieGap         air between the basket top and the cookie's lowest point (blocks)
 * @param cookieMaxHeight   the cookie centre never goes higher than this above the ground (eye level + 0.6)
 * @param moveTicks         the cookie glides to a new height over this many ticks (0..59, teleport duration)
 * @param tierUpSwitchTicks on a tier-up the look switches this many ticks after the effect starts (its peak)
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

    public static BasketSettings defaults() {
        return new BasketSettings(DEFAULT_STARTS, 0.05, DEFAULT_HEIGHTS, 0.1, 2.2, 10, 8);
    }

    /** Top height of look {@code tier}/{@code fill} above the basket bottom. */
    public double height(int tier, int fill) {
        return heights.get(TIERS.get(tier)).get(fill);
    }

    static BasketSettings from(ConfigurationSection c, Consumer<String> problems) {
        String p = "station.basket.";
        List<Double> starts = numbers(c, p + "tier-starts", 4, DEFAULT_STARTS, problems);
        for (int i = 0; i < starts.size(); i++) {
            if (!(starts.get(i) > 0) || (i > 0 && !(starts.get(i) > starts.get(i - 1)))) {
                problems.accept("config.yml: " + p + "tier-starts must be 4 increasing numbers > 0; using default");
                starts = DEFAULT_STARTS;
                break;
            }
        }
        Map<String, List<Double>> heights = new java.util.LinkedHashMap<>();
        for (String tier : TIERS) {
            List<Double> h = numbers(c, p + "heights." + tier, FILLS, DEFAULT_HEIGHTS.get(tier), problems);
            if (h.stream().anyMatch(v -> !(v >= 0) || v > 3)) {
                problems.accept("config.yml: " + p + "heights." + tier + " must be 0..3 blocks; using default");
                h = DEFAULT_HEIGHTS.get(tier);
            }
            heights.put(tier, h);
        }
        return new BasketSettings(starts,
                Math.clamp(c.getDouble(p + "hysteresis", 0.05), 0, 1),
                Map.copyOf(heights),
                Math.clamp(c.getDouble(p + "cookie-gap", 0.1), 0, 2),
                Math.clamp(c.getDouble(p + "cookie-max-height", 2.2), 0.5, 5),
                Math.clamp(c.getInt(p + "move-ticks", 10), 0, 59),
                Math.clamp(c.getInt(p + "tier-up-switch-ticks", 8), 0, 100));
    }

    private static List<Double> numbers(ConfigurationSection c, String path, int size, List<Double> fallback,
                                        Consumer<String> problems) {
        if (!c.contains(path)) {
            return fallback;
        }
        List<?> list = c.getList(path);
        if (list == null || list.size() != size || !list.stream().allMatch(o -> o instanceof Number)) {
            problems.accept("config.yml: " + path + " must be a list of " + size + " numbers; using default");
            return fallback;
        }
        List<Double> out = new ArrayList<>(size);
        for (Object o : list) {
            out.add(((Number) o).doubleValue());
        }
        return List.copyOf(out);
    }
}
