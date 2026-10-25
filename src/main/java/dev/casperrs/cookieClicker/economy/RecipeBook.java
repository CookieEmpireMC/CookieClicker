package dev.casperrs.cookieClicker.economy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Recipes from {@code upgrades.yml: recipes}: their state per player and the atomic buy (cookies + ingredients).
 * A bought recipe is stored as a level-1 entry in the pouch's upgrade levels under its own id (the parser keeps
 * recipe and upgrade ids apart). Pure Java, main thread only like {@link CookiePouch}; reads the economy's current
 * config on every call, so a reload applies at once.
 */
public final class RecipeBook {

    /**
     * Relative slack when comparing a stock with a price: production sums like 10 × 0.2 land a hair below the whole
     * number the player sees (1.9999999999999998), which must still pay for 2.
     */
    public static final double TOLERANCE = 1e-9;

    private final Economy economy;

    public RecipeBook(Economy economy) {
        this.economy = Objects.requireNonNull(economy);
    }

    /** Every recipe in {@code recipes:} order. */
    public List<RecipeStatus> statuses(CookiePouch pouch) {
        List<RecipeStatus> out = new ArrayList<>();
        boolean book = economy.recipesUnlocked(pouch);
        for (RecipeDef r : economy.config().recipes().values()) {
            out.add(status(pouch, r, book));
        }
        return out;
    }

    /** Empty when the recipe isn't in the current balance files. */
    public Optional<RecipeStatus> status(CookiePouch pouch, String recipeId) {
        RecipeDef r = economy.config().recipes().get(recipeId);
        return r == null ? Optional.empty() : Optional.of(status(pouch, r, economy.recipesUnlocked(pouch)));
    }

    /**
     * Buys a recipe, all or nothing: on BOUGHT the pouch paid {@code cost} and every ingredient amount and the
     * recipe's {@code cps-percent} counts in {@link Economy#totalCps}; on anything else nothing changed.
     */
    public RecipePurchase buy(CookiePouch pouch, String recipeId) {
        RecipeDef r = economy.config().recipes().get(recipeId);
        if (r == null) {
            return new RecipePurchase(PurchaseResult.UNKNOWN, null);
        }
        RecipeStatus s = status(pouch, r, economy.recipesUnlocked(pouch));
        switch (s.state()) {
            case BOUGHT -> {
                return new RecipePurchase(PurchaseResult.ALREADY_OWNED, s);
            }
            case LOCKED -> {
                return new RecipePurchase(PurchaseResult.LOCKED, s);
            }
            default -> {
                if (!s.affordable()) {
                    return new RecipePurchase(PurchaseResult.CANT_AFFORD, s);
                }
            }
        }
        pouch.spend(r.cost());
        r.ingredients().forEach(pouch::spendIngredient);
        pouch.addUpgradeLevels(r.id(), 1);
        return new RecipePurchase(PurchaseResult.BOUGHT, s);
    }

    private static RecipeStatus status(CookiePouch pouch, RecipeDef r, boolean book) {
        if (pouch.hasUpgrade(r.id())) {
            return new RecipeStatus(r, RecipeStatus.State.BOUGHT, 0, Map.of());
        }
        if (!book || pouch.baked() < r.unlockBaked()) {
            return new RecipeStatus(r, RecipeStatus.State.LOCKED, 0, Map.of());
        }
        double missingCookies = covers(pouch.pouch(), r.cost()) ? 0 : r.cost() - pouch.pouch();
        Map<String, Double> missing = new LinkedHashMap<>();
        r.ingredients().forEach((id, need) -> {
            double have = pouch.ingredient(id);
            if (!covers(have, need)) {
                missing.put(id, need - have);
            }
        });
        return new RecipeStatus(r, RecipeStatus.State.AVAILABLE, missingCookies, missing);
    }

    /** Whether {@code have} pays for {@code need}, within {@link #TOLERANCE} of {@code need}. */
    public static boolean covers(double have, double need) {
        return have >= need - TOLERANCE * Math.abs(need);
    }
}
