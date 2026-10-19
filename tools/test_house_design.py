"""Tests for the `default` house design file and the generator's validation.

The design file is parsed here independently of the generator (the way the Java
parser will), so a generator bug cannot hide behind its own checks. Needs PyYAML and
Pillow (installed by `mise run assets-deps`, which `tools-test` depends on).
"""

import importlib.util
import os
import re
import unittest

import yaml

REPO = os.path.realpath(os.path.join(os.path.dirname(__file__), ".."))
DESIGN = os.path.join(REPO, "src", "main", "resources", "houses", "default.yml")
GEN = os.path.join(REPO, "design", "scripts", "house_default.py")

spec = importlib.util.spec_from_file_location("house_default", GEN)
gen = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gen)

BLOCK_RE = re.compile(r"^minecraft:[a-z0-9_]+(\[[a-z0-9_]+=[a-z0-9_]+(,[a-z0-9_]+=[a-z0-9_]+)*\])?$")


def load():
    with open(DESIGN, encoding="utf-8") as fh:
        return yaml.safe_load(fh)


class DesignFileTest(unittest.TestCase):
    def setUp(self):
        self.d = load()

    def test_contract_shape(self):
        d = self.d
        self.assertEqual(d["size"], [9, 7, 7])
        self.assertEqual(d["origin"], [0, 0, 0])
        self.assertEqual(len(d["layers"]), 7)
        for layer in d["layers"]:
            self.assertEqual(len(layer), 7)
            for row in layer:
                self.assertIsInstance(row, str)
                self.assertEqual(len(row), 9)

    def test_keep_and_air_are_distinct(self):
        pal = self.d["palette"]
        self.assertNotIn(".", pal)
        self.assertEqual(pal["_"], "minecraft:air")
        chars = {c for layer in self.d["layers"] for row in layer for c in row}
        self.assertIn(".", chars)
        self.assertIn("_", chars)

    def test_special_chars_survive_yaml(self):
        pal = self.d["palette"]
        for c in "-|^=":
            self.assertIn(c, pal)
            self.assertIsInstance(pal[c], str)
        chars = {c for layer in self.d["layers"] for row in layer for c in row}
        for c in "-|^=":
            self.assertIn(c, chars)

    def test_every_char_known_and_every_palette_entry_used(self):
        pal = self.d["palette"]
        chars = {c for layer in self.d["layers"] for row in layer for c in row}
        self.assertFalse(chars - set(pal) - {"."})
        self.assertFalse(set(pal) - chars)

    def test_palette_syntax_and_no_gravity(self):
        for c, data in self.d["palette"].items():
            self.assertEqual(len(c), 1)
            self.assertRegex(data, BLOCK_RE)
            block = data[len("minecraft:"):].split("[")[0]
            self.assertNotIn(block, gen.GRAVITY)
            self.assertFalse(block.endswith("_concrete_powder"))

    def test_points_and_signs_in_bounds_and_meaningful(self):
        d = self.d
        sx, sy, sz = d["size"]

        def cell(p):
            x, y, z = p
            self.assertTrue(0 <= x < sx and 0 <= y < sy and 0 <= z < sz, p)
            return d["layers"][y][z][x]

        pal = d["palette"]
        self.assertIn(cell(d["points"]["chimney"]), ".")
        cx, cy, cz = d["points"]["chimney"]
        self.assertEqual(pal[cell([cx, cy - 1, cz])], "minecraft:bricks")
        self.assertIn("half=lower", pal[cell(d["points"]["door"])])
        self.assertIn("_door", pal[cell(d["points"]["door"])])
        for s in d["signs"]:
            self.assertIn("hanging_sign", pal[cell(s["at"])])
            self.assertLessEqual(len(s["lines"]), 4)

    def test_block_count_header_and_limit(self):
        placed = sum(1 for layer in self.d["layers"] for row in layer for c in row if c not in ".")
        air = sum(1 for layer in self.d["layers"] for row in layer for c in row if c == "_")
        self.assertEqual(placed - air, 170)
        self.assertLess(placed, 2000)

    def test_hanging_blocks_have_ceiling(self):
        d = self.d
        pal = d["palette"]
        for y, layer in enumerate(d["layers"]):
            for z, row in enumerate(layer):
                for x, c in enumerate(row):
                    if c != "." and ("hanging" in pal[c] and "hanging=false" not in pal[c]):
                        above = d["layers"][y + 1][z][x]
                        self.assertNotIn(above, "._", f"{(x, y, z)} hangs from nothing")
                        self.assertTrue(
                            "stairs" in pal[above] or "slab" in pal[above]
                            or "planks" in pal[above] or "bricks" in pal[above], pal[above])


class GeneratorValidationTest(unittest.TestCase):
    def test_generator_accepts_its_own_design(self):
        g = gen.build()
        layers = gen.to_layers(g)
        self.assertEqual(gen.validate(g, layers), 170)

    def test_committed_file_matches_generator(self):
        g = gen.build()
        self.assertEqual(load()["layers"], gen.to_layers(g))

    def test_block_data_rejects(self):
        bad = [
            "minecraft:oak_door[facing=up,half=lower,hinge=left,open=false,powered=false]",
            "minecraft:oak_door[facing=north,half=lower]",
            "minecraft:dark_oak_slab[type=middle,waterlogged=false]",
            "minecraft:sand",
            "minecraft:gravel",
            "minecraft:white_concrete_powder",
            "minecraft:unknown_block",
            "stone_bricks",
            "minecraft:Stone",
            "minecraft:spruce_log[axis=w]",
        ]
        for data in bad:
            with self.subTest(data=data):
                with self.assertRaises(ValueError):
                    gen.check_block_data(data)

    def test_validate_rejects_broken_layouts(self):
        g = gen.build()
        good = gen.to_layers(g)

        def mutated(fn):
            layers = [[r for r in layer] for layer in good]
            fn(layers)
            return layers

        cases = {
            "short row": lambda L: L[0].__setitem__(0, L[0][0][:-1]),
            "missing row": lambda L: L[1].pop(),
            "missing layer": lambda L: L.pop(),
            "unknown char": lambda L: L[0].__setitem__(1, "?" + L[0][1][1:]),
        }
        for name, fn in cases.items():
            with self.subTest(name):
                with self.assertRaises(AssertionError):
                    gen.validate(g, mutated(fn))

    def test_validate_rejects_point_out_of_bounds(self):
        g = gen.build()
        layers = gen.to_layers(g)
        old = dict(gen.POINTS)
        try:
            gen.POINTS["chimney"] = [9, 6, 3]
            with self.assertRaises(AssertionError):
                gen.validate(g, layers)
        finally:
            gen.POINTS.clear()
            gen.POINTS.update(old)


if __name__ == "__main__":
    unittest.main()
