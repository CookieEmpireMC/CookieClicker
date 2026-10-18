package dev.casperrs.cookieClicker.vfx;

import dev.casperrs.cookieClicker.vfx.shape.DisplayTrack;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Base for effects that spawn display entities: spawns them through {@link Spawner} at the layer's delay (after
 * reserving room under the caps), sends each entity's {@link DisplayTrack} keyframes on their ticks, and removes
 * every entity when the track ends, when one of them disappears (chunk unload) or on cancel. Nothing is left behind.
 */
abstract class DisplayJob extends Job {

    private final Spawner spawner;
    private final DisplayRegistry registry;
    private final @Nullable Player viewer;
    private final @Nullable UUID viewerId;
    private final Location origin;
    private final int delay;
    private final List<Display> entities = new ArrayList<>();
    private final List<DisplayTrack> tracks = new ArrayList<>();
    private int endTick;

    DisplayJob(Spawner spawner, DisplayRegistry registry, Location origin, @Nullable Player viewer, int delay) {
        this.spawner = spawner;
        this.registry = registry;
        this.origin = origin;
        this.viewer = viewer;
        this.viewerId = viewer == null ? null : viewer.getUniqueId();
        this.delay = delay;
    }

    @Nullable UUID viewer() {
        return viewerId;
    }

    /** How many entities {@link #spawnAll} will create. */
    protected abstract int entityCount();

    /** Spawns the entities (via {@link #add}); called once, at the layer's delay. */
    protected abstract void spawnAll(Location at, @Nullable Player viewer);

    /** Per-tick extras on top of the keyframes (e.g. text opacity); {@code t} is ticks since spawn. */
    protected void extra(int t) {
    }

    protected final Spawner spawner() {
        return spawner;
    }

    protected final void add(Display entity, DisplayTrack track) {
        entities.add(entity);
        tracks.add(track);
        endTick = Math.max(endTick, track.removeTick());
        registry.register(this, entity);
    }

    @Override
    protected final boolean tick(int age) {
        int t = age - delay;
        if (t < 0) {
            return true;
        }
        if (t == 0) {
            if (viewer != null && (!viewer.isOnline() || !viewer.getWorld().equals(origin.getWorld()))) {
                return finish();
            }
            if (!registry.reserve(viewerId, entityCount())) {
                return finish();
            }
            spawnAll(origin, viewer);
            if (entities.isEmpty()) {
                return finish();
            }
        }
        for (int i = 0; i < entities.size(); i++) {
            Display e = entities.get(i);
            if (!e.isValid()) {
                return finish(); // chunk unloaded or removed by someone else: end the whole effect
            }
            for (DisplayTrack.Keyframe k : tracks.get(i).keyframes()) {
                if (k.tick() == t) {
                    apply(e, k);
                }
            }
        }
        extra(t);
        return t < endTick || finish();
    }

    /** Sends one keyframe: interpolation starts now and lasts the keyframe's duration. */
    static void apply(Display e, DisplayTrack.Keyframe k) {
        e.setInterpolationDelay(0);
        e.setInterpolationDuration(k.duration());
        e.setTransformation(transformation(k));
    }

    static Transformation transformation(DisplayTrack.Keyframe k) {
        float s = (float) k.scale();
        return new Transformation(
                new Vector3f((float) k.tx(), (float) k.ty(), (float) k.tz()),
                new AxisAngle4f((float) Math.toRadians(k.angleDeg()), (float) k.ax(), (float) k.ay(), (float) k.az()),
                new Vector3f(s, s, s),
                new AxisAngle4f());
    }

    @Override
    protected final void cleanup() {
        for (Display e : entities) {
            if (e.isValid()) {
                e.remove();
            }
        }
        registry.release(this, entities);
    }
}
