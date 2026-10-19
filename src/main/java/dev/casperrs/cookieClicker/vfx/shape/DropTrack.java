package dev.casperrs.cookieClicker.vfx.shape;

import java.util.List;

/**
 * The precomputed animation of one falling block ({@code Effects.dropBlock}): a {@code BlockDisplay} anchored at the
 * target block's corner spawns {@code dropHeight} above it, then three client-interpolated segments run:
 * <ol>
 *   <li><b>fall</b>: at {@code startTick} (at least 1 tick after the spawn), down to the target over
 *       {@code fallTicks}; the block hits the ground at {@link #impactTick()}</li>
 *   <li><b>bounce</b>: up {@code bounceHeight} while squashing to {@code squash} over {@code bounceTicks}; the
 *       squash keeps the bottom face centred on the block, so it widens and flattens in place</li>
 *   <li><b>settle</b>: back to the target at scale 1 over {@code settleTicks}; it is there at {@link #landTick()},
 *       when the display is removed and the real block takes its place</li>
 * </ol>
 * One {@code setTransformation} per keyframe tick, never two in the same tick. Plain maths, no Bukkit types.
 *
 * @param spawn     the transformation the display spawns with (tick 0, duration 0)
 * @param keyframes fall, bounce and settle, in tick order
 */
public record DropTrack(Keyframe spawn, List<Keyframe> keyframes) {

    /**
     * One transformation target, relative to the block corner: translation in blocks and a per-axis scale (no
     * rotation). Sent at {@code tick} with interpolation delay 0 and {@code duration} ticks.
     */
    public record Keyframe(int tick, double tx, double ty, double tz, double sx, double sy, double sz, int duration) {
    }

    /**
     * Config values of a drop, already range-checked by the loader.
     *
     * @param startTick    first keyframe tick (≥ 1: the client must have the spawn state first)
     * @param dropHeight   blocks above the target the display starts
     * @param fallTicks    fall duration
     * @param bounceHeight how high it pops back up after the impact (0 = squash only)
     * @param bounceTicks  bounce duration
     * @param squashX      width multiplier at the top of the bounce (x)
     * @param squashY      height multiplier at the top of the bounce
     * @param squashZ      width multiplier at the top of the bounce (z)
     * @param settleTicks  settle duration
     */
    public record Params(int startTick, double dropHeight, int fallTicks, double bounceHeight, int bounceTicks,
                         double squashX, double squashY, double squashZ, int settleTicks) {

        /** The house cinematic defaults (also the fallback when the preset is missing). */
        public static final Params DEFAULTS = new Params(2, 4.0, 6, 0.25, 2, 1.08, 0.85, 1.08, 2);

        public Params {
            if (startTick < 1 || fallTicks < 1 || bounceTicks < 1 || settleTicks < 1) {
                throw new IllegalArgumentException("start tick and every segment need at least 1 tick");
            }
            if (!(squashX > 0 && squashY > 0 && squashZ > 0)) {
                throw new IllegalArgumentException("squash must be positive");
            }
        }
    }

    /** Builds the three segments from config values. */
    public static DropTrack of(Params p) {
        Keyframe spawn = new Keyframe(0, 0, p.dropHeight(), 0, 1, 1, 1, 0);
        int impact = p.startTick() + p.fallTicks();
        int peak = impact + p.bounceTicks();
        Keyframe fall = new Keyframe(p.startTick(), 0, 0, 0, 1, 1, 1, p.fallTicks());
        // scale grows from the corner, so shift by half the size change to keep the block centred on x/z
        Keyframe bounce = new Keyframe(impact, (1 - p.squashX()) / 2, p.bounceHeight(), (1 - p.squashZ()) / 2,
                p.squashX(), p.squashY(), p.squashZ(), p.bounceTicks());
        Keyframe settle = new Keyframe(peak, 0, 0, 0, 1, 1, 1, p.settleTicks());
        return new DropTrack(spawn, List.of(fall, bounce, settle));
    }

    /** Tick (after the spawn) at which the falling block first touches the target: the end of the fall. */
    public int impactTick() {
        Keyframe fall = keyframes.getFirst();
        return fall.tick() + fall.duration();
    }

    /** Tick at which the drop has settled: the display is removed and {@code onLanded} runs. */
    public int landTick() {
        Keyframe last = keyframes.getLast();
        return last.tick() + last.duration();
    }

    /**
     * What the client shows at {@code tick}: {@code {tx, ty, tz, sx, sy, sz}}. Emulates vanilla interpolation: a new
     * target starts from the currently rendered state and moves linearly over its duration.
     */
    public double[] renderedAt(int tick) {
        double[] cur = values(spawn);
        double[] from = cur.clone();
        Keyframe active = null;
        for (int t = 0; t <= tick; t++) {
            if (active != null) {
                double p = Easing.clamp01((double) (t - active.tick()) / active.duration());
                double[] to = values(active);
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

    private static double[] values(Keyframe k) {
        return new double[] {k.tx(), k.ty(), k.tz(), k.sx(), k.sy(), k.sz()};
    }
}
