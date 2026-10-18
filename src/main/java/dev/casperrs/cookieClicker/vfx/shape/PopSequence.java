package dev.casperrs.cookieClicker.vfx.shape;

import java.util.ArrayList;
import java.util.List;

/**
 * A squash-and-stretch "pop" on an existing display: a list of scale multipliers relative to the display's rest
 * scale, each interpolated over its ticks. Step {@code n} starts when step {@code n-1} has finished. The last step
 * always returns to (1, 1, 1). Plain maths, no Bukkit types.
 */
public record PopSequence(List<Step> steps) {

    /** One scale target, as multipliers of the rest scale, interpolated over {@code ticks}. */
    public record Step(double sx, double sy, double sz, int ticks) {
    }

    /** The contract default: squash 2 ticks, overshoot 2 ticks, settle 3 ticks. */
    public static PopSequence standard() {
        return new PopSequence(List.of(
                new Step(1.25, 0.75, 1.25, 2),
                new Step(0.92, 1.10, 0.92, 2),
                new Step(1, 1, 1, 3)));
    }

    public PopSequence {
        List<Step> fixed = new ArrayList<>(steps);
        Step last = fixed.isEmpty() ? null : fixed.getLast();
        if (last == null || last.sx() != 1 || last.sy() != 1 || last.sz() != 1) {
            fixed.add(new Step(1, 1, 1, 3));
        }
        steps = List.copyOf(fixed);
    }

    /** Tick (after the pop starts) at which step {@code index} is sent. */
    public int startTick(int index) {
        int t = 0;
        for (int i = 0; i < index; i++) {
            t += steps.get(i).ticks();
        }
        return t;
    }

    /** Total ticks until the display is back at rest. */
    public int totalTicks() {
        return startTick(steps.size());
    }
}
