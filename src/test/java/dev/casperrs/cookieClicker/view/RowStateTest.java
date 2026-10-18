package dev.casperrs.cookieClicker.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import dev.casperrs.cookieClicker.economy.CookiePouch;
import dev.casperrs.cookieClicker.economy.Economy;
import dev.casperrs.cookieClicker.economy.EconomyConfigParser;
import dev.casperrs.cookieClicker.economy.PouchSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class RowStateTest {

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static Economy economy() throws Exception {
        var buildings = map("cost-growth", 1.15, "buildings", map(
                "baker", map("base-cost", 15, "base-cps", 0.1, "unlock", map("baked", 0)),
                "farm", map("base-cost", 100, "base-cps", 1, "unlock", map("baked", 50))));
        var upgrades = map("click", map("base-power", 1, "crit-chance", 0.05, "crit-multiplier", 10),
                "upgrades", map(
                        "bakerx2", map("cost", 200, "effect", map("type", "building-multiplier", "building", "baker", "value", 2)),
                        "global", map("cost", 900, "effect", map("type", "global-multiplier", "value", 1.5))));
        return new Economy(EconomyConfigParser.parse(buildings, upgrades).config(), RandomGenerator.getDefault());
    }

    private static CookiePouch pouch(double cookies, double baked, Map<String, Integer> owned, String... upgrades) {
        return CookiePouch.fromSnapshot(new PouchSnapshot(cookies, baked, owned, Set.of(upgrades)));
    }

    @Test
    void affordableUnaffordableAndLocked() throws Exception {
        Economy e = economy();
        assertEquals(RowState.Style.AFFORDABLE, RowState.of(e, pouch(15, 15, Map.of()), "baker").style());
        assertEquals(RowState.Style.UNAFFORDABLE, RowState.of(e, pouch(14.9, 14.9, Map.of()), "baker").style());
        RowState locked = RowState.of(e, pouch(1000, 10, Map.of()), "farm");
        assertEquals(RowState.Style.LOCKED, locked.style(), "unlock needs 50 baked even with the cookies in hand");
        assertEquals(50, locked.needed());
        assertEquals(RowState.Style.AFFORDABLE, RowState.of(e, pouch(1000, 50, Map.of()), "farm").style());
    }

    @Test
    void showsOwnedNextCostAndNextCps() throws Exception {
        Economy e = economy();
        CookiePouch p = pouch(0, 0, Map.of("baker", 3), "bakerx2", "global");
        RowState s = RowState.of(e, p, "baker");
        assertEquals(3, s.owned());
        assertEquals(e.nextCost(p, "baker"), s.cost());
        assertEquals(0.1 * 2 * 1.5, s.cps(), 1e-12);
        assertEquals(1 * 1.5, RowState.of(e, p, "farm").cps(), 1e-12);
    }

    @Test
    void nextBuildingCpsEqualsTheRealTotalCpsDelta() throws Exception {
        Economy e = economy();
        for (String[] ups : new String[][] {{}, {"bakerx2"}, {"global"}, {"bakerx2", "global"}}) {
            for (String id : new String[] {"baker", "farm"}) {
                CookiePouch before = pouch(0, 0, Map.of("baker", 4, "farm", 2), ups);
                CookiePouch after = pouch(0, 0, Map.of("baker", id.equals("baker") ? 5 : 4,
                        "farm", id.equals("farm") ? 3 : 2), ups);
                assertEquals(e.totalCps(after) - e.totalCps(before), e.nextBuildingCps(before, id), 1e-9,
                        id + " with " + String.join(",", ups));
            }
        }
        assertEquals(0, e.nextBuildingCps(pouch(0, 0, Map.of()), "nope"));
    }

    @Test
    void unknownBuildingDoesNotThrow() throws Exception {
        RowState s = RowState.of(economy(), pouch(1e9, 1e9, Map.of()), "ghost");
        assertEquals(RowState.Style.LOCKED, s.style());
        assertEquals(0, s.needed());
    }

    @Test
    void equalStatesAreEqualSoTheRowIsNotRedrawn() throws Exception {
        Economy e = economy();
        RowState a = RowState.of(e, pouch(20, 20, Map.of()), "baker");
        // more cookies, still affordable, same owned/cost: no redraw
        RowState b = RowState.of(e, pouch(5000, 5000, Map.of()), "baker");
        assertEquals(a, b);
        assertNotEquals(a, RowState.of(e, pouch(5, 5, Map.of()), "baker"));
        assertNotEquals(a, RowState.of(e, pouch(20, 20, Map.of("baker", 1)), "baker"));
    }

    @Test
    void styleKeysMatchConfigAndMessagePaths() {
        assertEquals("affordable", RowState.Style.AFFORDABLE.key());
        assertEquals("locked", RowState.Style.LOCKED.key());
        assertEquals("panel.row.unaffordable", RowState.Style.UNAFFORDABLE.messagePath());
        assertEquals("panel.row.locked", RowState.Style.LOCKED.messagePath());
    }
}
