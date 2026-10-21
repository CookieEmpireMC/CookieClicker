package dev.casperrs.cookieClicker.plot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.config.YamlFiles;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** plots.yml {@code belt} and {@code camera} (brief conveyor-belt). */
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
    void negativeCoordinatesSurvive() throws Exception {
        Plot p = new Plot("n", "minecraft:overworld", null, null, null, null, List.of(), List.of(), null, null, null,
                new Plot.Belt(List.of(new Plot.Cell(-5, -60, -1)), 0.5), new Plot.Viewpoint(-8.5, 3, -9.5, -90f, 0f));
        assertEquals(List.of(p), viaYamlText(List.of(p), new ArrayList<>()));
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

    @Test
    void absentBeltAndCameraAreNotWritten() {
        Plot p = new Plot("main", "minecraft:overworld", null, null, null, null, List.of(), List.of(), null, null,
                null);
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) ((Map<String, Object>) PlotCodec.toMap(List.of(p))
                .get("plots")).get("main");
        assertTrue(!spec.containsKey("belt") && !spec.containsKey("camera"));
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

    @Test
    void badCamerasAreReportedAndDropped() {
        Object[] bad = {"up", Map.of("x", 1.0, "y", 2.0), Map.of("x", "a", "y", 2.0, "z", 3.0),
                Map.of("x", 1.0, "y", 2.0, "z", Double.NaN)};
        for (Object b : bad) {
            List<String> problems = problemsFor("camera", b);
            assertTrue(problems.stream().anyMatch(s -> s.contains(".camera")), problems.toString());
        }
    }

    @Test
    void cameraYawAndPitchAreOptional() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("world", "minecraft:overworld");
        spec.put("camera", Map.of("x", 1.0, "y", 70.0, "z", 3.0));
        List<String> problems = new ArrayList<>();
        Plot p = PlotCodec.fromMap(Map.of("plots", Map.of("main", spec)), problems::add).getFirst();
        assertNotNull(p.camera());
        assertEquals(0f, p.camera().yaw());
        assertTrue(problems.isEmpty());
    }

    @Test
    void derivedPlotsKeepBeltAndCamera() {
        Plot p = plot();
        assertEquals(p.belt(), p.withViewpoint(null).belt());
        assertEquals(p.camera(), p.withPedestal(null).camera());
        assertEquals(p.belt(), p.withSmoke(List.of()).belt());
        assertEquals(p.camera(), p.withSteam(List.of()).camera());
        assertEquals(p.belt(), p.withEntrance(null).belt());
        assertEquals(p.camera(), p.withLayout(null, null, List.of(), List.of(), null, null, null).camera());
        // moving the plot to another world drops the world-specific layout
        Plot moved = p.withWorld("minecraft:the_nether");
        assertNull(moved.belt());
        assertNull(moved.camera());
    }
}
