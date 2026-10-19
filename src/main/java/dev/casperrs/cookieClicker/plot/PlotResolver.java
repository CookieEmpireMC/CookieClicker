package dev.casperrs.cookieClicker.plot;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Finds a plot's regions (pure): the region whose {@code bakery-plot} flag equals the plot id, and the region whose
 * {@code bakery-house} flag equals it. Flags are the source of truth; {@code plot-region} / {@code house-region} in
 * {@code plots.yml} are used only when no region carries the flag. Two regions with the same flag value are an error
 * (logged, naming both) and leave that area unresolved.
 */
public final class PlotResolver {

    /** Region ids of one plot (null = not found). Ids only: region objects are re-read on every use. */
    public record Resolved(@Nullable String plotRegion, @Nullable String houseRegion) {
        public static final Resolved NONE = new Resolved(null, null);
    }

    private PlotResolver() {
    }

    public static Resolved resolve(Plot plot, Collection<PlotRegions.RegionInfo> regions, Consumer<String> problems) {
        List<String> plotHits = new ArrayList<>();
        List<String> houseHits = new ArrayList<>();
        boolean plotFallback = false;
        boolean houseFallback = false;
        for (PlotRegions.RegionInfo r : regions) {
            if (plot.id().equals(r.plotFlag())) {
                plotHits.add(r.id());
            }
            if (plot.id().equals(r.houseFlag())) {
                houseHits.add(r.id());
            }
            plotFallback |= r.id().equals(lower(plot.plotRegion()));
            houseFallback |= r.id().equals(lower(plot.houseRegion()));
        }
        String plotRegion = pick(plot, "bakery-plot", plotHits, plotFallback ? lower(plot.plotRegion()) : null,
                plot.plotRegion(), problems);
        String houseRegion = pick(plot, "bakery-house", houseHits, houseFallback ? lower(plot.houseRegion()) : null,
                plot.houseRegion(), problems);
        if (plotRegion != null && plotRegion.equals(houseRegion)) {
            problems.accept("plot '" + plot.id() + "': region '" + plotRegion
                    + "' is both the plot and the house; make a separate house region");
            houseRegion = null;
        }
        return new Resolved(plotRegion, houseRegion);
    }

    private static @Nullable String pick(Plot plot, String flag, List<String> hits, @Nullable String fallback,
                                         @Nullable String configured, Consumer<String> problems) {
        if (hits.size() == 1) {
            return hits.getFirst();
        }
        if (hits.size() > 1) {
            problems.accept("plot '" + plot.id() + "': regions " + String.join(", ", hits) + " all have flag "
                    + flag + " " + plot.id() + "; keep it on one region only (this area is ignored until then)");
            return null;
        }
        if (fallback != null) {
            return fallback;
        }
        if (configured != null) {
            problems.accept("plot '" + plot.id() + "': region '" + configured + "' from plots.yml does not exist in "
                    + plot.worldKey());
        }
        return null;
    }

    private static @Nullable String lower(@Nullable String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }
}
