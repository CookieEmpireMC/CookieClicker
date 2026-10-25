package dev.casperrs.cookieClicker.economy;

/** A building's {@code produces: { ingredient, per-second }}: units per second per building owned, before milestones. */
public record IngredientOutput(String ingredient, double perSecond) {
}
