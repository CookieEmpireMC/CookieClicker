"""Tests for the balance tooling: crit maths, config validation, milestones, buyer, dead-zone window, baked and
min-seconds targets, CLI, golden values.

Run with `mise run tools-test` (unittest discover -s tools).
"""
import json
import math
import random
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from balance import main as bm
from balance import model
from balance import sim

ROOT = Path(__file__).resolve().parents[2]
CONFIGS = ROOT / "src" / "main" / "resources"
MAIN_PY = ROOT / "tools" / "balance" / "main.py"

BUILDINGS = "cost-growth: 1.15\nbuildings:\n  a:\n    base-cost: 50\n    base-cps: 0.5\n"


def write_config(directory: Path, click: str = "base-power: 1", upgrades: str = "upgrades: {}",
                 buildings: str = BUILDINGS) -> Path:
    (directory / "buildings.yml").write_text(buildings, encoding="utf-8")
    (directory / "upgrades.yml").write_text(f"click:\n  {click}\n{upgrades}\n", encoding="utf-8")
    return directory


class TempDirCase(unittest.TestCase):
    def tmp(self) -> Path:
        d = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, d, ignore_errors=True)
        return d


class CritMathTest(TempDirCase):
    def test_real_config_crit_factor(self):
        eco = model.load(CONFIGS)
        self.assertAlmostEqual(model.crit_factor(eco), 1.45, places=12)
        self.assertEqual(eco.crit_chance, 0.05)
        self.assertEqual(eco.crit_multiplier, 10)

    def test_click_values_and_expected(self):
        eco = model.load(CONFIGS)
        s = model.State()
        self.assertEqual(model.click_value(eco, s, False), 1.0)
        self.assertEqual(model.click_value(eco, s, True), 10.0)
        self.assertAlmostEqual(model.expected_click(eco, s), 1.45, places=12)

    def test_crit_multiplies_cps_fraction_part(self):
        d = write_config(self.tmp(), "base-power: 1\n  crit-chance: 0.1\n  crit-multiplier: 5", """upgrades:
  u:
    cost: 1
    effect: { type: click-cps-fraction, value: 0.1 }
""")
        eco = model.load(d)
        state = model.State(owned={"a": 20}, upgrades={"u"})
        cps = 20 * 0.5
        power = 1 + 0.1 * cps                       # 2.0
        self.assertAlmostEqual(model.click_power(eco, state), power)
        self.assertAlmostEqual(model.click_value(eco, state, True), power * 5)   # whole click, fraction included
        self.assertAlmostEqual(model.expected_click(eco, state), power * (1 + 0.1 * 4))

    def test_missing_crit_fields_mean_no_crits(self):
        eco = model.load(write_config(self.tmp()))
        self.assertEqual(eco.crit_chance, 0.0)
        self.assertEqual(eco.crit_multiplier, 1.0)
        self.assertEqual(model.crit_factor(eco), 1.0)
        self.assertEqual(model.click_value(eco, model.State(), True), 1.0)

    def test_invalid_crit_chance(self):
        for bad in ("1.5", "-0.1"):
            with self.subTest(chance=bad), self.assertRaises(model.ConfigError):
                model.load(write_config(self.tmp(), f"base-power: 1\n  crit-chance: {bad}"))

    def test_invalid_crit_multiplier(self):
        with self.assertRaises(model.ConfigError):
            model.load(write_config(self.tmp(), "base-power: 1\n  crit-multiplier: 0.5"))

    def test_boundary_values_accepted(self):
        eco = model.load(write_config(self.tmp(), "base-power: 1\n  crit-chance: 1\n  crit-multiplier: 1"))
        self.assertEqual(model.crit_factor(eco), 1.0)

    def test_empty_upgrades_map_is_valid(self):
        d = write_config(self.tmp())  # upgrades: {} (the shipped file now has upgrades)
        self.assertEqual(model.load(d).upgrades, {})


class SeriesMathTest(TempDirCase):
    """model.md "Bulk cost and Max": series_cost / series_max (the simulator side of the Java BulkMath)."""

    def test_k1_is_the_single_cost_and_the_batch_is_rounded_once(self):
        for base, growth in [(15, 1.15), (50, 1.15), (1, 1.25), (10, 1.0)]:
            for owned in range(0, 101, 10):
                self.assertEqual(model.series_cost(base, growth, owned, 1), math.ceil(base * growth ** owned))
        # 15 × (1.15¹⁰ − 1) / 0.15 = 304.56 → 305 (ten singles rounded separately would be 306)
        self.assertEqual(model.series_cost(15, 1.15, 0, 10), 305)
        self.assertEqual(model.series_cost(15, 1.15, 0, 0), 0)

    def test_series_max_matches_a_brute_force_count(self):
        rnd = random.Random(20261021)
        for _ in range(1500):
            base = 1 + rnd.randrange(100_000)
            growth = rnd.choice([1.0, 1.05, 1.15, 1.25, 2.0])
            owned = rnd.randrange(80)
            cap = rnd.randrange(300)
            pouch = 10 ** (rnd.random() * 12)
            expected = next((k for k in range(min(cap, model.MAX_BULK), 0, -1)
                             if model.series_cost(base, growth, owned, k) <= pouch), 0)
            got = model.series_max(base, growth, owned, cap, pouch)
            self.assertEqual(got, expected, (base, growth, owned, cap, pouch))

    def test_series_max_stops_at_the_cap(self):
        self.assertEqual(model.series_max(10, 1.15, 0, 3, 1e18), 3)
        self.assertEqual(model.series_max(10, 1.15, 0, 0, 1e18), 0)  # at max-level
        self.assertEqual(model.series_max(1, 1.0, 0, 10**9, 1e300), model.MAX_BULK)  # hard cap, linear costs


class DeadZoneTest(unittest.TestCase):
    ZONES = [(10, 30), (850, 200), (1000, 500)]

    def result(self, zones):
        return bm.Result(profile=bm.Profile("p", 1), dead_zones=zones)

    def test_unwindowed(self):
        self.assertEqual(bm.longest_dead_zone(self.result(self.ZONES)), 500)

    def test_windowed_cuts_zone_at_edge(self):
        self.assertEqual(bm.longest_dead_zone(self.result(self.ZONES), 15), 51)

    def test_empty(self):
        self.assertEqual(bm.longest_dead_zone(self.result([])), 0)
        self.assertEqual(bm.longest_dead_zone(self.result([]), 15), 0)

    def test_zone_fully_outside_window_ignored(self):
        self.assertEqual(bm.longest_dead_zone(self.result([(2000, 99)]), 15), 0)


class CliTest(TempDirCase):
    def run_check(self, config_dir: Path) -> subprocess.CompletedProcess:
        return subprocess.run([sys.executable, str(MAIN_PY), "check", "--config-dir", str(config_dir)],
                              cwd=ROOT, capture_output=True, text=True, timeout=300)

    def test_check_passes_on_real_configs(self):
        r = self.run_check(CONFIGS)
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        m = re.search(r"(\d+)/(\d+) targets pass", r.stdout)
        self.assertIsNotNone(m, r.stdout)
        self.assertGreater(int(m.group(2)), 0)
        self.assertEqual(m.group(1), m.group(2))

    def test_check_fails_when_target_violated(self):
        d = self.tmp()
        shutil.copy(CONFIGS / "upgrades.yml", d)
        # every building 1000x dearer: the early max-seconds targets (first-villager-baker, no-dead-zones-early) fail
        real = (CONFIGS / "buildings.yml").read_text(encoding="utf-8")
        text = re.sub(r"base-cost: ([0-9.]+)", lambda m: f"base-cost: {float(m.group(1)) * 1000:.1f}", real)
        self.assertNotEqual(text, real)
        (d / "buildings.yml").write_text(text, encoding="utf-8")
        r = self.run_check(d)
        self.assertEqual(r.returncode, 1, r.stdout + r.stderr)
        self.assertIn("FAIL", r.stdout)


    def test_check_fails_on_invalid_config(self):
        r = self.run_check(write_config(self.tmp(), "base-power: 1\n  crit-chance: 2"))
        self.assertEqual(r.returncode, 1)
        self.assertIn("Balance config error", r.stdout)

    def test_check_writes_nothing(self):
        before = sorted(p.name for p in (ROOT / "docs" / "balance").iterdir())
        self.run_check(CONFIGS)
        self.assertEqual(before, sorted(p.name for p in (ROOT / "docs" / "balance").iterdir()))



class MilestoneTest(TempDirCase):
    """model.md "Milestones": factor = 2 ^ reached, per building (own list or the top-level one)."""
    ONE = "  a:\n    base-cost: 10\n    base-cps: 1\n"

    def load(self, text: str) -> model.Economy:
        return model.load(write_config(self.tmp(), buildings=text))

    def test_factor_at_the_boundaries(self):
        eco = self.load("cost-growth: 1.15\nmilestones: [10, 25, 50, 100, 150, 200]\nbuildings:\n" + self.ONE)
        expected = {9: 1, 10: 2, 24: 2, 25: 4, 199: 32, 200: 64, 250: 64}
        for owned, factor in expected.items():
            with self.subTest(owned=owned):
                self.assertEqual(model.milestone_factor(eco, "a", owned), factor)

    def test_invalid_milestones_rejected(self):
        for bad in ("[10, 10]", "[25, 10]", "[10.5]", "['10']", "[true]", "[0]", "x"):
            with self.subTest(milestones=bad), self.assertRaises(model.ConfigError):
                self.load(f"cost-growth: 1.15\nmilestones: {bad}\nbuildings:\n" + self.ONE)

    def test_per_building_override_replaces_the_top_level_list(self):
        eco = self.load("cost-growth: 1.15\nmilestones: [10, 25]\nbuildings:\n" + self.ONE
                        + "  b:\n    base-cost: 10\n    base-cps: 1\n    milestones: [5]\n"
                        + "  c:\n    base-cost: 10\n    base-cps: 1\n    milestones: []\n")
        self.assertEqual(model.milestones_of(eco, "a"), (10, 25))
        self.assertEqual(model.milestones_of(eco, "b"), (5,))
        self.assertEqual(model.milestones_of(eco, "c"), ())
        self.assertEqual(model.milestone_factor(eco, "a", 10), 2)
        self.assertEqual(model.milestone_factor(eco, "b", 5), 2)
        self.assertEqual(model.milestone_factor(eco, "c", 100), 1)
        with self.assertRaises(model.ConfigError):
            self.load("cost-growth: 1.15\nbuildings:\n  a:\n    base-cost: 10\n    base-cps: 1\n"
                      "    milestones: [5, 5]\n")


class MilestonePushTest(TempDirCase):
    """sim.best: the copies that reach a building's next milestone (at most MILESTONE_LOOKAHEAD) are one choice."""
    ONE = "cost-growth: 1.15\nbuildings:\n  a:\n    base-cost: 10\n    base-cps: 1\n"

    def best(self, text: str):
        eco = model.load(write_config(self.tmp(), buildings=text))
        return sim.best(eco, model.State(), 0.0)

    def test_push_wins_when_the_doubling_pays(self):
        # one copy: 10 cookies for +1 cps (score 10). Two copies to reach milestone 2: 10 + 12 = 22 cookies
        # for 2 cps × 2 = 4 cps (score 5.5), so the push is the choice
        score, total, kind, item, count = self.best("milestones: [2]\n" + self.ONE)
        self.assertEqual((total, kind, item, count), (22, "building", "a", 2))
        self.assertAlmostEqual(score, 5.5)

    def test_no_push_beyond_the_lookahead(self):
        _, total, _, _, count = self.best("milestones: [10]\n" + self.ONE)
        self.assertEqual((total, count), (10, 1))

    def test_push_uses_the_building_own_milestones(self):
        # the top-level [10] is out of reach, the building's own [2] is not
        text = "milestones: [10]\n" + self.ONE + "    milestones: [2]\n"
        _, total, _, _, count = self.best(text)
        self.assertEqual((total, count), (22, 2))


class BakedAndMinSecondsTest(unittest.TestCase):
    """targets.md metrics `baked` (cookies baked in total) and the `min-seconds` floor."""

    @staticmethod
    def reached_at(t: int, total: float = 10.0) -> list[float]:
        """baked[t - 1] == total: the target is reached at second t."""
        return [0.0] * (t - 1) + [total]

    @staticmethod
    def ok(target: dict, baked: list[float]) -> bool:
        result = bm.Result(profile=bm.Profile("p", 1), baked=baked)
        rows = bm.evaluate(None, {"targets": [{"id": "t", "profiles": ["p"], **target}]}, {"p": result})
        return rows[0]["ok"]

    def test_baked_time_is_the_first_second_at_or_above(self):
        r = bm.Result(profile=bm.Profile("p", 1), baked=[1.0, 5.0, 10.0])
        self.assertEqual(bm.baked_time(r, 5.0), 2)
        self.assertEqual(bm.baked_time(r, 5.5), 3)
        self.assertIsNone(bm.baked_time(r, 11.0))

    def test_min_only_never_reached_passes(self):
        self.assertTrue(self.ok({"metric": "baked", "cookies": 10, "min-seconds": 5}, [0.0] * 5))

    def test_min_only_reached_before_the_floor_fails(self):
        target = {"metric": "baked", "cookies": 10, "min-seconds": 5}
        self.assertFalse(self.ok(target, self.reached_at(4)))
        self.assertTrue(self.ok(target, self.reached_at(5)))   # the floor itself is allowed

    def test_max_only_never_reached_fails(self):
        self.assertFalse(self.ok({"metric": "baked", "cookies": 10, "max-seconds": 100}, [0.0] * 5))

    def test_min_and_max_window(self):
        target = {"metric": "baked", "cookies": 10, "min-seconds": 2, "max-seconds": 5}
        self.assertTrue(self.ok(target, self.reached_at(3)))
        self.assertFalse(self.ok(target, self.reached_at(1)))
        self.assertFalse(self.ok(target, self.reached_at(6)))
        self.assertFalse(self.ok(target, [0.0] * 5))

    def test_target_needs_a_limit(self):
        with self.assertRaises(bm.ConfigError):   # evaluate() raises the ConfigError that main.py imports
            bm.evaluate(None, {"targets": [{"id": "t", "profiles": ["p"], "metric": "baked", "cookies": 10}]},
                        {"p": bm.Result(profile=bm.Profile("p", 1), baked=[])})


class GoldenValuesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        eco = bm.load(CONFIGS)
        targets = bm.load_targets(ROOT / "docs" / "balance" / "targets.md")
        horizon = int(float(targets.get("horizon-minutes", 240)) * 60)
        results = {p.name: bm.simulate(eco, p, horizon) for p in bm.profiles_from(targets)}
        cls.eco = eco
        cls.regenerated = json.loads(json.dumps(bm.golden_values(eco, results)))
        cls.committed = json.loads((ROOT / "docs" / "balance" / "golden-values.json").read_text(encoding="utf-8"))

    def test_regenerates_identically(self):
        self.assertEqual(self.regenerated, self.committed)

    def test_click_keys_present(self):
        click = self.committed["click"]
        for key in ("base-power", "crit-chance", "crit-multiplier", "expected-factor", "base-state"):
            self.assertIn(key, click)
        for key in ("click-power", "normal-click", "crit-click", "expected-click"):
            self.assertIn(key, click["base-state"])
        self.assertAlmostEqual(click["expected-factor"], 1.45, places=12)
        self.assertEqual(click["base-state"]["crit-click"], 10.0)

    def test_state_snapshots_have_click_keys(self):
        self.assertTrue(self.committed["states"])
        for s in self.committed["states"]:
            for key in ("click-power", "normal-click", "crit-click", "expected-click"):
                self.assertIn(key, s)
            self.assertTrue(math.isclose(s["expected-click"], s["click-power"] * 1.45, rel_tol=1e-9))

    def test_costs_match_formula(self):
        for bid, b in self.eco.buildings.items():
            costs = self.committed["costs"][bid]["single"]
            self.assertEqual(len(costs), 101)
            self.assertEqual(costs[0], b.base_cost)
            self.assertEqual(costs[100], math.ceil(b.base_cost * self.eco.cost_growth ** 100))


if __name__ == "__main__":
    unittest.main()
