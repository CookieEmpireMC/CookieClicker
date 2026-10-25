package dev.casperrs.cookieClicker.farm;

import dev.casperrs.cookieClicker.house.design.DesignFiles;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Test fixtures: the countryside and two places under {@code src/test/resources/farm/} (not the shipped files). */
final class FarmFixtures {

    static final int[] REGION_MIN = {100, 64, 200};

    private FarmFixtures() {
    }

    /** The three fixture files, parsed fresh so a test can edit its own copy. */
    static Map<String, Map<String, Object>> files() throws Exception {
        Map<String, Map<String, Object>> out = new HashMap<>();
        for (String name : List.of("countryside", "mill", "market")) {
            out.put(name, fixture(name));
        }
        return out;
    }

    static FarmDesigns.Layout layout(Map<String, Map<String, Object>> files) {
        FarmDesigns.Result r = FarmDesigns.load(files::get);
        assertNotNull(r.layout(), () -> "fixture problems: " + r.problems());
        return r.layout();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> points(Map<String, Map<String, Object>> files, String place) {
        return (Map<String, Object>) files.get(place).get("points");
    }

    /** Moves a place inside the countryside: {@code at} in countryside design cells. */
    @SuppressWarnings("unchecked")
    static void moveTo(Map<String, Map<String, Object>> files, String place, List<Integer> at) {
        Map<String, Object> places = (Map<String, Object>) files.get("countryside").get("places");
        ((Map<String, Object>) places.get(place)).put("at", at);
    }

    /** A one-cell-wide house design of {@code sizeY} layers with {@code ground} of them below the region. */
    static Map<String, Object> flat(int sizeY, int ground) {
        List<Object> layers = new ArrayList<>();
        for (int y = 0; y < sizeY; y++) {
            layers.add(List.of("W"));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("size", List.of(1, sizeY, 1));
        m.put("ground-depth", ground);
        m.put("palette", Map.of("W", "minecraft:stone"));
        m.put("layers", layers);
        return m;
    }

    private static Map<String, Object> fixture(String name) throws Exception {
        try (InputStream in = FarmFixtures.class.getResourceAsStream("/farm/" + name + ".yml")) {
            assertNotNull(in, name + ".yml fixture is missing");
            return DesignFiles.parseYaml(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
