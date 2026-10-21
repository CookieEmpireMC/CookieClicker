package dev.casperrs.cookieClicker.economy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;

/** Shared fixtures: a small balance config and a fixed random source. */
final class EconomyTestSupport {

    private EconomyTestSupport() {
    }

    /** nextDouble() always returns {@code value}. */
    static RandomGenerator fixed(double value) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public double nextDouble() {
                return value;
            }
        };
    }

    static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    static Map<String, Object> buildingsYaml() {
        return map("cost-growth", 1.15, "buildings", map(
                "baker", map("base-cost", 15, "base-cps", 0.1, "unlock", map("baked", 0)),
                "farm", map("base-cost", 100, "base-cps", 1, "unlock", map("baked", 50))));
    }

    static Map<String, Object> upgradesYaml() {
        return map("click", map("base-power", 1, "crit-chance", 0.05, "crit-multiplier", 10),
                "upgrades", map(
                        "mitts", map("cost", 100, "effect", map("type", "click-multiplier", "value", 2),
                                "unlock", map("building", "baker", "owned", 1)),
                        "plus", map("cost", 50, "effect", map("type", "click-add", "value", 1)),
                        "frac", map("cost", 500, "effect", map("type", "click-cps-fraction", "value", 0.1),
                                "unlock", map("baked", 1000)),
                        "bakerx2", map("cost", 200, "effect", map("type", "building-multiplier", "building", "baker", "value", 2)),
                        "global", map("cost", 900, "effect", map("type", "global-multiplier", "value", 1.5))));
    }

    /** Like {@link #upgradesYaml()} plus repeatable upgrades: strength (+1 CPC/level, max 3), focus (×2/level, max 5). */
    static EconomyConfig repeatableConfig() {
        Map<String, Object> upgrades = map("click", map("base-power", 1, "crit-chance", 0.05, "crit-multiplier", 10),
                "upgrades", map(
                        "mitts", map("cost", 100, "effect", map("type", "click-multiplier", "value", 2),
                                "unlock", map("building", "baker", "owned", 1)),
                        "strength", map("cost", 200, "cost-growth", 1.25, "max-level", 3,
                                "effect", map("type", "click-add", "value", 1),
                                "unlock", map("building", "baker", "owned", 1)),
                        "focus", map("cost", 1000, "cost-growth", 2, "max-level", 5,
                                "effect", map("type", "click-multiplier", "value", 2))));
        try {
            return EconomyConfigParser.parse(buildingsYaml(), upgrades).config();
        } catch (EconomyConfigException e) {
            throw new AssertionError(e);
        }
    }

    static EconomyConfig config() {
        try {
            return EconomyConfigParser.parse(buildingsYaml(), upgradesYaml()).config();
        } catch (EconomyConfigException e) {
            throw new AssertionError(e);
        }
    }

    static CookiePouch pouch(double pouch, double baked, Map<String, Integer> owned, String... upgrades) {
        return CookiePouch.fromSnapshot(new PouchSnapshot(pouch, baked, owned, Set.of(upgrades)));
    }

    static List<String> noWarnings() {
        return List.of();
    }
}
