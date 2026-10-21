package dev.casperrs.cookieClicker.vfx;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The {@code min-interval-ticks} cap of a preset: at most one play per viewer (or one shared play for
 * {@code viewer == null}) per interval; plays in between are dropped. For effects code fires at a high rate (e.g.
 * {@code belt_arrive} once per belt cookie). Plain Java, unit-tested; the caller passes the server tick.
 */
final class Throttle {

    /** Stand-in for "everyone nearby" plays ({@code viewer == null}). */
    private static final UUID NOBODY = new UUID(0L, 0L);

    private record Key(UUID viewer, String effectId) {
    }

    private final Map<Key, Long> lastPlay = new HashMap<>();

    /**
     * @param interval minimum ticks between two plays; 0 or less = no cap (always true, nothing stored)
     * @return true (and the play is recorded) when at least {@code interval} ticks passed since the last allowed
     *         play of this effect for this viewer, or there was none
     */
    boolean allow(@Nullable UUID viewer, String effectId, long now, int interval) {
        if (interval <= 0) {
            return true;
        }
        Key key = new Key(viewer == null ? NOBODY : viewer, effectId);
        Long last = lastPlay.get(key);
        if (last != null && now - last < interval && now >= last) {
            return false;
        }
        lastPlay.put(key, now);
        return true;
    }

    /** Forgets one viewer (quit). */
    void forget(UUID viewer) {
        lastPlay.keySet().removeIf(k -> k.viewer().equals(viewer));
    }

    /** Forgets everything (reload, shutdown). */
    void clear() {
        lastPlay.clear();
    }

    int size() {
        return lastPlay.size();
    }
}
