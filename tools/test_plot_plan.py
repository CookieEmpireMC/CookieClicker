"""Tests for tools/review/plot_plan.py (the review plot planner): its keep-out gives the same answers as the Java
DecorKeepOut for the shipped factory layout. The cells are the ones DecorKeepOutTest asserts.
"""

import os
import sys
import unittest

REPO = os.path.realpath(os.path.join(os.path.dirname(__file__), ".."))
HOUSES = os.path.join(REPO, "src", "main", "resources", "houses")
sys.path.insert(0, os.path.join(REPO, "tools", "review"))
import plot_plan  # noqa: E402


class FactoryKeepOutTest(unittest.TestCase):
    def setUp(self):
        self.design = plot_plan.Design("factory", plot_plan.load_yaml(os.path.join(HOUSES, "factory.yml")))
        self.layout = plot_plan.Layout(self.design.layout)
        self.keep = plot_plan.KeepOut(self.design, self.layout)

    def test_surface_reasons_match_the_java_keep_out(self):
        k = self.keep
        self.assertEqual("the fence ring", k.reason(0, 15))
        self.assertEqual("the fence ring", k.reason(15, 29))
        self.assertEqual("the factory", k.reason(10, 15))
        self.assertEqual("the walk, pedestal and panels", k.reason(13, 3))
        self.assertEqual("outside the plot", k.reason(-1, 5))
        self.assertIsNone(k.reason(2, 2))

    def test_ground_decor_is_kept_out_of_the_house_box_and_the_belt_only(self):
        x, _, z = self.design.to_world(self.layout.house_at, 1, 0, 1)
        self.assertEqual("the house design box", self.keep.reason(x, z, ground=True))
        self.assertIsNone(self.keep.reason(13, 3, ground=True), "the walk is open to ground decor")
        # the belt's start is inside the house box; its far end is outside it
        self.assertIsNotNone(self.layout.belt_to)
        bx, _, bz = self.layout.belt_to
        self.assertEqual("the belt", self.keep.reason(bx, bz, ground=True))

    def test_ground_depth_above_two_is_refused_like_the_parser_does(self):
        raw = plot_plan.load_yaml(os.path.join(HOUSES, "factory.yml"))
        raw["ground-depth"] = 3
        with self.assertRaises(ValueError):
            plot_plan.Design("factory", raw)

    def test_the_shipped_factory_plot_plans_without_problems(self):
        planned = plot_plan.plan(HOUSES, "factory", (0, -60, 0))
        self.assertEqual([], planned.problems)
        self.assertTrue(planned.blocks, "the plan has blocks")


if __name__ == "__main__":
    unittest.main()
