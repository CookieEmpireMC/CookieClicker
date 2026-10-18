package dev.casperrs.cookieClicker.economy;

/**
 * One building from {@code buildings.yml}. Display names live in {@code messages.yml}.
 *
 * @param unlockBaked the building is visible and purchasable once {@code baked >= unlockBaked}
 */
public record BuildingDef(String id, double baseCost, double baseCps, double unlockBaked) {
}
