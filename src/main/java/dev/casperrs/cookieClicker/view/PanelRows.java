package dev.casperrs.cookieClicker.view;

import dev.casperrs.cookieClicker.economy.EconomyConfig;
import dev.casperrs.cookieClicker.economy.UpgradeDef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Which item goes in which panel row and shelf slot. Pure Java (no Bukkit), so the order is testable. */
public final class PanelRows {

    private PanelRows() {
    }

    /** Buildings panel: every building in {@code buildings.yml} order, at most {@code maxRows}. */
    public static List<String> buildings(EconomyConfig config, int maxRows) {
        return buildings(config, 0, maxRows);
    }

    /** Buildings panel window: {@code buildings.yml} order from index {@code top}, at most {@code maxRows}. */
    public static List<String> buildings(EconomyConfig config, int top, int maxRows) {
        return config.buildings().keySet().stream().skip(Math.max(0, top)).limit(Math.max(0, maxRows)).toList();
    }

    /** Highest top index of a buildings window of {@code rows} rows (0 = everything fits, no paging). */
    public static int maxTop(EconomyConfig config, int rows) {
        return Math.max(0, config.buildings().size() - Math.max(0, rows));
    }

    /**
     * Default buildings window: the next locked building (the first locked one in file order) in the bottom row and
     * the most expensive unlocked ones above it; with everything unlocked, the last {@code rows}. Clamped to
     * {@code 0..maxTop}.
     */
    public static int defaultTop(EconomyConfig config, Predicate<String> unlocked, int rows) {
        int max = maxTop(config, rows);
        int index = 0;
        for (String id : config.buildings().keySet()) {
            if (!unlocked.test(id)) {
                return Math.max(0, Math.min(index - Math.max(1, rows) + 1, max));
            }
            index++;
        }
        return max;
    }

    /**
     * Click upgrades panel: the repeatable upgrades pinned on top ({@code upgrades.yml} order, also when maxed), then
     * the one-time upgrades not bought yet, cheapest first (ties in file order), at most {@code maxRows} in all.
     * Bought one-time upgrades leave the list, so the rest move up.
     *
     * @param levels upgrade id → level (missing = 0)
     */
    public static List<String> clicks(EconomyConfig config, Map<String, Integer> levels, int maxRows) {
        List<String> out = new ArrayList<>();
        List<UpgradeDef> oneTime = new ArrayList<>();
        for (UpgradeDef u : config.upgrades().values()) {
            if (u.repeatable()) {
                out.add(u.id());
            } else if (levels.getOrDefault(u.id(), 0) < 1) {
                oneTime.add(u);
            }
        }
        oneTime.sort(Comparator.comparingDouble(UpgradeDef::cost)); // List.sort is stable: ties keep file order
        oneTime.forEach(u -> out.add(u.id()));
        return out.stream().limit(Math.max(0, maxRows)).toList();
    }

    /** Upgrade shelf: every bought one-time upgrade in {@code upgrades.yml} order, at most {@code maxIcons}. */
    public static List<String> shelf(EconomyConfig config, Map<String, Integer> levels, int maxIcons) {
        return config.upgrades().values().stream()
                .filter(u -> !u.repeatable() && levels.getOrDefault(u.id(), 0) >= 1)
                .map(UpgradeDef::id)
                .limit(Math.max(0, maxIcons))
                .toList();
    }
}
