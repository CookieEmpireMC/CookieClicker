package dev.casperrs.cookieClicker.plot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PERF-W1 guard: {@link PlotRegistry#mayContainPlot} must never say "no" for a block inside a flagged plot or house
 * region (that would hide a player from the tracker), and must follow {@code replaceAll} / {@code put}.
 */
class PlotRegistryBoundsTest {

    private static World world(String name) {
        UUID uid = UUID.nameUUIDFromBytes(name.getBytes());
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUID" -> uid;
                    case "hashCode" -> uid.hashCode();
                    case "equals" -> proxy == args[0];
                    case "toString" -> name;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static final class FakeRegions implements PlotRegions {
        final Map<World, List<RegionInfo>> infos = new HashMap<>();
        final Map<String, RegionShape> shapes = new HashMap<>();

        @Override
        public Presence at(World w, int x, int y, int z) {
            throw new UnsupportedOperationException("the bounds check must not query regions per block");
        }

        @Override
        public List<RegionInfo> regions(World w) {
            return infos.getOrDefault(w, List.of());
        }

        @Override
        public Optional<RegionShape> shape(World w, String regionId) {
            return Optional.ofNullable(shapes.get(regionId));
        }
    }

    private final World overworld = world("overworld");
    private final World nether = world("nether");
    private final FakeRegions regions = new FakeRegions();
    private PlotRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new PlotRegistry(regions, key -> switch (key) {
            case "minecraft:overworld" -> Optional.of(overworld);
            case "minecraft:the_nether" -> Optional.of(nether);
            default -> Optional.empty();
        });
        regions.infos.put(overworld, List.of(
                new PlotRegions.RegionInfo("plot_area", 0, "main", null),
                new PlotRegions.RegionInfo("house_area", 1, null, "main"),
                new PlotRegions.RegionInfo("foreign", 0, "not_a_plot", null)));
        regions.shapes.put("plot_area", RegionShape.cuboid("plot_area", 0, 60, 0, 30, 90, 30));
        regions.shapes.put("house_area", RegionShape.cuboid("house_area", 50, 60, 50, 60, 70, 60));
        regions.shapes.put("foreign", RegionShape.cuboid("foreign", 1000, 0, 1000, 1010, 10, 1010));
        registry.replaceAll(List.of(Plot.empty("main", "minecraft:overworld")), p -> { });
    }

    @Test
    void insideEdgeAndOutsideOfTheFlaggedRegions() {
        assertTrue(registry.mayContainPlot(overworld, 10, 70, 10));
        assertTrue(registry.mayContainPlot(overworld, 0, 60, 0), "min corner");
        assertTrue(registry.mayContainPlot(overworld, 30, 90, 30), "max corner");
        assertFalse(registry.mayContainPlot(overworld, 61, 70, 10), "outside the union box of plot + house");
        assertFalse(registry.mayContainPlot(overworld, 10, 91, 10));
        assertTrue(registry.mayContainPlot(overworld, 40, 70, 40), "one box over all flagged regions: conservative, never a false no");
        assertFalse(registry.mayContainPlot(overworld, -1, 70, 10));
        assertTrue(registry.mayContainPlot(overworld, 55, 65, 55), "the house region counts too");
        assertFalse(registry.mayContainPlot(overworld, 1005, 5, 1005), "a flag naming no plot is ignored");
    }

    @Test
    void worldsWithoutPlotsAndUnloadedWorldsNeverMatch() {
        regions.infos.put(nether, List.of(new PlotRegions.RegionInfo("x", 0, "main", null)));
        regions.shapes.put("x", RegionShape.cuboid("x", 0, 0, 0, 100, 100, 100));
        assertFalse(registry.mayContainPlot(nether, 10, 10, 10), "no plot lives in the nether");
        assertFalse(registry.mayContainPlot(world("elsewhere"), 10, 70, 10));
    }

    @Test
    void replaceAllAndPutRefreshTheBounds() {
        assertFalse(registry.mayContainPlot(overworld, 200, 70, 200));
        regions.shapes.put("plot_area", RegionShape.cuboid("plot_area", 0, 60, 0, 300, 90, 300));
        assertFalse(registry.mayContainPlot(overworld, 200, 70, 200), "cached until a reload (documented)");
        registry.replaceAll(List.of(Plot.empty("main", "minecraft:overworld")), p -> { });
        assertTrue(registry.mayContainPlot(overworld, 200, 70, 200));

        regions.shapes.put("plot_area", RegionShape.cuboid("plot_area", 0, 60, 0, 30, 90, 30));
        registry.put(Plot.empty("main", "minecraft:overworld"), p -> { });
        assertFalse(registry.mayContainPlot(overworld, 200, 70, 200));

        // a plot added to another world is picked up by put
        regions.infos.put(nether, List.of(new PlotRegions.RegionInfo("n", 0, "second", null)));
        regions.shapes.put("n", RegionShape.cuboid("n", 0, 0, 0, 5, 5, 5));
        assertFalse(registry.mayContainPlot(nether, 1, 1, 1));
        registry.put(Plot.empty("second", "minecraft:the_nether"), p -> { });
        assertTrue(registry.mayContainPlot(nether, 1, 1, 1));
    }
}
