package dev.casperrs.cookieClicker.economy;

import org.jspecify.annotations.Nullable;

/**
 * Outcome of {@link RecipeBook#buy}: BOUGHT, UNKNOWN, LOCKED, ALREADY_OWNED or CANT_AFFORD. status is the recipe as
 * it was checked (for CANT_AFFORD it says which part is missing), null only for UNKNOWN.
 */
public record RecipePurchase(PurchaseResult result, @Nullable RecipeStatus status) {

    public boolean bought() {
        return result == PurchaseResult.BOUGHT;
    }
}
