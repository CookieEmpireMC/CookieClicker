package dev.casperrs.cookieClicker.economy;

import static dev.casperrs.cookieClicker.economy.EconomyTestSupport.buildingsYaml;
import static dev.casperrs.cookieClicker.economy.EconomyTestSupport.map;
import static dev.casperrs.cookieClicker.economy.EconomyTestSupport.upgradesYaml;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EconomyConfigParserTest {

    private static EconomyConfigParser.Result parse(Map<String, ?> b, Map<String, ?> u) throws EconomyConfigException {
        return EconomyConfigParser.parse(b, u);
    }

    private static String error(Map<String, ?> b, Map<String, ?> u) {
        return assertThrows(EconomyConfigException.class, () -> parse(b, u)).getMessage();
    }

    @Test
    void parsesAFullConfigInFileOrder() throws Exception {
        EconomyConfig c = parse(buildingsYaml(), upgradesYaml()).config();
        assertTrue(c.enabled());
        assertEquals(1.15, c.costGrowth());
        assertEquals(1, c.basePower());
        assertEquals(0.05, c.critChance());
        assertEquals(10, c.critMultiplier());
        assertEquals(List.of("baker", "farm"), List.copyOf(c.buildings().keySet()));
        assertEquals(50, c.buildings().get("farm").unlockBaked());
        assertEquals(EffectType.BUILDING_MULTIPLIER, c.upgrades().get("bakerx2").type());
        assertEquals("baker", c.upgrades().get("mitts").unlockBuilding());
    }

    @Test
    void missingCritKeysWarnAndDisableCrits() throws Exception {
        Map<String, Object> u = upgradesYaml();
        u.put("click", map("base-power", 1));
        EconomyConfigParser.Result r = parse(buildingsYaml(), u);
        assertEquals(0, r.config().critChance());
        assertEquals(1, r.config().critMultiplier());
        assertEquals(1, r.warnings().size());
    }

    @Test
    void milestonesAreSortedDeduplicatedAndAnEmptyOverrideSwitchesThemOffForOneBuilding() throws Exception {
        Map<String, Object> b = map("cost-growth", 1.15, "milestones", List.of(25, 10, 10), "buildings", map(
                "baker", map("base-cost", 15, "base-cps", 0.1, "unlock", map("baked", 0)),
                "farm", map("base-cost", 100, "base-cps", 1, "milestones", List.of(), "unlock", map("baked", 50))));
        EconomyConfig c = parse(b, upgradesYaml()).config();
        assertEquals(List.of(10, 25), c.buildings().get("baker").milestones());
        assertEquals(List.of(), c.buildings().get("farm").milestones());
    }

    @Test
    void rejectsMilestonesThatAreNotListsOfWholeCounts() {
        Map<String, Object> zero = buildingsYaml();
        zero.put("milestones", List.of(0));
        assertTrue(error(zero, upgradesYaml()).contains("milestones"));
        Map<String, Object> text = buildingsYaml();
        text.put("milestones", "ten");
        assertTrue(error(text, upgradesYaml()).contains("must be a list"));
    }

    @Test
    void rejectsMissingOrEmptyBuildings() {
        assertTrue(error(map("cost-growth", 1.15), upgradesYaml()).contains("buildings"));
        assertTrue(error(map("cost-growth", 1.15, "buildings", map()), upgradesYaml()).contains("no buildings"));
    }

    @Test
    void rejectsBadCostGrowth() {
        Map<String, Object> b = buildingsYaml();
        b.put("cost-growth", 0.9);
        assertTrue(error(b, upgradesYaml()).contains("cost-growth"));
        b.remove("cost-growth");
        assertTrue(error(b, upgradesYaml()).contains("cost-growth"));
        b.put("cost-growth", "1.15");
        assertTrue(error(b, upgradesYaml()).contains("cost-growth"));
    }

    @Test
    void rejectsUnknownEffectTypeAndUnknownBuildingReferences() {
        Map<String, Object> u = upgradesYaml();
        u.put("upgrades", map("x", map("cost", 1, "effect", map("type", "explode", "value", 2))));
        assertTrue(error(buildingsYaml(), u).contains("effect.type"));
        u.put("upgrades", map("x", map("cost", 1, "effect", map("type", "building-multiplier", "building", "ghost", "value", 2))));
        assertTrue(error(buildingsYaml(), u).contains("ghost"));
        u.put("upgrades", map("x", map("cost", 1, "effect", map("type", "click-add", "value", 2),
                "unlock", map("building", "ghost", "owned", 1))));
        assertTrue(error(buildingsYaml(), u).contains("unlock.building"));
    }

}
