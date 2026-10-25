"""Tests for tools/review/farm_plan.py: a place file is planned alone on its cell [0, 0, 0] (ground-depth up to 4),
and a countryside whose origin puts it west of the region's min corner is reported, as FarmPlan.plan refuses it.
"""

import json
import os
import sys
import tempfile
import unittest

REPO = os.path.realpath(os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.join(REPO, "tools", "review"))
import farm_plan  # noqa: E402


def _design(size, ground, layers, **extra):
    """A design file; JSON is valid YAML, so plot_plan.load_yaml reads it."""
    raw = {"size": size, "ground-depth": ground, "palette": {"S": "minecraft:stone"}, "layers": layers}
    raw.update(extra)
    return raw


class FarmPlanTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.houses = self.dir.name

    def tearDown(self):
        self.dir.cleanup()

    def _write(self, name, raw):
        with open(os.path.join(self.houses, name + ".yml"), "w", encoding="utf-8") as f:
            json.dump(raw, f)

    def test_a_place_file_with_four_ground_layers_is_planned_alone_on_its_cell(self):
        # six layers, four of them ground: layer 0 is four blocks below the cell [0, 0, 0] at y 60
        self._write("well", _design([1, 6, 1], 4, [["S"]] * 6))
        p = farm_plan.plan(self.houses, "well", (10, 60, 20))
        self.assertEqual([], p.problems)
        self.assertEqual([(10, 60, 20)], [target for _, target in p.places])
        self.assertEqual({(10, y, 20) for y in range(56, 62)}, set(p.blocks))
        self.assertEqual((10, 20, 10, 20), p.box)

    def test_a_viewpoint_map_marks_a_place_file_and_a_list_marks_a_plot_house(self):
        self.assertTrue(farm_plan.is_farm_design("well", {"points": {"viewpoint": {"at": [1, 1, 1]}}}))
        self.assertFalse(farm_plan.is_farm_design("factory", {"points": {"viewpoint": [1, 1, 1]}}))

    def test_a_countryside_origin_west_of_the_region_is_reported(self):
        self._write("countryside", _design([4, 6, 4], 1, [["SSSS"] * 4] * 6, origin=[2, 0, 0]))
        p = farm_plan.plan(self.houses, "countryside", (100, 64, 200))
        self.assertTrue(any("origin" in problem for problem in p.problems), p.problems)


if __name__ == "__main__":
    unittest.main()
