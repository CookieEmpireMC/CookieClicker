package dev.casperrs.cookieClicker.plot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PlotCodecTest {

    private static Map<String, Object> spec(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("world", "minecraft:overworld");
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static Map<String, Object> root(String id, Map<String, Object> spec) {
        return Map.of("plots", new LinkedHashMap<>(Map.of(id, spec)));
    }

    private static List<Plot> parse(Map<String, Object> root, List<String> problems) {
        return PlotCodec.fromMap(root, problems::add);
    }

    @Test
    void roundTripsEveryField() {
        Plot p = new Plot("main", "minecraft:the_nether", "bakery_plot", "bakery_house",
                new Plot.Viewpoint(10.5, 64, -3.5, 90f, 10f), new Plot.Pedestal(12.5, 64, -3.5, -90f),
                new Plot.Point(20.5, 72, 1.5));
        List<String> problems = new ArrayList<>();
        List<Plot> back = parse(PlotCodec.toMap(List.of(p)), problems);
        assertEquals(List.of(p), back);
        assertTrue(problems.isEmpty(), problems.toString());
    }

    @Test
    void roundTripsThroughRealYamlText() throws Exception {
        Plot p = new Plot("a-b_1", "minecraft:overworld", null, "h", null, new Plot.Pedestal(1, 2, 3, 4f), null);
        YamlConfiguration out = new YamlConfiguration();
        PlotCodec.toMap(List.of(p)).forEach((k, v) -> out.createSection(k, (Map<?, ?>) v));
        YamlConfiguration in = new YamlConfiguration();
        in.loadFromString(out.saveToString());
        List<String> problems = new ArrayList<>();
        assertEquals(List.of(p), parse(dev.casperrs.cookieClicker.config.YamlFiles.toMap(in), problems));
        assertTrue(problems.isEmpty());
    }

    @Test
    void minimalPlotHasOnlyAWorld() {
        List<String> problems = new ArrayList<>();
        List<Plot> plots = parse(root("main", spec()), problems);
        assertEquals(1, plots.size());
        Plot p = plots.getFirst();
        assertNull(p.viewpoint());
        assertNull(p.pedestal());
        assertNull(p.chimney());
        assertNull(p.plotRegion());
        assertTrue(p.station().isEmpty());
        assertTrue(problems.isEmpty());
    }

    @Test
    void idsAreLowerCasedAndValidated() {
        List<String> problems = new ArrayList<>();
        assertEquals("main", parse(root("MAIN", spec()), problems).getFirst().id());
        assertTrue(problems.isEmpty());

        for (String bad : new String[] {"has space", "a/b", "../x", "x".repeat(65), "ü"}) {
            List<String> pr = new ArrayList<>();
            assertTrue(parse(root(bad, spec()), pr).isEmpty(), bad);
            assertEquals(1, pr.size(), bad);
        }
    }

    @Test
    void badWorldOrNonSectionSkipsOnlyThatPlot() {
        Map<String, Object> plots = new LinkedHashMap<>();
        plots.put("good", spec());
        plots.put("noworld", Map.of("viewpoint", Map.of("x", 1, "y", 2, "z", 3)));
        plots.put("worldname", Map.of("world", "world_nether")); // a name, not a key
        plots.put("scalar", 5);
        List<String> problems = new ArrayList<>();
        List<Plot> out = parse(Map.of("plots", plots), problems);
        assertEquals(List.of("good"), out.stream().map(Plot::id).toList());
        assertEquals(3, problems.size(), problems.toString());
    }

    @Test
    void badOptionalPointsAreDroppedButThePlotSurvives() {
        List<String> problems = new ArrayList<>();
        Map<String, Object> s = spec("viewpoint", Map.of("x", 1, "y", "high", "z", 3),
                "pedestal", "here", "chimney", Map.of("x", Double.NaN, "y", 1, "z", 2));
        Plot p = parse(root("main", s), problems).getFirst();
        assertNull(p.viewpoint());
        assertNull(p.pedestal());
        assertNull(p.chimney());
        assertEquals(3, problems.size(), problems.toString());
    }

    @Test
    void yawAndPitchAreOptionalAndDefaultToZero() {
        List<String> problems = new ArrayList<>();
        Plot p = parse(root("main", spec("viewpoint", Map.of("x", 1, "y", 2, "z", 3), "pedestal", Map.of("x", 4, "y", 5, "z", 6))),
                problems).getFirst();
        assertEquals(new Plot.Viewpoint(1, 2, 3, 0f, 0f), p.viewpoint());
        assertEquals(new Plot.Pedestal(4, 5, 6, 0f), p.pedestal());
        assertTrue(problems.isEmpty());
    }

    @Test
    void regionFallbackNamesAreTrimmedAndLowerCasedBlankIsNull() {
        Plot p = parse(root("main", spec("plot-region", "  Bakery_Plot ", "house-region", "   ")), new ArrayList<>()).getFirst();
        assertEquals("bakery_plot", p.plotRegion());
        assertNull(p.houseRegion());
    }

    @Test
    void missingOrMalformedPlotsSectionIsEmptyNotAnError() {
        List<String> problems = new ArrayList<>();
        assertTrue(parse(Map.of(), problems).isEmpty());
        assertTrue(parse(Map.of("plots", "nope"), problems).isEmpty());
        assertTrue(problems.isEmpty());
    }

    @Test
    void pedestalBecomesTheStationWithThePlotId() {
        Plot p = Plot.empty("main", "minecraft:overworld").withPedestal(new Plot.Pedestal(1.5, 64, 2.5, 90f));
        var s = p.station().orElseThrow();
        assertEquals("main", s.id());
        assertEquals("minecraft:overworld", s.worldKey());
        assertEquals(1.5, s.x());
        assertEquals(90f, s.yaw());
    }

    @Test
    void changingTheWorldDropsThePointsOfTheOldWorld() {
        Plot p = Plot.empty("main", "minecraft:overworld").withPedestal(new Plot.Pedestal(1, 2, 3, 0))
                .withViewpoint(new Plot.Viewpoint(1, 2, 3, 0, 0)).withChimney(new Plot.Point(1, 2, 3));
        assertEquals(p, p.withWorld("minecraft:overworld"));
        Plot moved = p.withWorld("minecraft:the_end");
        assertNull(moved.pedestal());
        assertNull(moved.viewpoint());
        assertNull(moved.chimney());
        assertEquals("minecraft:the_end", moved.worldKey());
    }
}
