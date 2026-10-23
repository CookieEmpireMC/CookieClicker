package dev.casperrs.cookieClicker.house.design;

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

}
