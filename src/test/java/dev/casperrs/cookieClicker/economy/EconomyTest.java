package dev.casperrs.cookieClicker.economy;

import static dev.casperrs.cookieClicker.economy.EconomyTestSupport.config;
import static dev.casperrs.cookieClicker.economy.EconomyTestSupport.fixed;
import static dev.casperrs.cookieClicker.economy.EconomyTestSupport.map;
import static dev.casperrs.cookieClicker.economy.EconomyTestSupport.pouch;
import static dev.casperrs.cookieClicker.economy.EconomyTestSupport.upgradesYaml;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EconomyTest {

    private final Economy economy = new Economy(config(), fixed(0.99));


    @Test
    void firstCostIsBaseCostRoundedUp() {
        assertEquals(15, economy.nextCost(new CookiePouch(), "baker"));
        assertEquals(100, economy.nextCost(new CookiePouch(), "farm"));
    }

    @Test
    void costsGrowAndRoundUpEachStep() {
        // 15 * 1.15^1 = 17.25 -> 18, 15 * 1.15^2 = 19.8375 -> 20
        assertEquals(18, economy.bulkCost("baker", 1, 1));
        assertEquals(20, economy.bulkCost("baker", 2, 1));
    }

    @Test
    void bulkCostIsRoundedOnceForTheBatch() {
        // 15 × (1.15³ − 1) / 0.15 = 52.09 → 53 (not 15 + 18 + 20 when each item is rounded on its own)
        assertEquals(53, economy.bulkCost("baker", 0, 3));
        // exact sum of the ten singles from 10 owned = 1232.10 → 1233; rounding each single first gives 1238
        assertEquals(1233, economy.bulkCost("baker", 10, 10));
        assertEquals(0, economy.bulkCost("baker", 5, 0));
    }


    @Test
    void cpsIsOwnedTimesBaseCps() {
        CookiePouch p = pouch(0, 0, Map.of("baker", 10, "farm", 2));
        assertEquals(1.0, economy.buildingCps(p, "baker"), 1e-12);
        assertEquals(2.0, economy.buildingCps(p, "farm"), 1e-12);
        assertEquals(3.0, economy.totalCps(p), 1e-12);
    }

    @Test
    void buildingAndGlobalMultipliersApply() {
        CookiePouch p = pouch(0, 0, Map.of("baker", 10, "farm", 2), "bakerx2", "global");
        assertEquals(2.0, economy.buildingCps(p, "baker"), 1e-12);
        assertEquals((2.0 + 2.0) * 1.5, economy.totalCps(p), 1e-12);
    }

    @Test
    void milestonesDoubleProductionAndABulkBuyCountsEveryMilestoneItCrosses() throws Exception {
        // baker: 0.1 CPS each, ×2 at 2 owned and again at 4 owned (listed out of order in the file)
        Economy milestones = new Economy(EconomyConfigParser.parse(
                map("cost-growth", 1.15, "milestones", List.of(4, 2), "buildings", map(
                        "baker", map("base-cost", 15, "base-cps", 0.1, "unlock", map("baked", 0)))),
                upgradesYaml()).config(), fixed(0.99));
        CookiePouch one = pouch(0, 0, Map.of("baker", 1));
        assertEquals(0.1, milestones.buildingCps(one, "baker"), 1e-9, "1 owned: no milestone yet");
        assertEquals(0.4, milestones.buildingCps(pouch(0, 0, Map.of("baker", 2)), "baker"), 1e-9, "2 owned: ×2");
        assertEquals(1.6, milestones.totalCps(pouch(0, 0, Map.of("baker", 4))), 1e-9, "4 owned: ×4");
        assertEquals(0.3, milestones.nextBuildingCps(one, "baker"), 1e-9, "the second one crosses the 2 milestone");
        assertEquals(1.5, milestones.buildingCpsGain(one, "baker", 3), 1e-9,
                "3 more cross 2 and 4: (1.6 - 0.1), not the linear 0.3");
        assertEquals(0.0, milestones.buildingCpsGain(one, "baker", 0), 1e-12);
    }

    @Test
    void clickPowerCombinesAddMultiplierAndCpsFraction() {
        assertEquals(1, economy.clickPower(new CookiePouch()));
        assertEquals(2, economy.clickPower(pouch(0, 0, Map.of(), "plus")));
        assertEquals(4, economy.clickPower(pouch(0, 0, Map.of(), "plus", "mitts")));
        // (1 + 1) * 2 + 0.1 * cps(1.0)
        CookiePouch p = pouch(0, 0, Map.of("baker", 10), "plus", "mitts", "frac");
        assertEquals(4.1, economy.clickPower(p), 1e-12);
    }


    @Test
    void normalClickAddsToPouchAndBaked() {
        CookiePouch p = new CookiePouch();
        ClickResult r = economy.click(p); // rng 0.99 >= 0.05
        assertFalse(r.crit());
        assertEquals(1, r.amount());
        assertEquals(1, p.pouch());
        assertEquals(1, p.baked());
    }

    @Test
    void critMultipliesTheClick() {
        Economy e = new Economy(config(), fixed(0.01));
        CookiePouch p = new CookiePouch();
        ClickResult r = e.click(p);
        assertTrue(r.crit());
        assertEquals(10, r.amount());
        assertEquals(10, p.pouch());
        assertEquals(10, p.baked());
    }

    @Test
    void purchasesDoNotTouchBaked() {
        CookiePouch p = pouch(100, 100, Map.of());
        assertEquals(PurchaseResult.BOUGHT, economy.buyBuilding(p, "baker"));
        assertEquals(85, p.pouch());
        assertEquals(100, p.baked());
        assertEquals(1, p.owned("baker"));
    }


    @Test
    void produceAddsCpsToPouchAndBaked() {
        CookiePouch p = pouch(0, 5, Map.of("baker", 10));
        assertEquals(1.0, economy.produce(p, 1), 1e-12);
        assertEquals(1.0, p.pouch(), 1e-12);
        assertEquals(6.0, p.baked(), 1e-12);
        economy.produce(p, 2.5);
        assertEquals(3.5, p.pouch(), 1e-12);
    }


    @Test
    void buildingUnlocksAtBakedThresholdAndStaysUnlockedByBaked() {
        assertFalse(economy.buildingUnlocked(pouch(0, 49.9, Map.of()), "farm"));
        assertTrue(economy.buildingUnlocked(pouch(0, 50, Map.of()), "farm"));
        assertFalse(economy.buildingUnlocked(new CookiePouch(), "ghost"));
    }

    @Test
    void lockedBuildingCannotBeBoughtEvenWithCookies() {
        CookiePouch p = pouch(500, 10, Map.of());
        assertEquals(PurchaseResult.LOCKED, economy.buyBuilding(p, "farm"));
        assertFalse(economy.canAfford(p, "farm"));
        assertEquals(500, p.pouch());
    }

    @Test
    void cantAffordAndUnknownDontChangeState() {
        CookiePouch p = pouch(14, 14, Map.of());
        assertEquals(PurchaseResult.CANT_AFFORD, economy.buyBuilding(p, "baker"));
        assertEquals(PurchaseResult.UNKNOWN, economy.buyBuilding(p, "nope"));
        assertEquals(14, p.pouch());
        assertEquals(0, p.owned("baker"));
    }

    @Test
    void upgradeUnlockRulesAndOneTimePurchase() {
        CookiePouch p = pouch(1000, 100, Map.of());
        assertFalse(economy.upgradeUnlocked(p, "mitts")); // needs 1 baker
        assertEquals(PurchaseResult.LOCKED, economy.buyUpgrade(p, "mitts"));
        assertFalse(economy.upgradeUnlocked(p, "frac")); // needs baked 1000
        CookiePouch q = pouch(1000, 100, Map.of("baker", 1));
        assertTrue(economy.upgradeUnlocked(q, "mitts"));
        assertEquals(PurchaseResult.BOUGHT, economy.buyUpgrade(q, "mitts"));
        assertEquals(900, q.pouch());
        assertEquals(PurchaseResult.ALREADY_OWNED, economy.buyUpgrade(q, "mitts"));
        assertFalse(economy.upgradeUnlocked(q, "mitts"));
        assertEquals(PurchaseResult.UNKNOWN, economy.buyUpgrade(q, "nope"));
        assertEquals(PurchaseResult.CANT_AFFORD, economy.buyUpgrade(pouch(10, 100, Map.of()), "plus"));
    }


    @Test
    void disabledConfigGivesNothingAndSellsNothing() {
        Economy e = new Economy(EconomyConfig.DISABLED, fixed(0.0));
        assertFalse(e.config().enabled());
        CookiePouch p = new CookiePouch();
        ClickResult r = e.click(p);
        assertEquals(0, r.amount());
        assertEquals(0, p.pouch());
        assertEquals(PurchaseResult.UNKNOWN, e.buyBuilding(pouch(1e9, 1e9, Map.of()), "baker"));
        assertEquals(0, e.totalCps(pouch(0, 0, Map.of("baker", 5))));
    }

    @Test
    void setConfigSwapsBalanceWithoutLosingProgress() {
        Economy e = new Economy(EconomyConfig.DISABLED, fixed(0.99));
        CookiePouch p = pouch(0, 0, Map.of("baker", 10));
        assertEquals(0, e.totalCps(p));
        e.setConfig(config());
        assertTrue(e.config().enabled());
        assertEquals(1.0, e.totalCps(p), 1e-12);
        assertSame(e.config(), e.config());
    }


    @Test
    void pouchSaturatesInsteadOfOverflowing() {
        CookiePouch p = pouch(Double.MAX_VALUE, Double.MAX_VALUE, Map.of());
        economy.click(p);
        assertEquals(Double.MAX_VALUE, p.pouch());
        assertEquals(Double.MAX_VALUE, p.baked());
        assertTrue(Double.isFinite(p.pouch()));
    }

    @Test
    void snapshotWithNanInfinityNegativeIsSanitised() {
        CookiePouch p = CookiePouch.fromSnapshot(new PouchSnapshot(Double.NaN, Double.POSITIVE_INFINITY,
                Map.of("baker", -3, "farm", 2), java.util.Set.of()));
        assertEquals(0, p.pouch());
        assertEquals(Double.MAX_VALUE, p.baked());
        assertEquals(0, p.owned("baker"));
        assertEquals(2, p.owned("farm"));
        CookiePouch neg = CookiePouch.fromSnapshot(new PouchSnapshot(-5, -5, Map.of(), java.util.Set.of()));
        assertEquals(0, neg.pouch());
    }

    @Test
    void snapshotIsADetachedCopy() {
        CookiePouch p = pouch(10, 10, Map.of("baker", 1));
        PouchSnapshot s = p.snapshot();
        economy.buyBuilding(p, "baker");
        assertEquals(10, s.pouch());
        assertEquals(1, s.owned().get("baker"));
    }


    private final Economy repeatable = new Economy(EconomyTestSupport.repeatableConfig(), fixed(0.99));

    @Test
    void bulkBuyChargesTheBatchOnceAndAddsTheCount() {
        CookiePouch p = pouch(10_000, 10_000, Map.of());
        BulkPurchase r = economy.buyBuildings(p, "baker", 10);
        assertEquals(PurchaseResult.BOUGHT, r.result());
        assertEquals(10, r.count());
        assertEquals(305, r.cost()); // 15 × (1.15¹⁰ − 1) / 0.15 = 304.56, rounded up once
        assertEquals(10_000 - 305, p.pouch());
        assertEquals(10, p.owned("baker"));
    }

    @Test
    void bulkBuyIsAllOrNothing() {
        CookiePouch p = pouch(304, 304, Map.of());
        BulkPurchase r = economy.buyBuildings(p, "baker", 10);
        assertEquals(PurchaseResult.CANT_AFFORD, r.result());
        assertEquals(0, r.count());
        assertEquals(0, r.cost());
        assertEquals(304, p.pouch());
        assertEquals(0, p.owned("baker"));
        assertEquals(PurchaseResult.BOUGHT, economy.buyBuildings(pouch(305, 305, Map.of()), "baker", 10).result());
    }

    @Test
    void maxBuysTheMostAffordableOrReportsWhyNothingWas() {
        CookiePouch p = pouch(100, 100, Map.of());
        assertEquals(4, economy.maxAffordableBuildings(p, "baker")); // ×4 costs 75, ×5 costs 102
        BulkPurchase r = economy.buyMaxBuildings(p, "baker");
        assertEquals(4, r.count());
        assertEquals(75, r.cost());
        assertEquals(25, p.pouch());
        assertEquals(4, p.owned("baker"));
        CookiePouch poor = pouch(5, 5, Map.of());
        BulkPurchase none = economy.buyMaxBuildings(poor, "baker");
        assertEquals(PurchaseResult.CANT_AFFORD, none.result());
        assertEquals(5, poor.pouch());
        assertEquals(0, poor.owned("baker"));
    }

    @Test
    void repeatableUpgradeBuysLevelsUpToMaxLevelAllOrNothing() {
        CookiePouch p = pouch(100_000, 100_000, Map.of("baker", 1));
        // strength: cost 200, ×1.25 per level, max-level 3. Levels 0 → 2 cost ceil(200 × (1.25² − 1) / 0.25) = 450
        BulkPurchase two = repeatable.buyUpgrades(p, "strength", 2);
        assertEquals(PurchaseResult.BOUGHT, two.result());
        assertEquals(450, two.cost());
        assertEquals(2, p.upgradeLevel("strength"));
        assertEquals(PurchaseResult.MAX_LEVEL, repeatable.buyUpgrades(p, "strength", 2).result()); // 2 + 2 > 3
        assertEquals(2, p.upgradeLevel("strength"));
        assertEquals(100_000 - 450, p.pouch());
        // one more level costs ceil(200 × 1.25²) = 313; a further level is over max-level
        assertEquals(313, repeatable.upgradeBulkCost("strength", 2, 5)); // 5 is cut to the 1 level left
        assertEquals(313, repeatable.buyUpgrades(p, "strength", 1).cost());
        assertEquals(3, p.upgradeLevel("strength"));
        assertEquals(PurchaseResult.MAX_LEVEL, repeatable.buyUpgrades(p, "strength", 1).result());
    }

    @Test
    void clickPowerGainUsesTheLevelOverrideForEveryEffectType() {
        CookiePouch p = pouch(0, 0, Map.of("baker", 1));
        assertEquals(1.0, repeatable.clickPowerGain(p, "mitts", 1), 1e-9); // ×2 on power 1
        assertEquals(2.0, repeatable.clickPowerGain(p, "strength", 2), 1e-9); // +1 per level, two levels
        CookiePouch maxed = CookiePouch.fromSnapshot(new PouchSnapshot(0, 0, Map.of("baker", 1), Map.of("strength", 3)));
        assertEquals(0.0, repeatable.clickPowerGain(maxed, "strength", 1), 1e-9);
        CookiePouch focus = CookiePouch.fromSnapshot(new PouchSnapshot(0, 0, Map.of(), Map.of("focus", 3)));
        assertEquals(8.0, repeatable.clickPower(focus), 1e-9); // ×2 per level: 2³ × base 1
        assertEquals(8.0, repeatable.clickPowerGain(focus, "focus", 1), 1e-9); // 2⁴ − 2³
    }
}
