package dev.casperrs.cookieClicker.view;

import dev.casperrs.cookieClicker.config.StationVisualSettings.Offset;
import dev.casperrs.cookieClicker.station.Station;

/**
 * A station's local frame: {@code forward} is the way the placing admin was facing, {@code right} their right hand,
 * {@code up} is world up. Pure maths (Minecraft yaw: 0 = +Z/south, 90 = -X/west), so layout is testable.
 */
public record StationFrame(double x, double y, double z, float yaw) {

    public static StationFrame of(Station s) {
        return new StationFrame(s.x(), s.y(), s.z(), s.yaw());
    }

    /** World position {x, y, z} of an offset in this frame. */
    public double[] point(Offset o) {
        double rad = Math.toRadians(yaw);
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        double rx = -Math.cos(rad);
        double rz = -Math.sin(rad);
        return new double[] {x + rx * o.right() + fx * o.forward(), y + o.up(), z + rz * o.right() + fz * o.forward()};
    }

    /** A world position in this frame, relative to {@code base} (the inverse of {@link #point(Offset, Offset)}). */
    public Offset local(double wx, double wy, double wz, Offset base) {
        Offset d = direction(wx - x, wy - y, wz - z);
        return new Offset(d.right() - base.right(), d.up() - base.up(), d.forward() - base.forward());
    }

    /** A world direction in this frame (right, up, forward); lengths are kept. */
    public Offset direction(double dx, double dy, double dz) {
        double rad = Math.toRadians(yaw);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        return new Offset(-cos * dx - sin * dz, dy, -sin * dx + cos * dz);
    }

    /** World position of {@code base + rel} (both in this frame). */
    public double[] point(Offset base, Offset rel) {
        return point(new Offset(base.right() + rel.right(), base.up() + rel.up(), base.forward() + rel.forward()));
    }

    /** Yaw of an entity that faces back toward the admin's side (where players stand), plus a calibration offset. */
    public float facingViewers(float extraDegrees) {
        float v = yaw + 180 + extraDegrees;
        v = ((v % 360) + 360) % 360;
        return v >= 180 ? v - 360 : v;
    }
}
