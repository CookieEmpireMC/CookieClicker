package dev.casperrs.cookieClicker.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** The geometric-series maths of model.md "Bulk cost and Max": boundaries, cap and a brute-force cross-check. */
class BulkMathTest {

    private static final double BASE = 15;
    private static final double GROWTH = 1.15;

    @Test
    void maxIsTheExactBoundaryOfTheBulkCost() {
        // affordable k but not k + 1: the pouch is exactly bulk_cost(k) (→ k) and one cookie less (→ k − 1)
        for (int k = 1; k <= 60; k++) {
            double cost = BulkMath.bulkCost(BASE, GROWTH, 0, k);
            assertEquals(k, BulkMath.maxAffordable(BASE, GROWTH, 0, BulkMath.MAX_BULK, cost), "exactly " + k);
            assertEquals(k - 1, BulkMath.maxAffordable(BASE, GROWTH, 0, BulkMath.MAX_BULK, cost - 1), "one short of " + k);
        }
    }

    @Test
    void zeroAffordableZeroCapAndTheMaxBulkCap() {
        assertEquals(0, BulkMath.maxAffordable(BASE, GROWTH, 0, BulkMath.MAX_BULK, 14.99)); // not even one
        assertEquals(0, BulkMath.maxAffordable(BASE, GROWTH, 0, 0, 1e30));                  // cap 0 (at max-level)
        assertEquals(3, BulkMath.maxAffordable(BASE, GROWTH, 0, 3, 1e30));                  // cap below the pouch
        assertEquals(BulkMath.MAX_BULK, BulkMath.maxAffordable(1, 1.0, 0, Integer.MAX_VALUE, 1e300)); // hard cap
        assertEquals(0, BulkMath.bulkCost(BASE, GROWTH, 5, 0));
        assertEquals(BulkMath.bulkCost(BASE, GROWTH, 5, 1), Math.ceil(BASE * Math.pow(GROWTH, 5)));
    }

    @Test
    void growthOfOneIsLinear() {
        assertEquals(40, BulkMath.bulkCost(10, 1.0, 3, 4));
        assertEquals(4, BulkMath.maxAffordable(10, 1.0, 3, BulkMath.MAX_BULK, 45));
    }

    @Test
    void matchesABruteForceCountOnRandomCases() {
        Random rnd = new Random(20261021L);
        double[] growths = {1.0, 1.05, 1.15, 1.25, 2.0};
        for (int i = 0; i < 3000; i++) {
            double base = 1 + rnd.nextInt(100_000);
            double g = growths[rnd.nextInt(growths.length)];
            int owned = rnd.nextInt(80);
            int cap = rnd.nextInt(300);
            double pouch = Math.pow(10, rnd.nextDouble() * 12);
            int expected = 0;
            for (int k = Math.min(cap, BulkMath.MAX_BULK); k >= 1; k--) {
                if (BulkMath.bulkCost(base, g, owned, k) <= pouch) {
                    expected = k;
                    break;
                }
            }
            assertEquals(expected, BulkMath.maxAffordable(base, g, owned, cap, pouch),
                    "base=" + base + " g=" + g + " owned=" + owned + " cap=" + cap + " pouch=" + pouch);
        }
    }
}
