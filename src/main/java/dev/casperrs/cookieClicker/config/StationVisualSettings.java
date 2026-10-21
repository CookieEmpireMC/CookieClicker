package dev.casperrs.cookieClicker.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The per-player station visuals from {@code config.yml} ({@code station.view-distance}, {@code station.cookie.*},
 * {@code station.pedestal.*}, {@code station.panel.*}). Pure data; every number here is layout/looks, not balance.
 * Offsets are in the station's own frame: {@code right}/{@code up}/{@code forward} as seen by the admin who placed
 * it (forward = the way they were facing).
 */
public record StationVisualSettings(double viewDistance, int refreshTicks, double clickDistance, boolean debugClicks,
                                    Cookie cookie, Pedestal pedestal, Panel panel) {

    /** A position in the station frame, in blocks. */
    public record Offset(double right, double up, double forward) {
        public static final Offset ZERO = new Offset(0, 0, 0);
    }

    /**
     * @param spinSecondsPerRevolution one full turn takes this long (0 = no spin)
     * @param segmentTicks             length of one interpolated motion segment
     * @param brightness               block and sky light of the cookie display (0-15)
     * @param clickEffectOffset        the click effect is pulled this far out of the cookie toward the player
     */
    public record Cookie(Offset offset, float scale, double bobAmplitude, int bobPeriodTicks,
                         double spinSecondsPerRevolution, int segmentTicks, double hitboxMargin, float viewRange,
                         int brightness, double clickEffectOffset) {

        /** Radians the cookie turns per segment (0 when the spin is off). */
        public double spinStepRadians() {
            return spinSecondsPerRevolution <= 0 ? 0 : 2 * Math.PI * segmentTicks / (spinSecondsPerRevolution * 20);
        }
    }

    /** A block display under the cookie. {@code offset} is the centre of its bottom face. */
    public record Pedestal(boolean enabled, String block, Offset offset, double width, double height) {
    }

    /**
     * @param offset        where the header text sits (its bottom centre)
     * @param textYawOffset degrees added to the text yaw (calibration if text shows mirrored)
     * @param iconYawOffset degrees added to the icon yaw (calibration for the FIXED item transform)
     * @param firstRowDrop  first row's text anchor sits this far below the header anchor
     * @param rowPitch      distance between row anchors
     * @param textOffset    row text anchor relative to the row anchor: the left edge of the text background when
     *                      {@code textLeftEdge}, else the centre of the text block
     * @param iconOffset    row icon centre relative to the row anchor
     * @param hitboxOffset  bottom centre of the row's Interaction relative to the row anchor
     * @param colors        background colours as {@code #AARRGGBB}: header, affordable, unaffordable, locked
     * @param iconFallbacks building id -> vanilla material name shown to players without the pack
     * @param textBrightness block and sky light of the header and row texts (0-15)
     * @param textLeftEdge  shift each row text right by half its estimated rendered width so every row starts at
     *                      {@code textOffset} (a text display is otherwise centred on its position)
     */
    public record Panel(Offset offset, float textYawOffset, float iconYawOffset, float headerScale, float textScale,
                        int lineWidth, double firstRowDrop, double rowPitch, Offset textOffset, Offset iconOffset,
                        float iconScale, Offset hitboxOffset, float hitboxWidth, float hitboxHeight,
                        Map<String, Integer> colors, int iconBrightAffordable, int iconBrightUnaffordable,
                        float viewRange, Map<String, String> iconFallbacks, String defaultFallback,
                        int textBrightness, boolean textShadowed, boolean textSeeThrough,
                        boolean textDefaultBackground, boolean textLeftEdge) {

        public int color(String state) {
            return colors.getOrDefault(state, 0x40000000);
        }

        public String fallbackFor(String buildingId) {
            return iconFallbacks.getOrDefault(buildingId, defaultFallback);
        }
    }

    public static StationVisualSettings from(ConfigurationSection c, Consumer<String> problems) {
        Reader r = new Reader(c, problems);
        Cookie cookie = new Cookie(
                r.offset("station.cookie.offset", new Offset(0, 1.8, 2.5)),
                (float) r.positive("station.cookie.scale", 1.4),
                Math.max(0, c.getDouble("station.cookie.bob-amplitude", 0.1)),
                (int) r.positive("station.cookie.bob-period-ticks", 80),
                Math.max(0, c.getDouble("station.cookie.spin-seconds-per-revolution", 6)),
                (int) Math.clamp(r.positive("station.cookie.segment-ticks", 10), 2, 40),
                Math.max(0, c.getDouble("station.cookie.hitbox-margin", 0.1)),
                (float) r.positive("station.cookie.view-range", 0.5),
                Math.clamp(c.getInt("station.cookie.brightness", 15), 0, 15),
                Math.max(0, c.getDouble("station.cookie.click-effect-offset", 0.1)));
        if (cookie.spinStepRadians() > Math.toRadians(120)) {
            problems.accept("config.yml: station.cookie spins more than 120 degrees per segment (the client would "
                    + "turn it the wrong way); raise spin-seconds-per-revolution or lower segment-ticks");
        }
        Pedestal pedestal = new Pedestal(c.getBoolean("station.pedestal.enabled", true),
                c.getString("station.pedestal.block", "minecraft:polished_deepslate"),
                r.offset("station.pedestal.offset", new Offset(0, 0, 2.5)),
                r.positive("station.pedestal.width", 0.9),
                r.positive("station.pedestal.height", 0.95));

        Map<String, Integer> colors = new LinkedHashMap<>();
        for (String state : List.of("header", "affordable", "unaffordable", "locked")) {
            colors.put(state, r.argb("station.panel.colors." + state, switch (state) {
                case "header" -> 0xC0301A0C;
                case "affordable" -> 0xB03A2A10;
                case "locked" -> 0x90181818;
                default -> 0xB0202020;
            }));
        }
        Map<String, String> fallbacks = new LinkedHashMap<>();
        ConfigurationSection icons = c.getConfigurationSection("station.panel.icons");
        if (icons != null) {
            for (String id : icons.getKeys(false)) {
                String m = icons.getString(id + ".fallback-material");
                if (m != null && !m.isBlank()) {
                    fallbacks.put(id, m.trim());
                }
            }
        }
        Panel panel = new Panel(
                r.offset("station.panel.offset", new Offset(1.9, 2.55, 2.5)),
                (float) c.getDouble("station.panel.text-yaw-offset", 0),
                (float) c.getDouble("station.panel.icon-yaw-offset", 0),
                (float) r.positive("station.panel.header-scale", 0.6),
                (float) r.positive("station.panel.text-scale", 0.5),
                (int) r.positive("station.panel.line-width", 120),
                Math.max(0, c.getDouble("station.panel.first-row-drop", 0.45)),
                r.positive("station.panel.row-pitch", 0.4),
                r.offset("station.panel.row.text-offset", new Offset(-0.3, 0, 0)),
                r.offset("station.panel.row.icon-offset", new Offset(-0.5, 0.13, -0.02)),
                (float) r.positive("station.panel.row.icon-scale", 0.3),
                r.offset("station.panel.row.hitbox-offset", new Offset(0.25, -0.02, 0)),
                (float) r.positive("station.panel.row.hitbox-width", 1.7),
                (float) r.positive("station.panel.row.hitbox-height", 0.34),
                Map.copyOf(colors),
                Math.clamp(c.getInt("station.panel.icon-brightness.affordable", 15), 0, 15),
                Math.clamp(c.getInt("station.panel.icon-brightness.unaffordable", 5), 0, 15),
                (float) r.positive("station.panel.view-range", 0.5),
                Map.copyOf(fallbacks),
                c.getString("station.panel.default-fallback-material", "COOKIE"),
                Math.clamp(c.getInt("station.panel.text-brightness", 15), 0, 15),
                c.getBoolean("station.panel.text-shadow", true),
                c.getBoolean("station.panel.text-see-through", false),
                c.getBoolean("station.panel.text-default-background", false),
                c.getBoolean("station.panel.row.text-left-edge", true));

        return new StationVisualSettings(
                r.positive("station.view-distance", 24),
                (int) r.positive("station.refresh-ticks", 20),
                r.positive("station.click-distance", 6),
                c.getBoolean("debug.log-click-events", false),
                cookie, pedestal, panel);
    }

    /** Reads with defaults and reports bad values. */
    private record Reader(ConfigurationSection c, Consumer<String> problems) {

        double positive(String path, double fallback) {
            double v = c.getDouble(path, fallback);
            if (!(v > 0) || !Double.isFinite(v)) {
                problems.accept("config.yml: " + path + " must be > 0; using " + fallback);
                return fallback;
            }
            return v;
        }

        Offset offset(String path, Offset fallback) {
            if (!c.contains(path)) {
                return fallback;
            }
            List<?> list = c.getList(path);
            if (list == null || list.size() != 3 || !list.stream().allMatch(o -> o instanceof Number)) {
                problems.accept("config.yml: " + path + " must be [right, up, forward] numbers; using default");
                return fallback;
            }
            return new Offset(((Number) list.get(0)).doubleValue(), ((Number) list.get(1)).doubleValue(),
                    ((Number) list.get(2)).doubleValue());
        }

        int argb(String path, int fallback) {
            String raw = c.getString(path);
            if (raw == null) {
                return fallback;
            }
            String hex = raw.trim().toLowerCase(Locale.ROOT);
            if (hex.matches("#[0-9a-f]{8}")) {
                return (int) Long.parseLong(hex.substring(1), 16);
            }
            if (hex.matches("#[0-9a-f]{6}")) {
                return 0xFF000000 | Integer.parseInt(hex.substring(1), 16);
            }
            problems.accept("config.yml: " + path + " must be #AARRGGBB or #RRGGBB; using default");
            return fallback;
        }
    }
}
