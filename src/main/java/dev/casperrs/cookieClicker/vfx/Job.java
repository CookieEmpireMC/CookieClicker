package dev.casperrs.cookieClicker.vfx;

/**
 * One running multi-tick effect piece. {@link #step()} is called once when the effect plays (age 0) and then once
 * per server tick by the {@link EffectTicker} until it returns false. A job cleans up after itself when it ends,
 * and {@link #cancel()} cleans up immediately (shutdown, reload, cap eviction, chunk unload).
 */
abstract class Job {

    private int age;
    private boolean cancelled;

    /** Runs the current tick; false when the job is done (and has cleaned up). */
    final boolean step() {
        if (cancelled) {
            return false;
        }
        boolean alive = tick(age);
        age++;
        return alive;
    }

    /** @param age ticks since the effect played (0 = the play call itself) */
    protected abstract boolean tick(int age);

    /** Removes everything this job spawned. Called at most once per job. */
    protected abstract void cleanup();

    final void cancel() {
        if (!cancelled) {
            cancelled = true;
            cleanup();
        }
    }

    /** Ends the job normally from inside {@link #tick}: cleans up and returns false for convenience. */
    protected final boolean finish() {
        cancel();
        return false;
    }
}
