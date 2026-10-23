package dev.casperrs.cookieClicker.house.cinematic;

import dev.casperrs.cookieClicker.config.HouseSettings;
import dev.casperrs.cookieClicker.plot.PlotRegistry;
import dev.casperrs.cookieClicker.plot.RegionShape;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * The house geometry per plot ({@link RegionShape#blocks()} is expensive), cached by plot id until the plot changes
 * or a reload. Houses over {@code house.max-blocks} get no cinematic. Main thread only.
 */
final class HouseShapes {

    record Shape(HouseBox box, long[] blocks) {
    }

    private final Logger log;
    private final PlotRegistry plots;
    private final Supplier<HouseSettings> settings;
    private final Map<String, Optional<Shape>> shapes = new HashMap<>();

    HouseShapes(Logger log, PlotRegistry plots, Supplier<HouseSettings> settings) {
        this.log = log;
        this.plots = plots;
        this.settings = settings;
    }

    @Nullable Shape get(String plotId) {
        return shapes.computeIfAbsent(plotId, k -> plots.houseShape(k).flatMap(s -> {
            long max = settings.get().maxBlocks();
            if (s.boxVolume() > max) {
                log.warning("House region of plot " + k + " is " + s.boxVolume()
                        + " blocks, more than house.max-blocks (" + max + "); no build cinematic there");
                return Optional.empty();
            }
            return Optional.of(new Shape(HouseBox.of(s), s.blocks()));
        })).orElse(null);
    }

    void forget(String plotId) {
        shapes.remove(plotId);
    }

    void clear() {
        shapes.clear();
    }
}
