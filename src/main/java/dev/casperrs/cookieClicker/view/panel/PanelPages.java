package dev.casperrs.cookieClicker.view.panel;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Each player's scroll position per panel (the ▲ / ▼ labels), for the session only. Without a scroll the panel shows
 * its default window ({@link PanelRows#defaultTop}); a scroll is kept until the default window itself moves (a new
 * building unlocked), so new unlocks always come into view. Forgotten on quit. Pure Java; main thread only.
 */
public final class PanelPages {

    /** A scrolled position and the default top it was chosen against. */
    private record Scroll(int top, int defaultTop) {
    }

    private final Map<UUID, Map<PanelKind, Scroll>> scrolls = new HashMap<>();

    /** Index of the first row shown: the player's scroll, else {@code defaultTop}; always within {@code 0..maxTop}. */
    public int top(UUID player, PanelKind panel, int defaultTop, int maxTop) {
        Map<PanelKind, Scroll> m = scrolls.get(player);
        Scroll s = m == null ? null : m.get(panel);
        if (s != null && s.defaultTop() != defaultTop) {
            m.remove(panel); // the default window moved (unlock): follow it again
            s = null;
        }
        return clamp(s == null ? defaultTop : s.top(), maxTop);
    }

    /**
     * Scrolls by {@code delta} rows (negative = up). Returns false (and changes nothing) when already at that end.
     */
    public boolean scroll(UUID player, PanelKind panel, int delta, int defaultTop, int maxTop) {
        int now = top(player, panel, defaultTop, maxTop);
        int next = clamp(now + delta, maxTop);
        if (next == now) {
            return false;
        }
        scrolls.computeIfAbsent(player, k -> new EnumMap<>(PanelKind.class)).put(panel, new Scroll(next, defaultTop));
        return true;
    }

    public void forget(UUID player) {
        scrolls.remove(player);
    }

    private static int clamp(int top, int maxTop) {
        return Math.max(0, Math.min(top, Math.max(0, maxTop)));
    }
}
