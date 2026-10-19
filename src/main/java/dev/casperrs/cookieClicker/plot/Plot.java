package dev.casperrs.cookieClicker.plot;

import dev.casperrs.cookieClicker.station.Station;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A bakery plot from {@code plots.yml}. Pure data (no Bukkit types). The plot and house <b>areas</b> are WorldGuard
 * regions found through the {@code bakery-plot} / {@code bakery-house} flags (value = this id); the optional
 * {@code plotRegion} / {@code houseRegion} names are only a fallback when no region carries the flag.
 *
 * @param id          plot id, {@code [a-z0-9_-]+} (also the flag value and the {@code house_seen.plot_id})
 * @param worldKey    world key, e.g. {@code minecraft:overworld}
 * @param plotRegion  fallback region id of the plot area, or null
 * @param houseRegion fallback region id of the house area, or null
 * @param viewpoint   where the first-visit cinematic is watched from, or null
 * @param pedestal    the player's cookie/panel anchor (offsets in {@code station.*} are relative to it), or null
 * @param chimney     where the chimney smoke starts (bottom centre of the smoke cell), or null
 */
public record Plot(String id, String worldKey, @Nullable String plotRegion, @Nullable String houseRegion,
                   @Nullable Viewpoint viewpoint, @Nullable Pedestal pedestal, @Nullable Point chimney) {

    public static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,64}");

    public static boolean validId(@Nullable String id) {
        return id != null && ID.matcher(id).matches();
    }

    public static Plot empty(String id, String worldKey) {
        return new Plot(id, worldKey, null, null, null, null, null);
    }

    public record Point(double x, double y, double z) {
    }

    public record Viewpoint(double x, double y, double z, float yaw, float pitch) {
    }

    public record Pedestal(double x, double y, double z, float yaw) {
    }

    public Plot withWorld(String world) {
        return world.equals(worldKey) ? this : new Plot(id, world, plotRegion, houseRegion, null, null, null);
    }

    public Plot withViewpoint(@Nullable Viewpoint v) {
        return new Plot(id, worldKey, plotRegion, houseRegion, v, pedestal, chimney);
    }

    public Plot withPedestal(@Nullable Pedestal p) {
        return new Plot(id, worldKey, plotRegion, houseRegion, viewpoint, p, chimney);
    }

    public Plot withChimney(@Nullable Point c) {
        return new Plot(id, worldKey, plotRegion, houseRegion, viewpoint, pedestal, c);
    }

    /** The station the per-player cookie/panel views anchor on: id = plot id, position = pedestal. */
    public Optional<Station> station() {
        return pedestal == null ? Optional.empty()
                : Optional.of(new Station(id, worldKey, pedestal.x(), pedestal.y(), pedestal.z(), pedestal.yaw()));
    }
}
