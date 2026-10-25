package dev.casperrs.cookieClicker.view.panel;

import dev.casperrs.cookieClicker.economy.EconomyConfig;
import dev.casperrs.cookieClicker.economy.UpgradeDef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Which item goes in which panel row and shelf slot. Pure Java (no Bukkit), so the order is testable. */
public final class PanelRows {

    /** Id of the Recipe Book row, appended after the last building once the book is owned. */
    public static final String RECIPE_BOOK = "recipe_book";

    private PanelRows() {
    }

    /** Buildings panel: every building in {@code buildings.yml} order, at most {@code maxRows}. */
    public static List<String> buildings(EconomyConfig config, int maxRows) {
        return buildings(config, 0, maxRows);
    }

    /** Buildings panel window: {@code buildings.yml} order from index {@code top}, at most {@code maxRows}. */
    public static List<String> buildings(EconomyConfig config, int top, int maxRows) {
        return buildings(config, top, maxRows, false);
    }

    /** Buildings panel window, with the {@link #RECIPE_BOOK} row after the last building when {@code recipeBook}. */
    public static List<String> buildings(EconomyConfig config, int top, int maxRows, boolean recipeBook) {
        List<String> all = new ArrayList<>(config.buildings().keySet());
        if (recipeBook) {
            all.add(RECIPE_BOOK);
        }
        return all.stream().skip(Math.max(0, top)).limit(Math.max(0, maxRows)).toList();
    }

    /** Highest top index of a buildings window of {@code rows} rows (0 = everything fits, no paging). */
    public static int maxTop(EconomyConfig config, int rows) {
        return maxTop(config, rows, false);
    }

    /** {@link #maxTop(EconomyConfig, int)} counting the {@link #RECIPE_BOOK} row when {@code recipeBook}. */
    public static int maxTop(EconomyConfig config, int rows, boolean recipeBook) {
        return Math.max(0, config.buildings().size() + (recipeBook ? 1 : 0) - Math.max(0, rows));
    }

    /**
     * Default buildings window: the next locked building (the first locked one in file order) in the bottom row and
     * the most expensive unlocked ones above it; with everything unlocked, the last {@code rows}. Clamped to
     * {@code 0..maxTop}.
     */
    public static int defaultTop(EconomyConfig config, Predicate<String> unlocked, int rows) {
        return defaultTop(config, unlocked, rows, false);
    }

    /** {@link #defaultTop(EconomyConfig, Predicate, int)} with the {@link #RECIPE_BOOK} row when {@code recipeBook}. */
    public static int defaultTop(EconomyConfig config, Predicate<String> unlocked, int rows, boolean recipeBook) {
        int max = maxTop(config, rows, recipeBook);
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
     * Bought one-time upgrades leave the list, so the rest move up. Missing levels count as 0.
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
