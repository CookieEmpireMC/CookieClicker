package dev.casperrs.cookieClicker.vfx;

import dev.casperrs.cookieClicker.vfx.config.LayerSpec;
import dev.casperrs.cookieClicker.vfx.shape.ShapePlan;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Emits one particle layer's precomputed {@link ShapePlan}, tick by tick. Personal effects go only to the viewer
 * ({@link Player#spawnParticle}); with no viewer they go to everyone nearby ({@link World#spawnParticle}).
 * Allocates nothing per point.
 */
final class ParticleJob extends Job {

    private final LayerSpec.Particles layer;
    private final @Nullable Object data;
    private final @Nullable Player viewer;
    private final World world;
    private final double bx;
    private final double by;
    private final double bz;
    private final double cos;
    private final double sin;

    ParticleJob(LayerSpec.Particles layer, @Nullable Object data, Location loc, @Nullable Player viewer) {
        this.layer = layer;
        this.data = data;
        this.viewer = viewer;
        this.world = loc.getWorld();
        this.bx = loc.getX() + layer.ox();
        this.by = loc.getY() + layer.oy();
        this.bz = loc.getZ() + layer.oz();
        double angle = 0;
        if (layer.plan().facing() && viewer != null) {
            Location eye = viewer.getEyeLocation();
            angle = Math.atan2(eye.getX() - bx, eye.getZ() - bz);
        }
        // turns the shape's +z face toward the viewer: x' = x cos + z sin, z' = -x sin + z cos
        this.cos = Math.cos(angle);
        this.sin = Math.sin(angle);
    }

    @Override
    protected boolean tick(int age) {
        int t = age - layer.delayTicks();
        ShapePlan plan = layer.plan();
        if (t < 0) {
            return true;
        }
        if (t >= plan.ticks() || (viewer != null && (!viewer.isOnline() || !viewer.getWorld().equals(world)))) {
            return finish();
        }
        double[] pts = plan.at(t);
        Particle particle = layer.particle();
        boolean directional = layer.directional();
        double off = layer.offset();
        for (int i = 0; i < pts.length; i += ShapePlan.STRIDE) {
            double x = pts[i] * cos + pts[i + 2] * sin;
            double z = -pts[i] * sin + pts[i + 2] * cos;
            double px = bx + x;
            double py = by + pts[i + 1];
            double pz = bz + z;
            if (directional) {
                // count 0: the offsets become the direction, speed its length
                double dx = pts[i + 3] * cos + pts[i + 5] * sin;
                double dz = -pts[i + 3] * sin + pts[i + 5] * cos;
                send(particle, px, py, pz, 0, dx, pts[i + 4], dz);
            } else {
                send(particle, px, py, pz, layer.count(), off, off, off);
            }
        }
        return t + 1 < plan.ticks() || finish();
    }

    private void send(Particle particle, double x, double y, double z, int count, double ox, double oy, double oz) {
        if (viewer != null) {
            viewer.spawnParticle(particle, x, y, z, count, ox, oy, oz, layer.speed(), data, layer.force());
        } else {
            world.spawnParticle(particle, x, y, z, count, ox, oy, oz, layer.speed(), data, layer.force());
        }
    }

    @Override
    protected void cleanup() {
        // particles are client-side; nothing to remove
    }
}
