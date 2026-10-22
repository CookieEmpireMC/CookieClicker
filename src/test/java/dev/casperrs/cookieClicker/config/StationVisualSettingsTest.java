package dev.casperrs.cookieClicker.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.config.StationVisualSettings.Offset;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class StationVisualSettingsTest {

    private static YamlConfiguration yaml(String text) {
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(text);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return y;
    }

    private static StationVisualSettings parse(String text, List<String> problems) {
        return StationVisualSettings.from(yaml(text), problems::add);
    }

    @Test
    void emptyConfigGivesDefaultsWithoutProblems() {
        List<String> problems = new ArrayList<>();
        StationVisualSettings s = parse("", problems);
        assertEquals(List.of(), problems);
        assertEquals(24, s.viewDistance());
        assertEquals(20, s.refreshTicks());
        assertEquals(10, s.cookie().segmentTicks());
        assertEquals(new Offset(0, 1.8, 2.5), s.cookie().offset());
        assertEquals("COOKIE", s.panel().fallbackFor("anything"));
        assertFalse(s.debugClicks());
    }

    @Test
    void shippedConfigYmlParsesCleanlyAndMatchesTheCodeDefaults() {
        YamlConfiguration shipped = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
        List<String> problems = new ArrayList<>();
        StationVisualSettings fromFile = StationVisualSettings.from(shipped, problems::add);
        assertEquals(List.of(), problems);
        StationVisualSettings defaults = parse("", new ArrayList<>());
        assertEquals(defaults.cookie(), fromFile.cookie(), "config.yml and the in-code defaults drifted apart");
        assertEquals(defaults.pedestal(), fromFile.pedestal());
        assertEquals(defaults.viewDistance(), fromFile.viewDistance());
        for (String state : List.of("header", "affordable", "unaffordable")) {
            assertEquals(defaults.panel().color(state), fromFile.panel().color(state), state);
        }
        // known drift (LOW, reported): the in-code default for "locked" equals "unaffordable"; the file has #90181818
        assertEquals(0x90181818, fromFile.panel().color("locked"));
        assertEquals("BREAD", fromFile.panel().fallbackFor("villager_baker"));
        assertEquals("WHEAT", fromFile.panel().fallbackFor("wheat_farm"));
    }

    @Test
    void badOffsetFallsBackAndReports() {
        List<String> problems = new ArrayList<>();
        StationVisualSettings s = parse("station:\n  cookie:\n    offset: [1, 2]\n  pedestal:\n    offset: [a, b, c]\n"
                + "  panel:\n    offset: 5\n", problems);
        assertEquals(3, problems.size(), problems.toString());
        assertEquals(new Offset(0, 1.8, 2.5), s.cookie().offset());
        assertEquals(new Offset(0, 0, 2.5), s.pedestal().offset());
        assertEquals(new Offset(1.9, 2.55, 2.5), s.panel().offset());
    }

    @Test
    void anOldPanelOffsetStillPlacesTheBuildingsPanelAndTheClicksPanelHasItsOwnDefault() {
        List<String> problems = new ArrayList<>();
        StationVisualSettings s = parse("station:\n  panel:\n    offset: [1, 2.5, -3]\n", problems);
        assertEquals(new Offset(1, 2.5, -3), s.buildings().offset());
        assertEquals(new Offset(-1.9, 3.2, 2.5), s.clicks().offset());
        assertTrue(problems.isEmpty(), problems.toString());
    }

    @Test
    void goodOffsetIsRead() {
        StationVisualSettings s = parse("station:\n  cookie:\n    offset: [1, 2.5, -3]\n", new ArrayList<>());
        assertEquals(new Offset(1, 2.5, -3), s.cookie().offset());
    }

    @Test
    void nonPositiveAndNonFiniteNumbersFallBack() {
        List<String> problems = new ArrayList<>();
        StationVisualSettings s = parse("station:\n  view-distance: -5\n  refresh-ticks: 0\n  click-distance: .nan\n"
                + "  cookie:\n    scale: 0\n    bob-period-ticks: -1\n", problems);
        assertEquals(5, problems.size(), problems.toString());
        assertEquals(24, s.viewDistance());
        assertEquals(20, s.refreshTicks());
        assertEquals(6, s.clickDistance());
        assertEquals(1.4f, s.cookie().scale());
        assertEquals(80, s.cookie().bobPeriodTicks());
    }

    @Test
    void segmentTicksAreClampedAndFastSpinIsReported() {
        assertEquals(2, parse("station:\n  cookie:\n    segment-ticks: 1\n", new ArrayList<>()).cookie().segmentTicks());
        assertEquals(40, parse("station:\n  cookie:\n    segment-ticks: 500\n", new ArrayList<>()).cookie().segmentTicks());
        List<String> problems = new ArrayList<>();
        parse("station:\n  cookie:\n    spin-seconds-per-revolution: 1\n    segment-ticks: 20\n", problems);
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("120"));
        List<String> none = new ArrayList<>();
        parse("station:\n  cookie:\n    spin-seconds-per-revolution: 0\n", none);
        assertEquals(List.of(), none, "no spin is fine");
    }

    @Test
    void colorsAcceptArgbAndRgbAndRejectGarbage() {
        List<String> problems = new ArrayList<>();
        StationVisualSettings s = parse("station:\n  panel:\n    colors:\n      header: \"#80FF0000\"\n"
                + "      affordable: \"#00ff00\"\n      unaffordable: \"red\"\n", problems);
        assertEquals(1, problems.size());
        assertEquals(0x80FF0000, s.panel().color("header"));
        assertEquals(0xFF00FF00, s.panel().color("affordable"));
        assertEquals(0xB0202020, s.panel().color("unaffordable"));
        assertEquals(0x40000000, s.panel().color("no-such-state"));
    }

    @Test
    void iconBrightnessIsClampedAndFallbacksAreRead() {
        StationVisualSettings s = parse("station:\n  panel:\n    icon-brightness:\n      affordable: 99\n"
                + "      unaffordable: -4\n    icons:\n      a:\n        fallback-material: STONE\n"
                + "      b:\n        fallback-material: \"  \"\n    default-fallback-material: DIRT\n", new ArrayList<>());
        assertEquals(15, s.panel().iconBrightAffordable());
        assertEquals(0, s.panel().iconBrightUnaffordable());
        assertEquals("STONE", s.panel().fallbackFor("a"));
        assertEquals("DIRT", s.panel().fallbackFor("b"));
    }

    @Test
    void spinStepRadians() {
        StationVisualSettings.Cookie c = parse("", new ArrayList<>()).cookie();
        assertEquals(Math.toRadians(30), c.spinStepRadians(), 1e-9);
        assertEquals(0, parse("station:\n  cookie:\n    spin-seconds-per-revolution: 0\n", new ArrayList<>())
                .cookie().spinStepRadians());
    }

    @Test
    void textLeftEdgeDefaultsToTrueAndParsesFalse() {
        assertTrue(parse("", new ArrayList<>()).panel().textLeftEdge());
        assertFalse(parse("station:\n  panel:\n    row:\n      text-left-edge: false\n", new ArrayList<>())
                .panel().textLeftEdge());
        assertTrue(parse("station:\n  panel:\n    row:\n      text-left-edge: true\n", new ArrayList<>())
                .panel().textLeftEdge());
    }

    @Test
    void panelRowDefaultsMatchShippedFile() {
        YamlConfiguration shipped = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
        StationVisualSettings.Panel file = StationVisualSettings.from(shipped, m -> { }).panel();
        StationVisualSettings.Panel code = parse("", new ArrayList<>()).panel();
        assertEquals(code.textOffset(), file.textOffset());
        assertEquals(code.textLeftEdge(), file.textLeftEdge());
        assertEquals(code.textPadding(), file.textPadding());
        assertEquals(code.sameWidth(), file.sameWidth());
        assertEquals(code.headerGap(), file.headerGap());
        assertEquals(code.toggleGap(), file.toggleGap());
        assertEquals(code.rowGap(), file.rowGap());
        assertEquals(code.clickMargin(), file.clickMargin());
        assertEquals(new Offset(-0.3, 0, 0), code.textOffset());
        assertEquals(0.03, code.clickMargin(), 1e-9);
    }

    @Test
    void basketInTheShippedConfigMatchesTheCodeDefaultsAndTheDesignHeights() {
        YamlConfiguration shipped = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
        List<String> problems = new ArrayList<>();
        BasketSettings fromFile = StationVisualSettings.from(shipped, problems::add).basket();
        assertEquals(List.of(), problems);
        assertEquals(BasketSettings.defaults(), fromFile, "config.yml and the in-code basket defaults drifted apart");
        // design/basket.md top heights; golden fill 0-1 is raised to 0.69 so the cookie clears its corner posts
        assertEquals(List.of(0.281, 0.313, 0.406, 0.5, 0.594), fromFile.heights().get("wicker"));
        assertEquals(List.of(0.484, 0.5, 0.594, 0.688, 0.781), fromFile.heights().get("bushel"));
        assertEquals(List.of(0.69, 0.69, 0.781, 0.969, 1.188), fromFile.heights().get("golden"));
    }

    @Test
    void basketTierStartsThatDoNotIncreaseFallBackWithOneProblem() {
        List<String> problems = new ArrayList<>();
        BasketSettings b = parse("station:\n  basket:\n    tier-starts: [1, 5, 3, 10]\n", problems).basket();
        assertEquals(1, problems.size());
        assertEquals(BasketSettings.defaults().tierStarts(), b.tierStarts());
    }
}
