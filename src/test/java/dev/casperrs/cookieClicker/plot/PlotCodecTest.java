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
    void missingOrMalformedPlotsSectionIsEmptyNotAnError() {
        List<String> problems = new ArrayList<>();
        assertTrue(parse(Map.of(), problems).isEmpty());
        assertTrue(parse(Map.of("plots", "nope"), problems).isEmpty());
        assertTrue(problems.isEmpty());
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
