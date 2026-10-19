package dev.casperrs.cookieClicker.plot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.station.Station;
import dev.casperrs.cookieClicker.station.StationFile;
import dev.casperrs.cookieClicker.station.StationRegistry;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** PlotService / PlotRegistry against a fake {@link PlotRegions} and a Proxy {@link World} (no server needed). */
class PlotServiceTest {

    private static final World WORLD = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "hashCode" -> 1;
                case "equals" -> proxy == args[0];
                case "toString" -> "FakeWorld";
                default -> throw new UnsupportedOperationException(method.getName());
            });

    /** Regions the test can change between loads, like /rg redefine. */
    private static final class FakeRegions implements PlotRegions {
        List<RegionInfo> infos = new ArrayList<>();
        /** plot flag value at a block, or null */
        BiFunction<Integer, Integer, String> plotAt = (x, z) -> null;
        int atCalls;

        @Override
        public Presence at(World world, int x, int y, int z) {
            atCalls++;
            String plot = plotAt.apply(x, z);
            return plot == null ? Presence.NONE : new Presence(plot, null);
        }

        @Override
        public List<RegionInfo> regions(World world) {
            return infos;
        }

        @Override
        public Optional<RegionShape> shape(World world, String regionId) {
            return Optional.of(RegionShape.cuboid(regionId, 0, 0, 0, 1, 1, 1));
        }
    }

    @TempDir
    Path dir;

    private final FakeRegions regions = new FakeRegions();
    private final Logger log = quiet();
    private StationRegistry stations;
    private PlotFile plotFile;
    private StationFile stationFile;
    private PlotService service;
    private final List<String> problems = new ArrayList<>();

    private static Logger quiet() {
        Logger l = Logger.getAnonymousLogger();
        l.setLevel(Level.OFF);
        return l;
    }

    @BeforeEach
    void setUp() {
        Optional<World> world = Optional.of(WORLD);
        stations = new StationRegistry(log);
        plotFile = new PlotFile(dir, log, Runnable::run);
        stationFile = new StationFile(dir, log, Runnable::run, () -> { }); // like production: nothing is copied from the jar
        service = new PlotService(new PlotRegistry(regions, k -> k.equals("minecraft:overworld") ? world : Optional.empty()),
                plotFile, stationFile, stations, dir, log, k -> k.equals("minecraft:overworld") ? world : Optional.empty());
    }

    @Test
    void firstStartWithoutAnyFilesCreatesAnEmptyPlotsYml() {
        service.load(problems::add);
        assertTrue(Files.isRegularFile(dir.resolve("plots.yml")));
        assertTrue(service.registry().all().isEmpty());
        assertTrue(stations.all().isEmpty());
        assertTrue(problems.isEmpty());
    }

    @Test
    void migrationTurnsStationsInsideAPlotIntoPedestalsAndKeepsTheOldFileAsBak() {
        stationFile.saveAsync(List.of(new Station("station-1", "minecraft:overworld", 5.5, 64, 5.5, 90f),
                new Station("station-2", "minecraft:overworld", 500.5, 64, 5.5, 0f),
                new Station("station-3", "minecraft:the_nether", 5.5, 64, 5.5, 0f)));
        regions.plotAt = (x, z) -> x < 100 ? "main" : null;
        regions.infos = List.of(new PlotRegions.RegionInfo("bakery", 0, "main", null));

        service.load(problems::add);

        assertEquals(List.of("main"), service.registry().all().stream().map(Plot::id).toList());
        Plot main = service.registry().get("main").orElseThrow();
        assertEquals(new Plot.Pedestal(5.5, 64, 5.5, 90f), main.pedestal());
        assertEquals(1, stations.all().size(), "only the migrated pedestal is a live station");
        assertEquals("main", stations.all().iterator().next().id());
        assertEquals(2, problems.stream().filter(p -> p.contains("no longer works")).count(), problems.toString());
        assertTrue(Files.notExists(dir.resolve("stations.yml")), "old file moved away once plots.yml is written");
        assertTrue(Files.isRegularFile(dir.resolve("stations.yml.bak")));
        assertTrue(Files.isRegularFile(dir.resolve("plots.yml")));

        // a second start reads plots.yml and does not migrate again
        problems.clear();
        service.load(problems::add);
        assertEquals(new Plot.Pedestal(5.5, 64, 5.5, 90f), service.registry().get("main").orElseThrow().pedestal());
    }

    @Test
    void migrationReadsTheServersDataFolderNotTheJarDefault() {
        // no stations.yml in the data folder (the jar no longer ships one): nothing to migrate, nothing invented
        service.load(problems::add);
        assertTrue(service.registry().all().isEmpty());
        assertTrue(Files.notExists(dir.resolve("stations.yml")), "must not create a stations.yml");
        assertTrue(Files.notExists(dir.resolve("stations.yml.bak")));
    }

    @Test
    void anUnreadableStationsYmlIsNotMovedAndNotMigrated() throws Exception {
        Files.writeString(dir.resolve("stations.yml"), "stations: [unclosed\n  - : :");
        service.load(problems::add);
        assertTrue(Files.isRegularFile(dir.resolve("stations.yml")), "left in place for the admin to fix");
        assertFalse(problems.isEmpty());
        assertTrue(service.registry().all().isEmpty());
    }

    @Test
    void reloadReResolvesRegionsSoRedefineWithoutRestartWorks() {
        plotFile.saveAsync(List.of(new Plot("main", "minecraft:overworld", null, null, null, null, null)));
        regions.infos = List.of(new PlotRegions.RegionInfo("old_plot", 0, "main", null),
                new PlotRegions.RegionInfo("old_house", 0, null, "main"));
        service.load(problems::add);
        assertEquals("old_plot", service.registry().resolved("main").plotRegion());
        assertEquals("old_house", service.registry().resolved("main").houseRegion());

        // admin: /rg remove old_house, /rg define new_house, flag it, then /bakery reload
        regions.infos = List.of(new PlotRegions.RegionInfo("old_plot", 0, "main", null),
                new PlotRegions.RegionInfo("new_house", 0, null, "main"));
        service.load(problems::add);
        assertEquals("new_house", service.registry().resolved("main").houseRegion());
        assertEquals("new_house", service.registry().houseShape("main").orElseThrow().id());
    }

    @Test
    void reloadWithPlotsYmlGoneBadKeepsTheCurrentPlots() throws Exception {
        plotFile.saveAsync(List.of(Plot.empty("main", "minecraft:overworld").withPedestal(new Plot.Pedestal(1, 2, 3, 0))));
        service.load(problems::add);
        assertEquals(1, stations.all().size());
        Files.writeString(dir.resolve("plots.yml"), "plots: [unclosed\n  - : :");
        problems.clear();
        service.load(problems::add);
        assertTrue(problems.stream().anyMatch(p -> p.contains("keeping the current plots")), problems.toString());
        assertEquals(1, service.registry().all().size());
        assertEquals(1, stations.all().size(), "pedestal still works");
    }

    @Test
    void aPlotInAnUnloadedWorldIsReportedNotCrashing() {
        plotFile.saveAsync(List.of(Plot.empty("far", "minecraft:the_end")));
        service.load(problems::add);
        assertTrue(problems.stream().anyMatch(p -> p.contains("not loaded")), problems.toString());
        assertEquals(PlotResolver.Resolved.NONE, service.registry().resolved("far"));
        assertTrue(service.registry().houseShape("far").isEmpty());
    }

    @Test
    void settingAPedestalUpdatesTheRegistryStationsAndFile() {
        service.load(problems::add);
        Plot p = service.getOrCreate("main", WORLD2());
        service.update(p.withPedestal(new Plot.Pedestal(9.5, 70, 9.5, 90f)), problems::add);
        assertEquals(1, stations.all().size());
        assertEquals(9.5, stations.get("main").orElseThrow().x());
        assertTrue(read(dir.resolve("plots.yml")).contains("pedestal"));
        assertNull(service.registry().get("main").orElseThrow().viewpoint());
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    /** getOrCreate needs a World for its key; the Proxy fails on getKey, so go through Plot.empty directly. */
    private static World WORLD2() {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getKey")) {
                        return org.bukkit.NamespacedKey.minecraft("overworld");
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
