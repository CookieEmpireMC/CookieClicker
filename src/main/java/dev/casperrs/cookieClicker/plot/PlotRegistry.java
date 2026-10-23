package dev.casperrs.cookieClicker.plot;

import dev.casperrs.cookieClicker.station.Station;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * All plots from {@code plots.yml} plus the region ids they resolved to (flags first, names as fallback). Region ids
 * and the per-world plot bounds are re-resolved on every {@link #replaceAll} ({@code /bakery reload}) and
 * {@link #put}, so {@code /rg redefine} + reload moves or resizes a plot without a restart; the bounds also expire after
 * {@value #BOUNDS_TTL_MILLIS} ms, so a new {@code /rg flag} or a region loaded late is picked up without a reload.
 * Main thread only.
 */
public final class PlotRegistry {

    private final PlotRegions regions;
    private final Function<String, Optional<World>> worlds;
    private final Map<String, Plot> plots = new LinkedHashMap<>();
    private final Map<String, PlotResolver.Resolved> resolved = new HashMap<>();
    /** How long a world's cached plot bounds are trusted before they are read from the regions again. */
    public static final long BOUNDS_TTL_MILLIS = 30_000;

    private final LongSupplier clockMillis;
    /** Per world UID: the bounding box of every region whose flag names a known plot (lazy, cleared on change). */
    private final Map<UUID, CachedBounds> bounds = new HashMap<>();

    private record CachedBounds(Bounds bounds, long readAt) {
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        static final Bounds EMPTY = new Bounds(0, 0, 0, -1, -1, -1);

        boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        Bounds union(RegionShape s) {
            if (this == EMPTY) {
                return new Bounds(s.minX(), s.minY(), s.minZ(), s.maxX(), s.maxY(), s.maxZ());
            }
            return new Bounds(Math.min(minX, s.minX()), Math.min(minY, s.minY()), Math.min(minZ, s.minZ()),
                    Math.max(maxX, s.maxX()), Math.max(maxY, s.maxY()), Math.max(maxZ, s.maxZ()));
        }
    }

    /** @param worlds world by key ({@code minecraft:overworld}), empty when not loaded */
    public PlotRegistry(PlotRegions regions, Function<String, Optional<World>> worlds) {
        this(regions, worlds, () -> System.nanoTime() / 1_000_000);
    }

    /** @param clockMillis a monotonic clock in milliseconds (the bounds expiry) */
    public PlotRegistry(PlotRegions regions, Function<String, Optional<World>> worlds, LongSupplier clockMillis) {
        this.regions = regions;
        this.worlds = worlds;
        this.clockMillis = clockMillis;
    }

    public PlotRegions regions() {
        return regions;
    }

    public Collection<Plot> all() {
        return Collections.unmodifiableCollection(plots.values());
    }

    public Optional<Plot> get(String id) {
        return Optional.ofNullable(plots.get(id));
    }

    public boolean has(String id) {
        return plots.containsKey(id);
    }

    public PlotResolver.Resolved resolved(String id) {
        return resolved.getOrDefault(id, PlotResolver.Resolved.NONE);
    }

    /** The plot's world, when loaded. */
    public Optional<World> world(Plot plot) {
        return worlds.apply(plot.worldKey());
    }

    /**
     * The plot's house: the box {@code /bakery plot setup} placed (plots.yml {@code house-box}) when there is one,
     * otherwise the current shape of the {@code bakery-house} region (read fresh from WorldGuard).
     */
    public Optional<RegionShape> houseShape(String id) {
        Plot plot = plots.get(id);
        if (plot != null && plot.house() != null) {
            Plot.Box b = plot.house();
            return Optional.of(RegionShape.cuboid("house-box:" + id, b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(),
                    b.maxZ()));
        }
        return shape(id, resolved(id).houseRegion());
    }

    /** Current shape of the plot region (read fresh from WorldGuard). */
    public Optional<RegionShape> plotShape(String id) {
        return shape(id, resolved(id).plotRegion());
    }

    private Optional<RegionShape> shape(String id, String regionId) {
        Plot plot = plots.get(id);
        if (plot == null || regionId == null) {
            return Optional.empty();
        }
        return world(plot).flatMap(w -> regions.shape(w, regionId));
    }

    /**
     * Whether block {@code x y z} may lie in a plot or house region: false means no flagged region of a known plot
     * covers it, so the WorldGuard query can be skipped. Cheap enough for every block change of every player.
     */
    public boolean mayContainPlot(World world, int x, int y, int z) {
        long now = clockMillis.getAsLong();
        CachedBounds cached = bounds.get(world.getUID());
        if (cached == null || now - cached.readAt() >= BOUNDS_TTL_MILLIS) {
            cached = new CachedBounds(boundsOf(world), now);
            bounds.put(world.getUID(), cached);
        }
        return cached.bounds().contains(x, y, z);
    }

    private Bounds boundsOf(World world) {
        boolean any = false;
        for (Plot p : plots.values()) {
            if (world(p).map(world::equals).orElse(false)) {
                any = true;
                break;
            }
        }
        Bounds b = Bounds.EMPTY;
        if (!any) {
            return b;
        }
        for (PlotRegions.RegionInfo r : regions.regions(world)) {
            boolean ours = (r.plotFlag() != null && plots.containsKey(r.plotFlag()))
                    || (r.houseFlag() != null && plots.containsKey(r.houseFlag()));
            if (ours) {
                RegionShape shape = regions.shape(world, r.id()).orElse(null);
                if (shape != null) {
                    b = b.union(shape);
                }
            }
        }
        return b;
    }

    /** Enable / reload: swap in the plots and resolve their regions. */
    public void replaceAll(Collection<Plot> fresh, Consumer<String> problems) {
        bounds.clear();
        plots.clear();
        fresh.forEach(p -> plots.put(p.id(), p));
        resolveAll(problems);
    }

    /** Adds or replaces one plot (commands) and resolves it. */
    public void put(Plot plot, Consumer<String> problems) {
        bounds.clear();
        plots.put(plot.id(), plot);
        resolved.put(plot.id(), resolve(plot, new HashMap<>(), problems));
    }

    private void resolveAll(Consumer<String> problems) {
        resolved.clear();
        Map<String, List<PlotRegions.RegionInfo>> perWorld = new HashMap<>();
        for (Plot p : plots.values()) {
            resolved.put(p.id(), resolve(p, perWorld, problems));
        }
    }

    private PlotResolver.Resolved resolve(Plot plot, Map<String, List<PlotRegions.RegionInfo>> perWorld,
                                          Consumer<String> problems) {
        Optional<World> world = world(plot);
        if (world.isEmpty()) {
            problems.accept("plot '" + plot.id() + "': world " + plot.worldKey() + " is not loaded");
            return PlotResolver.Resolved.NONE;
        }
        List<PlotRegions.RegionInfo> infos = perWorld.computeIfAbsent(plot.worldKey(),
                k -> regions.regions(world.get()));
        PlotResolver.Resolved r = PlotResolver.resolve(plot, infos, problems);
        if (r.plotRegion() == null) {
            problems.accept("plot '" + plot.id() + "' has no plot region: /rg flag <region> bakery-plot " + plot.id());
        }
        if (r.houseRegion() == null) {
            problems.accept("plot '" + plot.id() + "' has no house region: /rg flag <region> bakery-house " + plot.id());
        }
        return r;
    }

    /** One station per plot with a pedestal (the per-player cookie/panel anchor). */
    public List<Station> stations() {
        List<Station> out = new ArrayList<>();
        plots.values().forEach(p -> p.station().ifPresent(out::add));
        return out;
    }
}
