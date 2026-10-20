package dev.casperrs.cookieClicker.house;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.config.YamlFiles;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

/** points.smoke / points.steam lists, the legacy points.chimney, and the layout section inside a design. */
class HouseDesignPointsTest {

    private static Map<String, Object> design(Map<String, Object> points) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("size", List.of(3, 2, 2));
        m.put("palette", new LinkedHashMap<>(Map.of("P", "minecraft:oak_planks")));
        m.put("layers", List.of(List.of("PPP", "PPP"), List.of("PPP", "PPP")));
        if (points != null) {
            m.put("points", points);
        }
        return m;
    }

    private static Map<String, Object> load(String resource) throws Exception {
        try (InputStream in = HouseDesignPointsTest.class.getResourceAsStream(resource)) {
            Assumptions.assumeTrue(in != null, resource + " not on the classpath");
            YamlConfiguration y = new YamlConfiguration();
            y.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return YamlFiles.toMap(y);
        }
    }

    @Test
    void smokeAndSteamListsAreParsed() {
        Map<String, Object> pts = new LinkedHashMap<>();
        pts.put("smoke", List.of(List.of(0, 1, 0), List.of(2, 1, 1)));
        pts.put("steam", List.of(List.of(1, 0, 1)));
        HouseDesign d = HouseDesignParser.parse("t", design(pts)).design();
        assertNotNull(d);
        assertEquals(2, d.smoke().size());
        assertArrayEquals(new int[] {2, 1, 1}, d.smoke().get(1));
        assertEquals(1, d.steam().size());
        assertFalse(d.points().containsKey("smoke"), "lists are not single points");
    }

    @Test
    void legacyChimneyIsAOneItemSmokeList() {
        HouseDesign d = HouseDesignParser.parse("t", design(new LinkedHashMap<>(Map.of("chimney", List.of(1, 1, 1)))))
                .design();
        assertEquals(1, d.smoke().size());
        assertArrayEquals(new int[] {1, 1, 1}, d.smoke().getFirst());
        assertTrue(d.steam().isEmpty());
        assertNotNull(d.points().get("chimney"), "chimney stays a named point too");
    }

    @Test
    void smokeListWinsOverChimney() {
        Map<String, Object> pts = new LinkedHashMap<>();
        pts.put("chimney", List.of(1, 1, 1));
        pts.put("smoke", List.of(List.of(0, 0, 0)));
        HouseDesign d = HouseDesignParser.parse("t", design(pts)).design();
        assertEquals(1, d.smoke().size());
        assertArrayEquals(new int[] {0, 0, 0}, d.smoke().getFirst());
    }

    @Test
    void noPointsMeansNoSmokeAndNoSteam() {
        HouseDesign d = HouseDesignParser.parse("t", design(null)).design();
        assertTrue(d.smoke().isEmpty() && d.steam().isEmpty());
    }

    @Test
    void badListsAreRejectedWithTheIndex() {
        for (Object bad : new Object[] {"x", List.of(List.of(0, 0)), List.of(List.of(3, 0, 0)), List.of(List.of(0, 2, 0)),
                List.of(List.of(0, 0, -1)), List.of(List.of(0.5, 0, 0)), List.of("a")}) {
            Map<String, Object> pts = new LinkedHashMap<>();
            pts.put("steam", bad);
            HouseDesignParser.Result r = HouseDesignParser.parse("t", design(pts));
            assertNull(r.design(), String.valueOf(bad));
            assertTrue(String.join("|", r.problems()).contains("points.steam"), r.problems().toString());
        }
    }

    @Test
    void beltOutAndDoorStayNamedPoints() {
        Map<String, Object> pts = new LinkedHashMap<>();
        pts.put("belt_out", List.of(1, 0, 1));
        pts.put("door", List.of(2, 0, 0));
        HouseDesign d = HouseDesignParser.parse("t", design(pts)).design();
        assertArrayEquals(new int[] {1, 0, 1}, d.points().get("belt_out"));
        assertArrayEquals(new int[] {2, 0, 0}, d.points().get("door"));
    }

    @Test
    void layoutMustFitInsideTheFenceRing() {
        // 3 x 2 house; plot-size 31 x 30: house-at must leave the outer ring free
        for (int[] at : new int[][] {{0, 0, 5}, {5, 0, 0}, {29, 0, 5}, {5, 0, 29}}) {
            Map<String, Object> m = design(null);
            Map<String, Object> l = HouseLayoutTest.valid();
            l.put("house-at", List.of(at[0], at[1], at[2]));
            m.put("layout", l);
            HouseDesignParser.Result r = HouseDesignParser.parse("t", m);
            assertNull(r.design(), java.util.Arrays.toString(at));
            assertTrue(String.join("|", r.problems()).contains("house-at"));
        }
        Map<String, Object> m = design(null);
        Map<String, Object> l = HouseLayoutTest.valid();
        l.put("house-at", List.of(1, 0, 1));
        m.put("layout", l);
        assertNotNull(HouseDesignParser.parse("t", m).design().layout());
        l.put("house-at", List.of(28, 0, 27)); // 28 + 3 = 31 > 30
        assertNull(HouseDesignParser.parse("t", m).design());
    }

    @Test
    void layoutProblemsFailTheDesignAndNoLayoutIsFine() {
        Map<String, Object> m = design(null);
        m.put("layout", "x");
        assertNull(HouseDesignParser.parse("t", m).design());
        assertNull(HouseDesignParser.parse("t", design(null)).design().layout());
    }

    @Test
    void theFactoryFixtureParsesWithItsLayoutInsideTheRing() throws Exception {
        HouseDesignParser.Result r = HouseDesignParser.parse("factory",
                load("/fixtures/factory-makeover-factory.yml"));
        assertTrue(r.problems().isEmpty(), r.problems().toString());
        HouseDesign d = r.design();
        assertEquals(19, d.sizeX());
        assertEquals(17, d.sizeY());
        assertEquals(14, d.sizeZ());
        assertEquals(2, d.smoke().size());
        assertEquals(2, d.steam().size());
        assertNotNull(d.points().get("belt_out"));
        assertNotNull(d.layout());
        assertEquals(31, d.layout().sizeX());
        assertTrue(d.cells().size() < 40000);
        assertTrue(d.cells().stream().allMatch(c -> c.blockData().startsWith("minecraft:")));
    }

    @Test
    void theShippedFactoryAndCottageParseWhenPresent() throws Exception {
        HouseDesignParser.Result f = HouseDesignParser.parse("factory", load("/houses/factory.yml"));
        assertTrue(f.problems().isEmpty(), f.problems().toString());
        assertNotNull(f.design().layout(), "factory must carry a layout for /bakery plot setup");
        HouseDesignParser.Result c = HouseDesignParser.parse("default", load("/houses/default.yml"));
        assertTrue(c.problems().isEmpty(), c.problems().toString());
        assertEquals(1, c.design().smoke().size(), "the cottage chimney = one smoke point");
        FenceStyle.Result s = FenceStyle.parse(load("/houses/fence.yml"));
        assertTrue(s.problems().isEmpty(), s.problems().toString());
    }
}
