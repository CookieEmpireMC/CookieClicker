package dev.casperrs.cookieClicker.view.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.economy.CookiePouch;
import dev.casperrs.cookieClicker.economy.Economy;
import dev.casperrs.cookieClicker.economy.EconomyConfigParser;
import dev.casperrs.cookieClicker.economy.PouchSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

/** click upgrade rows per bulk mode (repeatable count cut to max-level, Max, one-time). */
class UpgradeRowStateTest {

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    /** strength: repeatable, 200 base, growth 1.25, max 3, +1 CPC a level. mitts: one-time 100. Both need a baker. */
    private static Economy economy() throws Exception {
        var buildings = map("cost-growth", 1.15, "buildings", map(
                "baker", map("base-cost", 15, "base-cps", 0.1, "unlock", map("baked", 0))));
        var upgrades = map("click", map("base-power", 1, "crit-chance", 0.05, "crit-multiplier", 10),
                "upgrades", map(
                        "strength", map("cost", 200, "cost-growth", 1.25, "max-level", 3,
                                "effect", map("type", "click-add", "value", 1),
                                "unlock", map("building", "baker", "owned", 1)),
                        "mitts", map("cost", 100, "effect", map("type", "click-add", "value", 2),
                                "unlock", map("building", "baker", "owned", 1))));
        return new Economy(EconomyConfigParser.parse(buildings, upgrades).config(), RandomGenerator.getDefault());
    }

    private static CookiePouch pouch(double cookies, Map<String, Integer> levels) {
        return CookiePouch.fromSnapshot(new PouchSnapshot(cookies, 0, Map.of("baker", 1), levels));
    }

    @Test
    void nearMaxLevelAMultiBuyBuysTheLevelsLeft() throws Exception {
        Economy e = economy();
        CookiePouch p = pouch(1e12, Map.of("strength", 2));
        UpgradeRowState s = UpgradeRowState.of(e, p, "strength", BulkMode.TEN);
        assertEquals(UpgradeRowState.Style.AFFORDABLE, s.style());
        assertEquals(1, s.count(), "max 3, level 2: only one level is left");
        assertEquals(e.upgradeBulkCost(p, "strength", 1), s.cost(), 0.0);
        assertEquals(e.clickPowerGain(p, "strength", 1), s.gain(), 1e-12);
    }

    @Test
    void maxBuysAsManyAsAffordableAndShowsOneWhenNoneIs() throws Exception {
        Economy e = economy();
        CookiePouch two = pouch(e.upgradeBulkCost(pouch(0, Map.of()), "strength", 2), Map.of());
        UpgradeRowState s = UpgradeRowState.of(e, two, "strength", BulkMode.MAX);
        assertEquals(UpgradeRowState.Style.AFFORDABLE, s.style());
        assertEquals(2, s.count());
        assertEquals(e.upgradeBulkCost(two, "strength", 2), s.cost(), 0.0);

        CookiePouch broke = pouch(0, Map.of());
        UpgradeRowState none = UpgradeRowState.of(e, broke, "strength", BulkMode.MAX);
        assertEquals(UpgradeRowState.Style.UNAFFORDABLE, none.style());
        assertEquals(1, none.count());
        assertEquals(e.upgradeBulkCost(broke, "strength", 1), none.cost(), 0.0);
        assertTrue(none.rolls());
    }

    @Test
    void maxedRepeatableShowsNothingToRoll() throws Exception {
        UpgradeRowState s = UpgradeRowState.of(economy(), pouch(1e12, Map.of("strength", 3)), "strength", BulkMode.MAX);
        assertEquals(UpgradeRowState.Style.MAXED, s.style());
        assertEquals(0, s.count());
        assertEquals(0, s.cost(), 0.0);
        assertEquals(0, s.gain(), 0.0);
        assertFalse(s.rolls());
    }

}
