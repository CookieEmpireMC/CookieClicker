package dev.casperrs.cookieClicker.economy;

/**
 * Outcome of an atomic bulk buy ({@link Economy#buyBuildings}, {@link Economy#buyUpgrades}, and the Max variants).
 * count and cost (the bulk cost, rounded up once) are 0 unless bought.
 */
public record BulkPurchase(PurchaseResult result, int count, double cost) {

    static BulkPurchase failed(PurchaseResult result) {
        return new BulkPurchase(result, 0, 0);
    }

    public boolean bought() {
        return result == PurchaseResult.BOUGHT;
    }
}
