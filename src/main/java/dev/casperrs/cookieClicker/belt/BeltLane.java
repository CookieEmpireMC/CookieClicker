package dev.casperrs.cookieClicker.belt;

/**
 * One player's cookie schedule on one belt: when the next cookie may come out, how long it rides, and the cap on
 * cookies on the belt at once. Pure Java (the entities live in {@link ConveyorBelts}). Main thread only.
 *
 * <p>The gap is measured from the last cookie with the <b>current</b> CPS, so a purchase shortens it right away. A
 * cookie never overtakes the one before it: it arrives at least one tick after it.
 */
public final class BeltLane {

    private long lastSpawn = Long.MIN_VALUE / 2;
    private long lastArrive = Long.MIN_VALUE / 2;
    private int active;

    /**
     * Whether a cookie comes out at tick {@code now}, and if so how many ticks it rides (else -1). Counts it as on the
     * belt until {@link #arrived()}.
     */
    public int trySpawn(long now, double cps, BeltCurve curve, BeltPath path, int maxCookies) {
        if (!curve.running(cps) || active >= maxCookies || now - lastSpawn < curve.interval(cps)) {
            return -1;
        }
        long arrive = Math.max(now + path.rideTicks(curve.speed(cps)), lastArrive + 1);
        lastSpawn = now;
        lastArrive = arrive;
        active++;
        return (int) (arrive - now);
    }

    /** A cookie left the belt (arrived, or was removed). */
    public void arrived() {
        if (active > 0) {
            active--;
        }
    }

    /** Cookies on the belt right now. */
    public int active() {
        return active;
    }
}
