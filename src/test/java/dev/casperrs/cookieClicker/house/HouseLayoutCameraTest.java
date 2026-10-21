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

/** {@code layout.camera} and the belt shape check (brief conveyor-belt). */
class HouseLayoutCameraTest {

    private static Map<String, Object> withCamera(Object camera) {
        Map<String, Object> m = HouseLayoutTest.valid();
        m.put("camera", camera);
        return m;
    }

    private static Map<String, Object> cam(Object at, Object yaw, Object pitch) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("at", at);
        if (yaw != null) {
            c.put("yaw", yaw);
        }
        if (pitch != null) {
            c.put("pitch", pitch);
        }
        return c;
    }

    @Test
    void cameraIsOptional() {
        HouseLayout l = HouseLayout.parse(HouseLayoutTest.valid(), new ArrayList<>());
        assertNotNull(l);
        assertNull(l.camera());
    }

    @Test
    void theFactoryCameraOutsideAndAboveThePlotParses() {
        List<String> p = new ArrayList<>();
        HouseLayout l = HouseLayout.parse(withCamera(cam(List.of(23, 12, -13), 20, 24)), p);
        assertNotNull(l, p.toString());
        assertArrayEquals(new double[] {23, 12, -13}, l.camera().at());
        assertEquals(20f, l.camera().yaw());
        assertEquals(24f, l.camera().pitch());
    }

    @Test
    void negativeCoordinatesAndFractionsAreAllowed() {
        HouseLayout l = HouseLayout.parse(withCamera(cam(List.of(-40.5, 90, -200.25), 0, 0)), new ArrayList<>());
        assertNotNull(l);
        assertEquals(-40.5, l.camera().at()[0]);
    }

    @Test
    void yawAndPitchDefaultToZeroAndPitchIsClamped() {
        HouseLayout l = HouseLayout.parse(withCamera(cam(List.of(1, 2, 3), null, null)), new ArrayList<>());
        assertEquals(0f, l.camera().yaw());
        assertEquals(0f, l.camera().pitch());
        HouseLayout steep = HouseLayout.parse(withCamera(cam(List.of(1, 2, 3), 0, 400)), new ArrayList<>());
        assertEquals(90f, steep.camera().pitch());
    }

    @Test
    void malformedCamerasAreReportedAndRejectTheLayout() {
        List<Object> bad = List.of(
                "north",
                cam(List.of(1, 2), 0, 0),
                cam(List.of(1, 2, 3, 4), 0, 0),
                cam(List.of("a", 2, 3), 0, 0),
                cam("1 2 3", 0, 0),
                cam(List.of(1, 2, 3), "left", 0),
                cam(List.of(1, 2, 3), 0, "up"),
                cam(List.of(1, 2, 3), Double.NaN, 0),
                cam(List.of(1, Double.NaN, 3), 0, 0),
                cam(List.of(1, 2, 1e9), 0, 0),
                cam(List.of(Double.POSITIVE_INFINITY, 2, 3), 0, 0),
                new LinkedHashMap<>());
        for (Object b : bad) {
            List<String> problems = new ArrayList<>();
            assertNull(HouseLayout.parse(withCamera(b), problems), String.valueOf(b));
            assertTrue(String.join("|", problems).contains("layout.camera"), problems.toString());
        }
    }

    @Test
    void beltMustBeStraightAndLevelAndShort() {
        for (Map<String, Object> belt : List.<Map<String, Object>>of(
                Map.of("from", List.of(18, 0, 11), "to", List.of(20, 0, 7)), // diagonal
                Map.of("from", List.of(18, 0, 11), "to", List.of(18, 1, 7)), // not level
                Map.of("from", List.of(18, 0, 0), "to", List.of(18, 0, 100)))) { // too long
            Map<String, Object> m = HouseLayoutTest.valid();
            m.put("belt", new LinkedHashMap<>(belt));
            List<String> problems = new ArrayList<>();
            assertNull(HouseLayout.parse(m, problems));
            assertFalse(problems.isEmpty());
        }
    }

    @Test
    void aBeltOfExactlyMaxCellsIsFine() {
        Map<String, Object> m = HouseLayoutTest.valid();
        m.put("belt", new LinkedHashMap<>(Map.of("from", List.of(0, 0, 0), "to", List.of(0, 0, HouseLayout.MAX_BELT - 1))));
        m.put("plot-size", List.of(31, 70));
        m.put("pedestal", new LinkedHashMap<>(Map.of("at", List.of(18, 0, 6), "yaw", 180)));
        List<String> p = new ArrayList<>();
        // may still fail on other cross-checks in a 31x70 plot; the belt itself must not be the problem
        HouseLayout.parse(m, p);
        assertFalse(String.join("|", p).contains("layout.belt must be a straight"), p.toString());
    }
}
