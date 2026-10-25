package dev.casperrs.cookieClicker.economy;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A player's economy state (model.md "State per player"). Mutable and <b>not thread-safe</b>: only
 * touch it on the main thread, and hand {@link #snapshot()} to async code (saving).
 * Building/upgrade IDs that the current balance files don't know are kept, so a config change
 * never deletes progress.
 */
public final class CookiePouch {

    private double pouch;
    private double baked;
    private final Map<String, Integer> owned = new HashMap<>();
    /** Upgrade ID → level; only levels {@code >= 1} are stored. */
    private final Map<String, Integer> upgrades = new HashMap<>();
    /** Ingredient ID → stock; only amounts {@code > 0} are stored. */
    private final Map<String, Double> ingredients = new HashMap<>();
    private int revision;

    public CookiePouch() {
    }

    public static CookiePouch fromSnapshot(PouchSnapshot snapshot) {
        CookiePouch p = new CookiePouch();
        p.pouch = SafeMath.sanitize(snapshot.pouch());
        p.baked = SafeMath.sanitize(snapshot.baked());
        snapshot.owned().forEach((id, n) -> {
            if (n != null && n > 0) {
                p.owned.put(id, n);
            }
        });
        p.upgrades.putAll(snapshot.upgradeLevels());
        p.ingredients.putAll(snapshot.ingredients());
        return p;
    }

    public PouchSnapshot snapshot() {
        return new PouchSnapshot(pouch, baked, owned, upgrades, ingredients);
    }

    /** Spendable cookies. */
    public double pouch() {
        return pouch;
    }

    /** Cookies baked all time; never decreases. */
    public double baked() {
        return baked;
    }

    public int owned(String buildingId) {
        return owned.getOrDefault(buildingId, 0);
    }

    public Map<String, Integer> ownedView() {
        return Collections.unmodifiableMap(owned);
    }

    /** Level 1 or more. */
    public boolean hasUpgrade(String upgradeId) {
        return upgrades.containsKey(upgradeId);
    }

    /** The upgrade's level: 0 = not bought, one-time upgrades are 0 or 1. */
    public int upgradeLevel(String upgradeId) {
        return upgrades.getOrDefault(upgradeId, 0);
    }

    /** IDs of every upgrade with level {@code >= 1} (read-only, live). */
    public Set<String> upgradesView() {
        return Collections.unmodifiableSet(upgrades.keySet());
    }

    /** Upgrade ID → level, only levels {@code >= 1} (read-only, live). */
    public Map<String, Integer> upgradeLevelsView() {
        return Collections.unmodifiableMap(upgrades);
    }

    /** Stock of one ingredient (0 when none). */
    public double ingredient(String ingredientId) {
        return ingredients.getOrDefault(ingredientId, 0.0);
    }

    /** Ingredient ID → stock, only amounts {@code > 0} (read-only, live). */
    public Map<String, Double> ingredientsView() {
        return Collections.unmodifiableMap(ingredients);
    }

    /** Changes whenever a building or upgrade level is added: a cheap "did the CPS inputs change" key. */
    public int revision() {
        return revision;
    }

    /** Adds to both pouch and baked (clicks, production). Negative or non-finite amounts are ignored. */
    void earn(double amount) {
        if (!(amount > 0) || !Double.isFinite(amount)) {
            return;
        }
        pouch = SafeMath.add(pouch, amount);
        baked = SafeMath.add(baked, amount);
    }

    /** Subtracts from the pouch only; caller checked affordability. */
    void spend(double amount) {
        pouch = Math.max(0, pouch - amount);
    }

    /** Adds to one ingredient's stock (no cap). Negative or non-finite amounts are ignored. */
    void addIngredient(String ingredientId, double amount) {
        if (!(amount > 0) || !Double.isFinite(amount)) {
            return;
        }
        ingredients.merge(ingredientId, amount, SafeMath::add);
    }

    /** Takes from one ingredient's stock; caller checked the stock. */
    void spendIngredient(String ingredientId, double amount) {
        double left = ingredient(ingredientId) - amount;
        if (left > 0) {
            ingredients.put(ingredientId, left);
        } else {
            ingredients.remove(ingredientId);
        }
    }

    void addBuilding(String buildingId, int count) {
        owned.merge(buildingId, count, (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b));
        revision++;
    }

    void addUpgradeLevels(String upgradeId, int levels) {
        upgrades.merge(upgradeId, levels, (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b));
        revision++;
    }
}
