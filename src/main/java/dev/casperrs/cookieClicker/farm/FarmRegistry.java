package dev.casperrs.cookieClicker.farm;

import dev.casperrs.cookieClicker.plot.Plot;
import dev.casperrs.cookieClicker.plot.PlotFile;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The placed farms ({@code plots.yml farms:}), stored through {@link PlotFile} next to the plots. Listeners hear every
 * change (load, setup, undo). Main thread only.
 */
public final class FarmRegistry {

    private final PlotFile file;
    private final Supplier<Collection<Plot>> plots;
    private final Map<String, Farm> farms = new LinkedHashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();

    /** {@code plots} gives the current plots, written together with the farms. */
    public FarmRegistry(PlotFile file, Supplier<Collection<Plot>> plots) {
        this.file = file;
        this.plots = plots;
    }

    public void addListener(Runnable onChange) {
        listeners.add(onChange);
    }

    /** Enable / reload, after the plots were loaded from the same file. */
    public void load(Consumer<String> problems) {
        farms.clear();
        FarmCodec.fromMap(file.farms(), problems).forEach(f -> farms.put(f.id(), f));
        changed();
    }

    public Collection<Farm> all() {
        return Collections.unmodifiableCollection(farms.values());
    }

    public Optional<Farm> get(String id) {
        return Optional.ofNullable(farms.get(id));
    }

    /** The farm whose id equals {@code plotId}, else the first one; empty when there is none. */
    public Optional<Farm> forPlot(@Nullable String plotId) {
        if (plotId != null && farms.containsKey(plotId)) {
            return Optional.of(farms.get(plotId));
        }
        return farms.values().stream().findFirst();
    }

    public void put(Farm farm) {
        farms.put(farm.id(), farm);
        save();
    }

    public void remove(String id) {
        if (farms.remove(id) != null) {
            save();
        }
    }

    private void save() {
        file.setFarms(FarmCodec.toMap(farms.values()));
        file.saveAsync(plots.get());
        changed();
    }

    private void changed() {
        listeners.forEach(Runnable::run);
    }
}
