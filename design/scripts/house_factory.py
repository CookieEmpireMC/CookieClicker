"""The `factory` cookie works (Norman/Tudor bakehouse): a block list file for `/bakery house place factory`.

Outputs:
  src/main/resources/houses/factory.yml                (the design file the plugin parses; ground-depth 1)
  design/scripts/previews/house_factory_*.png          (textured previews, see design_views.py; layer sheet)
Run with `mise run assets` (or `python design/scripts/house_factory.py` from the repo root).
Deterministic. Spec: design/factory.md, style: design/style-guide.md "Block structures", refs: design/refs/factory.md.

Design axes: x = west -> east (0..18), y = bottom -> top (0..16), z = north -> south (0..13); y -1 = the ground layer.
The front (door, sign, loading hatch) faces north (-Z). ONE chimney (R11): the 2 x 2 oven stack.
"""
from __future__ import annotations

import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
sys.path.insert(0, str(Path(__file__).resolve().parent))
import blocks as B  # noqa: E402
import build_lint  # noqa: E402
import design_views  # noqa: E402
import factory_layout as FL  # noqa: E402
import house_lib  # noqa: E402
from blocks import AIR, lg, plain as P, sl, st, stripped  # noqa: E402
from build import Build, Ramp, Wall, door, gable_roof, stack, window  # noqa: E402

OUT_YML = ROOT / "src/main/resources/houses/factory.yml"
OUT_DIR = ROOT / "design/scripts/previews"
SX, SY, SZ, SIZE, GROUND_DEPTH = FL.SX, FL.SY, FL.SZ, FL.SIZE, 1

# --- materials: every one is a ramp of 3+ blocks (R6/R7) -----------------------------------------------------------------
STONE = Ramp(P("stone_bricks"), [(P("cracked_stone_bricks"), .14, None), (P("mossy_stone_bricks"), .17, "low"),
                                 (P("andesite"), .08, None)], seed=3)
PLINTH = Ramp(P("cobblestone"), [(P("mossy_cobblestone"), .25, "low"), (P("andesite"), .15, None)], seed=4)
PLASTER = Ramp(P("calcite"), [(P("diorite"), .18, None), (P("polished_diorite"), .12, "low")], seed=5, height=3, y0=5)
BRICK = Ramp(P("bricks"), [(P("mud_bricks"), .12, None), (P("stone_bricks"), .08, None)], seed=6)
ROOF = Ramp("spruce", [("dark_oak", .12, None), ("oak", .07, None)], seed=8, height=12)
FLOOR = Ramp(P("spruce_planks"), [(P("oak_planks"), .22, None), (P("dark_oak_planks"), .15, None)], seed=9)
GROUND = Ramp(P("gravel"), [(P("coarse_dirt"), .16, None)], seed=11)
RAMPS = {
    "stone": ["stone_bricks", "cracked_stone_bricks", "mossy_stone_bricks", "andesite"],
    "plinth": ["cobblestone", "mossy_cobblestone", "andesite"],
    "plaster": ["calcite", "diorite", "polished_diorite"],
    "brick": ["bricks", "mud_bricks", "stone_bricks"],
    "timber": ["dark_oak_log", "stripped_dark_oak_log", "dark_oak_planks"],
    "roof": ["spruce_stairs", "dark_oak_stairs", "oak_stairs"],
    "floor": ["spruce_planks", "oak_planks", "dark_oak_planks"],
}
BEAM = "dark_oak"
POT_FLOWERS = ["potted_poppy", "potted_cornflower", "potted_azure_bluet", "potted_oxeye_daisy"]


def beam(axis: str) -> str:
    return stripped(BEAM, axis)


def build() -> Build:
    b = Build(SIZE, ground=GROUND_DEPTH)
    n = b.notes
    put, fill = b.put, b.fill

    # ================= bake hall: x6..14, z2..10 =================
    for x in range(6, 15):
        for z in range(2, 11):
            edge = x in (6, 14) or z in (2, 10)
            put(x, 0, z, PLINTH.pick(x, 0, z) if edge else FLOOR.pick(x, 0, z))
            if edge:
                for y in (1, 2, 3):
                    put(x, y, z, STONE.pick(x, y, z))
                put(x, 4, z, STONE.pick(x, 4, z) if z == 2 else beam("x" if z == 10 else "z"))   # fascia / string course
            else:
                fill(x, 1, z, x, 8, z, AIR)
    # upper floor ring: north row at z=1 (the jetty), south z=10, sides x=6/14
    ring_up = [(x, 1) for x in range(6, 15)] + [(x, 10) for x in range(6, 15)] + \
              [(6, z) for z in range(2, 10)] + [(14, z) for z in range(2, 10)]
    for (x, z) in ring_up:
        for y in (5, 6, 7):
            put(x, y, z, PLASTER.pick(x, y, z))
        put(x, 8, z, beam("x" if z in (1, 10) else "z"))                       # top plate
    for x in range(6, 15):
        put(x, 5, 1, beam("x"))                                               # jetty bressumer
    # timber frame: corner posts + close studs (every 2nd block) on all four upper walls
    for (x, z) in ((6, 1), (14, 1), (6, 10), (14, 10)):
        for y in (6, 7):
            put(x, y, z, lg(BEAM, "y"))
    for z in (1, 10):                                                          # square panels: posts at 6, 10, 14
        for y in (6, 7):
            put(10, y, z, lg(BEAM, "y"))
    for x in (6, 14):
        for y in (5, 6, 7):
            put(x, y, 5, lg(BEAM, "y"))
    for x in (7, 13):                                                          # short braces under the plate
        for z in (1, 10):
            put(x, 7, z, P("dark_oak_planks"))
    # ---- north facade: ground plane z=2 (WN), upper plane z=1 (WU)
    WN = Wall(b, "north", "east", (0, 2))
    WU = Wall(b, "north", "east", (0, 1))
    for x in (6, 14):                                                          # pilasters carry the jetty
        for y in range(0, 4):
            put(x, y, 1, (PLINTH if y == 0 else STONE).pick(x, y, 1))
        put(x, 4, 1, P("chiseled_stone_bricks"))
    door(WN, 8, 1, mat="spruce", hood=False)
    put(8, 0, 1, st("stone_brick", "south"))                                   # step
    put(7, 0, 1, P("potted_red_tulip")); put(9, 0, 1, P("potted_orange_tulip"))
    for x in (7, 8, 12, 13):                                                   # jetty brackets (inverted stairs)
        put(x, 4, 1, WN.stair(BEAM, "top", "in"))
    put(8, 3, 1, B.lantern(True))
    for x in (9, 10, 11):
        put(x, 4, 1, B.wall_sign("spruce", "north"))                           # signboard, text on the middle one
    # loading hatch: opening x12 y0..1, timber jambs, copper lintel + awning, wide window above
    fill(12, 0, 2, 12, 1, 2, AIR)
    put(11, 1, 2, lg(BEAM, "y")); put(13, 1, 2, lg(BEAM, "y"))
    for x in (11, 12, 13):
        put(x, 2, 2, P("waxed_cut_copper"))
        put(x, 1, 1, P("waxed_copper_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]"))
    window(WN, 11, 3, width=3, shutters=None, box="potted_poppy", sill="spruce")
    put(13, 0, 1, B.barrel())
    # upper north windows: 4 tall narrow ones in the stud bays, flower boxes, lit
    for i, x in enumerate((8, 12)):
        window(WU, x, 6, height=2, shutters=None, box=POT_FLOWERS[i])
    # gable north (plane z=1)
    def gable(wall: Wall, prof: dict, centre: int):
        for x in range(7, 14):
            for y in range(9, prof[x]):
                wall.put(x, y, 0, PLASTER.pick(*wall.xyz(x, y, 0)))
        for y in (9, 11):
            wall.put(10, y, 0, lg(BEAM, "y"))
        window(wall, 10, 10, shutters=BEAM, box="potted_cornflower", sill="spruce")
    # ---- roof: spruce eave + rake, dark oak field, ridge beam + slab cap
    pr = gable_roof(b, axis="z", w0=6, w1=14, a0=0, a1=11, y_eave=8, field="spruce", eave="dark_oak", rake="dark_oak",
                    ridge=stripped("spruce", "z"), cap=sl("dark_oak"), name="hall",
                    ramp_pick=lambda x, y, z: ROOF.pick(x, y, z))
    gable(WU, pr["profile"], pr["centre"])
    WS = Wall(b, "south", "east", (0, 10))
    gable(WS, pr["profile"], pr["centre"])
    for z in (5, 6, 7):                                                        # flush ridge louvre (not a chimney)
        put(10, 12, z, P("waxed_copper_grate[waterlogged=false]"))
        b.g.pop((10, 13, z), None)
    # ---- south (back) facade
    window(WS, 8, 2, height=2, box="potted_azure_bluet")
    door(WS, 11, 1, mat="spruce", hood=False)
    put(11, 0, 11, st("stone_brick", "north"))
    for i, x in enumerate((8, 12)):
        window(WS, x, 6, height=2, shutters=None, box=POT_FLOWERS[(i + 2) % 4])
    # ---- east facade (x=14): ground windows inside the shed, upper windows
    WE = Wall(b, "east", "south", (14, 0))
    for z in (3, 7):
        window(WE, z, 2, height=2, box=None, sill="spruce")
    for z in (3, 7):
        window(WE, z, 6, height=2, shutters=None, box=POT_FLOWERS[z % 4])
    # ---- west facade (x=6): upper windows beyond the annex roof
    WW = Wall(b, "west", "south", (6, 0))
    for z in (3, 8):
        window(WW, z, 6, height=2, shutters=None, box=POT_FLOWERS[(z + 1) % 4])

    # ================= oven house (west annex): x1..5, z4..8 =================
    for x in range(1, 6):
        for z in range(4, 9):
            edge = x == 1 or z in (4, 8)
            put(x, 0, z, PLINTH.pick(x, 0, z) if edge else FLOOR.pick(x, 0, z))
            if edge:
                for y in (1, 2):
                    put(x, y, z, STONE.pick(x, y, z))
                put(x, 3, z, beam("x" if z in (4, 8) else "z"))
            else:
                fill(x, 1, z, x, 3, z, AIR)
    WA = Wall(b, "north", "east", (0, 4))
    for x in (4, 5):
        for y in (1, 2):
            put(x, y, 4, "@waxed_copper_bars")
        put(x, 3, 4, P("chiseled_stone_bricks"))
        put(x, 1, 6, P("magma_block")); put(x, 2, 6, P("shroomlight"))
    put(5, 1, 5, P("magma_block")); put(4, 1, 5, B.campfire(True))
    fill(4, 2, 5, 5, 2, 5, AIR)
    window(WA, 2, 2, shutters="spruce", box="potted_dandelion")
    WAW = Wall(b, "west", "south", (1, 0))
    window(WAW, 6, 2, shutters="spruce", box="potted_red_tulip")
    ar = gable_roof(b, axis="x", w0=4, w1=8, a0=0, a1=5, y_eave=3, field="spruce", eave="dark_oak", rake="dark_oak",
                    ridge=stripped("spruce", "x"), cap=sl("dark_oak"), name="oven_house", rake_ends=(True, False),
                    ramp_pick=lambda x, y, z: ROOF.pick(x, y, z))
    for z in (5, 6, 7):
        put(1, 4, z, PLASTER.pick(1, 4, z))
    put(5, 6, 6, None)
    b.g.pop((5, 6, 6), None)
    # ---- the ONE chimney: 2 x 2 oven stack at x4..5, z5..6 against the hall's west wall
    smoke = stack(b, 4, 5, y0=3, y_top=15, base=STONE, shaft=BRICK, name="oven_stack")

    # ================= flour shed (east lean-to) =================
    for x in range(15, 18):
        for z in range(2, 9):
            put(x, 0, z, PLINTH.pick(x, 0, z))
            for y in (1, 2, 3):
                if (x, y, z) not in b.g:
                    put(x, y, z, AIR)
    for z in (2, 5, 8):
        fill(17, 0, z, 17, 2, z, lg(BEAM, "y"))
    for z in range(2, 9):
        put(17, 3, z, beam("z"))
    for z in (3, 4, 6, 7):
        put(17, 0, z, "@stone_brick_wall")
    eave, field = [], []
    for z in range(1, 10):
        rake = z in (1, 9)
        put(18, 3, z, st("spruce", "west")); eave.append((18, 3, z))
        put(17, 4, z, sl("spruce" if rake else "dark_oak")); field.append((17, 4, z))
        put(16, 4, z, st("spruce" if rake else "dark_oak", "west")); field.append((16, 4, z))
        put(15, 4, z, sl("spruce" if rake else "dark_oak")); field.append((15, 4, z))
    b.notes.roofs.append({"name": "shed", "eave": eave, "field": field, "overhang": 1})
    b.notes.roof_cells |= set(eave) | set(field)
    put(16, 3, 5, B.lantern(True))
    put(16, 0, 3, B.barrel()); put(16, 1, 3, B.barrel()); put(16, 0, 7, B.barrel("west"))
    for p in ((15, 0, 4), (15, 0, 5), (16, 0, 5), (15, 1, 4)):
        put(*p, P("white_wool"))
    put(15, 1, 5, P("white_carpet"))
    b.volume("hall", (6, 2, 14, 10), 13)
    b.volume("oven_house", (1, 4, 5, 8), 6)
    b.volume("flour_shed", (15, 2, 18, 8), 4)

    ground_layer(b)
    n.ramps = {k: [i for i in v] for k, v in RAMPS.items()}
    n.plinth = {"blocks": RAMPS["plinth"], "courses": 1,
                "ring": [(x, z) for x in range(6, 15) for z in range(2, 11) if x in (6, 14) or z in (2, 10)]}
    n.body_box = (1, 1, 18, 11)
    n.wall_rows = (1, 8)
    n.ground_region = (-1, -1, SX, SZ)
    n.seal = {(12, 0, 2), (12, 1, 2)}
    n.exempt_windows = set()
    return b, smoke


PATHSTONE = Ramp(P("cobblestone"), [(P("mossy_cobblestone"), .2, None), (P("andesite"), .15, None)], seed=12)


def ground_layer(b: Build) -> None:
    """y = -1: a cobblestone path from the door and the hatch to the box edge, a gravel apron one block wide around the
    walls whose outer edge is pushed out in soft clusters by smooth noise (R13/R14/R15). No lone squares."""
    import numpy as np
    from scipy.ndimage import gaussian_filter
    f = gaussian_filter(np.random.default_rng(9).standard_normal((SX + 4, SZ + 4)), 1.2)
    f = (f - f.mean()) / f.std()
    solid = {(x, z) for (x, y, z), d in b.g.items() if 0 <= y <= 1 and B.block_id(d) != "air"}
    path = {(x, z) for z in (0, 1) for x in (7, 8, 9, 11, 12, 13)}
    for (x, z) in sorted(path):
        b.put(x, -1, z, PATHSTONE.pick(x, 0, z))
    near = lambda x, z, d: any((x + dx, z + dz) in solid for dx in range(-d, d + 1) for dz in range(-d, d + 1))  # noqa
    for x in range(SX):
        for z in range(SZ):
            if (x, z) in solid or (x, z) in path:
                continue
            n = f[x, z]
            if near(x, z, 1) and n > -1.0 or near(x, z, 2) and n > 0.5:
                b.put(x, -1, z, GROUND.pick(x, 0, z))


def main() -> int:
    b, smoke = build()
    FL.POINTS["smoke"] = [list(smoke)]
    g = dict(b.g)
    B.resolve(g)
    chars, palette = B.to_palette(g)
    layers = house_lib.to_layers(chars, (SX, SY + GROUND_DEPTH, SZ), GROUND_DEPTH)
    blocks = house_lib.validate(chars, layers, palette, (SX, SY + GROUND_DEPTH, SZ), (0, 0, 0), FL.POINTS, SIGNS,
                                ground=GROUND_DEPTH)
    try:
        build_lint.lint_house(g, b.notes, FL.POINTS, palette)
    except build_lint.LintError:
        OUT_DIR.mkdir(parents=True, exist_ok=True)
        design_views.factory_previews(g, OUT_DIR)          # look at it anyway; the YAML is NOT written
        raise
    FL.validate_layout(g)
    header = [
        "# The `factory` cookie works. GENERATED by design/scripts/house_factory.py; do not edit by hand.",
        "# Spec and plot layout: design/factory.md (format: design/house-default.md, plus list points).",
        "# Axes: x = west->east, y = bottom->top, z = north->south. Front (door, sign, hatch) faces north (-Z).",
        f"# {blocks} blocks (not counting air/keep). ground-depth 1: the first layer is design y -1.",
    ]
    text = house_lib.write_yaml(header, layers, palette, (SX, SY + GROUND_DEPTH, SZ), (0, 0, 0), FL.POINTS, SIGNS,
                                ground=GROUND_DEPTH) + "\n".join(FL.layout_yaml()) + "\n"
    parsed = yaml.safe_load(text)
    assert parsed["layers"] == layers and parsed["size"] == [SX, SY + GROUND_DEPTH, SZ] and parsed["ground-depth"] == GROUND_DEPTH
    assert parsed["palette"] == palette and parsed["points"] == FL.POINTS and parsed["signs"] == SIGNS
    assert parsed["layout"] == FL.LAYOUT
    OUT_YML.parent.mkdir(parents=True, exist_ok=True)
    OUT_YML.write_text(text, encoding="utf-8")
    print(f"{OUT_YML.relative_to(ROOT)}: {blocks} blocks, {len(palette)} palette entries")
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    design_views.factory_previews(g, OUT_DIR)
    return 0


SIGNS = [{"at": [10, 4, 1], "lines": ["", "Cookie Works", "", ""]}]

if __name__ == "__main__":
    sys.exit(main())
