package dev.casperrs.cookieClicker.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.config.StationVisualSettings;
import dev.casperrs.cookieClicker.config.StationVisualSettings.Offset;
import dev.casperrs.cookieClicker.economy.CookiePouch;
import dev.casperrs.cookieClicker.economy.Economy;
import dev.casperrs.cookieClicker.economy.EconomyConfigException;
import dev.casperrs.cookieClicker.economy.EconomyConfigParser;
import dev.casperrs.cookieClicker.economy.PouchSnapshot;
import dev.casperrs.cookieClicker.text.RealMessages;
import dev.casperrs.cookieClicker.station.Station;
import dev.casperrs.cookieClicker.text.Messages;
import dev.casperrs.cookieClicker.text.TextPadding;
import dev.casperrs.cookieClicker.text.TextWidth;
import dev.casperrs.cookieClicker.view.panel.PanelLayout;
import dev.casperrs.cookieClicker.view.panel.BulkMode;
import dev.casperrs.cookieClicker.view.panel.BulkModes;
import dev.casperrs.cookieClicker.view.panel.PanelKind;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.junit.jupiter.api.Test;

/** The price roll of a row: a change inside a text step window waits for the next due step, and still lands exact. */
class PanelUpdaterTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    /** Text sent to the row's text display (the one under test). */
    private final List<String> rowTexts = new ArrayList<>();
    private final StationVisualSettings settings = StationVisualSettings.from(new YamlConfiguration(),
            problem -> {
                throw new AssertionError(problem);
            });
    private final Economy economy = economy();
    private final Messages messages = new Messages(Logger.getLogger("panel-updater-test"));

    private static Economy economy() {
        try {
            return new Economy(EconomyConfigParser.parse(
                    Map.of("cost-growth", 1.15, "buildings", Map.of("baker",
                            Map.of("base-cost", 15, "base-cps", 0.1, "unlock", Map.of("baked", 0)))),
                    Map.of("click", Map.of("base-power", 1, "crit-chance", 0.05, "crit-multiplier", 10),
                            "upgrades", Map.of())).config(), new Random(1));
        } catch (EconomyConfigException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void aValueChangeInsideAStepWindowRendersOnTheNextDueStepAndStillLandsExact() {
        int step = settings.tween().stepTicks();
        int ticks = settings.tween().ticks();
        assertTrue(step > 1 && ticks > 2 * step, "the test needs a step window of more than one tick");
        StationView view = view(new Station("s1", "minecraft:overworld", 0, 64, 0, 0f));
        StationView.Row row = view.buildings.rows.get(0);
        PanelUpdater updater = new PanelUpdater(() -> settings, economy, messages, new BulkModes(),
                (kind, id, withPack) -> null);
        long t0 = 100;

        updater.update(view, pouch(Map.of()), t0, false); // the first text: one baker costs 15
        assertEquals(1, rowTexts.size());

        // the cost moves 15 -> 18 one tick into the step window: no text yet, the row is still animating
        updater.update(view, pouch(Map.of("baker", 1)), t0 + 1, false);
        assertEquals(1, rowTexts.size(), "no text inside the step window");
        assertTrue(row.animating);
        updater.animate(view, t0 + 1);
        assertEquals(1, rowTexts.size());

        // the next due step sends the roll
        updater.animate(view, t0 + step);
        assertEquals(2, rowTexts.size(), "the next due step renders");

        for (long now = t0 + step + 1; now <= t0 + ticks + step; now++) {
            updater.animate(view, now);
        }
        assertFalse(row.animating, "the roll has settled");
        // renderedKey = path|id|owned|count|mode|cost|gain: the cost field is the exact new value
        assertEquals("18", row.renderedKey.split("\\|")[5]);
    }

    /** The shipped messages.yml, loaded like the server does it. */
    private static Messages shippedMessages() {
        Messages m = new Messages(Logger.getLogger("panel-updater-test"));
        m.load(RealMessages.yaml());
        return m;
    }

    /** The shipped config.yml, loaded like the server does it. */
    private static StationVisualSettings shippedSettings() {
        try (InputStream in = PanelUpdaterTest.class.getClassLoader().getResourceAsStream("config.yml");
             Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return StationVisualSettings.from(YamlConfiguration.loadConfiguration(r), problem -> {
                throw new AssertionError(problem);
            });
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** {@code station.panel.line-width} from the shipped config.yml. */
    private static int shippedLineWidth() {
        return shippedSettings().panel().lineWidth();
    }

    /** Half a space at {@code scale}, in blocks: the tolerance for "the same width". */
    private static double halfSpace(float scale) {
        return TextWidth.SPACE_PX / 2.0 * scale / TextWidth.PIXELS_PER_BLOCK;
    }

    /** Asserts a background's left and right edge are the column's, each within half a space at {@code scale}. */
    private static void assertSameColumn(String what, double left, double right, double colLeft, double colRight,
                                         float scale) {
        double tol = halfSpace(scale);
        assertTrue(Math.abs(left - colLeft) <= tol && Math.abs(right - colRight) <= tol,
                what + " spans " + left + ".." + right + ", the column " + colLeft + ".." + colRight);
    }

    @Test
    void theHeaderToggleAndRowsShareOneTextColumnAndTheirTitlesFitIt() {
        StationVisualSettings s = shippedSettings();
        StationVisualSettings.Panel p = s.panel();
        Messages shipped = shippedMessages();
        PanelLayout layout = PanelLayout.of(s, 8);
        double colLeft = layout.textLeft();
        double colRight = colLeft + layout.columnWidth();
        float bulkScale = layout.toggleScale();
        PanelUpdater updater = new PanelUpdater(() -> s, economy, shipped, new BulkModes(),
                (kind, id, withPack) -> null);

        // header and toggle are centred on the anchor, so their background is -w/2 .. w/2
        for (String path : List.of("panel.buildings.header", "panel.clicks.header")) {
            Component header = TextPadding.centred(shipped.component(path), p.textPadding(), layout.headerTargetPx());
            double w = TextWidth.blocks(TextWidth.width(header, TextWidth.SINGLE_LINE_WIDTH), p.headerScale());
            assertSameColumn(path, -w / 2, w / 2, colLeft, colRight, p.headerScale());
        }
        // every toggle line: the plain ×N line and the paged one with each arrow on or off, in each selected mode
        for (BulkMode mode : BulkMode.values()) {
            List<Component> lines = new ArrayList<>(List.of(updater.text().toggleText(mode)));
            for (boolean up : new boolean[] {false, true}) {
                for (boolean down : new boolean[] {false, true}) {
                    lines.add(updater.text().pagedToggle(updater.text().toggleText(mode), up, down));
                }
            }
            for (Component line : lines) {
                Component toggle = TextPadding.centred(line, p.textPadding(), layout.toggleTargetPx());
                double w = TextWidth.blocks(TextWidth.width(toggle, TextWidth.SINGLE_LINE_WIDTH), bulkScale);
                assertSameColumn("toggle " + mode + " " + PLAIN.serialize(line), -w / 2, w / 2, colLeft, colRight,
                        bulkScale);
                // padded in whole spaces to the column's pixel width: at most half a space wider than the target, in px
                int px = TextWidth.width(toggle, TextWidth.SINGLE_LINE_WIDTH);
                assertTrue(px <= layout.toggleTargetPx() + TextWidth.SPACE_PX / 2,
                        "toggle " + PLAIN.serialize(line) + " is " + px + " px, the column " + layout.toggleTargetPx());
            }
        }
        // building rows: left edge at the column, and the background as wide as the column (half a space each way)
        int lineWidth = p.lineWidth();
        for (String id : shippedBuildingIds()) {
            for (String path : List.of("panel.row.affordable", "panel.row.unaffordable")) {
                for (Component row : List.of(
                        PanelText.buildingRow(shipped, path, p.textPadding(), id, 1, shipped.number(15),
                                shipped.number(0.1), 0, Component.empty()),
                        PanelText.buildingRow(shipped, path, p.textPadding(), id, 9999, shipped.number(1.234e15),
                                shipped.number(9.99e9), 1e15, PanelText.amount(shipped, BulkMode.MAX, 10000)))) {
                    Component padded = TextPadding.right(row, p.textPadding(), layout.rowWidthPx(), lineWidth);
                    double w = TextWidth.blocks(TextWidth.width(padded, lineWidth), p.textScale());
                    assertSameColumn(id + " " + path, colLeft, colLeft + w, colLeft, colRight, p.textScale());
                }
            }
        }
    }

    /** Every building id in the shipped buildings.yml (all 11 names must fit, not just the longest-looking ones). */
    private static java.util.Set<String> shippedBuildingIds() {
        try (InputStream in = PanelUpdaterTest.class.getClassLoader().getResourceAsStream("buildings.yml");
             Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(r).getConfigurationSection("buildings").getKeys(false);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void theLongestBuildingRowsStayTwoLinesWithinTheShippedLineWidth() {
        Messages shipped = shippedMessages();
        int lineWidth = shippedLineWidth();
        // longest shipped names with a ×10000 amount and a huge cost, in both affordability styles
        for (String id : shippedBuildingIds()) {
            for (String path : List.of("panel.row.affordable", "panel.row.unaffordable")) {
                for (double cost : new double[] {1.234e15, 9.99e33}) {
                    Component row = PanelText.buildingRow(shipped, path, 1, id, 9999,
                            shipped.number(cost), shipped.number(9.99e9), 1e15,
                            PanelText.amount(shipped, BulkMode.MAX, 10000));
                    String text = PLAIN.serialize(row);
                    List<Integer> lines = TextWidth.lineWidths(TextWidth.runs(row), lineWidth);
                    assertEquals(TextWidth.ROW_LINES, lines.size(), "wrapped: " + text);
                    assertTrue(lines.stream().allMatch(w -> w <= lineWidth), "too wide " + lines + ": " + text);
                }
            }
        }
    }

    private StationView view(Station station) {
        StationFrame frame = StationFrame.of(station);
        Offset none = new Offset(0, 0, 0);
        List<String> ignored = new ArrayList<>();
        StationView.Row row = new StationView.Row(0, entity(ItemDisplay.class, ignored),
                entity(TextDisplay.class, rowTexts), new double[3], new double[3]);
        StationView.Panel buildings = new StationView.Panel(PanelKind.BUILDINGS, frame, none, 0f,
                entity(TextDisplay.class, ignored), entity(TextDisplay.class, ignored),
                entity(Interaction.class, ignored), List.of(row));
        StationView.Panel clicks = new StationView.Panel(PanelKind.CLICKS, frame, none, 0f,
                entity(TextDisplay.class, ignored), entity(TextDisplay.class, ignored),
                entity(Interaction.class, ignored), List.of());
        return new StationView(UUID.randomUUID(), station, entity(ItemDisplay.class, ignored),
                entity(Interaction.class, ignored), new double[3], null, buildings, clicks, false);
    }

    private static CookiePouch pouch(Map<String, Integer> owned) {
        return CookiePouch.fromSnapshot(new PouchSnapshot(1000, 1000, owned, Map.of()));
    }

    /** A Bukkit display stand-in: valid, with an id, counting the text it is sent; everything else is a no-op. */
    private static <T> T entity(Class<T> type, List<String> texts) {
        UUID id = UUID.randomUUID();
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "isValid" -> true;
            case "getUniqueId" -> id;
            case "text" -> {
                texts.add(PLAIN.serialize((Component) args[0]));
                yield null;
            }
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> defaultValue(method.getReturnType());
        };
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == double.class) {
            return 0d;
        }
        return null;
    }
}
