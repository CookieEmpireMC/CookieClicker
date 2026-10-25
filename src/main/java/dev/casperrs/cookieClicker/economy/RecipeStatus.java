package dev.casperrs.cookieClicker.economy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One recipe as a player sees it right now. missingCookies and missingIngredients (ingredient id → amount still
 * needed, only those short, in the recipe's order) are filled for {@link State#AVAILABLE} only; both empty means
 * affordable.
 */
public record RecipeStatus(RecipeDef recipe, State state, double missingCookies, Map<String, Double> missingIngredients) {

    public enum State {
        /** No Recipe Book yet, or {@code baked < unlock.baked}. */
        LOCKED,
        AVAILABLE,
        BOUGHT
    }

    public RecipeStatus {
        missingIngredients = Collections.unmodifiableMap(new LinkedHashMap<>(missingIngredients));
    }

    /** Available and nothing missing: a buy would go through. */
    public boolean affordable() {
        return state == State.AVAILABLE && missingCookies <= 0 && missingIngredients.isEmpty();
    }

    public boolean missesCookies() {
        return missingCookies > 0;
    }

    public boolean missesIngredients() {
        return !missingIngredients.isEmpty();
    }
}
