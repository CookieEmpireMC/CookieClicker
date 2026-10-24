package dev.casperrs.cookieClicker.house.cinematic;

import dev.casperrs.cookieClicker.config.HouseSettings;
import dev.casperrs.cookieClicker.plot.Plot;
import dev.casperrs.cookieClicker.plot.PlotRegistry;
import dev.casperrs.cookieClicker.plot.RegionShape;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * The house geometry per plot ({@link RegionShape#blocks()} is expensive), cached by plot id until the plot changes
 * or a reload. Houses over {@code house.max-blocks} get no cinematic. The design's ground layers below a setup house
 * box ({@link Plot.Box#ground()}) belong to the house here, so the cinematic builds them first. Main thread only.
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
            int ground = plots.get(k).map(Plot::house).map(Plot.Box::ground).orElse(0);
            RegionShape below = ground <= 0 ? null : RegionShape.cuboid(s.id() + ":ground", s.minX(),
                    s.minY() - ground, s.minZ(), s.maxX(), s.minY() - 1, s.maxZ());
            long volume = s.boxVolume() + (below == null ? 0 : below.boxVolume());
            if (volume > max) {
                log.warning("House region of plot " + k + " is " + volume
                        + " blocks, more than house.max-blocks (" + max + "); no build cinematic there");
                return Optional.empty();
            }
            if (below == null) {
                return Optional.of(new Shape(HouseBox.of(s), s.blocks()));
            }
            long[] house = s.blocks();
            long[] groundBlocks = below.blocks();
            long[] all = Arrays.copyOf(groundBlocks, groundBlocks.length + house.length);
            System.arraycopy(house, 0, all, groundBlocks.length, house.length);
            return Optional.of(new Shape(new HouseBox(s.minX(), s.minY() - ground, s.minZ(), s.maxX(), s.maxY(),
                    s.maxZ()), all));
        })).orElse(null);
    }

    void forget(String plotId) {
        shapes.remove(plotId);
    }

    void clear() {
        shapes.clear();
    }
}
