package dev.casperrs.cookieClicker.plot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.config.YamlFiles;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** plots.yml {@code belt} and {@code camera}. */
class PlotCodecBeltCameraTest {

    private static Plot plot() {
        return new Plot("main", "minecraft:overworld", null, null, new Plot.Viewpoint(1.5, 64, 2.5, 0f, -14f),
                new Plot.Pedestal(18.5, 64, 6.5, 180f), List.of(), List.of(), null, null, null,
                new Plot.Belt(List.of(new Plot.Cell(18, 64, 11), new Plot.Cell(18, 64, 10), new Plot.Cell(18, 64, 9)),
                        1.0),
                new Plot.Viewpoint(23.5, 76.0, -13.5, 20f, 24f));
    }

    private static List<Plot> viaYamlText(List<Plot> in, List<String> problems) throws Exception {
        YamlConfiguration out = new YamlConfiguration();
        PlotCodec.toMap(in).forEach(out::set);
        YamlConfiguration back = new YamlConfiguration();
        back.loadFromString(out.saveToString());
        return PlotCodec.fromMap(YamlFiles.toMap(back), problems::add);
    }

    @Test
    void beltAndCameraRoundTripThroughYamlText() throws Exception {
        List<String> problems = new ArrayList<>();
        assertEquals(List.of(plot()), viaYamlText(List.of(plot()), problems));
        assertTrue(problems.isEmpty(), problems.toString());
    }

    @Test
    void oldFilesWithoutBeltOrCameraStillLoad() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("world", "minecraft:overworld");
        Plot p = PlotCodec.fromMap(Map.of("plots", Map.of("main", spec)), x -> {
        }).getFirst();
        assertNull(p.belt());
        assertNull(p.camera());
    }

    private static List<String> problemsFor(String key, Object value) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("world", "minecraft:overworld");
        spec.put(key, value);
        List<String> problems = new ArrayList<>();
        List<Plot> plots = PlotCodec.fromMap(Map.of("plots", Map.of("main", spec)), problems::add);
        assertEquals(1, plots.size(), "the plot itself stays");
        Plot p = plots.getFirst();
        assertNull("belt".equals(key) ? p.belt() : p.camera(), "the bad optional is left out");
        return problems;
    }

    @Test
    void badBeltsAreReportedAndDropped() {
        Object[] bad = {
                "belt", Map.of("height", 1.0), Map.of("cells", List.of(List.of(1, 2, 3))),
                Map.of("height", 1.0, "cells", List.of()), Map.of("height", 1.0, "cells", List.of(List.of(1, 2))),
                Map.of("height", 1.0, "cells", List.of(List.of("a", 2, 3))),
                Map.of("height", Double.NaN, "cells", List.of(List.of(1, 2, 3))),
                Map.of("height", "tall", "cells", List.of(List.of(1, 2, 3))),
                Map.of("height", 1.0, "cells", "none")};
        for (Object b : bad) {
            List<String> problems = problemsFor("belt", b);
            assertTrue(problems.stream().anyMatch(s -> s.contains(".belt")), problems.toString());
        }
    }

}
