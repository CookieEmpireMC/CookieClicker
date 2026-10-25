"""Tests for the `default` house design file and the generator's validation.

The design file is parsed here independently of the generator (the way the Java
parser will), so a generator bug cannot hide behind its own checks. Needs PyYAML and
Pillow (installed by `mise run assets-deps`, which `tools-test` depends on).
"""

import importlib.util
import os
import re
import sys
import unittest

import yaml

REPO = os.path.realpath(os.path.join(os.path.dirname(__file__), ".."))
DESIGN = os.path.join(REPO, "src", "main", "resources", "houses", "default.yml")
GEN = os.path.join(REPO, "design", "scripts", "house_default.py")

spec = importlib.util.spec_from_file_location("house_default", GEN)
gen = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gen)

sys.path.insert(0, os.path.join(REPO, "design", "scripts", "lib"))
import house_lib  # noqa: E402

SIZE = (gen.SX, gen.SY + gen.GROUND_DEPTH, gen.SZ)


def generate():
    """The generator's own steps from main() up to the written file: (chars, layers, palette, points)."""
    b, smoke = gen.build()
    points = {"chimney": list(smoke), "door": gen.POINTS["door"]}
    g = dict(b.g)
    gen.B.resolve(g)
    chars, palette = gen.B.to_palette(g)
    return chars, house_lib.to_layers(chars, SIZE, gen.GROUND_DEPTH), palette, points


def validate(chars, layers, palette, points):
    return house_lib.validate(chars, layers, palette, SIZE, (0, 0, 0), points, gen.SIGNS, ground=gen.GROUND_DEPTH)


BLOCK_RE = re.compile(r"^minecraft:[a-z0-9_]+(\[[a-z0-9_]+=[a-z0-9_]+(,[a-z0-9_]+=[a-z0-9_]+)*\])?$")


def load():
    with open(DESIGN, encoding="utf-8") as fh:
        return yaml.safe_load(fh)


class DesignFileTest(unittest.TestCase):
    def setUp(self):
        self.d = load()

    def test_contract_shape(self):
        d = self.d
        self.assertEqual(d["size"], [17, 14, 15])
        self.assertEqual(d["ground-depth"], 1)
        self.assertEqual(d["origin"], [0, 0, 0])
        self.assertEqual(len(d["layers"]), 14)
        for layer in d["layers"]:
            self.assertEqual(len(layer), 15)
            for row in layer:
                self.assertIsInstance(row, str)
                self.assertEqual(len(row), 17)

    def test_every_char_known_and_every_palette_entry_used(self):
        pal = self.d["palette"]
        chars = {c for layer in self.d["layers"] for row in layer for c in row}
        self.assertFalse(chars - set(pal) - {"."})
        self.assertFalse(set(pal) - chars)

    def test_points_and_signs_in_bounds_and_meaningful(self):
        d = self.d
        sx, sy, sz = d["size"]
        ground = d["ground-depth"]

        def cell(p):
            x, y, z = p
            self.assertTrue(0 <= x < sx and -ground <= y < sy - ground and 0 <= z < sz, p)
            return d["layers"][y + ground][z][x]

        pal = d["palette"]
        self.assertIn(cell(d["points"]["chimney"]), ".")
        cx, cy, cz = d["points"]["chimney"]
        self.assertEqual(pal[cell([cx, cy - 1, cz])], "minecraft:bricks")
        self.assertIn("half=lower", pal[cell(d["points"]["door"])])
        self.assertIn("_door", pal[cell(d["points"]["door"])])
        for s in d["signs"]:
            self.assertIn("hanging_sign", pal[cell(s["at"])])
            self.assertLessEqual(len(s["lines"]), 4)


class GeneratorValidationTest(unittest.TestCase):
    def test_generator_accepts_its_own_design(self):
        chars, layers, palette, points = generate()
        self.assertEqual(validate(chars, layers, palette, points), 590)

    def test_committed_file_matches_generator(self):
        self.assertEqual(load()["layers"], generate()[1])

    def test_validate_rejects_broken_layouts(self):
        chars, good, palette, points = generate()

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
                    validate(chars, mutated(fn), palette, points)


if __name__ == "__main__":
    unittest.main()
