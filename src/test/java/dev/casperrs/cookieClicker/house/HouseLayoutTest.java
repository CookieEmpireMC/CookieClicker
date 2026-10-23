package dev.casperrs.cookieClicker.house;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HouseLayoutTest {

    /** The factory's real layout. */
    static Map<String, Object> valid() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("plot-size", List.of(31, 30));
        m.put("house-at", List.of(6, 0, 10));
        m.put("pedestal", new LinkedHashMap<>(Map.of("at", List.of(18, 0, 6), "yaw", 180)));
        m.put("shop", new LinkedHashMap<>(Map.of("at", List.of(20, 0, 6), "yaw", 180)));
        m.put("viewpoint", new LinkedHashMap<>(Map.of("at", List.of(15, 0, 2), "yaw", 0, "pitch", -14)));
        m.put("entrance", List.of(15, 0));
        m.put("belt", new LinkedHashMap<>(Map.of("from", List.of(18, 0, 11), "to", List.of(18, 0, 7))));
        return m;
    }

    private static String problems(Object raw) {
        List<String> p = new ArrayList<>();
        assertNull(HouseLayout.parse(raw, p));
        assertFalse(p.isEmpty());
        return String.join(" | ", p);
    }

    @Test
    void parsesTheFactoryLayout() {
        List<String> p = new ArrayList<>();
        HouseLayout l = HouseLayout.parse(valid(), p);
        assertNotNull(l, p.toString());
        assertEquals(31, l.sizeX());
        assertEquals(30, l.sizeZ());
        assertArrayEquals(new int[] {6, 0, 10}, l.houseAt());
        assertEquals(180f, l.pedestal().yaw());
        assertArrayEquals(new double[] {20, 0, 6}, l.shop().at());
        assertEquals(-14f, l.viewpoint().pitch());
        assertArrayEquals(new int[] {15, 0}, l.entrance());
        assertArrayEquals(new int[] {18, 0, 7}, l.beltTo());
    }

    @Test
    void plotSizeIsBounded() {
        for (Object size : new Object[] {List.of(4, 30), List.of(30, 4), List.of(257, 30), List.of(31), "31x30",
                List.of(31.5, 30), List.of(-31, 30)}) {
            Map<String, Object> m = valid();
            m.put("plot-size", size);
            assertTrue(problems(m).contains("plot-size"), String.valueOf(size));
        }
    }

    @Test
    void entranceMustBeOnTheOuterRing() {
        for (List<Integer> e : List.of(List.of(15, 5), List.of(31, 0), List.of(-1, 3), List.of(15, 30))) {
            Map<String, Object> m = valid();
            m.put("entrance", e);
            assertTrue(problems(m).contains("entrance"), e.toString());
        }
        for (List<Integer> e : List.of(List.of(0, 7), List.of(30, 7), List.of(7, 29), List.of(0, 0))) {
            Map<String, Object> m = valid();
            m.put("entrance", e);
            assertNotNull(HouseLayout.parse(m, new ArrayList<>()), e.toString());
        }
    }

    @Test
    void missingOrMalformedKeysAreAllReported() {
        Map<String, Object> m = valid();
        m.remove("pedestal");
        m.remove("viewpoint");
        m.put("house-at", List.of(1, 2));
        m.put("belt", "x");
        String p = problems(m);
        assertTrue(p.contains("house-at") && p.contains("pedestal") && p.contains("viewpoint") && p.contains("belt"), p);
    }

}
