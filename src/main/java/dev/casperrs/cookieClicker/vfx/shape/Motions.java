package dev.casperrs.cookieClicker.vfx.shape;

import dev.casperrs.cookieClicker.vfx.shape.DisplayTrack.Keyframe;
import dev.casperrs.cookieClicker.vfx.shape.DisplayTrack.OpacityStep;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the {@link DisplayTrack}s for floating text and item crumbs. Piecewise-linear keyframes approximate an
 * ease-out (most of the motion early, a hang at the end), because the client only interpolates linearly.
 */
public final class Motions {

    /** Share of the rise covered by the pop-in keyframe. */
    static final double POP_IN_RISE = 0.35;
    /** Share of the rise covered when the fade starts. */
    static final double HOLD_RISE = 0.85;
    /** The smallest scale we send (a zero scale matrix is degenerate). */
    static final double MIN_SCALE = 0.01;

    private Motions() {
    }

    /**
     * Floating text: pops in (small, overshoot, settle), rises with ease-out, then shrinks and fades at the top.
     *
     * @param rise          blocks risen over the whole life
     * @param duration      ticks until removal
     * @param scale         text scale at rest
     * @param popInScale    spawn scale as a share of {@code scale}
     * @param overshoot     peak scale of the pop-in as a share of {@code scale} (1 = none)
     * @param popInTicks    length of the pop-in (0 = no pop-in, starts at {@code scale})
     * @param fadeTicks     length of the shrink/fade at the end
     * @param startTick     first keyframe tick (the client needs the spawn state before it can interpolate)
     */
    public static DisplayTrack floatingText(double rise, int duration, double scale, double popInScale,
                                            double overshoot, int popInTicks, int fadeTicks, int startTick) {
        List<Keyframe> keys = new ArrayList<>();
        int fadeStart = duration - fadeTicks;
        double spawnScale = popInTicks > 0 ? scale * popInScale : scale;
        int holdTick = startTick;
        if (popInTicks > 0) {
            keys.add(key(startTick, rise * POP_IN_RISE, scale * overshoot, 0, popInTicks));
            holdTick = startTick + popInTicks;
        }
        keys.add(key(holdTick, rise * HOLD_RISE, scale, 0, fadeStart - holdTick));
        keys.add(key(fadeStart, rise, scale * MIN_SCALE, 0, fadeTicks));
        List<OpacityStep> fade = new ArrayList<>();
        if (fadeTicks >= 3) {
            fade.add(new OpacityStep(fadeStart + fadeTicks / 3, 170));
            fade.add(new OpacityStep(fadeStart + 2 * fadeTicks / 3, 90));
        }
        return new DisplayTrack(key(0, 0, spawnScale, 0, 0), List.copyOf(keys), List.copyOf(fade), duration);
    }

    /** The shortest life a floating text needs for these settings. */
    public static int minTextDuration(int popInTicks, int fadeTicks, int startTick) {
        return startTick + popInTicks + 1 + fadeTicks;
    }

    /**
     * Crumb {@code i} of {@code n}: flies out along an upward cone, tumbles, then drops and shrinks away.
     *
     * @param distance  horizontal-ish distance travelled, blocks
     * @param spreadDeg cone half-angle around straight up
     * @param lift      extra height at the top of the arc, blocks
     * @param drop      how far below the origin height it ends, blocks
     * @param duration  ticks until removal
     * @param scale     item scale
     * @param spinDeg   total tumble in degrees (at most 200, sent in two steps)
     * @param startTick first keyframe tick
     */
    public static DisplayTrack crumb(int i, int n, double distance, double spreadDeg, double lift, double drop,
                                     int duration, double scale, double spinDeg, int startTick) {
        double[] d = Shapes.coneDir(i, n, spreadDeg);
        // tumble axis: perpendicular to the flight direction and up (falls back to x when flying straight up)
        double ax = d[2];
        double az = -d[0];
        double len = Math.sqrt(ax * ax + az * az);
        if (len < 1e-6) {
            ax = 1;
            az = 0;
            len = 1;
        }
        ax /= len;
        az /= len;
        int flight = Math.max(2, (int) Math.round((duration - startTick) * 0.35));
        int fallTick = startTick + flight;
        int fall = Math.max(1, duration - fallTick);
        List<Keyframe> keys = List.of(
                new Keyframe(startTick, d[0] * distance * 0.6, d[1] * distance * 0.6 + lift, d[2] * distance * 0.6,
                        scale, spinDeg * 0.45, ax, 0, az, flight),
                new Keyframe(fallTick, d[0] * distance, d[1] * distance * 0.5 - drop, d[2] * distance,
                        scale * MIN_SCALE, spinDeg, ax, 0, az, fall));
        return new DisplayTrack(new Keyframe(0, 0, 0, 0, scale, 0, ax, 0, az, 0), keys, List.of(),
                fallTick + fall);
    }

    private static Keyframe key(int tick, double y, double scale, double angle, int duration) {
        return new Keyframe(tick, 0, y, 0, scale, angle, 0, 1, 0, duration);
    }
}
