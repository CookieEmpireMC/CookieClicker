"""Tests for the farm kits (lib/farm_kits.py) and the L-F lint (lib/farm_lint.py).

Run: python -m unittest discover -s design/scripts/lib/tests   (also run by design/scripts/farm_kit_tests.py, i.e. by
`mise run assets`). Small grids, fixed seeds.
"""
import sys
import unittest
from pathlib import Path

LIB = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(LIB))

import blocks as B  # noqa: E402
import farm_kits as K  # noqa: E402
import farm_lint as FL  # noqa: E402
from blocks import plain as P, sl, st  # noqa: E402
from build import Build, Ramp, Wall  # noqa: E402
from build_lint import Notes, bid  # noqa: E402

RAMP = Ramp(P("stone_bricks"), [(P("mossy_stone_bricks"), .3, None), (P("cobblestone"), .2, None)], seed=1)
ROOF = dict(field="dark_oak", eave="spruce", rake="spruce", ridge=P("stripped_dark_oak_log[axis=x]"), cap=sl("dark_oak"))
STONES = ("stone_bricks", "mossy_stone_bricks", "cobblestone")


def roof_build(ks, rect=(1, 1, 15, 9), pitch=K.PITCH_STEEP):
    b = Build((20, 24, 20), ground=2)
    r = K.hipped_roof(b, rect, 2, ks, pitch=pitch, riser=P("dark_oak_planks"), name="t", **ROOF)
    return b, r


class RoofTests(unittest.TestCase):
    def test_gable_levels_and_ridge(self):
        b, r = roof_build({"N": 0, "S": 0, "W": None, "E": None})
        self.assertEqual(r["lmax"], 4)
        self.assertEqual(r["ridge_y"], 2 + K.PITCH_STEEP[3])
        self.assertEqual(r["surface"][(5, 5)], r["ridge_y"] + 1)
        self.assertTrue(bid(b.get(5, r["ridge_y"], 5)).endswith("_log"))
        self.assertIn("facing=south", b.get(5, 2, 1))
        self.assertEqual(len(b.notes.gables), 2)
        self.assertEqual(b.notes.roofs[0]["overhang"], 1)

    def test_steep_pitch_fills_2_high_risers(self):
        b, r = roof_build({"N": 0, "S": 0, "W": None, "E": None})
        self.assertEqual(bid(b.get(5, 2 + K.PITCH_STEEP[2] - 1, 3)), "dark_oak_planks")

    def test_half_hip_end_is_a_hip_face(self):
        b, r = roof_build({"N": 0, "S": 0, "W": None, "E": 3})
        self.assertEqual(r["levels"][(15, 5)], 3)
        self.assertEqual(r["levels"][(14, 5)], 4)
        self.assertIn("facing=west", b.get(15, 2 + K.PITCH_STEEP[3], 5))
        self.assertEqual(sum(1 for g in b.notes.gables if g["side"] == "E"), 0)

    def test_pyramid_single_peak(self):
        b = Build((12, 20, 12), ground=2)
        r = K.pyramid_roof(b, (1, 1, 9, 9), 2, riser=P("dark_oak_planks"), name="p", **ROOF)
        self.assertEqual([c for c, level in r["levels"].items() if level == r["lmax"]], [(5, 5)])

    def test_deterministic(self):
        a, _ = roof_build({"N": 0, "S": 0, "W": None, "E": 3})
        c, _ = roof_build({"N": 0, "S": 0, "W": None, "E": 3})
        self.assertEqual(a.g, c.g)


class PartTests(unittest.TestCase):
    def setUp(self):
        self.b = Build((24, 14, 20), ground=4)
        self.wall = Wall(self.b, "north", "east", (0, 10))

    def test_pent_skirt_registers_height(self):
        K.pent_skirt(self.b, self.wall, 2, 8, 5, "spruce")
        self.assertEqual(sum(1 for v in self.b.g.values() if "spruce_stairs" in v), 7)
        self.assertIn(5, self.b.notes.roof_heights)
        self.assertIn("facing=south", self.b.get(2, 5, 9))

    def test_dormer_stops_at_main_roof_and_registers_break(self):
        b, r = roof_build({"N": 0, "S": 0, "W": None, "E": None})
        wall = Wall(b, "north", "east", (0, 2))
        K.dormer(b, r, wall, 8, 0, 4, wall_mat=lambda x, y, z: P("oak_planks"), trim="dark_oak", roof_mat="spruce")
        self.assertEqual(len(b.notes.breaks), 1)
        self.assertEqual(bid(b.get(7, 4, 2)), "oak_planks")
        self.assertIsNone(b.g.get((8, 4, 8)))

    def test_wheel_slot_clear_interior_and_fallback(self):
        b = Build((24, 14, 20), ground=4)
        wall = Wall(b, "north", "east", (0, 10))
        slot = K.wheel_slot(b, wall, 12, 0, radius=3, depth=3, ramp=RAMP, y_floor=-4, y_top=4)
        self.assertEqual(slot["axle"], (12, 0, 11))
        for (x, y, z) in slot["interior"]:
            if (x, y, z) not in slot["fallback"]:
                self.assertEqual(bid(b.get(x, y, z)), "air")
        self.assertIn(bid(b.get(8, 0, 10)), STONES)
        self.assertTrue(all(y >= -1 for (_, y, _) in slot["fallback"]))
        self.assertEqual(bid(b.get(12, 4, 10)), "stripped_dark_oak_log")

    def test_mill_race_encloses_water(self):
        b = Build((16, 8, 12), ground=2)
        w = K.mill_race(b, [(x, 5) for x in range(2, 10)], 0, -1, ramp=RAMP)
        self.assertEqual(len(w), 8)
        self.assertIn(bid(b.get(5, -1, 5)), STONES)
        self.assertIn(bid(b.get(5, 0, 4)), STONES)
        g = dict(b.g)
        for z in range(12):
            for x in range(16):
                for y in (-2, -1):
                    g.setdefault((x, y, z), P("dirt"))
        self.assertEqual(FL.lf4_clearance(g, {}, b.notes, (16, 8, 12), 2), [])

    def test_retaining_wall_and_coping(self):
        b = Build((16, 12, 12), ground=4)
        self.assertEqual(K.retaining_wall(b, 2, 3, 9, 3, -4, 1, ramp=RAMP), 8 * 6)
        self.assertEqual(bid(b.get(5, 2, 3)), "stone_brick_slab")

    def test_stone_bridge_deck_and_parapet(self):
        b = Build((16, 12, 12), ground=2)
        deck = K.stone_bridge(b, 3, 2, 6, axis="z", y_deck=2, width=3, open_under=frozenset({(4, 4), (4, 5)}))
        self.assertEqual(len(deck), 18)
        self.assertIn("stairs", b.get(3, 2, 2))
        self.assertTrue(b.get(3, 3, 4).startswith("@"))
        self.assertIsNone(b.get(4, 1, 4))

    def test_staddle_stones_three_parts(self):
        b = Build((12, 8, 12), ground=2)
        K.staddle_stones(b, [(2, 2), (5, 2)])
        self.assertEqual([bid(b.get(2, y, 2)) for y in (-1, 0, 1)], ["mossy_cobblestone", "stone_brick_wall", "stone_brick_slab"])

    def test_pond_water_levels(self):
        b = Build((16, 8, 16), ground=4)
        cells = {(x, z) for x in range(4, 12) for z in range(4, 12)}
        w = K.pond(b, cells, -2, -4, bed=RAMP, rim=RAMP, lily=0)
        self.assertEqual(bid(b.get(7, -2, 7)), "water")
        self.assertEqual(bid(b.get(7, -3, 7)), "water")
        self.assertNotEqual(bid(b.get(4, -3, 4)), "water")
        self.assertEqual(len(w), 6 * 6 * 2 + 28)

    def test_terrace_and_cut_and_fill(self):
        h = {(x, z): -1 for x in range(12) for z in range(12)}
        K.cut_and_fill(h, (4, 4, 7, 7), 1, 1)
        self.assertEqual(h[(5, 5)], 1)
        self.assertEqual(h[(3, 5)], 0)
        self.assertEqual(h[(2, 5)], -1)
        K.terrace(h, (0, 0, 1, 1), 2)
        self.assertEqual(h[(1, 1)], 2)
        b = Build((12, 6, 12), ground=4)
        K.height_field_ground(b, {(1, 1): 1}, top=Ramp(B.grass(), []), sub=Ramp(P("dirt"), []), y_floor=-4)
        self.assertEqual([bid(b.get(1, y, 1)) for y in (-4, 0, 1)], ["dirt", "dirt", "grass_block"])

    def test_coped_gable_caps(self):
        b = Build((12, 12, 8), ground=1)
        wall = Wall(b, "south", "east", (0, 4))
        K.coped_gable(b, wall, 2, 6, 3, lambda u: 6 - abs(u - 4))
        self.assertEqual(b.notes.gables[0]["finish"], "coping")
        self.assertEqual(bid(b.get(4, 6, 4)), "stone_brick_slab")

    def test_simple_kits(self):
        b = Build((20, 10, 12), ground=2)
        w = Wall(b, "north", "east", (0, 6))
        K.vent_slits(w, 2, 2, 3)
        self.assertEqual(sum(1 for v in b.g.values() if v == "@iron_bars"), 6)
        K.board_and_batten(w, 2, 8, 1, 3, board=lambda u, y: P("spruce_planks"), batten=lambda u, y: P("dark_oak_log[axis=y]"))
        self.assertIn("dark_oak_log", b.get(5, 2, 6))
        K.nest_box_row(w, 10, 1, 4)
        self.assertEqual(sum(1 for v in b.g.values() if "spruce_trapdoor" in v), 4)
        K.hurdle_fence(b, [(x, 9) for x in range(2, 9)])
        K.stone_pillar_fence(b, [(x, 10) for x in range(2, 11)], every=4)
        self.assertEqual(b.get(6, 1, 10), "@cobblestone_wall")
        K.drying_rack(b, 12, 8, 6)
        K.furrow_field(b, 12, 1, 6, 4, ["wheat", "carrots"])
        self.assertEqual(bid(b.get(14, -1, 2)), "coarse_dirt")
        self.assertEqual(K.orchard_grid(0, 0, 20, 12, 5, 1, seed=3), K.orchard_grid(0, 0, 20, 12, 5, 1, seed=3))
        K.buttress(b, w, 14, 5, ramp=RAMP)
        K.awning(b, w, 2, 6, 3)

    def test_stubs_raise_not_implemented(self):
        for f in (K.round_building, K.cone_roof, K.hay_roll_thatch, K.eyebrow_dormer):
            with self.assertRaises(NotImplementedError):
                f()

    def test_catslide_drops_half_a_block_per_row(self):
        b = Build((16, 12, 8), ground=1)
        r = K.catslide_roof(b, (2, 1, 9, 5), "W", 6, field="brick", eave="nether_brick")
        self.assertEqual([r["surface"][(x, 3)] for x in range(2, 10)], [6, 6, 5, 5, 4, 4, 3, 3])
        self.assertEqual([bid(b.get(x, r["surface"][(x, 3)], 3)) for x in (2, 3)], ["brick_stairs", "brick_slab"])
        self.assertEqual(bid(b.get(9, 3, 3)), "nether_brick_slab")
        self.assertEqual(b.notes.roof_info[0]["form"], "catslide")
        self.assertEqual(K.fill_under(b, r["surface"], [(4, 3)], 1, lambda x, y, z: P("stone_bricks")), 4)

    def test_corbie_gable_steps_and_caps(self):
        b = Build((16, 14, 8), ground=1)
        wall = Wall(b, "north", "east", (0, 4))
        caps = K.corbie_gable(b, wall, 8, 3, 5, {0: 11, 1: 10, 2: 9, 3: 8}, body=lambda x, y, z: P("stone_bricks"))
        self.assertEqual(len(caps), 7)
        self.assertEqual([c[1] for c in caps], [9, 10, 11, 12, 11, 10, 9])
        self.assertEqual(bid(b.get(8, 12, 4)), "stone_brick_slab")
        self.assertEqual(bid(b.get(5, 8, 4)), "stone_bricks")
        self.assertEqual(b.notes.gables[0]["finish"], "corbie")

    def test_barn_door_double_braced_with_lintel(self):
        b = Build((20, 10, 8), ground=1)
        wall = Wall(b, "north", "east", (0, 4))
        d = K.barn_door_double(b, wall, 10, 1, leaf=lambda x, y, z: P("spruce_planks"), brace=P("dark_oak_planks"))
        self.assertEqual(len(d["cells"]), 20)
        self.assertEqual(len(d["lintel"]), 7)                      # the 5 cells plus a jamb cell each side
        self.assertTrue(all(bid(b.get(*c)) != "air" for c in d["lintel"]))
        self.assertEqual(bid(b.get(10, 2, 3)), "stripped_dark_oak_log")        # stile proud of the plane (north-facing)
        self.assertEqual(bid(b.get(8, 2, 4)), "dark_oak_planks")               # outer brace low
        self.assertEqual(bid(b.get(9, 3, 4)), "dark_oak_planks")               # inner brace high
        g = {p: v for p, v in b.g.items()}
        self.assertEqual(FL.lf5_doors(g, b.notes), [])

    def test_cross_gable_clipped_by_main_roof(self):
        b = Build((24, 24, 24), ground=1)
        main = K.hipped_roof(b, (1, 1, 21, 17), 4, {"N": 0, "S": 0, "W": None, "E": None}, riser=P("dark_oak_planks"),
                             name="m", **ROOF)
        before = dict(b.g)
        porch = K.hipped_roof(b, (8, 0, 14, 8), 4, {"W": 0, "E": 0, "N": None, "S": None}, riser=P("dark_oak_planks"),
                              name="p", clip=main, **{**ROOF, "ridge": P("spruce_planks")})
        for (x, y, z) in porch["cells"]:
            self.assertGreater(y, main["surface"].get((x, z), -99) - 1)
        self.assertTrue(all(b.g.get(p) == v for p, v in before.items() if p[2] >= 5 and p[1] >= main["surface"].get((p[0], p[2]), 0)))


def small_grid():
    g = {}
    for x in range(14):
        for z in range(14):
            g[(x, -1, z)] = P("cobblestone") if (x + z) % 3 else P("andesite")
    return g


POINTS = {"viewpoint": {"at": [7, 0, 4], "yaw": 0, "pitch": 10}, "yard": {"from": [2, 2], "to": [12, 8]},
          "hologram": [7, 1, 6], "panel": {"at": [10, 0, 6], "yaw": 180}, "ripe": [4, 0, 6]}


class LintTests(unittest.TestCase):
    def test_f12_roof_families(self):
        b, r = roof_build({"N": 0, "S": 0, "W": None, "E": None})
        g = dict(b.g)
        self.assertEqual(FL.lf12_roof_families(g, b.notes), [])
        b2 = Build((20, 24, 20), ground=2)
        K.hipped_roof(b2, (1, 1, 15, 9), 2, {"N": 0, "S": 0, "W": None, "E": None}, riser=P("dark_oak_planks"), name="t",
                      ramp_pick=lambda x, y, z: "brick" if (x + z) % 2 else "mud_brick", **ROOF)
        self.assertTrue(any("L-F12" in e for e in FL.lf12_roof_families(dict(b2.g), b2.notes)))

    def test_f3_yard_ok_and_blocked(self):
        g = small_grid()
        self.assertEqual(FL.lf3_yard(g, POINTS, Notes()), [])
        g[(6, 0, 5)] = P("barrel[facing=up,open=false]")
        self.assertTrue(any("clear yard" in e for e in FL.lf3_yard(g, POINTS, Notes())))
        small = dict(POINTS, yard={"from": [2, 2], "to": [8, 6]})
        self.assertTrue(any("smaller" in e for e in FL.lf3_yard(small_grid(), small, Notes())))

    def test_f1_volumes(self):
        n = Notes()
        n.volumes = [{"name": "a", "footprint": (0, 0, 9, 9), "top": 12}, {"name": "b", "footprint": (10, 0, 12, 3), "top": 5}]
        self.assertTrue(FL.lf1_volumes(n, True))
        n.volumes[1]["footprint"] = (10, 0, 16, 9)
        self.assertEqual(FL.lf1_volumes(n, True), [])
        self.assertEqual(FL.lf1_volumes(Notes(), False), [])

    def test_f2_forms_and_big_plane(self):
        b, r = roof_build({"N": 0, "S": 0, "W": None, "E": None}, rect=(1, 1, 18, 17))
        errs = FL.lf2_roof_forms(dict(b.g), b.notes)
        self.assertTrue(any("2 of" in e for e in errs))
        self.assertTrue(any("roof plane" in e for e in errs))
        b.notes.breaks += [{"name": "d", "cell": (9, 3, 6)}, {"name": "e", "cell": (9, 3, 12)}]
        self.assertFalse(any("roof plane" in e for e in FL.lf2_roof_forms(dict(b.g), b.notes)))

    def test_f4_wheel_clearance_open_water_cocoa(self):
        n = Notes()
        pts = {"wheel": {"at": [5, 0, 5], "axis": "z"}}
        g2 = {(7, 0, 5): P("stone_bricks")}
        self.assertTrue(FL.lf4_clearance(g2, pts, n, (12, 8, 12), 2))
        n.wheel_fallback.add((7, 0, 5))
        self.assertEqual(FL.lf4_clearance(g2, pts, n, (12, 8, 12), 2), [])
        spill = {(3, 0, 3): P("water[level=0]")}
        self.assertTrue(any("open" in e for e in FL.lf4_clearance(spill, {}, Notes(), (12, 8, 12), 2)))
        edge = {(0, 0, 3): P("water[level=0]"), (0, -1, 3): P("stone")}
        self.assertTrue(any("edge" in e for e in FL.lf4_clearance(edge, {}, Notes(), (12, 8, 12), 2)))
        self.assertTrue(any("cocoa" in e for e in FL.lf4_clearance({(2, 0, 2): P("cocoa")}, {}, Notes(), (12, 8, 12), 2)))

    def test_f5_f7_f9_f10_f11(self):
        n = Notes()
        n.big_doors.append({"cells": [(1, 0, 1), (2, 0, 1), (3, 0, 1)], "lintel": [(0, 3, 1), (4, 3, 1)]})
        self.assertTrue(FL.lf5_doors({}, n))
        self.assertEqual(FL.lf5_doors({(0, 3, 1): P("stone"), (4, 3, 1): P("stone")}, n), [])
        n.chimneys = ["a", "b"]
        self.assertTrue(FL.lf7_vents({}, n))
        camp = {(2, 0, 2): B.campfire(True), (2, 3, 2): P("stone")}
        self.assertTrue(FL.lf7_vents(camp, Notes()))
        v = Notes()
        v.vents.add((2, 3, 2))
        self.assertEqual(FL.lf7_vents(camp, v), [])
        self.assertTrue(FL.lf9_roof_heights(Notes()))
        n9 = Notes()
        n9.roof_heights = {3, 6, 9}
        self.assertEqual(FL.lf9_roof_heights(n9, (0.5, 0.65), 0.55), [])
        self.assertTrue(FL.lf9_roof_heights(n9, (0.5, 0.65), 0.4))
        water = {(x, -2, z): P("water[level=0]") for x in range(4) for z in range(4)}
        self.assertEqual(FL.lf10_water(water, (0, 0, 7, 3)), [])
        self.assertTrue(FL.lf10_water(water, (0, 0, 15, 15)))
        n11 = Notes()
        n11.roofs = [{"name": "r", "eave": [], "field": [(0, 0, 0)], "overhang": 1}]
        n11.gables = [{"name": "g", "finish": "bargeboard", "rake": [(1, 0, 0)]}]
        same = {(0, 0, 0): st("spruce", "north"), (1, 0, 0): st("spruce", "north")}
        self.assertTrue(FL.lf11_gables(same, n11))
        dark = {(0, 0, 0): st("spruce", "north"), (1, 0, 0): st("dark_oak", "north")}
        self.assertEqual(FL.lf11_gables(dark, n11), [])

    def test_f6_ground_patch(self):
        g = {(x, -1, z): P("gravel") for x in range(6) for z in range(6)}
        self.assertTrue(FL.lf6_ground(g, Notes()))
        g[(2, -1, 2)] = P("coarse_dirt")
        self.assertEqual(FL.lf6_ground(g, Notes()), [])


class DetailLintTests(unittest.TestCase):
    GATE = "minecraft:spruce_fence_gate[facing=north,in_wall=false,open=false,powered=false]"

    def test_gate_needs_full_block_posts(self):
        g = {(1, 0, 0): self.GATE, (0, 0, 0): "minecraft:spruce_fence[east=true,north=false,south=false,waterlogged=false,west=false]",
             (2, 0, 0): P("cobblestone")}
        self.assertEqual(len(FL.ld1_gates(g)), 1)
        g[(0, 0, 0)] = P("cobblestone")
        self.assertEqual(FL.ld1_gates(g), [])

    def test_trapdoor_budget_and_rows(self):
        td = B.td("spruce", "north", "bottom", True)
        g = {(x, 0, 0): td for x in range(7)}
        msgs = FL.ld2_trapdoors(g, 12)
        self.assertEqual(len(msgs), 1)
        self.assertEqual(len(FL.ld2_trapdoors({(x, 0, 0): td for x in range(0, 14, 2)}, 5)), 1)

    def test_one_fence_family(self):
        f = "minecraft:%s_fence[east=false,north=false,south=false,waterlogged=false,west=false]"
        self.assertEqual(FL.ld3_fence_family({(0, 0, 0): f % "spruce", (2, 0, 0): f % "spruce"}), [])
        self.assertEqual(len(FL.ld3_fence_family({(0, 0, 0): f % "spruce", (2, 0, 0): f % "oak"})), 1)

    def test_race_open_and_flume_support(self):
        n = Notes()
        n.race_cells = {(0, 0, 0)}
        self.assertEqual(len(FL.ld4_race_open({(0, 1, 0): P("stone_bricks")}, n)), 1)
        self.assertEqual(FL.ld4_race_open({}, n), [])
        n.flume = {(0, 5, 0)}
        self.assertEqual(len(FL.ld5_flume({(0, 5, 0): P("spruce_planks")}, n)), 1)
        self.assertEqual(FL.ld5_flume({(0, 5, 0): P("spruce_planks"), (0, 3, 0): P("dark_oak_log")}, n), [])

    def test_pen_ratio_and_props(self):
        n = Notes()
        n.pen = {"building": [(x, 0) for x in range(10)], "cells": [(x, z) for x in range(5) for z in range(2)]}
        g = {(x, -1, z): P("coarse_dirt") for x in range(5) for z in range(2)}
        self.assertEqual(len(FL.ld6_pen(g, n)), 2)
        n.pen["cells"] = [(x, z) for x in range(8) for z in range(2)]
        g = {(x, -1, z): P("coarse_dirt" if x % 2 else "grass_block") for x in range(8) for z in range(2)}
        self.assertEqual(FL.ld6_pen(g, n), [])
        n.props = [("yard", "rick", True), ("yard", "cart", True), ("gate", "churns", True)]
        self.assertEqual(len(FL.ld7_props(n)), 1)


if __name__ == "__main__":
    unittest.main()
