package dev.casperrs.cookieClicker.config;

import dev.casperrs.cookieClicker.text.TextWidth;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Map;
import java.util.function.Consumer;

/**
 * The per-player station visuals: cookie, pedestal, panels, shelf and basket. Only the distances and the debug
 * flag come from {@code config.yml}; the rest are looks fixed in code. Offsets are in the station's own frame:
 * {@code right}/{@code up}/{@code forward} as seen by the admin who placed it (forward = the way they faced).
 */
public record StationVisualSettings(double viewDistance, int refreshTicks, double clickDistance, boolean debugClicks,
                                    Cookie cookie, Pedestal pedestal, Panel panel, PanelSpot buildings,
                                    PanelSpot clicks, Bulk bulk, Tween tween, Shelf shelf,
                                    Map<String, String> upgradeFallbacks, BasketSettings basket) {

    /**
     * Where one hologram panel stands: {@code offset} is its header's bottom centre from the pedestal (with a plot
     * layout only its "up" part counts: the panel stands on the layout spot).
     */
    public record PanelSpot(Offset offset, int maxRows) {
    }

    /** The ×1/×5/×10/Max toggle row under each panel header; where it stands follows from {@link Panel#headerGap()}. */
    public record Bulk(float scale) {
    }

    /**
     * Price roll on the panels: values roll over {@code ticks}, the row text is re-sent at most every
     * {@code stepTicks}, and briefly grows to {@code pulseScale} × its size.
     */
    public record Tween(int ticks, int stepTicks, float pulseScale) {
    }

    /**
     * The upgrade shelf under the click upgrades panel; each icon costs 2 entities per player. offset is the first
     * (top left) icon's centre relative to the click panel's header anchor; flyTicks 1-59.
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

    /** Vanilla item shown for an upgrade icon to players without the pack. */
    public String upgradeFallbackFor(String upgradeId) {
        return upgradeFallbacks.getOrDefault(upgradeId, panel.defaultFallback());
    }

    /** A position in the station frame, in blocks. */
    public record Offset(double right, double up, double forward) {
        public static final Offset ZERO = new Offset(0, 0, 0);
    }

    /** spinSecondsPerRevolution 0 = no spin; brightness 0-15; clickEffectOffset pulls the effect toward the player. */
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
     * The looks and layout shared by both hologram panels. Header, ×N toggle and rows are centred on one column under
     * the panel anchor; the vertical gaps are air between the rendered text blocks. Gaps and clickMargin in blocks, yaw
     * offsets in degrees (calibration), colours ARGB, brightness 0-15; offset is the header's bottom centre, text and
     * icon offsets are relative to the row bottom (icon + text centred as one); textLeftEdge makes rows start at
     * textOffset.
     */
    public record Panel(Offset offset, float textYawOffset, float iconYawOffset, float headerScale, float textScale,
                        int lineWidth, Offset textOffset, Offset iconOffset,
                        float iconScale,
                        Map<String, Integer> colors, int iconBrightAffordable, int iconBrightUnaffordable,
                        float viewRange, Map<String, String> iconFallbacks, String defaultFallback,
                        int textBrightness, boolean textShadowed, boolean textSeeThrough,
                        boolean textDefaultBackground, boolean textLeftEdge, int textPadding, boolean sameWidth,
                        double headerGap, double toggleGap, double rowGap, double clickMargin) {

        /** Distance between two row bottoms: {@link TextWidth#ROW_LINES} lines at {@code textScale}, plus the gap. */
        public double rowPitch() {
            return TextWidth.blocks(TextWidth.ROW_LINES * TextWidth.LINE_HEIGHT_PX, textScale) + rowGap;
        }

        public int color(String state) {
            return colors.getOrDefault(state, 0x40000000);
        }

        public String fallbackFor(String buildingId) {
            return iconFallbacks.getOrDefault(buildingId, defaultFallback);
        }
    }

    private static final Cookie COOKIE = new Cookie(new Offset(0, 1.8, 2.5), 1.4f, 0.1, 80, 6, 10, 0.1, 0.5f, 15, 0.1);
    private static final Pedestal PEDESTAL =
            new Pedestal(true, "minecraft:polished_deepslate", new Offset(0, 0, 2.5), 0.9, 0.95);
    /** Background colours {@code #AARRGGBB} per row state; alpha below 0x1A is invisible. */
    private static final Map<String, Integer> COLORS = Map.of(
            "header", 0xC0301A0C,
            "affordable", 0xB03A2A10,
            "unaffordable", 0xB0202020,
            "locked", 0x90181818,
            "maxed", 0x90181818,
            "toggle", 0xA0201810);
    /*
     * Text yaw offset stays 0: click picking assumes the text faces the panel's yaw. Line width 150 fits the longest
     * building names with x1000 and big costs on two lines.
     */
    private static final Panel PANEL = new Panel(new Offset(2.15, 2.55, 2.5), 0, 0, 0.72f, 0.5f, 150,
            new Offset(-0.3, 0, 0), new Offset(-0.5, 0.13, -0.02), 0.3f, COLORS, 15, 5, 0.5f,
            VisualFallbacks.BUILDING_ICONS, VisualFallbacks.DEFAULT_ICON, 15, true, false, false, true, 1, true,
            0.22, 0.28, 0.17, 0.03);
    private static final PanelSpot BUILDINGS = new PanelSpot(new Offset(2.15, 2.55, 2.5), 8);
    private static final PanelSpot CLICKS = new PanelSpot(new Offset(-2.15, 3.2, 2.5), 4);
    private static final Bulk BULK = new Bulk(0.5f);
    private static final Tween TWEEN = new Tween(8, 2, 1.08f);
    private static final Shelf SHELF = new Shelf(new Offset(-0.55, -2.45, 0), 6, 0.22, 0.18f, 24, 0.2f, 14, 3);
    private static final int REFRESH_TICKS = 20;

    public static StationVisualSettings from(ConfigurationSection c, Consumer<String> problems) {
        return new StationVisualSettings(
                positive(c, "station.view-distance", 24, problems),
                REFRESH_TICKS,
                positive(c, "station.click-distance", 6, problems),
                c.getBoolean("debug.log-click-events", false),
                COOKIE, PEDESTAL, PANEL, BUILDINGS, CLICKS, BULK, TWEEN, SHELF, VisualFallbacks.UPGRADE_ICONS,
                BasketSettings.defaults());
    }

    private static double positive(ConfigurationSection c, String path, double fallback, Consumer<String> problems) {
        double v = c.getDouble(path, fallback);
        if (!(v > 0) || !Double.isFinite(v)) {
            problems.accept("config.yml: " + path + " must be > 0; using " + fallback);
            return fallback;
        }
        return v;
    }
}
