package dev.casperrs.cookieClicker.config;

import dev.casperrs.cookieClicker.view.PanelLayout;
import dev.casperrs.cookieClicker.view.TextWidth;
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
                                    Cookie cookie, Pedestal pedestal, Panel panel, PanelSpot buildings,
                                    PanelSpot clicks, Bulk bulk, Tween tween, Shelf shelf,
                                    Map<String, String> upgradeFallbacks, BasketSettings basket) {

    /**
     * Where one hologram panel ({@code station.panels.<panel>}) stands: {@code offset} is its header's bottom centre
     * from the pedestal (with a plot layout only its "up" part counts: the panel stands on the layout spot), and it
     * shows at most {@code maxRows} rows.
     */
    public record PanelSpot(Offset offset, int maxRows) {
    }

    /**
     * The ×1/×5/×10/Max toggle row under each panel header ({@code station.panel.bulk.*}): its text size. Where it
     * stands follows from {@link Panel#headerGap()} (centred on the panel column).
     */
    public record Bulk(float scale) {
    }

    /**
     * Price roll on the panels ({@code station.panel.tween.*}): values roll over {@code ticks}, the row text is
     * re-sent at most every {@code stepTicks}, and the row text briefly grows to {@code pulseScale} × its size.
     */
    public record Tween(int ticks, int stepTicks, float pulseScale) {
    }

    /**
     * The upgrade shelf under the click upgrades panel ({@code station.shelf.*}).
     *
     * @param offset         centre of the first (top left) icon, relative to the click panel's header anchor
     * @param spacing        distance between icon centres, right and down
     * @param hitboxSize     width and height of each icon's click box
     * @param flyTicks       how long a bought upgrade's icon glides from its row to its slot (1-59)
     * @param tooltipSeconds how long the action bar shows an icon's name and effect before the HUD returns
     */
    public record Shelf(Offset offset, int columns, double spacing, float scale, int maxIcons, float hitboxSize,
                        int flyTicks, int tooltipSeconds) {

        /** Centre of icon {@code index} (row by row, left to right), relative to the click panel's header anchor. */
        public Offset slot(int index) {
            int row = index / columns;
            int col = index % columns;
            return new Offset(offset.right() + col * spacing, offset.up() - row * spacing, offset.forward());
        }
    }

    /** Vanilla item shown for an upgrade icon to players without the pack ({@code station.upgrade-fallback-items}). */
    public String upgradeFallbackFor(String upgradeId) {
        return upgradeFallbacks.getOrDefault(upgradeId, panel.defaultFallback());
    }

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
     * The looks and layout shared by both hologram panels ({@code station.panel.*}). Header, ×N toggle and rows are
     * centred on one column under the panel anchor; the vertical gaps are air between the rendered text blocks.
     *
     * @param offset        where the header text sits (its bottom centre)
     * @param textYawOffset degrees added to the text yaw (calibration if text shows mirrored)
     * @param iconYawOffset degrees added to the icon yaw (calibration for the FIXED item transform)
     * @param textOffset    row text left edge, relative to the row's bottom; with {@link #iconOffset} it only sets
     *                      how icon and text sit next to each other: the row (icon + text) is centred on the column
     * @param iconOffset    row icon centre relative to the row's bottom (see textOffset)
     * @param colors        background colours as {@code #AARRGGBB}: header, affordable, unaffordable, locked
     * @param iconFallbacks building id -> vanilla material name shown to players without the pack
     * @param textBrightness block and sky light of the header and row texts (0-15)
     * @param textLeftEdge  shift each row text right by half its estimated rendered width so every row starts at
     *                      {@code textOffset} (a text display is otherwise centred on its position)
     * @param textPadding   spaces added inside every text background, left and right
     * @param sameWidth     pad the header, toggle and every row to the full column width
     * @param headerGap     air between the header and the toggle (blocks)
     * @param toggleGap     air between the toggle and the first row
     * @param rowGap        air between two rows
     * @param clickMargin   a click this close (blocks) outside a row's or the toggle's rendered text still counts
     */
    public record Panel(Offset offset, float textYawOffset, float iconYawOffset, float headerScale, float textScale,
                        int lineWidth, Offset textOffset, Offset iconOffset,
                        float iconScale,
                        Map<String, Integer> colors, int iconBrightAffordable, int iconBrightUnaffordable,
                        float viewRange, Map<String, String> iconFallbacks, String defaultFallback,
                        int textBrightness, boolean textShadowed, boolean textSeeThrough,
                        boolean textDefaultBackground, boolean textLeftEdge, int textPadding, boolean sameWidth,
                        double headerGap, double toggleGap, double rowGap, double clickMargin) {

        /**
         * Distance between two row bottoms: a row of {@link PanelLayout#ROW_LINES} lines
         * ({@link TextWidth#LINE_HEIGHT_PX} each) at {@code textScale}, plus {@link #rowGap}.
         */
        public double rowPitch() {
            return TextWidth.blocks(PanelLayout.ROW_LINES * TextWidth.LINE_HEIGHT_PX, textScale) + rowGap;
        }

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
        for (String state : List.of("header", "affordable", "unaffordable", "locked", "maxed", "toggle")) {
            colors.put(state, r.argb("station.panel.colors." + state, switch (state) {
                case "header" -> 0xC0301A0C;
                case "affordable" -> 0xB03A2A10;
                case "locked", "maxed" -> 0x90181818;
                case "toggle" -> 0xA0201810;
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
                (int) r.positive("station.panel.line-width", 150),
                r.offset("station.panel.row.text-offset", new Offset(-0.3, 0, 0)),
                r.offset("station.panel.row.icon-offset", new Offset(-0.5, 0.13, -0.02)),
                (float) r.positive("station.panel.row.icon-scale", 0.3),
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
                c.getBoolean("station.panel.row.text-left-edge", true),
                (int) Math.clamp(c.getInt("station.panel.text-padding", 1), 0, 8),
                c.getBoolean("station.panel.same-width", true),
                Math.max(0, c.getDouble("station.panel.gap-after-header", 0.2)),
                Math.max(0, c.getDouble("station.panel.gap-after-toggle", 0.25)),
                Math.max(0, c.getDouble("station.panel.row-gap", 0.17)),
                Math.clamp(c.getDouble("station.panel.click-margin", 0.03), 0, 0.5));

        PanelSpot buildings = new PanelSpot(r.offset("station.panels.buildings.offset", panel.offset()),
                r.rows("station.panels.buildings.max-rows", 8));
        PanelSpot clicks = new PanelSpot(r.offset("station.panels.clicks.offset", new Offset(-1.9, 3.2, 2.5)),
                r.rows("station.panels.clicks.max-rows", 4));
        Bulk bulk = new Bulk((float) r.positive("station.panel.bulk.scale", 0.45));
        Tween tween = new Tween(
                (int) Math.clamp(r.positive("station.panel.tween.ticks", 8), 1, 100),
                (int) Math.clamp(r.positive("station.panel.tween.step-ticks", 2), 1, 20),
                (float) Math.clamp(r.positive("station.panel.tween.pulse-scale", 1.08), 1, 2));
        Shelf shelf = new Shelf(
                r.offset("station.shelf.offset", new Offset(-0.55, -2.45, 0)),
                (int) Math.clamp(r.positive("station.shelf.columns", 6), 1, 32),
                r.positive("station.shelf.spacing", 0.22),
                (float) r.positive("station.shelf.scale", 0.18),
                (int) Math.clamp(c.getInt("station.shelf.max-icons", 24), 0, 64),
                (float) r.positive("station.shelf.hitbox-size", 0.2),
                (int) Math.clamp(r.positive("station.shelf.fly-ticks", 14), 1, 59),
                (int) Math.clamp(r.positive("station.shelf.tooltip-seconds", 3), 1, 30));
        Map<String, String> upgradeFallbacks = new LinkedHashMap<>();
        ConfigurationSection items = c.getConfigurationSection("station.upgrade-fallback-items");
        if (items != null) {
            for (String id : items.getKeys(false)) {
                String m = items.getString(id);
                if (m != null && !m.isBlank()) {
                    upgradeFallbacks.put(id, m.trim());
                }
            }
        }

        return new StationVisualSettings(
                r.positive("station.view-distance", 24),
                (int) r.positive("station.refresh-ticks", 20),
                r.positive("station.click-distance", 6),
                c.getBoolean("debug.log-click-events", false),
                cookie, pedestal, panel, buildings, clicks, bulk, tween, shelf, Map.copyOf(upgradeFallbacks),
                BasketSettings.from(c, problems));
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

        int rows(String path, int fallback) {
            return (int) Math.clamp(positive(path, fallback), 1, 20);
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
