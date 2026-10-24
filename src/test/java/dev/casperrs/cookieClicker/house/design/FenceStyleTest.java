package dev.casperrs.cookieClicker.house.design;

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

    @Test
    void postSpacingCyclesThroughTheGapsFromTheStartingCorner() {
        Map<String, Object> m = minimal();
        m.put("post-spacing", List.of(4, 3, 5)); // posts at 0, 4, 7, then again 12, 16, 19 ...
        FenceStyle s = FenceStyle.parse(m).style();
        assertNotNull(s);
        for (int offset : new int[] {0, 4, 7, 12, 16, 19, 24}) {
            assertTrue(s.postAt(offset), "post at " + offset);
        }
        for (int offset : new int[] {1, 3, 5, 6, 8, 11}) {
            assertFalse(s.postAt(offset), "no post at " + offset);
        }
        assertEquals(4, s.postEvery(), "the first gap");
        assertEquals(3, s.postIndex(12), "posts before cell 12 are 0, 4 and 7");
        assertEquals(5, s.postIndex(19));
    }

    @Test
    void topperSpacingCountsPostsNotCells() {
        Map<String, Object> m = minimal();
        m.put("post-spacing", List.of(4, 3, 5));
        m.put("topper", "minecraft:lantern");
        m.put("topper-spacing", List.of(2, 3)); // posts 0, 2, 5, 7, 10 ... get the topper
        FenceStyle s = FenceStyle.parse(m).style();
        assertNotNull(s);
        assertTrue(s.toppedPost(0));
        assertFalse(s.toppedPost(1));
        assertTrue(s.toppedPost(2));
        assertFalse(s.toppedPost(3));
        assertFalse(s.toppedPost(4));
        assertTrue(s.toppedPost(5));
        assertTrue(s.toppedPost(s.postIndex(7)), "cell 7 is the third post");
        assertFalse(s.toppedPost(s.postIndex(4)), "cell 4 is the second post");
    }

    @Test
    void newSpacingKeysAndTheOldEveryKeysCannotBeMixedAndAnEmptyPostListIsRejected() {
        Map<String, Object> posts = minimal();
        posts.put("post-spacing", List.of(4, 3));
        posts.put("post-every", 4);
        assertTrue(FenceStyle.parse(posts).problems().toString().contains("post-spacing"));

        Map<String, Object> toppers = minimal();
        toppers.put("topper", "minecraft:lantern");
        toppers.put("topper-spacing", List.of(2));
        toppers.put("topper-every", 2);
        assertTrue(FenceStyle.parse(toppers).problems().toString().contains("topper-spacing"));

        Map<String, Object> empty = minimal();
        empty.put("post-spacing", List.of());
        assertFalse(FenceStyle.parse(empty).problems().isEmpty());
    }

    @Test
    void oldEveryKeysStillGiveTheEvenRhythmAndToppersOnEveryOtherPost() throws Exception {
        FenceStyle s = FenceStyle.parse(fixture()).style(); // post-every: 4, topper-every: 2
        assertNotNull(s);
        assertTrue(s.postAt(8));
        assertFalse(s.postAt(6));
        assertTrue(s.toppedPost(s.postIndex(8)), "the third post (index 2) gets the topper");
        assertFalse(s.toppedPost(s.postIndex(4)), "the second post does not");
    }
}
