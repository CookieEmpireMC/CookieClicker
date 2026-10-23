package dev.casperrs.cookieClicker.economy;

/**
 * The geometric-series maths from {@code docs/balance/model.md} "Bulk cost and Max", shared by
 * buildings and repeatable upgrades. Same evaluation order and rounding as {@code tools/balance/model.py}
 * ({@code series_cost} / {@code series_max}), so the results match {@code golden-values.json} exactly.
 * Pure and stateless.
 */
public final class BulkMath {

    /** {@code MAX_BULK} from model.md: the hard upper bound for any single bulk or Max buy. */
    public static final int MAX_BULK = 10_000;

    private BulkMath() {
    }

    /**
     * {@code bulk_cost(base, g, n, k) = ceil(base × g^n × ratio)}, with {@code ratio = (g^k − 1)/(g − 1)}
     * ({@code k} when {@code g == 1}). Rounded up <b>once</b> for the whole batch; {@code k <= 0} costs 0.
     * {@code g = growth >= 1}, {@code n = owned} (or the upgrade's level), {@code k = count}.
     */
    public static double bulkCost(double base, double growth, int owned, int count) {
        if (count <= 0) {
            return 0;
        }
        double first = base * Math.pow(growth, owned);
        double ratio = growth == 1 ? (double) count : (Math.pow(growth, count) - 1) / (growth - 1);
        return Math.ceil(first * ratio);
    }

    /**
     * {@code max_affordable}: the largest {@code k} in {@code [0, min(cap, MAX_BULK)]} with
     * {@code bulkCost(base, growth, owned, k) <= pouch}. Closed-form log estimate plus the model's
     * bounded ±1 correction loops; never a search from 1.
     */
    public static int maxAffordable(double base, double growth, int owned, int cap, double pouch) {
        cap = Math.min(cap, MAX_BULK);
        if (cap <= 0 || !(pouch >= bulkCost(base, growth, owned, 1))) {
            return 0;
        }
        double first = base * Math.pow(growth, owned);
        double estimate = growth == 1
                ? pouch / first
                : Math.log(1 + pouch * (growth - 1) / first) / Math.log(growth);
        int k = Double.isFinite(estimate) ? (int) Math.min(Math.max(Math.floor(estimate), 1), cap) : cap;
        while (k > 1 && bulkCost(base, growth, owned, k) > pouch) {
            k--;
        }
        while (k < cap && bulkCost(base, growth, owned, k + 1) <= pouch) {
            k++;
        }
        return k;
    }
}
