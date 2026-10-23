package dev.casperrs.cookieClicker.house.setup;

/**
 * At most one operation at a time, cancellable (main thread only). {@link #reset()} makes every token handed out so
 * far stale, and {@link #end(long)} only frees the gate for the token that owns it, so a late callback can neither
 * act nor end a newer operation.
 */
final class OperationGate {

    /** Returned by {@link #begin()} when another operation is running. Never a valid token. */
    static final long REFUSED = 0;

    private long generation;
    private long running = REFUSED;

    long begin() {
        if (running != REFUSED) {
            return REFUSED;
        }
        running = ++generation;
        return running;
    }

    boolean valid(long token) {
        return token != REFUSED && token == running;
    }

    void end(long token) {
        if (valid(token)) {
            running = REFUSED;
        }
    }

    void reset() {
        generation++;
        running = REFUSED;
    }

    boolean busy() {
        return running != REFUSED;
    }
}
