package dev.casperrs.cookieClicker.vfx.shape;

import java.util.List;

/**
 * A precomputed display-entity animation: keyframes the server sends (one {@code setTransformation} each, with
 * interpolation delay 0 and the keyframe's duration), text-opacity steps, and the tick the entity is removed.
 * The client interpolates between keyframes, so nothing runs per tick. Plain maths, no Bukkit types.
 *
 * @param spawn       the transformation the entity spawns with (tick 0)
 * @param keyframes   targets sent at {@link Keyframe#tick()}, in tick order
 * @param opacity     text opacity steps (TextDisplay only; empty for items), in tick order
 * @param removeTick  the tick the entity is removed (after the last keyframe has finished)
 */
public record DisplayTrack(Keyframe spawn, List<Keyframe> keyframes, List<OpacityStep> opacity, int removeTick) {

    /**
     * One transformation target.
     *
     * @param tick     when to send it (ticks after spawn)
     * @param tx       translation, blocks
     * @param scale    uniform scale
     * @param angleDeg left rotation around (ax, ay, az), degrees; keep steps between keyframes under 120
     * @param duration interpolation duration in ticks (0 for the spawn state)
     */
    public record Keyframe(int tick, double tx, double ty, double tz, double scale,
                           double angleDeg, double ax, double ay, double az, int duration) {
    }

    /** Set text opacity (27..255; lower values are discarded by the client) at a tick. */
    public record OpacityStep(int tick, int opacity) {
    }

    /**
     * What the client shows at {@code tick}: {x, y, z, scale, angleDeg}. Emulates vanilla interpolation: a new
     * target starts from the currently rendered state and moves linearly over its duration.
     */
    public double[] renderedAt(int tick) {
        double[] cur = {spawn.tx(), spawn.ty(), spawn.tz(), spawn.scale(), spawn.angleDeg()};
        double[] from = cur.clone();
        Keyframe active = null;
        for (int t = 0; t <= tick; t++) {
            // first where the running interpolation is at tick t, then any new target starts from there
            if (active != null) {
                double p = active.duration() <= 0 ? 1 : Easing.clamp01((double) (t - active.tick()) / active.duration());
                double[] to = {active.tx(), active.ty(), active.tz(), active.scale(), active.angleDeg()};
                for (int i = 0; i < cur.length; i++) {
                    cur[i] = Easing.lerp(from[i], to[i], p);
                }
            }
            for (Keyframe k : keyframes) {
                if (k.tick() == t) {
                    from = cur.clone();
                    active = k;
                }
            }
        }
        return cur;
    }
}
