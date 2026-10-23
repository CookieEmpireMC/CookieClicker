package dev.casperrs.cookieClicker.plot;

import dev.casperrs.cookieClicker.config.YamlFiles;
import dev.casperrs.cookieClicker.station.Station;
import dev.casperrs.cookieClicker.station.StationFile;
import dev.casperrs.cookieClicker.station.StationRegistry;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Plots as a whole: loading {@code plots.yml} (or migrating {@code stations.yml} on the first start), keeping the
 * {@link StationRegistry} (the per-player cookie/panel anchors) equal to the plots' pedestals, and saving changes.
 * Main thread only.
 */
public final class PlotService {

    public static final String STATIONS_BACKUP = StationFile.NAME + ".bak";

    private final PlotRegistry registry;
    private final PlotFile file;
    private final StationFile stationFile;
    private final StationRegistry stations;
    private final Path dataFolder;
    private final Logger log;
    private final Function<String, Optional<World>> worlds;

    public PlotService(PlotRegistry registry, PlotFile file, StationFile stationFile, StationRegistry stations,
                       Path dataFolder, Logger log, Function<String, Optional<World>> worlds) {
        this.registry = registry;
        this.file = file;
        this.stationFile = stationFile;
        this.stations = stations;
        this.dataFolder = dataFolder;
        this.log = log;
        this.worlds = worlds;
    }

    public PlotRegistry registry() {
        return registry;
    }

    public PlotFile file() {
        return file;
    }

    /**
     * Enable and {@code /bakery reload}: reads {@code plots.yml} (migrating {@code stations.yml} first when it
     * doesn't exist yet), re-resolves every plot's regions and updates the stations.
     */
    public void load(Consumer<String> problems) {
        List<Plot> plots;
        if (!file.exists()) {
            plots = migrate(problems);
        } else {
            try {
                plots = file.load(problems);
            } catch (YamlFiles.BadYamlException e) {
                problems.accept(e.getMessage() + " (keeping the current plots)");
                plots = List.copyOf(registry.all());
            }
        }
        registry.replaceAll(plots, problems);
        stations.replaceAll(registry.stations());
    }

    /** Stations inside a bakery-plot region become that plot's pedestal; the old file becomes .bak. */
    private List<Plot> migrate(Consumer<String> problems) {
        Path old = dataFolder.resolve(StationFile.NAME);
        if (!Files.isRegularFile(old)) {
            file.saveAsync(List.of()); // first start: an empty plots.yml with its explanation
            return List.of();
        }
        List<Station> oldStations;
        try {
            oldStations = stationFile.load();
        } catch (YamlFiles.BadYamlException e) {
            problems.accept(e.getMessage() + "; stations were not migrated to plots.yml (fix it and restart)");
            return List.of();
        }
        StationMigration.Plan plan = StationMigration.plan(oldStations, this::plotAt);
        for (Plot p : plan.plots()) {
            log.info("Migrated a station to the pedestal of plot '" + p.id() + "'");
        }
        for (Station s : plan.leftovers()) {
            problems.accept("stations.yml: station " + s.id() + " at " + s.worldKey() + " " + s.x() + " " + s.y()
                    + " " + s.z() + " is not inside a bakery-plot region (or its plot already has a pedestal); it "
                    + "no longer works. It is kept in " + STATIONS_BACKUP + "; set the plot up with /bakery plot setup");
        }
        file.saveAsync(plan.plots(), () -> {
            try { // only once plots.yml is safely written
                Files.move(old, dataFolder.resolve(STATIONS_BACKUP), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                log.log(Level.WARNING, "Could not rename " + StationFile.NAME + " to " + STATIONS_BACKUP, e);
            }
        });
        return plan.plots();
    }

    private Optional<String> plotAt(Station s) {
        Optional<World> world = worlds.apply(s.worldKey());
        if (world.isEmpty()) {
            return Optional.empty();
        }
        Location l = new Location(world.get(), s.x(), s.y(), s.z());
        return Optional.ofNullable(registry.regions().at(world.get(), l.getBlockX(), l.getBlockY(), l.getBlockZ()).plot());
    }

    /** The plot whose {@code bakery-plot} region contains the location (by flag value; may be new to plots.yml). */
    public Optional<String> plotIdAt(Location loc) {
        World world = loc.getWorld();
        return world == null ? Optional.empty()
                : Optional.ofNullable(registry.regions().at(world, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()).plot());
    }

    /** The plot from plots.yml, or a new empty one in {@code world} (switching world drops points of the old one). */
    public Plot getOrCreate(String id, World world) {
        String key = world.getKey().asString();
        return registry.get(id).map(p -> p.withWorld(key)).orElseGet(() -> Plot.empty(id, key));
    }

    /** Stores a changed plot: registry (re-resolved), stations (pedestal), plots.yml (async). */
    public void update(Plot plot, Consumer<String> problems) {
        registry.put(plot, problems);
        stations.replaceAll(registry.stations());
        file.saveAsync(registry.all());
    }

    public static Optional<World> worldByKey(String key) {
        NamespacedKey k = NamespacedKey.fromString(key);
        return k == null ? Optional.empty() : Optional.ofNullable(org.bukkit.Bukkit.getWorld(k));
    }
}
