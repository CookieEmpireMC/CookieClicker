package dev.casperrs.cookieClicker.view;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Each player's ×1/×5/×10/Max choice per panel, for the session only (default ×1, forgotten on quit; kept across
 * view respawns and {@code /bakery reload}). Pure Java; main thread only.
 */
public final class BulkModes {

    private final Map<UUID, Map<PanelKind, BulkMode>> modes = new HashMap<>();

    public BulkMode get(UUID player, PanelKind panel) {
        Map<PanelKind, BulkMode> m = modes.get(player);
        return m == null ? BulkMode.ONE : m.getOrDefault(panel, BulkMode.ONE);
    }

    /** The toggle was clicked: switch to the next mode and return it. */
    public BulkMode cycle(UUID player, PanelKind panel) {
        BulkMode next = get(player, panel).next();
        modes.computeIfAbsent(player, k -> new EnumMap<>(PanelKind.class)).put(panel, next);
        return next;
    }

    public void forget(UUID player) {
        modes.remove(player);
    }
}
