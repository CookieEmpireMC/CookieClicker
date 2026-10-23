package dev.casperrs.cookieClicker.house.cinematic;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * The state machine of one player's build cinematic, without Bukkit (pure, main thread only). It knows which blocks
 * are still hidden (fake air for that player), which are falling and which are revealed, and paces the work per tick:
 * <ol>
 *   <li><b>hiding</b>: fake air for every block, {@code sendPerTick} blocks per tick;</li>
 *   <li><b>revealing</b>: after {@code layerDelayTicks}, blocks are handed out in {@link RevealOrder} at
 *       {@code blocksPerTick} (fractions accumulate), at most {@code maxFalling} falling at once. A layer starts only
 *       when every block of the layer below has landed, plus {@code layerDelayTicks};</li>
 *   <li><b>done</b> once every block {@link #landed}; or after {@link #abort()}.</li>
 * </ol>
 * Re-asserting fake air ({@link #requestResend()}, and automatically every {@code refreshTicks}) goes over the still
 * hidden blocks with the same per-tick send budget.
 *
 * <p>Re-entrancy: {@link #nextDrop()} marks the block as falling <b>before</b> returning it, so the caller may run
 * {@link #landed} for it inside its own drop call (an effect engine that "lands instantly"), and then simply ask for
 * the next block. {@link #landed} is idempotent.
 */
public final class RevealPlan {

    public enum Phase { HIDING, REVEALING, DONE }

    /** blocksPerTick accumulates fractions (0.5 = every other tick); refreshTicks 0 = re-assert only on request. */
    public record Pace(double blocksPerTick, int maxFalling, int layerDelayTicks, int sendPerTick, int refreshTicks) {
        public Pace {
            if (!(blocksPerTick > 0) || maxFalling < 1 || layerDelayTicks < 0 || sendPerTick < 1 || refreshTicks < 0) {
                throw new IllegalArgumentException("bad pace " + this);
            }
        }
    }

    private static final byte HIDDEN = 0;
    private static final byte FALLING = 1;
    private static final byte REVEALED = 2;

    private final RevealOrder order;
    private final Pace pace;
    private final byte[] state;
    private final Map<Long, Integer> indexOf;
    private Phase phase;
    private int hideCursor;
    private int next;
    private int layer;
    private int layerLeft;
    private int falling;
    private int revealed;
    private int delay;
    private double startBudget;
    private int sendBudget;
    private int resendCursor = -1;
    private int sinceRefresh;

    public RevealPlan(RevealOrder order, Pace pace) {
        this.order = order;
        this.pace = pace;
        this.state = new byte[order.size()];
        this.indexOf = new HashMap<>(Math.max(16, order.size() * 2));
        for (int i = 0; i < order.size(); i++) {
            indexOf.put(order.positions()[i], i);
        }
        this.phase = order.size() == 0 ? Phase.DONE : Phase.HIDING;
        this.layerLeft = order.size() == 0 ? 0 : order.layerEnd(0);
    }

    /** Call once at the start of every tick: refills the budgets and counts down delays and the refresh. */
    public void beginTick() {
        sendBudget = pace.sendPerTick();
        if (phase != Phase.REVEALING) {
            return;
        }
        if (delay > 0) {
            delay--;
        } else {
            // never bank more than one tick's worth plus the carried fraction (< 1): keeps 2.5 averaging 2.5,
            // while a long layer wait still yields floor(blocksPerTick + 1 - eps) blocks, not a burst
            startBudget = Math.min(startBudget + pace.blocksPerTick(), Math.nextDown(pace.blocksPerTick() + 1.0));
        }
        if (pace.refreshTicks() > 0 && ++sinceRefresh >= pace.refreshTicks()) {
            requestResend();
        }
    }

    /** Hiding phase: hands out the next blocks to send as fake air (index into {@link #position}). */
    public void nextHide(IntConsumer out) {
        if (phase != Phase.HIDING) {
            return;
        }
        while (sendBudget > 0 && hideCursor < state.length) {
            out.accept(hideCursor++);
            sendBudget--;
        }
        if (hideCursor >= state.length) {
            phase = Phase.REVEALING;
            delay = pace.layerDelayTicks();
        }
    }

    /** Re-send fake air for every still-hidden block (spread over ticks by the send budget). */
    public void requestResend() {
        if (phase != Phase.DONE) {
            resendCursor = 0;
            sinceRefresh = 0;
        }
    }

    /**
     * Hands out still-hidden blocks (hidden or falling) that need their fake air again, after {@link #requestResend()}.
     * During hiding only blocks that were already hidden are covered (the rest is still on its way).
     */
    public void nextResend(IntConsumer out) {
        if (resendCursor < 0 || phase == Phase.DONE) {
            return;
        }
        int limit = phase == Phase.HIDING ? hideCursor : state.length;
        while (sendBudget > 0 && resendCursor < limit) {
            int i = resendCursor++;
            if (state[i] != REVEALED) {
                out.accept(i);
                sendBudget--;
            }
        }
        if (resendCursor >= limit) {
            resendCursor = -1;
        }
    }

    /**
     * The next block to drop this tick, already marked falling, or -1 when nothing more may start this tick (budget,
     * falling cap, layer still landing or layer delay).
     */
    public int nextDrop() {
        if (phase != Phase.REVEALING || delay > 0 || falling >= pace.maxFalling() || startBudget < 1.0) {
            return -1;
        }
        if (next >= order.layerEnd(layer)) {
            if (layerLeft > 0 || layer + 1 >= order.layers()) {
                return -1; // wait for this layer to land (or everything is out already)
            }
            layer++;
            layerLeft = order.layerEnd(layer) - order.layerStarts()[layer];
            delay = pace.layerDelayTicks();
            if (delay > 0) {
                return -1;
            }
        }
        int i = next++;
        state[i] = FALLING;
        falling++;
        startBudget -= 1.0;
        return i;
    }

    /** Block {@code i} landed: it is real again for the player. False when it wasn't falling (already handled). */
    public boolean landed(int i) {
        if (i < 0 || i >= state.length || state[i] != FALLING) {
            return false;
        }
        state[i] = REVEALED;
        falling--;
        revealed++;
        layerLeft--;
        if (revealed == state.length) {
            phase = Phase.DONE;
        }
        return true;
    }

    /** Ends the plan now and returns every block that was still hidden or falling (to be restored). */
    public long[] abort() {
        long[] out = new long[state.length - revealed];
        int n = 0;
        for (int i = 0; i < state.length; i++) {
            if (state[i] != REVEALED) {
                out[n++] = order.positions()[i];
                state[i] = REVEALED;
            }
        }
        revealed = state.length;
        falling = 0;
        phase = Phase.DONE;
        resendCursor = -1;
        return out;
    }

    public Phase phase() {
        return phase;
    }

    /** True once every block is revealed (also right away for an empty house). */
    public boolean complete() {
        return revealed == state.length;
    }

    public boolean isFalling(int i) {
        return state[i] == FALLING;
    }

    /** Whether the block at this packed position is still fake air (or falling) for the player. */
    public boolean isHidden(long packed) {
        Integer i = indexOf.get(packed);
        return i != null && state[i] != REVEALED;
    }

    public long position(int i) {
        return order.positions()[i];
    }

    /** Whether block {@code i} is in the bottom layer (foundation dust). */
    public boolean bottomLayer(int i) {
        return i < order.layerEnd(0);
    }

    public int size() {
        return state.length;
    }

    public int hiddenCount() {
        return state.length - revealed;
    }

    public int fallingCount() {
        return falling;
    }
}
