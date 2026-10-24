"""The `default` bakery cottage (Tudor): a block list file for `/bakery house place default`.

Outputs:
  src/main/resources/houses/default.yml          (the design file the plugin parses; ground-depth 1)
  design/scripts/previews/house_default_*.png    (textured previews: front, 3/4 both sides, back, above, sheet)
Run with `mise run assets` (or `python design/scripts/house_default.py` from the repo root).
Deterministic. Spec and file format: design/house-default.md, refs: design/refs/default.md

Design axes: x = west -> east (0..16), y = bottom -> top (0..11), z = north -> south (0..14); y -1 = the ground layer.
The front (door, sign) faces north (-Z). Two volumes: the two-storey-high hall (ridge along x) and a lower service wing
east of it; ONE chimney on the hall's west gable.
"""
from __future__ import annotations

import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
import blocks as B  # noqa: E402
import build_lint  # noqa: E402
import design_views  # noqa: E402
import house_lib  # noqa: E402
import scenes as S  # noqa: E402
from blocks import AIR, lg, plain as P, sl, st, stripped  # noqa: E402
from build import Build, Ramp, Wall, door, gable_roof, stack, window  # noqa: E402

OUT_YML = ROOT / "src/main/resources/houses/default.yml"
OUT_DIR = ROOT / "design/scripts/previews"
SX, SY, SZ, GROUND_DEPTH = 17, 13, 15, 1

PLINTH = Ramp(P("cobblestone"), [(P("mossy_cobblestone"), .28, "low"), (P("andesite"), .24, None)], seed=14)
PLASTER = Ramp(P("white_terracotta"), [(P("calcite"), .17, "high"), (P("light_gray_terracotta"), .17, "low")], seed=15,
               height=4, y0=1)
BRICK = Ramp(P("bricks"), [(P("red_nether_bricks"), .12, None), (P("mud_bricks"), .12, None)], seed=16)
STONE = Ramp(P("stone_bricks"), [(P("cracked_stone_bricks"), .14, None), (P("mossy_stone_bricks"), .17, "low"),
                                 (P("andesite"), .08, None)], seed=17)
FLOOR = Ramp(P("spruce_planks"), [(P("oak_planks"), .30, None), (P("dark_oak_planks"), .25, None)], seed=18)
ROOF = Ramp("mud_brick", [("brick", .14, None), ("spruce", .09, None)], seed=19, height=7)
RAMPS = {
    "plinth": ["cobblestone", "mossy_cobblestone", "andesite"],
    "plaster": ["white_terracotta", "calcite", "light_gray_terracotta"],
    "brick": ["bricks", "red_nether_bricks", "mud_bricks"],
    "timber": ["dark_oak_log", "stripped_dark_oak_log", "dark_oak_planks"],
    "roof": ["mud_brick_stairs", "brick_stairs", "spruce_stairs", "dark_oak_stairs"],
    "floor": ["spruce_planks", "oak_planks", "dark_oak_planks"],
}
BEAM = "dark_oak"

POINTS = {"chimney": [1, 12, 8], "door": [6, 1, 6]}
SIGNS = [{"at": [7, 3, 5], "lines": ["", "Bakery", "", ""]}]


def beam(axis: str) -> str:
    return stripped(BEAM, axis)


def build() -> tuple:
    b = Build((SX, SY, SZ), ground=GROUND_DEPTH)
    put, fill = b.put, b.fill
    # ================= hall A: walls x3..9, z6..12 =================
    for x in range(3, 10):
        for z in range(6, 13):
            edge = x in (3, 9) or z in (6, 12)
            put(x, 0, z, PLINTH.pick(x, 0, z) if edge else FLOOR.pick(x, 0, z))
            if edge:
                put(x, 1, z, PLINTH.pick(x, 1, z))                             # plinth: two courses
                for y in (2, 3, 4):
                    put(x, y, z, PLASTER.pick(x, y, z))
                put(x, 5, z, beam("x" if z in (6, 12) else "z"))
            else:
                for y in range(1, 9):
                    put(x, y, z, AIR)
    for (x, z) in ((3, 6), (9, 6), (3, 12), (9, 12)):
        for y in (2, 3, 4):
            put(x, y, z, lg(BEAM, "y"))
    for z in (6, 12):                                                          # mid posts, square panels
        for y in (2, 3, 4):
            put(6, y, z, lg(BEAM, "y"))
    for x in (3, 9):
        for y in (2, 3, 4):
            put(x, y, 9, lg(BEAM, "y"))
    WN = Wall(b, "north", "east", (0, 6))
    door(WN, 6, 1, mat="spruce", hood=True)
    put(5, 3, 5, B.lantern(True))
    put(7, 3, 5, "minecraft:spruce_hanging_sign[attached=false,rotation=8,waterlogged=false]")
    put(6, 0, 5, st("stone_brick", "south"))
    put(5, 0, 5, P("potted_red_tulip")); put(7, 0, 5, P("potted_orange_tulip"))
    window(WN, 4, 3, shutters=None, box="potted_cornflower", sill="spruce")
    window(WN, 8, 3, shutters=None, box="potted_azure_bluet", sill="spruce")
    WS = Wall(b, "south", "east", (0, 12))
    window(WS, 4, 3, shutters="spruce", box="potted_oxeye_daisy")
    window(WS, 8, 3, shutters="spruce", box="potted_poppy")
    WW = Wall(b, "west", "south", (3, 0))
    window(WW, 7, 3, shutters=None, box="potted_dandelion")
    window(WW, 11, 3, shutters=None, box="potted_azure_bluet")
    ra = gable_roof(b, axis="x", w0=6, w1=12, a0=2, a1=9, y_eave=5, field="mud_brick", eave="dark_oak",
                    rake="dark_oak", ridge=stripped("spruce", "x"), cap=sl("dark_oak"), name="hall",
                    rake_ends=(True, False), ramp_pick=lambda x, y, z: ROOF.pick(x, y, z))
    for z in range(7, 12):                                                     # gable infill, west (x3) and east (x9)
        for x in (3, 9):
            for y in range(6, ra["profile"][z]):
                put(x, y, z, PLASTER.pick(x, y, z))
    for x, z in ((3, 9),):
        put(x, 6, z, lg(BEAM, "y"))
        put(x, 7, z, P("dark_oak_planks"))
    window(Wall(b, "west", "south", (3, 0)), 11, 6, shutters=None, box="potted_cornflower", sill="spruce")
    # ================= service wing B: x9..14, z7..11 =================
    for x in range(10, 15):
        for z in range(7, 12):
            edge = x == 14 or z in (7, 11)
            put(x, 0, z, PLINTH.pick(x, 0, z) if edge else FLOOR.pick(x, 0, z))
            if edge:
                for y in (1, 2):
                    put(x, y, z, PLASTER.pick(x, y, z))
                put(x, 3, z, beam("x" if z in (7, 11) else "z"))
            else:
                for y in range(1, 4):
                    put(x, y, z, AIR)
    for (x, z) in ((14, 7), (14, 11)):
        for y in (1, 2):
            put(x, y, z, lg(BEAM, "y"))
    WB = Wall(b, "north", "east", (0, 7))
    window(WB, 12, 2, shutters="spruce", box="potted_poppy")
    window(Wall(b, "south", "east", (0, 11)), 12, 2, shutters="spruce", box="potted_dandelion")
    window(Wall(b, "east", "south", (14, 0)), 9, 2, shutters="spruce", box="potted_cornflower")
    rb = gable_roof(b, axis="x", w0=7, w1=11, a0=10, a1=15, y_eave=3, field="mud_brick", eave="dark_oak",
                    rake="dark_oak", ridge=stripped("spruce", "x"), cap=sl("dark_oak"), name="wing",
                    rake_ends=(False, True), ramp_pick=lambda x, y, z: ROOF.pick(x, y, z))
    for z in (8, 9, 10):
        for y in range(4, rb["profile"][z]):
            put(14, y, z, PLASTER.pick(14, y, z))
    # ================= the chimney: west gable of the hall =================
    smoke = stack(b, 1, 8, y0=0, y_top=10, base=PLINTH, shaft=BRICK, base_courses=3, name="hearth_stack")
    # ================= the yard =================
    yard(b)
    n = b.notes
    n.volumes += [{"name": "hall", "footprint": (3, 6, 9, 12), "top": 9},
                  {"name": "wing", "footprint": (9, 7, 14, 11), "top": 6}]
    n.ramps = dict(RAMPS)
    n.plinth = {"blocks": RAMPS["plinth"], "courses": 2,
                "ring": [(x, z) for x in range(3, 10) for z in range(6, 13) if x in (3, 9) or z in (6, 12)]}
    n.body_box = (3, 6, 14, 12)
    n.wall_rows = (1, 4)
    n.ground_region = (-1, -1, SX, SZ)
    n.front_yard = {"rect": (2, 0, 10, 4), "corridor": (5, 7), "max_y": 1}
    n.seal = set()
    return b, smoke


def yard(b: Build) -> None:
    rng = b.rng
    # path from the door to the north edge: 3 wide, wandering, with gravel and coarse dirt (R13)
    path = set()
    cx = 6
    for z in range(5, -1, -1):
        cx = max(5, min(7, cx + rng.choice((-1, 0, 0, 1)) if z % 2 == 0 else cx))
        for x in range(cx - 1, cx + 2):
            if 4 <= x <= 8:
                path.add((x, z))
    for z in (4, 5):
        path |= {(5, z), (6, z), (7, z)}
    S.ground(b, path, S.PATH)
    # front beds either side of the path, edged with cobblestone slabs
    for name, (x0, x1) in (("front_bed_west", (1, 3)), ("front_bed_east", (9, 11))):
        b.scene(name, "path")
        cells = {(x, z) for x in range(x0, x1 + 1) for z in range(1, 5) if (x, z) not in path}
        cells -= {(x0, 1)} if x0 == 1 else {(x1, 1)}
        S.flower_bed(b, cells)
    b.scene("bed_edge", "path")
    for z in range(1, 5):
        for x in (4, 8):
            if (x, z) not in path and b.get(x, -1, z) is None:
                b.put(x, -1, z, P("coarse_dirt"))
    # woodpile against the back wall of the hall
    b.scene("woodpile", "wall")
    S.woodpile(b, 5, 13, "x", 3)
    # herb and veg bed behind the wing
    b.scene("herb_bed", "wall")
    S.flower_bed(b, {(x, z) for x in range(10, 14) for z in range(13, 15)}, kinds=["allium", "lily_of_the_valley",
                                                                                "cornflower", "dandelion"])
    b.put(14, 0, 12, B.barrel()); b.put(14, 0, 13, B.hay("x")); b.put(15, 0, 12, B.barrel())
    # apron of gravel and coarse dirt around the walls
    solid = {(x, z) for (x, y, z), d in b.g.items() if 0 <= y <= 1 and B.block_id(d) != "air"}
    near = lambda x, z, d: any((x + dx, z + dz) in solid for dx in range(-d, d + 1) for dz in range(-d, d + 1))  # noqa
    for x in range(SX):
        for z in range(SZ):
            if (x, -1, z) in b.g or (x, z) in solid:
                continue
            if near(x, z, 1) and rng.random() < .7:
                b.put(x, -1, z, S.GRAVEL.pick(x, 0, z))
            elif near(x, z, 2) and rng.random() < .35:
                b.put(x, -1, z, P("coarse_dirt"))


def main() -> int:
    b, smoke = build()
    points = {"chimney": list(smoke), "door": POINTS["door"]}
    g = dict(b.g)
    B.resolve(g)
    chars, palette = B.to_palette(g)
    size = (SX, SY + GROUND_DEPTH, SZ)
    layers = house_lib.to_layers(chars, size, GROUND_DEPTH)
    blocks = house_lib.validate(chars, layers, palette, size, (0, 0, 0), points, SIGNS, ground=GROUND_DEPTH)
    try:
        build_lint.lint_house(g, b.notes, points, palette)
    except build_lint.LintError:
        OUT_DIR.mkdir(parents=True, exist_ok=True)
        design_views.default_previews(g, OUT_DIR)
        raise
    header = [
        "# The `default` bakery cottage (Tudor). GENERATED by design/scripts/house_default.py; do not edit by hand.",
        "# Format and coordinates: design/house-default.md",
        "# Axes: x = west->east, y = bottom->top, z = north->south. Front (door) faces north (-Z).",
        f"# {blocks} blocks (not counting air/keep). ground-depth 1: the first layer is design y -1.",
    ]
    text = house_lib.write_yaml(header, layers, palette, size, (0, 0, 0), points, SIGNS, ground=GROUND_DEPTH)
    parsed = yaml.safe_load(text)
    assert parsed["layers"] == layers and parsed["size"] == list(size) and parsed["ground-depth"] == GROUND_DEPTH
    assert parsed["palette"] == palette and parsed["points"] == points and parsed["signs"] == SIGNS
    OUT_YML.write_text(text, encoding="utf-8")
    print(f"{OUT_YML.relative_to(ROOT)}: {blocks} blocks, {len(palette)} palette entries")
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    design_views.default_previews(g, OUT_DIR)
    return 0


if __name__ == "__main__":
    sys.exit(main())
