package dev.casperrs.cookieClicker.vfx;

/**
 * A running looped effect from {@link Effects#loop}. Cancel it when the effect should stop (the viewer left the
 * plot, the cinematic restarted). The engine also stops it on {@link Effects#cancelFor}, viewer quit or world
 * change, reload and shutdown; after that {@link #isActive()} is false and a new loop must be started.
 */
public interface EffectHandle {

    /** A handle that was never running (unknown ID, cap hit, viewer gone, engine not loaded). */
    EffectHandle NONE = new EffectHandle() {
        @Override
        public void cancel() {
        }

        @Override
        public boolean isActive() {
            return false;
        }
    };

    /** Stops the loop. Particles already in the air finish on the client. Idempotent, main thread only. */
    void cancel();

    /** True while the loop is still playing. */
    boolean isActive();
}
