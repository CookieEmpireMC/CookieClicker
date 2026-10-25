package dev.casperrs.cookieClicker.config;

import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * {@code config.yml: recipes.gui}: the chest slot of each recipe (in {@code upgrades.yml: recipes} order) and the
 * vanilla items players without the resource pack see.
 */
public record RecipeGuiSettings(List<Integer> slots, Map<String, String> fallbackItems) {

    /** Top inventory size of the 4-row Recipe Book. */
    public static final int SIZE = 36;
    /** Fallback-item key of the locked look. */
    public static final String LOCKED = "locked";
    /** Fallback for a recipe id without a {@code fallback-items} entry. */
    public static final String DEFAULT_ITEM = "BOOK";

    public static final List<Integer> DEFAULT_SLOTS = List.of(10, 11, 12, 13, 14, 15, 16);
    public static final Map<String, String> DEFAULT_FALLBACKS = Map.of(
            LOCKED, "GRAY_DYE",
            "butter", "HONEYCOMB",
            "brown_sugar", "SUGAR",
            "chocolate_chips", "COCOA_BEANS",
            "oatmeal", "WHEAT",
            "hazelnut", "COOKIE",
            "macadamia", "COOKIE",
            "caramel", "HONEY_BOTTLE");

    public static final RecipeGuiSettings DEFAULTS = new RecipeGuiSettings(DEFAULT_SLOTS, DEFAULT_FALLBACKS);

    public RecipeGuiSettings {
        slots = List.copyOf(slots);
        fallbackItems = Map.copyOf(fallbackItems);
    }

    /** Material name for a recipe id or {@link #LOCKED}. */
    public String fallbackFor(String key) {
        return fallbackItems.getOrDefault(key, DEFAULT_FALLBACKS.getOrDefault(key, DEFAULT_ITEM));
    }

    /** Slot of the recipe at {@code index} in file order, or -1 when the list has no slot for it. */
    public int slot(int index) {
        return index >= 0 && index < slots.size() ? slots.get(index) : -1;
    }

    static RecipeGuiSettings from(@Nullable ConfigurationSection gui, Consumer<String> problems) {
        if (gui == null) {
            return DEFAULTS;
        }
        List<Integer> slots = DEFAULT_SLOTS;
        if (gui.isList("slots")) {
            List<Integer> read = new ArrayList<>();
            Set<Integer> seen = new HashSet<>();
            for (Object o : gui.getList("slots", List.of())) {
                if (o instanceof Integer i && i >= 0 && i < SIZE && seen.add(i)) {
                    read.add(i);
                } else {
                    problems.accept("config.yml: recipes.gui.slots entry '" + o + "' is not a free slot 0-"
                            + (SIZE - 1) + "; skipped");
                }
            }
            slots = read;
        } else if (gui.contains("slots")) {
            problems.accept("config.yml: recipes.gui.slots must be a list of slots; using the default");
        }
        Map<String, String> fallbacks = new LinkedHashMap<>(DEFAULT_FALLBACKS);
        ConfigurationSection items = gui.getConfigurationSection("fallback-items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                String material = items.getString(key, "");
                if (material.isBlank()) {
                    problems.accept("config.yml: recipes.gui.fallback-items." + key + " is empty; using the default");
                } else {
                    fallbacks.put(key, material.trim().toUpperCase(Locale.ROOT));
                }
            }
        }
        return new RecipeGuiSettings(slots, fallbacks);
    }
}
