package dev.casperrs.cookieClicker.vfx;

import dev.casperrs.cookieClicker.vfx.shape.DropTrack;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * One falling block of {@code Effects.dropBlock}: sends the {@link DropTrack} keyframes on their ticks, runs the
 * impact at {@link DropTrack#impactTick()}, and at {@link DropTrack#landTick()} removes the display and then runs
 * {@code onLanded}, in that order, in the same tick.
 *
 * <p>Callback rules (the contract the cinematic relies on):
 * <ul>
 *   <li>{@code onImpact} and {@code onLanded} each run at most once, and {@code onImpact} always runs before
 *       {@code onLanded}.</li>
 *   <li>A normal landing, or the display vanishing early (chunk unload, removed by someone else) while the viewer
 *       is still there, lands it now: impact (if not yet), remove, {@code onLanded}. The reveal never stalls.</li>
 *   <li>{@link #cancel()} ({@code Effects.cancelFor}, viewer quit, reload, shutdown) and the viewer leaving
 *       (offline or another world) remove the display and <b>never</b> run a callback that hasn't run yet.</li>
 * </ul>
 * Entity access goes through {@link Body}, so this class is plain Java and unit-tested with a fake body.
 */
final class DropJob extends Job {

    /** The display, seen through the few operations a drop needs. */
    interface Body {
        /** False once the entity is gone (removed, chunk unloaded). */
        boolean valid();

        /** Sends one keyframe: interpolation delay 0, the keyframe's duration, its transformation. */
        void send(DropTrack.Keyframe keyframe);

        /** Removes the entity (no-op when it's already gone). */
        void remove();
    }

    private final DropTrack track;
    private final Body body;
    private final BooleanSupplier viewerPresent;
    private final Runnable onImpact;
    private final Runnable onLanded;
    private final Consumer<DropJob> onEnd;
    private boolean impacted;
    private boolean landed;

    /** {@code onImpact} = engine impact effects plus the caller's callback; {@code onEnd} releases the cap slot. */
    DropJob(DropTrack track, Body body, BooleanSupplier viewerPresent, Runnable onImpact, Runnable onLanded,
            Consumer<DropJob> onEnd) {
        this.track = track;
        this.body = body;
        this.viewerPresent = viewerPresent;
        this.onImpact = onImpact;
        this.onLanded = onLanded;
        this.onEnd = onEnd;
    }

    @Override
    protected boolean tick(int age) {
        if (!viewerPresent.getAsBoolean()) {
            return finish(); // nobody to show it to: clean up, no callbacks
        }
        if (!body.valid()) {
            return land();
        }
        if (age == track.impactTick()) {
            impact();
            if (ended()) {
                return false; // the impact callback cancelled this drop
            }
        }
        if (age >= track.landTick()) {
            return land();
        }
        for (DropTrack.Keyframe k : track.keyframes()) {
            if (k.tick() == age) {
                body.send(k);
            }
        }
        return true;
    }

    private void impact() {
        if (!impacted) {
            impacted = true;
            onImpact.run();
        }
    }

    /** Impact (if it hasn't happened), remove the display and release, then {@code onLanded}. */
    private boolean land() {
        impact();
        if (ended()) {
            return false;
        }
        landed = true;
        cancel(); // cleanup: remove the display and free the cap slot before the caller resends the real block
        onLanded.run();
        return false;
    }

    /** True once {@code onLanded} ran. */
    boolean landed() {
        return landed;
    }

    /** True once {@code onImpact} ran. */
    boolean impacted() {
        return impacted;
    }

    @Override
    protected void cleanup() {
        body.remove();
        onEnd.accept(this);
    }
}
