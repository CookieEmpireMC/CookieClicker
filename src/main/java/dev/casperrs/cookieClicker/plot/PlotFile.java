package dev.casperrs.cookieClicker.plot;

import dev.casperrs.cookieClicker.config.YamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads {@code plots.yml} (enable/reload) and writes it on the IO thread after changes. While a write is queued or
 * running, {@link #load} returns the plots of the newest write, so a {@code /bakery plot …} directly followed by
 * {@code /bakery reload} never reads the old file.
 */
public final class PlotFile {

    public static final String NAME = "plots.yml";

    private static final String HEADER = """
            Bakery plots. /bakery plot setup (standing anywhere in the plot) fills in every point from the house
            design's layout; edit by hand, then /bakery reload. The plot and house AREAS are WorldGuard regions:
              /rg flag <plot region> bakery-plot <id>    and    /rg flag <house region> bakery-house <id>
            plot-region / house-region below are only a fallback for regions without those flags.
            world is the world KEY (e.g. minecraft:overworld), not its folder name.""";

    private final Path file;
    private final Logger log;
    private final Executor io;
    private final AtomicInteger pendingWrites = new AtomicInteger();
    private List<Plot> lastSaved = List.of();

    public PlotFile(Path dataFolder, Logger log, Executor io) {
        this.file = dataFolder.resolve(NAME);
        this.log = log;
        this.io = io;
    }

    /** Whether {@code plots.yml} exists (or a write of it is pending). False = first start: migrate stations. */
    public boolean exists() {
        return pendingWrites.get() > 0 || Files.isRegularFile(file);
    }

    public List<Plot> load(Consumer<String> problems) throws YamlFiles.BadYamlException {
        if (pendingWrites.get() > 0) {
            log.info(NAME + " is still being written; using the plots in memory");
            return lastSaved;
        }
        return YamlFiles.load(file.toFile(), NAME)
                .map(yaml -> PlotCodec.fromMap(YamlFiles.toMap(yaml), problems))
                .orElseGet(List::of);
    }

    public void saveAsync(Collection<Plot> plots) {
        saveAsync(plots, null);
    }

    /**
     * Serialises on the calling (main) thread, writes atomically on {@code io}.
     *
     * @param afterWrite runs on {@code io} only when the write succeeded (e.g. backing up {@code stations.yml})
     */
    public void saveAsync(Collection<Plot> plots, @Nullable Runnable afterWrite) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(HEADER.split("\n")));
        for (Map.Entry<String, Object> e : PlotCodec.toMap(plots).entrySet()) {
            if (e.getValue() instanceof Map<?, ?> map) {
                yaml.createSection(e.getKey(), map);
            } else {
                yaml.set(e.getKey(), e.getValue());
            }
        }
        String text = yaml.saveToString();
        Path target = file;
        lastSaved = List.copyOf(plots);
        pendingWrites.incrementAndGet();
        try {
            io.execute(() -> {
                try {
                    Files.createDirectories(target.getParent());
                    Path tmp = target.resolveSibling(NAME + ".tmp");
                    Files.writeString(tmp, text, StandardCharsets.UTF_8);
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    if (afterWrite != null) {
                        afterWrite.run();
                    }
                } catch (IOException | RuntimeException e) {
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
