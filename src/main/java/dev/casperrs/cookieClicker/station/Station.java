package dev.casperrs.cookieClicker.station;

/**
 * A bakery station from {@code stations.yml}. Pure data: no Bukkit types, so registry logic is testable.
 *
 * @param id       stable id ({@code station-1}, …)
 * @param worldKey world key as {@code namespace:value} (e.g. {@code minecraft:overworld}), never a world name
 * @param x        position of the station's base (block centre if {@code station.snap-to-block})
 * @param yaw      the admin's facing when placing it (snapped by {@code station.snap-yaw-degrees});
 *                 pass 2 decides how the cookie/panel are laid out relative to it
 */
public record Station(String id, String worldKey, double x, double y, double z, float yaw) {

    public double distanceSquared(String world, double ox, double oy, double oz) {
        if (!worldKey.equals(world)) {
            return Double.POSITIVE_INFINITY;
        }
        double dx = x - ox;
        double dy = y - oy;
        double dz = z - oz;
        return dx * dx + dy * dy + dz * dz;
    }
}
