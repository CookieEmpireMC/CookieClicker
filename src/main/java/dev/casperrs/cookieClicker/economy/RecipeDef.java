package dev.casperrs.cookieClicker.economy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One recipe from {@code upgrades.yml: recipes} (bought once in the Recipe Book). cpsPercent is a fraction
 * ({@code 0.02} = +2% CPS); ingredients keep file order (ingredient id → amount).
 */
public record RecipeDef(String id, double cost, Map<String, Double> ingredients, double cpsPercent, double unlockBaked) {

    public RecipeDef {
        ingredients = Collections.unmodifiableMap(new LinkedHashMap<>(ingredients));
    }
}
