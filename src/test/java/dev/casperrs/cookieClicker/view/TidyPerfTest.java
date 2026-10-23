package dev.casperrs.cookieClicker.view;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.economy.CookiePouch;
import dev.casperrs.cookieClicker.economy.Economy;
import dev.casperrs.cookieClicker.economy.EconomyConfig;
import dev.casperrs.cookieClicker.economy.EconomyConfigParser;
import dev.casperrs.cookieClicker.economy.PouchSnapshot;
import dev.casperrs.cookieClicker.text.RealMessages;
import dev.casperrs.cookieClicker.hud.Hud;
import dev.casperrs.cookieClicker.text.Messages;
import dev.casperrs.cookieClicker.text.TextPadding;
import dev.casperrs.cookieClicker.text.TextWidth;
import dev.casperrs.cookieClicker.view.panel.PanelRows;
import dev.casperrs.cookieClicker.view.panel.BulkMode;
import dev.casperrs.cookieClicker.view.panel.RowState;
import dev.casperrs.cookieClicker.view.panel.UpgradeRowState;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Timing test (no JMH) for the hot paths of the tidy review: prints median ns per operation and writes them to
 * {@code target/tidy-perf.txt}. Run with {@code mise run test}; numbers go into docs/reviews/2026-10-23-tidy-perf.md.
 * Only sanity is asserted, never a time, so a slow machine cannot fail the build.
 */
class TidyPerfTest {

    private static final int WARMUP = 3000;
    private static final int ROUNDS = 7;
    private static final int OPS_PER_ROUND = 4000;
    private static final List<String> REPORT = new ArrayList<>();
    private static volatile double sink;

    private static Map<String, Object> yaml(String name) throws IOException {
        try (InputStream in = TidyPerfTest.class.getClassLoader().getResourceAsStream(name)) {
            return new Yaml().load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static Economy economy() throws Exception {
        EconomyConfig config = EconomyConfigParser.parse(yaml("buildings.yml"), yaml("upgrades.yml")).config();
        return new Economy(config, RandomGenerator.getDefault());
    }

    /** A mid-game player: the first 8 buildings owned, a few upgrades bought, cookies in hand. */
    private static CookiePouch midGame(Economy e) {
        Map<String, Integer> owned = new HashMap<>();
        int n = 0;
        for (String id : e.config().buildings().keySet()) {
            owned.put(id, n++ < 8 ? 40 : 0);
        }
        Map<String, Integer> levels = new HashMap<>();
        e.config().upgrades().values().stream().limit(6).forEach(u -> levels.put(u.id(), 1));
        return CookiePouch.fromSnapshot(new PouchSnapshot(5e9, 5e11, owned, levels));
    }

    private static Messages shippedMessages() {
        Messages m = new Messages(Logger.getLogger("tidy-perf"));
        m.load(RealMessages.yaml());
        return m;
    }

    /** Median ns per call of {@code op}, after a warm-up; the result is kept so the JIT cannot drop the work. */
    private static double median(String label, Runnable op) {
        for (int i = 0; i < WARMUP; i++) {
            op.run();
        }
        double[] rounds = new double[ROUNDS];
        for (int r = 0; r < ROUNDS; r++) {
            long start = System.nanoTime();
            for (int i = 0; i < OPS_PER_ROUND; i++) {
                op.run();
            }
            rounds[r] = (System.nanoTime() - start) / (double) OPS_PER_ROUND;
        }
        Arrays.sort(rounds);
        double ns = rounds[ROUNDS / 2];
        String line = String.format("%-62s %10.0f ns/op  (%.1f us)", label, ns, ns / 1000);
        REPORT.add(line);
        System.out.println("[tidy-perf] " + line);
        return ns;
    }

    @Test
    void panelRowStatesPerRefresh() throws Exception {
        Economy e = economy();
        CookiePouch pouch = midGame(e);
        List<String> buildings = PanelRows.buildings(e.config(), 8);
        List<String> clicks = PanelRows.clicks(e.config(), pouch.upgradeLevelsView(), 4);
        for (BulkMode mode : new BulkMode[] {BulkMode.ONE, BulkMode.MAX}) {
            double ns = median("rows of both panels (8 buildings + 4 upgrades), mode " + mode, () -> {
                double sum = 0;
                for (String id : buildings) {
                    sum += RowState.of(e, pouch, id, mode).cost();
                }
                for (String id : clicks) {
                    var s = UpgradeRowState.of(e, pouch, id, mode);
                    sum += s == null ? 0 : s.cost();
                }
                sink = sum;
            });
            assertTrue(ns > 0);
        }
        // the same work happens once per second per viewing player in StationViews.refresh AND in BakeryTicker
        median("economy.totalCps (1 Hz produce, HUD, every row's gain)", () -> sink = e.totalCps(pouch));
    }

    @Test
    void panelRowTextPerRender() throws Exception {
        Messages m = shippedMessages();
        YamlConfiguration cfg;
        try (InputStream in = TidyPerfTest.class.getClassLoader().getResourceAsStream("config.yml");
             Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            cfg = YamlConfiguration.loadConfiguration(r);
        }
        int lineWidth = cfg.getInt("station.panel.line-width", 150);
        @SuppressWarnings("unchecked")
        List<String> buildingIds = new ArrayList<>(((Map<String, Object>) yaml("buildings.yml").get("buildings")).keySet());
        List<String> eight = buildingIds.subList(0, Math.min(8, buildingIds.size()));
        PanelText text = new PanelText(m);
        double ns = median("8 building row texts (MiniMessage + padding + width), one roll step", () -> {
            int w = 0;
            for (String id : eight) {
                Component row = text.buildingRow("panel.row.affordable", 1, id, "40", m.number(123456.7),
                        m.number(98.5), "0", BulkMode.ONE, 0);
                Component padded = TextPadding.right(row, 1, 150, lineWidth);
                w += TextWidth.lineWidths(TextWidth.runs(padded), lineWidth).size();
            }
            sink = w;
        });
        median("  of which buildingRow only (template parse + placeholders)", () -> {
            int w = 0;
            for (String id : eight) {
                w += text.buildingRow("panel.row.affordable", 1, id, "40", m.number(123456.7),
                        m.number(98.5), "0", BulkMode.ONE, 0).children().size();
            }
            sink = w;
        });
        median("  reference: real MiniMessage parse of the same rows (PanelText static)", () -> {
            int w = 0;
            for (String id : eight) {
                w += PanelText.buildingRow(m, "panel.row.affordable", 1, id, 40, m.number(123456.7),
                        m.number(98.5), 0, Component.empty()).children().size();
            }
            sink = w;
        });
        Component sample = TextPadding.right(text.buildingRow("panel.row.affordable", 1, eight.get(0), "40",
                m.number(123456.7), m.number(98.5), "0", BulkMode.ONE, 0), 1, 150, lineWidth);
        median("  of which TextWidth.lineWidths(runs) of one row", () ->
                sink = TextWidth.lineWidths(TextWidth.runs(sample), lineWidth).size());
        assertTrue(ns > 0);
    }

    @Test
    void clickPathParts() throws Exception {
        Economy e = economy();
        CookiePouch pouch = midGame(e);
        Messages m = shippedMessages();
        Hud hud = new Hud(m, e, p -> true);
        median("economy.click (clickPower + crit roll)", () -> sink = e.click(pouch).amount());
        median("Hud.render (2 number formats + template swap)", () -> sink = hud.render(pouch, true).children().size());
        var mm = Messages.miniMessage();
        var template = dev.casperrs.cookieClicker.text.MarkerTemplate.of(mm, "<#f5c542>+<amount>", "amount");
        median("floating text cookie_click via MarkerTemplate.render (Effects.floatingText)", () ->
                sink = template.render(m.number(1234.5)).children().size());
        median("floating text '<#f5c542>+<amount>' MiniMessage parse (reference, before)", () ->
                sink = mm.deserialize("<#f5c542>+<amount>", Placeholder.unparsed("amount", m.number(1234.5)))
                        .children().size());
        assertTrue(Files.isRegularFile(Path.of("pom.xml")));
    }

    @org.junit.jupiter.api.AfterAll
    static void writeReport() throws IOException {
        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target", "tidy-perf.txt"), REPORT);
    }
}
