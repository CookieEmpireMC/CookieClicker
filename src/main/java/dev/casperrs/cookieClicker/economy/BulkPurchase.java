package dev.casperrs.cookieClicker.economy;

/**
 * Outcome of an atomic bulk buy ({@link Economy#buyBuildings}, {@link Economy#buyUpgrades}, and the Max variants).
 *
 * @param result {@link PurchaseResult#BOUGHT}, or why nothing was bought
 * @param count  buildings / levels bought (0 unless bought)
 * @param cost   cookies paid, the batch's {@code bulk_cost} rounded up once (0 unless bought)
 */
public record BulkPurchase(PurchaseResult result, int count, double cost) {

    static BulkPurchase failed(PurchaseResult result) {
        return new BulkPurchase(result, 0, 0);
    }

    public boolean bought() {
        return result == PurchaseResult.BOUGHT;
    }
}
