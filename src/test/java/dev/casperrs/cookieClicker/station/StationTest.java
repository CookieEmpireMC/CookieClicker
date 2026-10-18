package dev.casperrs.cookieClicker.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class StationTest {

    private static final String OW = "minecraft:overworld";

    private static Logger quiet() {
        Logger l = Logger.getAnonymousLogger();
        l.setLevel(Level.OFF);
        return l;
    }

    private static Station st(String id, double x) {
        return new Station(id, OW, x, 64, 0, 90f);
    }

    private static final class Recorder implements StationListener {
        final List<String> log = new ArrayList<>();

        @Override
        public void onStationAdded(Station s) {
            log.add("+" + s.id());
        }

        @Override
        public void onStationRemoved(Station s) {
            log.add("-" + s.id());
        }
    }

    // ---- registry ----

    @Test
    void addRemoveFireEventsAndReplaceFiresRemoveFirst() {
        StationRegistry r = new StationRegistry(quiet());
        Recorder rec = new Recorder();
        r.addListener(rec);
        r.add(st("station-1", 0));
        r.add(new Station("station-1", OW, 5, 64, 0, 0f));
        assertEquals(List.of("+station-1", "-station-1", "+station-1"), rec.log);
        assertEquals(5, r.get("station-1").orElseThrow().x());
        assertTrue(r.remove("station-1").isPresent());
        assertTrue(r.remove("station-1").isEmpty());
        assertEquals("-station-1", rec.log.get(rec.log.size() - 1));
        assertEquals(4, rec.log.size());
    }

    @Test
    void newListenerIsToldAboutExistingStations() {
        StationRegistry r = new StationRegistry(quiet());
        r.add(st("station-1", 0));
        r.add(st("station-2", 20));
        Recorder rec = new Recorder();
        r.addListener(rec);
        assertEquals(List.of("+station-1", "+station-2"), rec.log);
    }

    @Test
    void clearFiresRemoveForEveryStationAndEmptiesRegistry() {
        StationRegistry r = new StationRegistry(quiet());
        Recorder rec = new Recorder();
        r.addListener(rec);
        r.add(st("station-1", 0));
        r.add(st("station-2", 20));
        rec.log.clear();
        r.clear();
        assertEquals(List.of("-station-1", "-station-2"), rec.log);
        assertTrue(r.all().isEmpty());
    }

    @Test
    void replaceAllOnlyTouchesWhatChanged() {
        StationRegistry r = new StationRegistry(quiet());
        r.add(st("station-1", 0));
        r.add(st("station-2", 20));
        r.add(st("station-3", 40));
        Recorder rec = new Recorder();
        r.addListener(rec);
        rec.log.clear();
        r.replaceAll(List.of(st("station-1", 0), new Station("station-2", OW, 21, 64, 0, 90f), st("station-4", 80)));
        assertEquals(List.of("-station-2", "-station-3", "+station-2", "+station-4"), rec.log);
        assertEquals(List.of("station-1", "station-2", "station-4"), r.all().stream().map(Station::id).sorted().toList());
    }

    @Test
    void replaceAllWithSameContentFiresNothing() {
        StationRegistry r = new StationRegistry(quiet());
        r.add(st("station-1", 0));
        Recorder rec = new Recorder();
        r.addListener(rec);
        rec.log.clear();
        r.replaceAll(List.of(st("station-1", 0)));
        assertTrue(rec.log.isEmpty());
    }

    @Test
    void listenerExceptionDoesNotBreakTheRegistryOrOtherListeners() {
        StationRegistry r = new StationRegistry(quiet());
        r.addListener(new StationListener() {
            @Override
            public void onStationAdded(Station s) {
                throw new IllegalStateException("boom");
            }

            @Override
            public void onStationRemoved(Station s) {
                throw new IllegalStateException("boom");
            }
        });
        Recorder rec = new Recorder();
        r.addListener(rec);
        r.add(st("station-1", 0));
        r.remove("station-1");
        assertEquals(List.of("+station-1", "-station-1"), rec.log);
        assertTrue(r.all().isEmpty());
    }

    @Test
    void nearestIsPerWorldAndRespectsRadius() {
        StationRegistry r = new StationRegistry(quiet());
        r.add(st("station-1", 0));
        r.add(st("station-2", 6));
        r.add(new Station("station-3", "minecraft:the_nether", 1, 64, 0, 0f));
        assertEquals("station-2", r.nearest(OW, 5, 64, 0, 8).orElseThrow().id());
        assertEquals("station-3", r.nearest("minecraft:the_nether", 0, 64, 0, 8).orElseThrow().id());
        assertTrue(r.nearest(OW, 100, 64, 0, 8).isEmpty());
        assertTrue(r.nearest("minecraft:the_end", 0, 64, 0, 8).isEmpty());
        assertTrue(r.nearest(OW, 0, 64, 0, 0).isPresent(), "exactly on top of it counts at radius 0");
    }

    @Test
    void nextIdIsAboveTheHighestAndIgnoresCustomIds() {
        StationRegistry r = new StationRegistry(quiet());
        assertEquals("station-1", r.nextId());
        r.add(st("station-1", 0));
        r.add(st("station-7", 20));
        r.add(st("custom", 40));
        r.add(st("station-x", 60));
        assertEquals("station-8", r.nextId());
        r.remove("station-7");
        assertEquals("station-2", r.nextId());
    }

    // ---- codec ----

    @Test
    void codecRoundTrips() {
        List<Station> stations = List.of(new Station("station-1", OW, 10.5, 64.0, -3.5, 90f),
                new Station("station-2", "minecraft:the_nether", -1, 5, 2, -180f));
        List<Station> back = StationCodec.fromMap(StationCodec.toMap(stations), p -> {
            throw new AssertionError(p);
        });
        assertEquals(stations, back);
    }

    @Test
    void codecSkipsBadEntriesAndReportsThem() {
        Map<String, Object> stations = new LinkedHashMap<>();
        stations.put("ok", Map.of("world", OW, "x", 1, "y", 2, "z", 3));
        stations.put("worldname", Map.of("world", "world_nether", "x", 1, "y", 2, "z", 3));
        stations.put("noworld", Map.of("x", 1, "y", 2, "z", 3));
        stations.put("upper", Map.of("world", "Minecraft:Overworld", "x", 1, "y", 2, "z", 3));
        stations.put("nocoord", Map.of("world", OW, "x", 1, "z", 3));
        stations.put("nan", Map.of("world", OW, "x", Double.NaN, "y", 2, "z", 3));
        stations.put("text", Map.of("world", OW, "x", "1", "y", 2, "z", 3));
        stations.put("badyaw", Map.of("world", OW, "x", 1, "y", 2, "z", 3, "yaw", "north"));
        stations.put("notsection", "hello");
        List<String> problems = new ArrayList<>();
        List<Station> out = StationCodec.fromMap(Map.of("stations", stations), problems::add);
        assertEquals(1, out.size());
        assertEquals("ok", out.get(0).id());
        assertEquals(0f, out.get(0).yaw(), "yaw defaults to 0");
        assertEquals(8, problems.size(), problems.toString());
    }

    @Test
    void codecToleratesMissingOrWrongRoot() {
        assertTrue(StationCodec.fromMap(Map.of(), p -> { }).isEmpty());
        assertTrue(StationCodec.fromMap(Map.of("stations", "nope"), p -> { }).isEmpty());
        assertFalse(StationCodec.toMap(List.of()).isEmpty());
    }

    @Test
    void worldKeyIsStoredNotTheWorldName() {
        Station s = new Station("a", OW, 0, 0, 0, 0f);
        Map<?, ?> spec = (Map<?, ?>) ((Map<?, ?>) StationCodec.toMap(List.of(s)).get("stations")).get("a");
        assertEquals(OW, spec.get("world"));
        assertEquals(Double.POSITIVE_INFINITY, s.distanceSquared("minecraft:the_end", 0, 0, 0));
    }
}
