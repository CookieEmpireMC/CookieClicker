package dev.casperrs.cookieClicker.house;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class FenceStyleTest {

    static Map<String, Object> fixture() throws Exception {
        try (InputStream in = FenceStyleTest.class.getResourceAsStream("/fixtures/factory-makeover-fence.yml")) {
            YamlConfiguration y = new YamlConfiguration();
            y.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return YamlFiles.toMap(y);
        }
    }

    private static Map<String, Object> minimal() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rail", "minecraft:oak_fence");
        m.put("post", "minecraft:oak_log");
        Map<String, Object> gate = new LinkedHashMap<>();
        gate.put("left", List.of("minecraft:stone"));
        m.put("gate", gate);
        return m;
    }

    @Test
    void parsesTheRealFenceStyle() throws Exception {
        FenceStyle.Result r = FenceStyle.parse(fixture());
        assertTrue(r.problems().isEmpty(), r.problems().toString());
        FenceStyle s = r.style();
        assertNotNull(s);
        assertEquals("minecraft:spruce_fence", s.rail());
        assertEquals(4, s.postEvery());
        assertEquals(2, s.corner().size());
        assertEquals(2, s.topperEvery());
        assertEquals(3, s.gateWidth());
        assertEquals(5, s.gateLeft().size());
        assertEquals(s.gateLeft(), s.gateRight());
        assertEquals(5, s.arch().size());
        assertEquals(FenceStyle.AIR, s.arch().getFirst(), "null in arch = air");
        assertEquals("minecraft:dark_oak_planks", s.arch().get(3));
        assertTrue(s.allBlockData().contains("minecraft:lantern[hanging=false,waterlogged=false]"));
    }

    @Test
    void minimalStyleGetsDefaults() {
        FenceStyle s = FenceStyle.parse(minimal()).style();
        assertNotNull(s);
        assertEquals(List.of("minecraft:oak_log"), s.post());
        assertEquals(s.post(), s.corner(), "corner defaults to post");
        assertEquals(4, s.postEvery());
        assertNull(s.topper());
        assertEquals(0, s.topperEvery());
        assertEquals(3, s.gateWidth());
        assertEquals(List.of(FenceStyle.AIR, FenceStyle.AIR), s.arch());
    }

    @Test
    void topperWithoutEveryDefaultsToEveryPost() {
        Map<String, Object> m = minimal();
        m.put("topper", "minecraft:lantern");
        assertEquals(1, FenceStyle.parse(m).style().topperEvery());
    }

    @Test
    void everyProblemIsCollected() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("post-every", 0);
        m.put("rail", 5);
        FenceStyle.Result r = FenceStyle.parse(m);
        assertNull(r.style());
        String all = String.join(" | ", r.problems());
        assertTrue(all.contains("post must") && all.contains("rail") && all.contains("post-every")
                && all.contains("gate"), all);
    }

    @Test
    void badGateValuesAreRejected() {
        for (Object width : new Object[] {0, 16, 2.5, "3"}) {
            Map<String, Object> m = minimal();
            ((Map<String, Object>) m.get("gate")).put("width", width);
            assertNull(FenceStyle.parse(m).style(), String.valueOf(width));
        }
        Map<String, Object> m = minimal();
        ((Map<String, Object>) m.get("gate")).put("left", List.of());
        assertNull(FenceStyle.parse(m).style());
    }

    @Test
    void columnsAreLimitedAndNullOnlyMeansAirInTheArch() {
        Map<String, Object> m = minimal();
        m.put("post", java.util.Collections.nCopies(17, "minecraft:oak_log"));
        assertNull(FenceStyle.parse(m).style());
        m = minimal();
        java.util.List<Object> withNull = new java.util.ArrayList<>();
        withNull.add(null);
        m.put("post", withNull);
        assertNull(FenceStyle.parse(m).style(), "null in a post column is not air");
        m = minimal();
        ((Map<String, Object>) m.get("gate")).put("arch", java.util.Arrays.asList(null, "minecraft:stone"));
        assertEquals(List.of(FenceStyle.AIR, "minecraft:stone"), FenceStyle.parse(m).style().arch());
    }

    @Test
    void blankBlockDataIsRejected() {
        Map<String, Object> m = minimal();
        m.put("rail", "  ");
        assertNull(FenceStyle.parse(m).style());
    }
}
