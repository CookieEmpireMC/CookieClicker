package dev.casperrs.cookieClicker.vfx;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Replays an effect every {@code every} ticks (the first play is immediate) until cancelled or until
 * {@code stillValid} turns false (viewer quit or changed world). It is its own {@link EffectHandle}. Plain Java.
 */
final class LoopJob extends Job implements EffectHandle {

    private final int every;
    private final BooleanSupplier stillValid;
    private final Runnable playOnce;
    private final Consumer<LoopJob> onEnd;

    /**
     * @param every      ticks between plays (at least 1)
     * @param stillValid checked every tick; false ends the loop
     * @param playOnce   plays the preset once (plus any per-cycle callback)
     * @param onEnd      called once when the loop ends for any reason
     */
    LoopJob(int every, BooleanSupplier stillValid, Runnable playOnce, Consumer<LoopJob> onEnd) {
        this.every = Math.max(1, every);
        this.stillValid = stillValid;
        this.playOnce = playOnce;
        this.onEnd = onEnd;
    }

    @Override
    protected boolean tick(int age) {
        if (!stillValid.getAsBoolean()) {
            return finish();
        }
        if (age % every == 0) {
            playOnce.run();
        }
        return !ended();
    }

    @Override
    protected void cleanup() {
        onEnd.accept(this);
    }

    @Override
    public boolean isActive() {
        return !ended();
    }
}
