package dev.casperrs.cookieClicker.plot;

import dev.casperrs.cookieClicker.station.Station;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * One-time migration of {@code stations.yml} to {@code plots.yml}, as a pure plan:
 * every station that stands inside a {@code bakery-plot} region becomes that plot's pedestal (the first one wins
 * when a plot has several); every other station is a leftover that is logged and stops working (the whole old file
 * is kept as {@code stations.yml.bak}).
 */
public final class StationMigration {

    /** Plots get their station as pedestal; leftovers are outside every plot or a plot's second station. */
    public record Plan(List<Plot> plots, List<Station> leftovers) {
    }

    private StationMigration() {
    }

    public static Plan plan(Collection<Station> stations, Function<Station, Optional<String>> plotAt) {
        Map<String, Plot> plots = new LinkedHashMap<>();
        List<Station> leftovers = new ArrayList<>();
        for (Station s : stations) {
            Optional<String> plotId = plotAt.apply(s).filter(Plot::validId);
            if (plotId.isEmpty() || plots.containsKey(plotId.get())) {
                leftovers.add(s);
                continue;
            }
            plots.put(plotId.get(), Plot.empty(plotId.get(), s.worldKey())
                    .withPedestal(new Plot.Pedestal(s.x(), s.y(), s.z(), s.yaw())));
        }
        return new Plan(List.copyOf(plots.values()), List.copyOf(leftovers));
    }
}
