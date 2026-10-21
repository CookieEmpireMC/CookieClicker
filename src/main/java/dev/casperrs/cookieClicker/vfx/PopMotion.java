package dev.casperrs.cookieClicker.vfx;

import dev.casperrs.cookieClicker.vfx.shape.PopSequence;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The caller's own motion of a display (spin, bob), sampled for a pop. A display has one interpolation, so while a
 * pop squashes the scale the same keyframe must also carry where the spin and bob will be when the step ends;
 * otherwise the pop re-sends the old target and the motion freezes. Plain maths (JOML only, no Bukkit types).
 *
 * <p>Both methods answer for {@code ticksAhead} ticks after the moment of the call (the implementation reads the
 * server tick itself) and must be cheap.
 */
public interface PopMotion {

    /** Translation of the display {@code ticksAhead} ticks from now. */
    Vector3f translation(int ticksAhead);

    /** Left rotation of the display {@code ticksAhead} ticks from now. */
    Quaternionf leftRotation(int ticksAhead);

    /**
     * One pop keyframe: translation and left rotation from {@code motion} at the step's end tick, scale from the
     * preset's step relative to {@code base}.
     */
    static Frame frame(PopMotion motion, Vector3f base, PopSequence.Step step) {
        return new Frame(
                new Vector3f(motion.translation(step.ticks())),
                new Quaternionf(motion.leftRotation(step.ticks())),
                new Vector3f((float) (base.x * step.sx()), (float) (base.y * step.sy()), (float) (base.z * step.sz())),
                step.ticks());
    }

    /** The values sent for one step: where the display is {@code ticks} ticks from now. */
    record Frame(Vector3f translation, Quaternionf leftRotation, Vector3f scale, int ticks) {
    }
}
