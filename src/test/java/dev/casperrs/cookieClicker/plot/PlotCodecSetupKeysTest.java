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

/** plots.yml keys added by /bakery plot setup: smoke, steam, entrance, shop, house-box. */
class PlotCodecSetupKeysTest {

    private static Map<String, Object> pt(double x, double y, double z) {
        return new LinkedHashMap<>(Map.of("x", x, "y", y, "z", z));
    }

    private static Map<String, Object> root(Map<String, Object> spec) {
        spec.put("world", "minecraft:overworld");
        return Map.of("plots", new LinkedHashMap<>(Map.of("main", spec)));
    }

    private static Plot full() {
        return new Plot("main", "minecraft:overworld", null, null, new Plot.Viewpoint(1.5, 64, 2.5, 0f, -14f),
                new Plot.Pedestal(3.5, 64, 4.5, 0f), List.of(new Plot.Point(5.5, 70, 6.5), new Plot.Point(7.5, 71, 8.5)),
                List.of(new Plot.Point(9.5, 72, 1.5), new Plot.Point(2.5, 60, 1.5)), new Plot.Point(0.5, 64, 0.5),
                new Plot.Pedestal(2.5, 64, 4.5, 90f), new Plot.Box(1, 64, 2, 10, 80, 20));
    }

    @Test
    void everyNewKeyRoundTripsThroughYamlText() throws Exception {
        YamlConfiguration out = new YamlConfiguration();
        PlotCodec.toMap(List.of(full())).forEach(out::set);
        YamlConfiguration in = new YamlConfiguration();
        in.loadFromString(out.saveToString());
        List<String> problems = new ArrayList<>();
        List<Plot> back = PlotCodec.fromMap(dev.casperrs.cookieClicker.config.YamlFiles.toMap(in), problems::add);
        assertTrue(problems.isEmpty(), problems.toString());
        assertEquals(List.of(full()), back);
    }

    @Test
    void oneSmokePointIsWrittenAsChimneyForOldFiles() {
        Plot one = new Plot("main", "minecraft:overworld", null, null, null, null, new Plot.Point(1, 2, 3));
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) ((Map<String, Object>) PlotCodec.toMap(List.of(one))
                .get("plots")).get("main");
        assertTrue(spec.containsKey("chimney"));
        assertNull(spec.get("smoke"));
    }

    @Test
    void legacyChimneyAndSmokeListAreMerged() {
        List<String> problems = new ArrayList<>();
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("chimney", pt(1, 2, 3));
        spec.put("smoke", List.of(pt(4, 5, 6)));
        Plot p = PlotCodec.fromMap(root(spec), problems::add).getFirst();
        assertEquals(List.of(new Plot.Point(1, 2, 3), new Plot.Point(4, 5, 6)), p.smoke());
        assertEquals(new Plot.Point(1, 2, 3), p.chimney());
        assertTrue(problems.isEmpty());
    }

    @Test
    void badOptionalValuesAreReportedAndLeftOutNotFatal() {
        List<String> problems = new ArrayList<>();
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("smoke", List.of(pt(4, 5, 6), "oops", new LinkedHashMap<>(Map.of("x", 1))));
        spec.put("steam", "not a list");
        spec.put("entrance", Map.of("x", "a", "y", 1, "z", 2));
        spec.put("shop", List.of(1));
        spec.put("house-box", Map.of("min", List.of(1, 2), "max", List.of(3, 4, 5)));
        List<Plot> plots = PlotCodec.fromMap(root(spec), problems::add);
        assertEquals(1, plots.size(), "the plot itself survives");
        Plot p = plots.getFirst();
        assertEquals(List.of(new Plot.Point(4, 5, 6)), p.smoke());
        assertTrue(p.steam().isEmpty());
        assertNull(p.entrance());
        assertNull(p.shop());
        assertNull(p.house());
        assertEquals(6, problems.size(), problems.toString());
    }

    @Test
    void houseBoxCornersAreNormalised() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("house-box", Map.of("min", List.of(10, 80, 20), "max", List.of(1, 64, 2)));
        Plot p = PlotCodec.fromMap(root(spec), s -> { }).getFirst();
        assertEquals(new Plot.Box(1, 64, 2, 10, 80, 20), p.house());
        assertTrue(p.house().contains(1, 64, 2) && p.house().contains(10, 80, 20));
        assertTrue(!p.house().contains(11, 64, 2));
    }

    @Test
    void shopYawDefaultsToZero() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("shop", pt(1, 2, 3));
        assertEquals(new Plot.Pedestal(1, 2, 3, 0f), PlotCodec.fromMap(root(spec), s -> { }).getFirst().shop());
    }

    @Test
    void clicksSpotRoundTripsAndFeedsTheStation() {
        Plot withClicks = new Plot("main", "minecraft:overworld", null, null, null, new Plot.Pedestal(3.5, 64, 4.5, 0f),
                List.of(), List.of(), null, new Plot.Pedestal(2.5, 64, 4.5, 90f), null, null, null,
                new Plot.Pedestal(4.5, 64, 6.5, 270f));
        YamlConfiguration out = new YamlConfiguration();
        PlotCodec.toMap(List.of(withClicks)).forEach(out::set);
        YamlConfiguration in = new YamlConfiguration();
        try {
            in.loadFromString(out.saveToString());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        List<String> problems = new ArrayList<>();
        Plot back = PlotCodec.fromMap(dev.casperrs.cookieClicker.config.YamlFiles.toMap(in), problems::add).getFirst();
        assertTrue(problems.isEmpty(), problems.toString());
        assertEquals(new Plot.Pedestal(4.5, 64, 6.5, 270f), back.clicks());
        assertEquals(new Plot.Pedestal(4.5, 64, 6.5, 270f).yaw(),
                back.station().orElseThrow().clicks().yaw(), "the station's clicks anchor comes from the spot");
    }

    @Test
    void aBadClicksSpotIsReportedAndDroppedWithoutLosingThePlot() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("pedestal", pt(1, 2, 3));
        spec.put("clicks", "north");
        List<String> problems = new ArrayList<>();
        Plot plot = PlotCodec.fromMap(root(spec), problems::add).getFirst();
        assertNull(plot.clicks());
        assertEquals(new Plot.Pedestal(1, 2, 3, 0f), plot.pedestal());
        assertTrue(problems.stream().anyMatch(p -> p.contains("clicks")), problems.toString());
    }
}
