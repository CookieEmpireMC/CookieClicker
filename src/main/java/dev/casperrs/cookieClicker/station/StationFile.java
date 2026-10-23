package dev.casperrs.cookieClicker.station;

import dev.casperrs.cookieClicker.config.YamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Migration only (the one-time {@code stations.yml} to {@code plots.yml} move in {@code PlotService}; delete with it):
 * reads {@code stations.yml} and writes it off the main thread. While a write is queued or running, {@link #load()}
 * returns the newest written stations instead of the file.
 */
public final class StationFile {

    public static final String NAME = "stations.yml";

    /** Copies the jar's default {@code stations.yml} into the data folder when it is missing. */
    @FunctionalInterface
    public interface Defaults {
        void saveIfMissing();
    }

    private final Path file;
    private final Logger log;
    private final Defaults defaults;
    private final Executor io;
    /** Writes queued or running on {@code io}. */
    private final AtomicInteger pendingWrites = new AtomicInteger();
    /** Stations of the newest {@link #saveAsync} call (main thread only). */
    private List<Station> lastSaved = List.of();

    public StationFile(JavaPlugin plugin, Executor io) {
        this(plugin.getDataFolder().toPath(), plugin.getLogger(), io, () -> {
            if (!new java.io.File(plugin.getDataFolder(), NAME).isFile() && plugin.getResource(NAME) != null) {
                plugin.saveResource(NAME, false);
            }
        });
    }

    /** Without a plugin (tests): {@code stations.yml} lives in {@code dataFolder}. */
    public StationFile(Path dataFolder, Logger log, Executor io, Defaults defaults) {
        this.file = dataFolder.resolve(NAME);
        this.log = log;
        this.io = io;
        this.defaults = defaults;
    }

    public List<Station> load() throws YamlFiles.BadYamlException {
        if (pendingWrites.get() > 0) {
            log.info(NAME + " is still being written; using the stations in memory");
            return lastSaved;
        }
        defaults.saveIfMissing();
        return YamlFiles.load(file.toFile(), NAME)
                .map(yaml -> StationCodec.fromMap(YamlFiles.toMap(yaml), log::warning))
                .orElseGet(List::of);
    }

    /** Serialises on the calling (main) thread, writes atomically on {@code io}. */
    public void saveAsync(Collection<Station> stations) {
        YamlConfiguration yaml = new YamlConfiguration();
        StationCodec.toMap(stations).forEach((key, value) -> {
            if (value instanceof java.util.Map<?, ?> map) {
                yaml.createSection(key, map);
            } else {
                yaml.set(key, value);
            }
        });
        String text = yaml.saveToString();
        Path target = file;
        lastSaved = List.copyOf(stations);
        pendingWrites.incrementAndGet();
        try {
            io.execute(() -> {
                try {
                    Files.createDirectories(target.getParent());
                    Path tmp = target.resolveSibling(NAME + ".tmp");
                    Files.writeString(tmp, text, StandardCharsets.UTF_8);
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException e) {
                    log.log(Level.SEVERE, "Could not write " + NAME, e);
                } finally {
                    pendingWrites.decrementAndGet();
                }
            });
        } catch (RuntimeException e) { // IO thread already stopped (shutdown)
            pendingWrites.decrementAndGet();
            log.log(Level.SEVERE, "Could not queue the write of " + NAME, e);
        }
    }
}
