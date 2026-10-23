package dev.casperrs.cookieClicker.station;

import org.jspecify.annotations.Nullable;

/**
 * A bakery station from {@code stations.yml}. Pure data: no Bukkit types, so registry logic is testable. worldKey is
 * {@code namespace:value}, never a world name. With a plot layout shop/clicks are the panel spots (cookie on the
 * station); null shop = default offsets, null clicks = the shop spot mirrored across the station.
 */
public record Station(String id, String worldKey, double x, double y, double z, float yaw, @Nullable Anchor shop,
                      @Nullable Anchor clicks) {

    /** A position with a facing (same yaw meaning as {@link Station#yaw}). */
    public record Anchor(double x, double y, double z, float yaw) {
    }

    public Station(String id, String worldKey, double x, double y, double z, float yaw) {
        this(id, worldKey, x, y, z, yaw, null, null);
    }

    public Station(String id, String worldKey, double x, double y, double z, float yaw, @Nullable Anchor shop) {
        this(id, worldKey, x, y, z, yaw, shop, null);
    }

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
