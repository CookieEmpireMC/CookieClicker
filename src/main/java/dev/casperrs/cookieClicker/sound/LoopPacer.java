package dev.casperrs.cookieClicker.sound;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps looping ambient sounds ({@code factory_ambient}, {@code house_chimney_crackle}, {@code belt_loop}) seamless
 * per player (brief plot-polish "Ambient sound"): a loop may only be replayed once its last play has run its full
 * period (the file length at its fixed pitch). Whoever plays a loop asks {@link #tryPlay} first, so a restarted timer
 * (re-entering the plot, the end of the cinematic, a reload, the belt starting again) never plays over a play that is
 * still sounding, and two timers for one loop can't double it: the first one keeps the beat, the other is refused.
 * {@link #delay} gives a new timer the start that keeps the old beat. Pure; main thread only (not thread-safe).
 */
public final class LoopPacer {

    /** Entries older than this many ticks are forgotten (the sound has long ended). */
    static final long FORGET_AFTER = 6000;
    private static final int PRUNE_EVERY = 256;

    private final Map<UUID, Map<String, Long>> last = new HashMap<>();
    private int calls;

    /**
     * Whether {@code loop} may play for {@code player} at server tick {@code now} with a period of {@code period}
     * ticks; when it may, the play is recorded.
     */
    public boolean tryPlay(UUID player, String loop, int period, long now) {
        if (++calls % PRUNE_EVERY == 0) {
            prune(now);
        }
        Map<String, Long> mine = last.computeIfAbsent(player, k -> new HashMap<>());
        Long before = mine.get(loop);
        if (before != null && now < before + period && now >= before) {
            return false;
        }
        mine.put(loop, now);
        return true;
    }

    /** Ticks until {@code loop} may play again for {@code player} (0 = now): the first delay of a new timer. */
    public long delay(UUID player, String loop, int period, long now) {
        Map<String, Long> mine = last.get(player);
        Long before = mine == null ? null : mine.get(loop);
        if (before == null || now < before) {
            return 0;
        }
        return Math.max(0, before + period - now);
    }

    /** The player left the server: their client stopped every sound. */
    public void forget(UUID player) {
        last.remove(player);
    }

    private void prune(long now) {
        for (Iterator<Map<String, Long>> it = last.values().iterator(); it.hasNext(); ) {
            Map<String, Long> mine = it.next();
            mine.values().removeIf(t -> now - t > FORGET_AFTER || t > now);
            if (mine.isEmpty()) {
                it.remove();
            }
        }
    }
}
