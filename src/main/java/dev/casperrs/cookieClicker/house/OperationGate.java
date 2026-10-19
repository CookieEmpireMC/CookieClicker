package dev.casperrs.cookieClicker.house;

/**
 * At most one house operation at a time, and a way to cancel it (pure, main thread only). {@link #begin()} hands
 * out a token; every later step of that operation checks {@link #valid(long)} first and stops if the token is stale.
 * {@link #reset()} ({@code /bakery reload}, disable) makes every outstanding token stale at once, so a callback that
 * arrives late can neither change the world nor end a newer operation: {@link #end(long)} only frees the gate for the
 * token that owns it.
 */
public final class OperationGate {

    /** Returned by {@link #begin()} when another operation is running. Never a valid token. */
    public static final long REFUSED = 0;

    private long generation;
    /** Token of the running operation, or {@link #REFUSED} when idle. */
    private long running = REFUSED;

    /** Starts an operation: its token, or {@link #REFUSED} while another one runs. */
    public long begin() {
        if (running != REFUSED) {
            return REFUSED;
        }
        running = ++generation;
        return running;
    }

    /** Whether {@code token} is the running operation (false after it ended or after a {@link #reset()}). */
    public boolean valid(long token) {
        return token != REFUSED && token == running;
    }

    /** Ends the operation of {@code token}; a stale token changes nothing. */
    public void end(long token) {
        if (valid(token)) {
            running = REFUSED;
        }
    }

    /** Cancels whatever runs: every token handed out so far becomes stale and the gate is free. */
    public void reset() {
        generation++;
        running = REFUSED;
    }

    public boolean busy() {
        return running != REFUSED;
    }
}
