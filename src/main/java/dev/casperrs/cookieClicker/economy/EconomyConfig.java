package dev.casperrs.cookieClicker.economy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The parsed balance config ({@code buildings.yml} + {@code upgrades.yml}). Immutable; every number
 * comes from those files, none is defined in code. Buildings and recipes keep file order (the panel / book order).
 * ingredients = every ingredient a building produces, in building order (the HUD order).
 */
public record EconomyConfig(double costGrowth, double basePower, double critChance, double critMultiplier,
                            Map<String, BuildingDef> buildings, Map<String, UpgradeDef> upgrades,
                            Map<String, RecipeDef> recipes, List<String> ingredients) {

    /**
     * Used while the balance files are missing or invalid: clicks give nothing, nothing can be bought.
     * The zeros mean "economy disabled", not balance values.
     */
    public static final EconomyConfig DISABLED = new EconomyConfig(1, 0, 0, 1, Map.of(), Map.of());

    public EconomyConfig {
        buildings = Collections.unmodifiableMap(new LinkedHashMap<>(buildings));
        upgrades = Collections.unmodifiableMap(new LinkedHashMap<>(upgrades));
        recipes = Collections.unmodifiableMap(new LinkedHashMap<>(recipes));
        ingredients = List.copyOf(ingredients);
    }

    /** Ingredients derived from the buildings. */
    public EconomyConfig(double costGrowth, double basePower, double critChance, double critMultiplier,
                         Map<String, BuildingDef> buildings, Map<String, UpgradeDef> upgrades,
                         Map<String, RecipeDef> recipes) {
        this(costGrowth, basePower, critChance, critMultiplier, buildings, upgrades, recipes, ingredientsOf(buildings));
    }

    /** Without recipes. */
    public EconomyConfig(double costGrowth, double basePower, double critChance, double critMultiplier,
                         Map<String, BuildingDef> buildings, Map<String, UpgradeDef> upgrades) {
        this(costGrowth, basePower, critChance, critMultiplier, buildings, upgrades, Map.of());
    }

    public boolean enabled() {
        return this != DISABLED;
    }

    private static List<String> ingredientsOf(Map<String, BuildingDef> buildings) {
        List<String> ids = new ArrayList<>();
        for (BuildingDef b : buildings.values()) {
            if (b.produces() != null && !ids.contains(b.produces().ingredient())) {
                ids.add(b.produces().ingredient());
            }
        }
        return ids;
    }
}
