package dev.casperrs.cookieClicker.plot;

import dev.casperrs.cookieClicker.station.Station;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A bakery plot from {@code plots.yml}. Pure data (no Bukkit types). The plot and house <b>areas</b> are WorldGuard
 * regions found through the {@code bakery-plot} / {@code bakery-house} flags (value = this id); the optional
 * {@code plotRegion} / {@code houseRegion} names are only a fallback when no region carries the flag. id matches
 * {@code [a-z0-9_-]+}; a set house box replaces the {@code bakery-house} region; null shop = beside the pedestal,
 * null clicks = mirrored from the shop.
 */
public record Plot(String id, String worldKey, @Nullable String plotRegion, @Nullable String houseRegion,
                   @Nullable Viewpoint viewpoint, @Nullable Pedestal pedestal, List<Point> smoke, List<Point> steam,
                   @Nullable Point entrance, @Nullable Pedestal shop, @Nullable Box house, @Nullable Belt belt,
                   @Nullable Viewpoint camera, @Nullable Pedestal clicks) {

    public static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,64}");

    public Plot {
        smoke = List.copyOf(smoke);
        steam = List.copyOf(steam);
    }

    /** A plot without the click upgrades panel spot. */
    public Plot(String id, String worldKey, @Nullable String plotRegion, @Nullable String houseRegion,
                @Nullable Viewpoint viewpoint, @Nullable Pedestal pedestal, List<Point> smoke, List<Point> steam,
                @Nullable Point entrance, @Nullable Pedestal shop, @Nullable Box house, @Nullable Belt belt,
                @Nullable Viewpoint camera) {
        this(id, worldKey, plotRegion, houseRegion, viewpoint, pedestal, smoke, steam, entrance, shop, house, belt,
                camera, null);
    }

    /** A plot without belt, camera and click panel spot. */
    public Plot(String id, String worldKey, @Nullable String plotRegion, @Nullable String houseRegion,
                @Nullable Viewpoint viewpoint, @Nullable Pedestal pedestal, List<Point> smoke, List<Point> steam,
                @Nullable Point entrance, @Nullable Pedestal shop, @Nullable Box house) {
        this(id, worldKey, plotRegion, houseRegion, viewpoint, pedestal, smoke, steam, entrance, shop, house, null,
                null);
    }

    /** A plot with at most one smoke point, no steam and no entrance. */
    public Plot(String id, String worldKey, @Nullable String plotRegion, @Nullable String houseRegion,
                @Nullable Viewpoint viewpoint, @Nullable Pedestal pedestal, @Nullable Point chimney) {
        this(id, worldKey, plotRegion, houseRegion, viewpoint, pedestal, chimney == null ? List.of() : List.of(chimney),
                List.of(), null, null, null);
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

    /** A block cell. */
    public record Cell(int x, int y, int z) {
    }

    /**
     * A straight conveyor belt: at least one cell, from the hatch to the cell before the pedestal; height in blocks.
     */
    public record Belt(List<Cell> cells, double height) {
        public Belt {
            cells = List.copyOf(cells);
            if (cells.isEmpty()) {
                throw new IllegalArgumentException("a belt needs at least one cell");
            }
        }
    }

    /**
     * The house box (inclusive). {@code ground}: how many layers of the pasted design lie below {@code minY} (the
     * design's {@code ground-depth}, 0..2); they belong to the house for the build cinematic but not to the box.
     */
    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int ground) {

        public Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this(minX, minY, minZ, maxX, maxY, maxZ, 0);
        }

        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        public Box withGround(int layers) {
            return layers == ground ? this : new Box(minX, minY, minZ, maxX, maxY, maxZ, layers);
        }
    }

    /** The first smoke point, or null (the single chimney of the cottage). */
    public @Nullable Point chimney() {
        return smoke.isEmpty() ? null : smoke.getFirst();
    }

    public Plot withWorld(String world) {
        return world.equals(worldKey) ? this
                : new Plot(id, world, plotRegion, houseRegion, null, null, List.of(), List.of(), null, null, null, null, null, null);
    }

    public Plot withViewpoint(@Nullable Viewpoint v) {
        return new Plot(id, worldKey, plotRegion, houseRegion, v, pedestal, smoke, steam, entrance, shop, house, belt, camera, clicks);
    }

    public Plot withPedestal(@Nullable Pedestal p) {
        return new Plot(id, worldKey, plotRegion, houseRegion, viewpoint, p, smoke, steam, entrance, shop, house, belt, camera, clicks);
    }

    /** Replaces every smoke point with this one (null = no smoke). */
    public Plot withChimney(@Nullable Point c) {
        return withSmoke(c == null ? List.of() : List.of(c));
    }

    public Plot withSmoke(List<Point> points) {
        return new Plot(id, worldKey, plotRegion, houseRegion, viewpoint, pedestal, points, steam, entrance, shop, house, belt, camera, clicks);
    }

    public Plot withSteam(List<Point> points) {
        return new Plot(id, worldKey, plotRegion, houseRegion, viewpoint, pedestal, smoke, points, entrance, shop, house, belt, camera, clicks);
    }

    public Plot withHouse(@Nullable Box h) {
        return new Plot(id, worldKey, plotRegion, houseRegion, viewpoint, pedestal, smoke, steam, entrance, shop, h, belt, camera, clicks);
    }

    public Plot withEntrance(@Nullable Point e) {
        return new Plot(id, worldKey, plotRegion, houseRegion, viewpoint, pedestal, smoke, steam, e, shop, house, belt, camera, clicks);
    }

    /** Every layout point of a {@code /bakery plot setup} at once. */
    public Plot withLayout(@Nullable Viewpoint v, @Nullable Pedestal p, List<Point> smokePoints, List<Point> steamPoints,
                           @Nullable Point e, @Nullable Pedestal s, @Nullable Box h) {
        return new Plot(id, worldKey, plotRegion, houseRegion, v, p, smokePoints, steamPoints, e, s, h, belt, camera,
                clicks);
    }

    /** Every layout point of a {@code /bakery plot setup} at once, with the belt and the camera. */
    public Plot withLayout(@Nullable Viewpoint v, @Nullable Pedestal p, List<Point> smokePoints, List<Point> steamPoints,
                           @Nullable Point e, @Nullable Pedestal s, @Nullable Box h, @Nullable Belt b,
                           @Nullable Viewpoint c) {
        return new Plot(id, worldKey, plotRegion, houseRegion, v, p, smokePoints, steamPoints, e, s, h, b, c, clicks);
    }

    /** Every layout point of a {@code /bakery plot setup} at once, with the belt, camera and click panel spot. */
    public Plot withLayout(@Nullable Viewpoint v, @Nullable Pedestal p, List<Point> smokePoints, List<Point> steamPoints,
                           @Nullable Point e, @Nullable Pedestal s, @Nullable Box h, @Nullable Belt b,
                           @Nullable Viewpoint c, @Nullable Pedestal k) {
        return new Plot(id, worldKey, plotRegion, houseRegion, v, p, smokePoints, steamPoints, e, s, h, b, c, k);
    }

    /** The station the per-player cookie/panel views anchor on: id = plot id, position = pedestal. */
    public Optional<Station> station() {
        return pedestal == null ? Optional.empty()
                : Optional.of(new Station(id, worldKey, pedestal.x(), pedestal.y(), pedestal.z(), pedestal.yaw(),
                        anchor(shop), anchor(clicks)));
    }

    private static Station.@Nullable Anchor anchor(@Nullable Pedestal p) {
        return p == null ? null : new Station.Anchor(p.x(), p.y(), p.z(), p.yaw());
    }
}
