package dev.casperrs.cookieClicker.plot;

import dev.casperrs.cookieClicker.station.Station;
import org.jspecify.annotations.Nullable;

import java.util.List;
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
 * @param smoke       where chimney smoke starts (bottom centre of each smoke cell); empty = no smoke
 * @param steam       where {@code factory_steam} puffs start (roof vents); may be empty
 * @param entrance    centre of the fence gate at ground level ({@code /bakery plot fence place}), or null
 */
public record Plot(String id, String worldKey, @Nullable String plotRegion, @Nullable String houseRegion,
                   @Nullable Viewpoint viewpoint, @Nullable Pedestal pedestal, List<Point> smoke, List<Point> steam,
                   @Nullable Point entrance) {

    public static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,64}");

    public Plot {
        smoke = List.copyOf(smoke);
        steam = List.copyOf(steam);
    }

    /** A plot with at most one smoke point (the starter-house shape), no steam and no entrance. */
    public Plot(String id, String worldKey, @Nullable String plotRegion, @Nullable String houseRegion,
                @Nullable Viewpoint viewpoint, @Nullable Pedestal pedestal, @Nullable Point chimney) {
        this(id, worldKey, plotRegion, houseRegion, viewpoint, pedestal, chimney == null ? List.of() : List.of(chimney),
                List.of(), null);
    }

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

    /** The first smoke point, or null (the single chimney of the cottage). */
    public @Nullable Point chimney() {
        return smoke.isEmpty() ? null : smoke.getFirst();
    }

    public Plot withWorld(String world) {
        return world.equals(worldKey) ? this
                : new Plot(id, world, plotRegion, houseRegion, null, null, List.of(), List.of(), null);
    }

    public Plot withViewpoint(@Nullable Viewpoint v) {
        return new Plot(id, worldKey, plotRegion, houseRegion, v, pedestal, smoke, steam, entrance);
    }

    public Plot withPedestal(@Nullable Pedestal p) {
        return new Plot(id, worldKey, plotRegion, houseRegion, viewpoint, p, smoke, steam, entrance);
    }

    /** Replaces every smoke point with this one (null = no smoke). */
    public Plot withChimney(@Nullable Point c) {
        return withSmoke(c == null ? List.of() : List.of(c));
    }

    public Plot withSmoke(List<Point> points) {
        return new Plot(id, worldKey, plotRegion, houseRegion, viewpoint, pedestal, points, steam, entrance);
    }

    public Plot withSteam(List<Point> points) {
        return new Plot(id, worldKey, plotRegion, houseRegion, viewpoint, pedestal, smoke, points, entrance);
    }

    public Plot withEntrance(@Nullable Point e) {
        return new Plot(id, worldKey, plotRegion, houseRegion, viewpoint, pedestal, smoke, steam, e);
    }

    /** The station the per-player cookie/panel views anchor on: id = plot id, position = pedestal. */
    public Optional<Station> station() {
        return pedestal == null ? Optional.empty()
                : Optional.of(new Station(id, worldKey, pedestal.x(), pedestal.y(), pedestal.z(), pedestal.yaw()));
    }
}
