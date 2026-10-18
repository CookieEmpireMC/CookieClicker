package dev.casperrs.cookieClicker.vfx;

import dev.casperrs.cookieClicker.vfx.shape.PopSequence;
import java.util.function.Consumer;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

/**
 * Squash/stretch on a display the caller owns. Every step is a read-modify-write of the <b>scale only</b>: it
 * re-reads the display's current target transformation and keeps its translation and both rotations, so the
 * caller's spin/bob segments are never overwritten. Scales are relative to {@code base}, the rest scale captured
 * when the first of a run of pops started (a pop during a pop restarts from the same rest scale, so fast clicking
 * never drifts the size).
 */
final class PopJob extends Job {

    private final Display display;
    private final Vector3f base;
    private final PopSequence sequence;
    private final Consumer<PopJob> onEnd;
    private int next;

    PopJob(Display display, Vector3f base, PopSequence sequence, Consumer<PopJob> onEnd) {
        this.display = display;
        this.base = new Vector3f(base);
        this.sequence = sequence;
        this.onEnd = onEnd;
    }

    Display display() {
        return display;
    }

    Vector3f base() {
        return new Vector3f(base);
    }

    @Override
    protected boolean tick(int age) {
        if (!display.isValid()) {
            return finish();
        }
        if (next < sequence.steps().size() && age == sequence.startTick(next)) {
            PopSequence.Step step = sequence.steps().get(next);
            setScale(step.sx(), step.sy(), step.sz(), step.ticks());
            next++;
        }
        return age < sequence.totalTicks() || finish();
    }

    private void setScale(double sx, double sy, double sz, int ticks) {
        Transformation cur = display.getTransformation();
        Vector3f scale = new Vector3f((float) (base.x * sx), (float) (base.y * sy), (float) (base.z * sz));
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(new Transformation(
                cur.getTranslation(), cur.getLeftRotation(), scale, cur.getRightRotation()));
    }

    /** Stops without touching the display (a new pop on the same display takes over). */
    void handOver() {
        next = Integer.MAX_VALUE;
        cancel();
    }

    @Override
    protected void cleanup() {
        // cut short by shutdown/reload: put the rest scale back so the display isn't left squashed
        if (next != Integer.MAX_VALUE && next < sequence.steps().size() && display.isValid()) {
            setScale(1, 1, 1, 1);
        }
        onEnd.accept(this);
    }
}
