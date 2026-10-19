package dev.casperrs.cookieClicker.plot;

import org.jspecify.annotations.Nullable;

/**
 * Where a player (block) is: the plot id of the {@code bakery-plot} region and of the {@code bakery-house} region
 * there, or null. Both are flag values, i.e. plot ids.
 */
public record Presence(@Nullable String plot, @Nullable String house) {

    public static final Presence NONE = new Presence(null, null);

    public boolean inPlot(String plotId) {
        return plotId.equals(plot);
    }

    public boolean inHouse(String plotId) {
        return plotId.equals(house);
    }
}
