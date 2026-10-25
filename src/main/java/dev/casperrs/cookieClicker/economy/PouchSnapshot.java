package dev.casperrs.cookieClicker.economy;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Immutable copy of a {@link CookiePouch}, safe to pass to other threads (saving). upgradeLevels keeps only levels
 * {@code > 0} (bought recipes are level-1 entries there); ingredients keeps only finite amounts {@code > 0}.
 */
public record PouchSnapshot(double pouch, double baked, Map<String, Integer> owned, Map<String, Integer> upgradeLevels,
                            Map<String, Double> ingredients) {

    public static final PouchSnapshot EMPTY = new PouchSnapshot(0, 0, Map.of(), Map.of());

    public PouchSnapshot {
        owned = Map.copyOf(owned);
        Map<String, Double> stock = new HashMap<>();
        ingredients.forEach((id, amount) -> {
            if (id != null && amount != null && amount > 0 && Double.isFinite(amount)) {
                stock.put(id, amount);
            }
        });
        ingredients = Map.copyOf(stock);
        Map<String, Integer> levels = new HashMap<>();
        upgradeLevels.forEach((id, level) -> {
            if (id != null && level != null && level > 0) {
                levels.put(id, level);
            }
        });
        upgradeLevels = Map.copyOf(levels);
    }

    /** Without ingredients. */
    public PouchSnapshot(double pouch, double baked, Map<String, Integer> owned, Map<String, Integer> upgradeLevels) {
        this(pouch, baked, owned, upgradeLevels, Map.of());
    }

    /** Old save format: a set of bought upgrade IDs, each loaded as level 1 (model.md migration rule). */
    public PouchSnapshot(double pouch, double baked, Map<String, Integer> owned, Set<String> upgrades) {
        this(pouch, baked, owned, levelOne(upgrades));
    }

    /** IDs of every upgrade with level {@code >= 1}. */
    public Set<String> upgrades() {
        return upgradeLevels.keySet();
    }

    private static Map<String, Integer> levelOne(Set<String> ids) {
        Map<String, Integer> levels = new HashMap<>();
        ids.forEach(id -> levels.put(id, 1));
        return levels;
    }
}
