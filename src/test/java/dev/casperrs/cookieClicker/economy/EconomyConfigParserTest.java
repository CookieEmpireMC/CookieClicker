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
    void onlyOneCritKeyIsTreatedAsMissing() throws Exception {
        Map<String, Object> u = upgradesYaml();
        u.put("click", map("base-power", 1, "crit-chance", 0.5));
        assertEquals(0, parse(buildingsYaml(), u).config().critChance());
    }

    @Test
    void upgradesSectionIsOptional() throws Exception {
        Map<String, Object> u = map("click", map("base-power", 1));
        assertTrue(parse(buildingsYaml(), u).config().upgrades().isEmpty());
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
    void rejectsCritChanceAboveOne() {
        Map<String, Object> u = upgradesYaml();
        u.put("click", map("base-power", 1, "crit-chance", 1.5, "crit-multiplier", 10));
        assertTrue(error(buildingsYaml(), u).contains("crit-chance"));
    }

    @Test
    void rejectsNegativeNanAndNonNumericValues() {
        for (Object bad : new Object[] {-1, Double.NaN, Double.POSITIVE_INFINITY, "ten", true, null}) {
            Map<String, Object> b = map("cost-growth", 1.15, "buildings",
                    map("baker", map("base-cost", bad, "base-cps", 0.1)));
            assertTrue(error(b, upgradesYaml()).contains("base-cost"), String.valueOf(bad));
        }
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

    @Test
    void rejectsSectionsThatAreNotSections() {
        assertTrue(error(map("cost-growth", 1.15, "buildings", "oops"), upgradesYaml()).contains("must be a section"));
        Map<String, Object> u = upgradesYaml();
        u.put("upgrades", List.of("a"));
        assertTrue(error(buildingsYaml(), u).contains("must be a section"));
        assertTrue(error(buildingsYaml(), map()).contains("click"));
    }

    @Test
    void effectTypeLookup() {
        for (EffectType t : EffectType.values()) {
            assertEquals(t, EffectType.fromConfigName(t.configName()).orElseThrow());
        }
        assertTrue(EffectType.fromConfigName("nope").isEmpty());
        assertTrue(EffectType.fromConfigName("null").isEmpty());
    }
}
