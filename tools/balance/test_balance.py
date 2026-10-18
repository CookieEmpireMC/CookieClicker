"""Tests for the balance tooling: crit maths, config validation, dead-zone window, CLI, golden values.

Run with `mise run tools-test` (unittest discover -s tools).
"""
import json
import math
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from balance import main as bm
from balance import model

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
        self.assertEqual(model.load(CONFIGS).upgrades, {})


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
        self.assertIn("6/6 targets pass", r.stdout)

    def test_check_fails_when_target_violated(self):
        d = self.tmp()
        shutil.copy(CONFIGS / "upgrades.yml", d)
        text = (CONFIGS / "buildings.yml").read_text(encoding="utf-8").replace("base-cost: 50", "base-cost: 5000")
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
