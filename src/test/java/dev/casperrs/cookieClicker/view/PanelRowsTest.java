package dev.casperrs.cookieClicker.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.casperrs.cookieClicker.economy.EconomyConfig;
import dev.casperrs.cookieClicker.economy.EconomyConfigParser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** upgrade-holograms: which items the panels list, in which order, and what the shelf holds. */
class PanelRowsTest {

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    /**
     * File order: rep (repeatable), b (50), a (50, a tie with b), c (20). Expected clicks order: rep pinned, then
     * c (cheapest), then b and a (tie keeps file order).
     */
    private static EconomyConfig config() throws Exception {
        Map<String, Object> buildings = map("cost-growth", 1.15, "buildings", map(
                "baker", map("base-cost", 15, "base-cps", 0.1, "unlock", map("baked", 0)),
                "farm", map("base-cost", 100, "base-cps", 1, "unlock", map("baked", 50))));
        Map<String, Object> upgrades = map("click", map("base-power", 1, "crit-chance", 0.05, "crit-multiplier", 10),
                "upgrades", map(
                        "rep", map("cost", 10, "cost-growth", 2, "max-level", 2,
                                "effect", map("type", "click-add", "value", 1)),
                        "b", map("cost", 50, "effect", map("type", "click-add", "value", 1)),
                        "a", map("cost", 50, "effect", map("type", "click-add", "value", 1)),
                        "c", map("cost", 20, "effect", map("type", "click-add", "value", 1))));
        return EconomyConfigParser.parse(buildings, upgrades).config();
    }

    @Test
    void repeatablesArePinnedThenTheCheapestUnboughtOneTimeUpgrades() throws Exception {
        assertEquals(List.of("rep", "c", "b", "a"), PanelRows.clicks(config(), Map.of(), 10));
    }

    @Test
    void boughtOneTimeUpgradesLeaveAndTheRestMoveUp() throws Exception {
        assertEquals(List.of("rep", "b", "a"), PanelRows.clicks(config(), Map.of("c", 1), 10));
    }

    @Test
    void aMaxedRepeatableStaysPinnedAtTheTop() throws Exception {
        assertEquals(List.of("rep", "c", "b", "a"), PanelRows.clicks(config(), Map.of("rep", 2), 10));
    }

    @Test
    void maxRowsCapsTheList() throws Exception {
        assertEquals(List.of("rep", "c"), PanelRows.clicks(config(), Map.of(), 2));
        assertEquals(List.of(), PanelRows.clicks(config(), Map.of(), 0));
        assertEquals(List.of("baker"), PanelRows.buildings(config(), 1));
        assertEquals(List.of("baker", "farm"), PanelRows.buildings(config(), 5), "buildings.yml order");
    }

    @Test
    void theShelfHoldsBoughtOneTimeUpgradesInFileOrderAndNoRepeatables() throws Exception {
        assertEquals(List.of("b", "c"), PanelRows.shelf(config(), Map.of("c", 1, "b", 1, "rep", 2), 24));
        assertEquals(List.of("b"), PanelRows.shelf(config(), Map.of("c", 1, "b", 1), 1), "max-icons caps it");
    }
}
