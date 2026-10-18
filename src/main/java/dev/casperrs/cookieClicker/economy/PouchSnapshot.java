package dev.casperrs.cookieClicker.economy;

import java.util.Map;
import java.util.Set;

/** Immutable copy of a {@link CookiePouch}, safe to pass to other threads (saving). */
public record PouchSnapshot(double pouch, double baked, Map<String, Integer> owned, Set<String> upgrades) {

    public static final PouchSnapshot EMPTY = new PouchSnapshot(0, 0, Map.of(), Set.of());

    public PouchSnapshot {
        owned = Map.copyOf(owned);
        upgrades = Set.copyOf(upgrades);
    }
}
