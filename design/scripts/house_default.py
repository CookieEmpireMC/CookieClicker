"""The `default` bakery cottage: a block list file for `/bakery house place default`.

Outputs:
  src/main/resources/houses/default.yml           (the design file the plugin parses)
  design/scripts/previews/house_default_layers.png (top view of every layer)
  design/scripts/previews/house_default_iso.png    (orthographic 3/4 views, front and back)
Run with `mise run assets` (or `python design/scripts/house_default.py` from the repo root).
Deterministic. Spec and file format: design/house-default.md

Design axes: x = west -> east (0..8), y = bottom -> top (0..6), z = north -> south (0..6).
The front (door, windows, sign) faces north (-Z).
"""
from __future__ import annotations

import sys
from pathlib import Path

import yaml
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
import house_lib  # noqa: E402

OUT_YML = ROOT / "src/main/resources/houses/default.yml"
OUT_DIR = ROOT / "design/scripts/previews"

SX, SY, SZ = 9, 7, 7
ORIGIN = (0, 0, 0)
KEEP = "."

# char -> block data string (Minecraft 26.3 ids and states, every property spelled out)
PALETTE = {
    "_": "minecraft:air",
    "S": "minecraft:stone_bricks",
    "M": "minecraft:mossy_stone_bricks",
    "s": "minecraft:stone_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]",
    "F": "minecraft:spruce_planks",
    "P": "minecraft:spruce_log[axis=y]",
    "X": "minecraft:stripped_spruce_log[axis=x]",
    "Z": "minecraft:stripped_spruce_log[axis=z]",
    "W": "minecraft:white_terracotta",
    "B": "minecraft:bricks",
    "-": "minecraft:glass_pane[east=true,north=false,south=false,waterlogged=false,west=true]",
    "|": "minecraft:glass_pane[east=false,north=true,south=true,waterlogged=false,west=false]",
    "d": "minecraft:oak_door[facing=north,half=lower,hinge=left,open=false,powered=false]",
    "D": "minecraft:oak_door[facing=north,half=upper,hinge=left,open=false,powered=false]",
    "v": "minecraft:dark_oak_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]",
    "^": "minecraft:dark_oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]",
    "=": "minecraft:dark_oak_slab[type=bottom,waterlogged=false]",
    "L": "minecraft:lantern[hanging=true,waterlogged=false]",
    "H": "minecraft:spruce_hanging_sign[attached=false,rotation=8,waterlogged=false]",
    "r": "minecraft:potted_red_tulip",
    "o": "minecraft:potted_orange_tulip",
}

FULL_SOLID = {"S", "M", "F", "P", "X", "Z", "W", "B"}   # full cubes with sturdy faces (preview notes only)


# --- the design --------------------------------------------------------------------------------
def build() -> dict[tuple[int, int, int], str]:
    g: dict[tuple[int, int, int], str] = {}

    def put(x, y, z, c):
        assert 0 <= x < SX and 0 <= y < SY and 0 <= z < SZ, (x, y, z)
        g[(x, y, z)] = c

    body_x, body_z = range(1, 8), range(1, 6)          # walls on x=1,7 and z=1,5
    inner_x, inner_z = range(2, 7), range(2, 5)

    # y0: stone plinth (visible, the house sits on the ground) + spruce floor + front step
    mossy = {(1, 1), (2, 5), (7, 4), (6, 1)}
    for x in body_x:
        for z in body_z:
            edge = x in (1, 7) or z in (1, 5)
            put(x, 0, z, ("M" if (x, z) in mossy else "S") if edge else "F")
    put(4, 0, 0, "s")                                   # step up to the door
    put(3, 0, 0, "r"); put(5, 0, 0, "o")               # potted tulips either side (pots need no support)
    put(8, 0, 3, "B")                                   # chimney base

    # y1..y3: timber-framed walls, interior air
    for y in (1, 2, 3):
        for x in body_x:
            for z in body_z:
                if x in inner_x and z in inner_z:
                    put(x, y, z, "_")
                elif x in (1, 7) and z in (1, 5):
                    put(x, y, z, "P")                   # corner posts
                elif y == 3:
                    put(x, y, z, "X" if z in (1, 5) else "Z")   # top beam
                else:
                    put(x, y, z, "W")
    # front (z=1): windows at x=2,6 (y2), door at x=4
    put(2, 2, 1, "-"); put(6, 2, 1, "-")
    put(4, 1, 1, "d"); put(4, 2, 1, "D")
    # back (z=5): windows at x=3,5
    put(3, 2, 5, "-"); put(5, 2, 5, "-")
    # west gable wall (x=1): window at z=3
    put(1, 2, 3, "|")
    # east gable wall (x=7): chimney breast at z=3, windows either side
    for y in (1, 2, 3):
        put(7, y, 3, "B")
    put(7, 2, 2, "|"); put(7, 2, 4, "|")

    # roof: shallow stair/slab pitch over x=0..8 (one block overhang all round)
    profile = {0: (3, "v"), 1: (4, "="), 2: (4, "v"), 3: (5, "="), 4: (4, "^"), 5: (4, "="), 6: (3, "^")}
    for x in range(SX):
        for z, (y, c) in profile.items():
            put(x, y, z, c)
    # gables (y4, z3 under the ridge) + attic air
    put(1, 4, 3, "F")
    put(7, 4, 3, "B")
    for x in inner_x:
        put(x, 4, 3, "_")

    # chimney: brick stack outside the east gable, through the roof overhang
    for y in range(0, 6):
        put(8, y, 3, "B")

    # porch: lantern and hanging sign under the front eave, either side of the door
    put(3, 2, 0, "L")
    put(5, 2, 0, "H")
    return g


POINTS = {"chimney": [8, 6, 3], "door": [4, 1, 1]}
SIGNS = [{"at": [5, 2, 0], "lines": ["", "Bakery", "", ""]}]


# --- structural checks (shared with every design: lib/house_lib.py) ----------------------------
HEADER = [
    "# The `default` bakery cottage. GENERATED by design/scripts/house_default.py; do not edit by hand.",
    "# Format and coordinates: design/house-default.md",
    "# Axes: x = west->east, y = bottom->top, z = north->south. Front (door) faces north (-Z).",
]


# --- previews ----------------------------------------------------------------------------------
# Approximate average colours of the vanilla textures, for previews only.
COLOURS = {
    "S": (122, 121, 122), "M": (108, 121, 94), "s": (122, 121, 122), "F": (115, 85, 52),
    "P": (62, 44, 26), "X": (118, 90, 58), "Z": (118, 90, 58), "W": (209, 178, 161),
    "B": (150, 82, 66), "-": (178, 214, 226), "|": (178, 214, 226), "d": (140, 110, 64),
    "D": (140, 110, 64), "v": (66, 44, 22), "^": (66, 44, 22), "=": (78, 52, 28),
    "L": (236, 178, 72), "H": (115, 85, 52), "_": (255, 255, 255),
    "r": (200, 60, 48), "o": (232, 130, 40),
}


def boxes_for(c: str, x: int, y: int, z: int):
    col = COLOURS[c]
    b = lambda x1, y1, z1, x2, y2, z2, rgb=col: ((x + x1, y + y1, z + z1), (x + x2, y + y2, z + z2), rgb)  # noqa: E731
    if c == "_":
        return []
    if c == "=":
        return [b(0, 0, 0, 1, .5, 1)]
    if c in ("v", "s"):
        return [b(0, 0, 0, 1, .5, 1), b(0, .5, .5, 1, 1, 1)]
    if c == "^":
        return [b(0, 0, 0, 1, .5, 1), b(0, .5, 0, 1, 1, .5)]
    if c == "-":
        return [b(0, 0, 7 / 16, 1, 1, 9 / 16)]
    if c == "|":
        return [b(7 / 16, 0, 0, 9 / 16, 1, 1)]
    if c in ("d", "D"):
        return [b(0, 0, 13 / 16, 1, 1, 1)]
    if c == "L":
        return [b(5 / 16, 1 / 16, 5 / 16, 11 / 16, 8 / 16, 11 / 16),
                b(7 / 16, 8 / 16, 7 / 16, 9 / 16, 1, 9 / 16, (60, 60, 66))]
    if c in ("r", "o"):
        return [b(5 / 16, 0, 5 / 16, 11 / 16, 6 / 16, 11 / 16, (142, 76, 56)),
                b(6 / 16, 6 / 16, 6 / 16, 10 / 16, 12 / 16, 10 / 16)]
    if c == "H":
        return [b(1 / 16, 0, 7 / 16, 15 / 16, 10 / 16, 9 / 16),
                b(3 / 16, 10 / 16, 7.5 / 16, 4 / 16, 1, 8.5 / 16, (60, 60, 66)),
                b(12 / 16, 10 / 16, 7.5 / 16, 13 / 16, 1, 8.5 / 16, (60, 60, 66))]
    return [b(0, 0, 0, 1, 1, 1)]


def ground_boxes():
    """A patch of grass around the house (preview only, not part of the design)."""
    return [((-2, -1, -3), (SX + 2, 0, SZ + 2), (96, 140, 62))]


def iso_sheet(g: dict) -> Image.Image:
    boxes = ground_boxes()
    for (x, y, z), c in sorted(g.items()):
        boxes += boxes_for(c, x, y, z)
    return house_lib.iso_sheet(boxes)


def main() -> int:
    g = build()
    size = (SX, SY, SZ)
    layers = house_lib.to_layers(g, size)
    blocks = house_lib.validate(g, layers, PALETTE, size, ORIGIN, POINTS, SIGNS)
    text = house_lib.write_yaml(HEADER + [f"# {blocks} blocks (not counting air/keep)."], layers, PALETTE, size,
                                ORIGIN, POINTS, SIGNS)
    parsed = yaml.safe_load(text)
    assert parsed["layers"] == layers and parsed["size"] == [SX, SY, SZ]
    assert parsed["palette"] == PALETTE and parsed["points"] == POINTS and parsed["signs"] == SIGNS
    OUT_YML.parent.mkdir(parents=True, exist_ok=True)
    OUT_YML.write_text(text, encoding="utf-8")
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    house_lib.layer_sheet(layers, size, COLOURS, "default cottage: layers bottom->top, x ->, z (north at top) v; "
                          "'.' = keep").save(OUT_DIR / "house_default_layers.png")
    iso_sheet(g).save(OUT_DIR / "house_default_iso.png")
    print(f"{OUT_YML.relative_to(ROOT)}: {blocks} blocks")
    print(f"previews in {OUT_DIR.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
