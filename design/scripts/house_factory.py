"""The `factory` cookie works: a block list file for `/bakery house place factory`.

Outputs:
  src/main/resources/houses/factory.yml            (the design file the plugin parses)
  design/scripts/previews/house_factory_layers.png (top view of every layer)
  design/scripts/previews/house_factory_iso.png    (orthographic 3/4 views, front and back, front elevation)
  design/scripts/previews/house_factory_plot.png   (top-down plot layout: belt, pedestal, shop, fence margin)
  design/scripts/previews/house_factory_camera.png (the build cinematic's view from `layout.camera`, 16:9, FOV 70)
Run with `mise run assets` (or `python design/scripts/house_factory.py` from the repo root).
Deterministic. Spec: design/factory.md (format: design/house-default.md + list points).

Design axes: x = west -> east (0..18), y = bottom -> top (0..16), z = north -> south (0..13).
The front (door, sign, loading hatch) faces north (-Z).
"""
from __future__ import annotations

import sys
from pathlib import Path

import yaml
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
import house_lib  # noqa: E402
from house_lib import KEEP, parse_block_data  # noqa: E402
from voxel_render import project, render, render_perspective  # noqa: E402

sys.path.insert(0, str(Path(__file__).resolve().parent))
import belt_style  # noqa: E402
import fence_style  # noqa: E402

OUT_YML = ROOT / "src/main/resources/houses/factory.yml"
OUT_DIR = ROOT / "design/scripts/previews"

SX, SY, SZ = 19, 17, 14
SIZE = (SX, SY, SZ)
ORIGIN = (0, 0, 0)

AIR = "minecraft:air"
STONE, MOSSY, CHISELED = "minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:chiseled_stone_bricks"
BRICK, TERRA, WTERRA = "minecraft:bricks", "minecraft:terracotta", "minecraft:white_terracotta"
FLOOR, RIDGE = "minecraft:spruce_planks", "minecraft:dark_oak_planks"
POST = "minecraft:spruce_log[axis=y]"
BEAM_X, BEAM_Z = "minecraft:stripped_spruce_log[axis=x]", "minecraft:stripped_spruce_log[axis=z]"
LOG_X = "minecraft:spruce_log[axis=x]"
MAGMA, SHROOM = "minecraft:magma_block", "minecraft:shroomlight"
COPPER, GRATE = "minecraft:waxed_cut_copper", "minecraft:waxed_copper_grate[waterlogged=false]"
COPPER_SLAB = "minecraft:waxed_cut_copper_slab[type=bottom,waterlogged=false]"
AWNING = "minecraft:waxed_copper_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]"
ROD = "minecraft:waxed_lightning_rod[facing=up,powered=false,waterlogged=false]"
CU_LANTERN = "minecraft:waxed_copper_lantern[hanging=false,waterlogged=false]"
LANTERN_HANG = "minecraft:lantern[hanging=true,waterlogged=false]"
LANTERN_STAND = "minecraft:lantern[hanging=false,waterlogged=false]"
CAMPFIRE = "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]"
SIGN = "minecraft:dark_oak_wall_sign[facing=north,waterlogged=false]"
BARREL_UP, BARREL_W = "minecraft:barrel[facing=up,open=false]", "minecraft:barrel[facing=west,open=false]"
SACK, SACK_TOP = "minecraft:white_wool", "minecraft:white_carpet"
SKYLIGHT = "minecraft:glass"
POT_RED, POT_ORANGE = "minecraft:potted_red_tulip", "minecraft:potted_orange_tulip"
ROOF_SLAB = "minecraft:dark_oak_slab[type=bottom,waterlogged=false]"


def stairs(mat: str, facing: str, half: str = "bottom") -> str:
    return f"minecraft:{mat}_stairs[facing={facing},half={half},shape=straight,waterlogged=false]"


def door(facing: str, half: str) -> str:
    return f"minecraft:spruce_door[facing={facing},half={half},hinge=left,open=false,powered=false]"


# connectable blocks: the generator fills in the connection flags from the neighbours
PANE, BARS, FENCE = "pane", "bars", "fence"
CONNECT_ID = {PANE: "glass_pane", BARS: "waxed_copper_bars", FENCE: "spruce_fence"}

# main hall: walls x=6..14, z=2..10; roof gable with the ridge along z (x=10)
ROOF = {5: 6, 6: 7, 7: 8, 8: 9, 9: 10, 10: 10, 11: 10, 12: 9, 13: 8, 14: 7, 15: 6}
# west annex (oven house) lean-to: x -> roof y, sloping down to the west
ANNEX_ROOF = {0: (3, "stair"), 1: (4, "slab"), 2: (4, "stair"), 3: (5, "slab"), 4: (5, "stair"), 5: (5, "planks")}
# east shed lean-to, sloping down to the east
SHED_ROOF = {18: (3, "stair"), 17: (4, "slab"), 16: (4, "stair"), 15: (5, "slab")}

POINTS = {
    "smoke": [[16, 16, 12], [4, 12, 4]],      # tall boiler stack, short oven stack
    "steam": [[10, 13, 6], [13, 10, 8]],      # ridge vent (cupola), copper pipe on the east slope
    "belt_out": [12, 0, 1],                   # first belt cell outside the loading hatch; belt runs north (-Z)
    "door": [8, 1, 2],                        # lower half of the front door
}
SIGNS = [{"at": [10, 4, 1], "lines": ["", "Cookie Works", "", ""]}]


def build() -> dict[tuple[int, int, int], str]:
    g: dict[tuple[int, int, int], str] = {}

    def put(x, y, z, data):
        assert 0 <= x < SX and 0 <= y < SY and 0 <= z < SZ, (x, y, z)
        g[(x, y, z)] = data

    # ---------------- main hall ----------------
    hall_x, hall_z = range(6, 15), range(2, 11)
    mossy = {(6, 5), (9, 10), (14, 3), (13, 10), (6, 9)}
    for x in hall_x:
        for z in hall_z:
            edge = x in (6, 14) or z in (2, 10)
            put(x, 0, z, (MOSSY if (x, z) in mossy else STONE) if edge else FLOOR)
            if not edge:
                for y in range(1, ROOF[x]):
                    put(x, y, z, AIR)
                continue
            for y in range(1, 5):
                corner = x in (6, 14) and z in (2, 10)
                put(x, y, z, STONE if corner and y % 2 == 1 else BRICK)     # stone quoins on the corners
            put(x, 5, z, TERRA)                                              # terracotta string course
            put(x, 6, z, BEAM_Z if x in (6, 14) and z not in (2, 10) else BEAM_X)
    # gables (front z=2, back z=10): white terracotta with spruce timbers under the roof line
    for z in (2, 10):
        for x in hall_x:
            for y in range(7, ROOF[x]):
                put(x, y, z, POST if y == ROOF[x] - 1 and x != 10 else WTERRA)
    for x, y in ((10, 7), (9, 8), (10, 8), (11, 8), (10, 9)):                # rose window, front gable
        put(x, y, 2, PANE)
    put(10, 8, 10, PANE)                                                     # back gable window

    # roof: dark oak stairs, plank ridge with a slab cap, overhang 1 all round (z=1..11)
    for z in range(1, 12):
        for x, y in ROOF.items():
            if x == 10:
                put(x, y, z, RIDGE)
                put(x, y + 1, z, ROOF_SLAB)
            else:
                put(x, y, z, stairs("dark_oak", "east" if x < 10 else "west"))
    for x in (8, 12):                                                        # skylights in both slopes
        for z in (4, 5, 7, 8):
            put(x, ROOF[x], z, SKYLIGHT)
    for z in (5, 6, 7):                                                      # ridge vent: copper louvre + cap
        put(10, 11, z, GRATE)
        put(10, 12, z, COPPER_SLAB)
    put(13, 9, 8, ROD)                                                       # copper steam pipe, east slope

    # front (z=2): door bay (west), timber post, loading hatch bay (east)
    put(8, 1, 2, door("north", "lower")); put(8, 2, 2, door("north", "upper"))
    put(8, 0, 1, stairs("stone_brick", "south"))                             # step up to the door
    for x in (7, 9):
        put(x, 2, 2, PANE); put(x, 3, 2, PANE)
    for y in range(1, 5):
        put(10, y, 2, POST)
    put(12, 0, 2, AIR); put(12, 1, 2, AIR)                                   # loading hatch opening
    for x in (11, 12, 13):
        put(x, 2, 2, COPPER)                                                 # copper lintel
        put(x, 3, 2, PANE)                                                   # wide window over the hatch
        put(x, 1, 1, AWNING)                                                 # copper awning over the hatch
    for x in (6, 14):                                                        # corner pilasters + lanterns
        put(x, 0, 1, STONE)
        for y in range(1, 5):
            put(x, y, 1, BRICK)
        put(x, 5, 1, CHISELED)
        put(x, 6, 1, CU_LANTERN)
    for x in (7, 8, 9):
        put(x, 3, 1, ROOF_SLAB)                                              # door canopy
    put(9, 2, 1, LANTERN_HANG)
    for x in (9, 10, 11):
        put(x, 4, 1, SIGN)                                                   # signboard (text on the middle one)
    put(7, 0, 1, POT_RED); put(9, 0, 1, POT_ORANGE)
    put(13, 0, 1, BARREL_UP)

    # back (z=10): small door, windows
    put(11, 1, 10, door("south", "lower")); put(11, 2, 10, door("south", "upper"))
    put(11, 0, 11, stairs("stone_brick", "north"))
    for x in (7, 8, 13):
        put(x, 2, 10, PANE); put(x, 3, 10, PANE)
    # sides: tall windows on the east wall (x=14); the west wall is covered by the annex
    for z in (4, 6):
        for y in (2, 3, 4):
            put(14, y, z, PANE)
    for z in (8, 9):
        put(14, 2, z, PANE); put(14, 3, z, PANE)

    # ---------------- west annex: the oven house ----------------
    for x in range(0, 6):
        ry, kind = ANNEX_ROOF[x]
        for z in range(2, 11):
            put(x, ry, z, {"stair": stairs("dark_oak", "east"), "slab": ROOF_SLAB, "planks": RIDGE}[kind])
    for x in range(1, 6):
        top = ANNEX_ROOF[x][0] - 1
        for z in range(3, 10):
            edge = x == 1 or z in (3, 9)
            put(x, 0, z, STONE if edge else FLOOR)
            for y in range(1, top + 1):
                put(x, y, z, BRICK if edge else AIR)
    put(2, 2, 3, PANE)                                                       # front window
    put(1, 2, 6, PANE)                                                       # west window
    # oven: copper bars in front, campfire + magma bed, shroomlight glow at the back
    for x in (3, 4):
        put(x, 1, 3, BARS); put(x, 2, 3, BARS)
        put(x, 3, 3, CHISELED)
        put(x, 1, 5, MAGMA); put(x, 2, 5, SHROOM)
        put(x, 2, 4, AIR)
        put(x, 3, 4, BRICK)
    put(3, 1, 4, MAGMA); put(4, 1, 4, CAMPFIRE)
    for y in (1, 2):
        put(2, y, 4, BRICK); put(5, y, 4, BRICK)
    # short oven stack right above the fire, with a corbelled crown
    for y in range(3, 12):
        put(4, y, 4, BRICK)
    put(4, 10, 3, stairs("brick", "south", "top")); put(4, 10, 5, stairs("brick", "north", "top"))
    put(3, 10, 4, stairs("brick", "east", "top")); put(5, 10, 4, stairs("brick", "west", "top"))
    for x, z in ((3, 3), (5, 3), (3, 5), (5, 5)):                           # square cap, not a cross
        put(x, 10, z, "minecraft:brick_slab[type=top,waterlogged=false]")
    put(4, 12, 4, AIR)                                                       # smoke cell

    # ---------------- east shed (flour store) ----------------
    for x, (ry, kind) in SHED_ROOF.items():
        for z in range(2, 9):
            put(x, ry, z, stairs("dark_oak", "west") if kind == "stair" else ROOF_SLAB)
    for z in (3, 7):
        for y in (0, 1, 2):
            put(18, y, z, FENCE)
    put(17, 0, 4, BARREL_UP); put(17, 1, 4, BARREL_UP); put(17, 0, 6, BARREL_W)
    put(16, 0, 4, SACK); put(16, 0, 5, SACK); put(17, 0, 5, SACK); put(16, 1, 4, SACK); put(16, 1, 5, SACK_TOP)
    put(16, 3, 5, LANTERN_HANG)

    # ---------------- tall boiler stack (back east) ----------------
    cx, cz = 16, 12
    for y in range(0, 3):
        for x in range(cx - 1, cx + 2):
            for z in range(cz - 1, cz + 2):
                put(x, y, z, STONE)
    plus = [(cx, cz), (cx - 1, cz), (cx + 1, cz), (cx, cz - 1), (cx, cz + 1)]
    for y in range(3, 16):
        for x, z in plus:
            put(x, y, z, COPPER if y == 9 else BRICK)
    for x in range(cx - 1, cx + 2):                                          # crown ring, hollow centre
        for z in range(cz - 1, cz + 2):
            put(x, 16, z, AIR if (x, z) == (cx, cz) else BRICK)
    for y in range(0, 3):
        put(15, y, 10, BRICK)                                                # flue duct to the hall
    put(15, 3, 10, "minecraft:brick_slab[type=bottom,waterlogged=false]")

    # ---------------- yard props ----------------
    put(1, 0, 2, BARREL_UP); put(1, 1, 2, BARREL_UP); put(2, 0, 2, SACK)
    put(0, 0, 4, SACK); put(0, 0, 5, SACK); put(0, 1, 4, SACK_TOP)
    for x in (2, 3, 4):
        put(x, 0, 10, LOG_X)                                                 # firewood under the annex eave
    put(3, 1, 10, LOG_X)
    for x in (5, 15):                                                        # lamp posts framing the front
        put(x, 0, 0, FENCE); put(x, 1, 0, FENCE); put(x, 2, 0, LANTERN_STAND)
    return g


def resolve_connections(g: dict) -> None:
    """Fill in pane/bars/fence connection flags from the neighbours (same rule the validator checks)."""
    def bid(p):
        v = g.get(p, AIR)
        return v if v in CONNECT_ID else parse_block_data(v)[0]

    def fam(b):
        return {PANE: "pane", BARS: "pane", FENCE: "wood_fence"}.get(b) or house_lib.family(b)

    out = {}
    for p, v in g.items():
        if v not in CONNECT_ID:
            continue
        flags = {}
        for d in ("east", "north", "south", "west"):
            dx, _, dz = house_lib.DIRS[d]
            nb = bid((p[0] + dx, p[1], p[2] + dz))
            flags[d] = "true" if house_lib.is_solid(nb) or fam(nb) == fam(v) else "false"
        flags["waterlogged"] = "false"
        out[p] = f"minecraft:{CONNECT_ID[v]}[" + ",".join(f"{k}={flags[k]}" for k in sorted(flags)) + "]"
    g.update(out)


# preferred palette chars per block data (the rest get chars from POOL, in first-use order)
PREFERRED = {
    AIR: "_", STONE: "S", MOSSY: "M", CHISELED: "C", BRICK: "B", TERRA: "T", WTERRA: "W", FLOOR: "F",
    RIDGE: "K", POST: "P", BEAM_X: "X", BEAM_Z: "Z", LOG_X: "l", MAGMA: "m", SHROOM: "g", COPPER: "c",
    GRATE: "#", COPPER_SLAB: "u", AWNING: "a", ROD: "i", CU_LANTERN: "j", LANTERN_HANG: "L",
    LANTERN_STAND: "k", CAMPFIRE: "f", SIGN: "H", BARREL_UP: "O", BARREL_W: "Q", SACK: "w", SACK_TOP: "y",
    SKYLIGHT: "G", POT_RED: "r", POT_ORANGE: "o", ROOF_SLAB: "=",
    stairs("dark_oak", "east"): ">", stairs("dark_oak", "west"): "<",
    stairs("stone_brick", "south"): "s", stairs("stone_brick", "north"): "n",
    door("north", "lower"): "d", door("north", "upper"): "D", door("south", "lower"): "e", door("south", "upper"): "E",
}
POOL = "-|+1234567890bhpqtvxzANRUVY"


def to_palette(g: dict) -> tuple[dict, dict]:
    """grid of block data -> grid of chars + palette (deterministic: scan order x, y, z)."""
    palette: dict[str, str] = {}
    rev: dict[str, str] = {}
    pool = iter(POOL)
    for p in sorted(g, key=lambda p: (p[1], p[2], p[0])):
        data = g[p]
        if data in rev:
            continue
        c = PREFERRED.get(data) or next(c for c in pool if c not in PREFERRED.values() and c not in palette)
        assert c not in palette, (c, data)
        palette[c] = data
        rev[data] = c
    ordered = dict(sorted(palette.items(), key=lambda kv: list(PREFERRED.values()).index(kv[0])
                          if kv[0] in PREFERRED.values() else 100 + POOL.index(kv[0])))
    return {p: rev[d] for p, d in g.items()}, ordered


# --- previews ----------------------------------------------------------------------------------
# Approximate average colours of the vanilla textures, for previews only.
BASE_COLOURS = {
    "air": (255, 255, 255), "stone_bricks": (122, 121, 122), "mossy_stone_bricks": (108, 121, 94),
    "chiseled_stone_bricks": (118, 117, 118), "bricks": (150, 82, 66), "terracotta": (152, 94, 67),
    "white_terracotta": (209, 178, 161), "spruce_planks": (115, 85, 52), "dark_oak_planks": (66, 44, 22),
    "spruce_log": (62, 44, 26), "stripped_spruce_log": (118, 90, 58), "magma_block": (190, 80, 30),
    "shroomlight": (240, 150, 70), "waxed_cut_copper": (192, 107, 79), "waxed_copper_grate": (180, 100, 74),
    "waxed_cut_copper_slab": (192, 107, 79), "waxed_copper_trapdoor": (190, 104, 76),
    "waxed_lightning_rod": (200, 112, 82), "waxed_copper_lantern": (120, 200, 120), "lantern": (236, 178, 72),
    "campfire": (250, 160, 50), "dark_oak_wall_sign": (78, 52, 28), "barrel": (125, 92, 54),
    "white_wool": (234, 236, 236), "white_carpet": (234, 236, 236), "glass": (190, 220, 230),
    "glass_pane": (178, 214, 226), "waxed_copper_bars": (190, 104, 76), "spruce_fence": (115, 85, 52),
    "potted_red_tulip": (200, 60, 48), "potted_orange_tulip": (232, 130, 40), "dark_oak_slab": (78, 52, 28),
    "dark_oak_stairs": (66, 44, 22), "stone_brick_stairs": (122, 121, 122), "brick_stairs": (150, 82, 66),
    "brick_slab": (150, 82, 66), "spruce_door": (106, 80, 48),
}


def colour(data: str):
    return BASE_COLOURS[parse_block_data(data)[0]]


def boxes_for(data: str, x: int, y: int, z: int):
    block, pr = parse_block_data(data)
    col = BASE_COLOURS[block]
    s = 1 / 16

    def b(x1, y1, z1, x2, y2, z2, rgb=col):
        return ((x + x1, y + y1, z + z1), (x + x2, y + y2, z + z2), rgb)

    if block == "air":
        return []
    if block.endswith("_slab"):
        return [b(0, 0, 0, 1, .5, 1)] if pr["type"] == "bottom" else [b(0, .5, 0, 1, 1, 1)]
    if block.endswith("_stairs"):
        lo, hi = (0, .5) if pr["half"] == "bottom" else (.5, 1)
        part = {"north": (0, 0, 1, .5), "south": (0, .5, 1, 1), "east": (.5, 0, 1, 1), "west": (0, 0, .5, 1)}
        x1, z1, x2, z2 = part[pr["facing"]]
        ylo, yhi = (.5, 1) if pr["half"] == "bottom" else (0, .5)
        return [b(0, lo, 0, 1, hi, 1), b(x1, ylo, z1, x2, yhi, z2)]
    if block.endswith("_door"):
        return [b(0, 0, 13 * s, 1, 1, 1)] if pr["facing"] == "north" else [b(0, 0, 0, 1, 1, 3 * s)]
    if block.endswith("_trapdoor"):
        return [b(0, 13 * s, 0, 1, 1, 1)]
    if block in ("glass_pane", "waxed_copper_bars", "spruce_fence"):
        w = 2 * s if block == "spruce_fence" else 1 * s
        post = [b(.5 - w, 0, .5 - w, .5 + w, 1, .5 + w)]
        arms = {"north": (.5 - w, 0, .5 + w, .5), "south": (.5 - w, .5, .5 + w, 1),
                "west": (0, .5 - w, .5, .5 + w), "east": (.5, .5 - w, 1, .5 + w)}
        for d, (x1, z1, x2, z2) in arms.items():
            if pr[d] == "true":
                if block == "spruce_fence":
                    post += [b(x1, 6 * s, z1, x2, 9 * s, z2), b(x1, 12 * s, z1, x2, 15 * s, z2)]
                else:
                    post.append(b(x1, 0, z1, x2, 1, z2))
        return post
    if block.endswith("_wall_sign"):
        return [b(0, 4.5 * s, 14 * s, 1, 12.5 * s, 1)]
    if block.endswith("lantern"):
        y0 = 1 * s if pr["hanging"] == "true" else 0
        return [b(5 * s, y0, 5 * s, 11 * s, y0 + 7 * s, 11 * s),
                b(7 * s, y0 + 7 * s, 7 * s, 9 * s, y0 + 9 * s, 9 * s, (60, 60, 66))]
    if block == "campfire":
        return [b(1 * s, 0, 1 * s, 15 * s, 4 * s, 15 * s, (90, 66, 40)), b(4 * s, 4 * s, 4 * s, 12 * s, 14 * s, 12 * s)]
    if block.endswith("lightning_rod"):
        return [b(6 * s, 0, 6 * s, 10 * s, 1, 10 * s)]
    if block == "white_carpet":
        return [b(0, 0, 0, 1, 1 * s, 1)]
    if block.startswith("potted_"):
        return [b(5 * s, 0, 5 * s, 11 * s, 6 * s, 11 * s, (142, 76, 56)), b(6 * s, 6 * s, 6 * s, 10 * s, 12 * s, 10 * s)]
    return [b(0, 0, 0, 1, 1, 1)]


# --- plot layout (contract: the `layout:` section) ----------------------------------------------
# Plot cells relative to the plot's min corner: x west->east, z north->south, y 0 = first air layer
# above the ground. The fence runs on the outer ring of plot-size. Design cell d lands on HOUSE_AT + d.
PLOT_X, PLOT_Z = 31, 30
HOUSE_AT = (6, 0, 10)
MARGIN = 5                                           # free blocks between the design box and the fence
BELT_LEN = 5


def to_plot(p):
    return (p[0] + HOUSE_AT[0], p[1] + HOUSE_AT[1], p[2] + HOUSE_AT[2])


BELT_FROM = to_plot(POINTS["belt_out"])
BELT_TO = (BELT_FROM[0], BELT_FROM[1], BELT_FROM[2] - (BELT_LEN - 1))   # last belt cell, next to the pedestal
PEDESTAL = (BELT_TO[0], 0, BELT_TO[2] - 1)
SHOP = (PEDESTAL[0] + 2, 0, PEDESTAL[2])
CLICKS = (PEDESTAL[0] - 2, 0, PEDESTAL[2])        # click upgrades panel: mirror of the shop, 2 west
VIEWPOINT = (15, 0, 2)
# build-cinematic camera: a feet cell like the viewpoint (eye = cell bottom centre + 1.62), outside the plot in
# front of the plot, north-east of the gate and raised, looking south-south-west and down: a 3/4 view of the facade
# with the belt and pedestal in clear view (see design/conveyor-belt.md). Checked against a 4:3 screen (the
# narrowest common one) at the default FOV 70, keeping 5% from the frame edge.
CAMERA = (23, 12, -13)
CAMERA_YAW, CAMERA_PITCH = 20, 24
CAMERA_FOV, CAMERA_ASPECT, CAMERA_MARGIN = 70, 4 / 3, 0.95
EYE_HEIGHT = 1.62
ENTRANCE = (15, 0)                                   # gate centre on the front (north) ring, 3 wide
LAYOUT = {
    "plot-size": [PLOT_X, PLOT_Z],
    "house-at": list(HOUSE_AT),
    "pedestal": {"at": list(PEDESTAL), "yaw": 180},
    "shop": {"at": list(SHOP), "yaw": 180},
    "clicks": {"at": list(CLICKS), "yaw": 180},
    "viewpoint": {"at": list(VIEWPOINT), "yaw": 0, "pitch": -14},
    "entrance": list(ENTRANCE),
    "belt": {"from": list(BELT_FROM), "to": list(BELT_TO)},
    "camera": {"at": list(CAMERA), "yaw": CAMERA_YAW, "pitch": CAMERA_PITCH},
}
BELT = [(BELT_FROM[0], z) for z in range(BELT_FROM[2], BELT_TO[2] - 1, -1)]   # plot (x, z) cells


def layout_yaml() -> list[str]:
    v = lambda p: "[" + ", ".join(str(i) for i in p) + "]"  # noqa: E731
    L = LAYOUT
    return [
        "layout:",
        f"  plot-size: {v(L['plot-size'])}",
        f"  house-at: {v(L['house-at'])}",
        f"  pedestal: {{at: {v(L['pedestal']['at'])}, yaw: {L['pedestal']['yaw']}}}",
        f"  shop: {{at: {v(L['shop']['at'])}, yaw: {L['shop']['yaw']}}}",
        f"  clicks: {{at: {v(L['clicks']['at'])}, yaw: {L['clicks']['yaw']}}}",
        f"  viewpoint: {{at: {v(L['viewpoint']['at'])}, yaw: {L['viewpoint']['yaw']}, "
        f"pitch: {L['viewpoint']['pitch']}}}",
        f"  entrance: {v(L['entrance'])}",
        f"  belt: {{from: {v(L['belt']['from'])}, to: {v(L['belt']['to'])}}}",
        f"  camera: {{at: {v(L['camera']['at'])}, yaw: {L['camera']['yaw']}, pitch: {L['camera']['pitch']}}}",
    ]


def validate_layout(chars: dict, palette: dict) -> None:
    solid3 = {to_plot((x, y, z)) for (x, y, z), c in chars.items() if parse_block_data(palette[c])[0] != "air"}

    class Occupied:                                  # (x, z) "occupied" = a block at belt/feet/head level
        def __init__(self, levels):
            self.levels = levels

        def __contains__(self, p):
            return any((p[0], y, p[1]) in solid3 for y in self.levels)

    occupied = Occupied((0, 1))
    bx0, bz0, bx1, bz1 = HOUSE_AT[0], HOUSE_AT[2], HOUSE_AT[0] + SX - 1, HOUSE_AT[2] + SZ - 1

    def inner(x, z):
        return 1 <= x <= PLOT_X - 2 and 1 <= z <= PLOT_Z - 2

    def in_box(x, z):
        return bx0 <= x <= bx1 and bz0 <= z <= bz1

    assert HOUSE_AT[1] == 0
    assert bx0 - MARGIN >= 1 and bx1 + MARGIN <= PLOT_X - 2 and bz1 + MARGIN <= PLOT_Z - 2, "margin to fence"
    assert len(BELT) >= 4 and BELT[0] == (BELT_FROM[0], BELT_FROM[2])
    for x, z in BELT:
        assert inner(x, z) and (x, z) not in Occupied((0,)), f"belt cell {(x, z)} is not free"
    assert (PEDESTAL[0], PEDESTAL[2] + 1) == (BELT_TO[0], BELT_TO[2]), "belt must end next to the pedestal"
    for name, (x, y, z) in (("pedestal", PEDESTAL), ("shop", SHOP), ("clicks", CLICKS), ("viewpoint", VIEWPOINT)):
        assert y == 0 and inner(x, z) and (x, z) not in occupied and (x, z) not in BELT, f"{name} not free"
        assert not in_box(x, z), f"{name} must be outside the house footprint"
    assert PEDESTAL[2] - 1 >= MARGIN, "room in front of the pedestal"
    ex, ez = ENTRANCE
    assert ez == 0 and 2 <= ex <= PLOT_X - 3, "entrance: front ring, gate fully between the corners"
    for gx in (ex - 1, ex, ex + 1):                  # the walk-in from the gate to the viewpoint is free
        assert (gx, 1) not in occupied
    bx, by, bz = POINTS["belt_out"]
    assert parse_block_data(palette[chars[(bx, by, bz + 1)]])[0] == "air", "hatch behind belt_out is open"
    # the belt frame (houses/belt.yml) fills y 0 .. surface-height-1 of the belt cells; the surface display and
    # the cookies need the cell above (a closed top-half trapdoor awning is fine, anything else is not)
    for x, z in BELT:
        y = belt_style.STYLE["surface-height"]
        if (x, y, z) in solid3:
            d = (x - HOUSE_AT[0], y, z - HOUSE_AT[2])
            b, pr = parse_block_data(palette[chars[d]])
            assert b.endswith("_trapdoor") and pr["half"] == "top" and pr["open"] == "false", f"belt clearance {d}"
    # camera: above ground, outside the house, in the air, and it frames everything
    cx, cy, cz = CAMERA
    assert cy >= 1 and not in_box(cx, cz), "camera: raised and outside the design box"
    assert (cx, cy, cz) not in solid3 and (cx, cy + 1, cz) not in solid3
    sx, sy, depth = project(framing_points(), camera_eye(), CAMERA_YAW, CAMERA_PITCH, CAMERA_FOV, CAMERA_ASPECT)
    assert (depth > 1).all() and abs(sx).max() <= CAMERA_MARGIN and abs(sy).max() <= CAMERA_MARGIN, \
        f"camera framing: x {sx.min():.2f}..{sx.max():.2f}, y {sy.min():.2f}..{sy.max():.2f}"


def camera_eye():
    """Camera eye in plot block coordinates (bottom centre of the feet cell + eye height)."""
    return (CAMERA[0] + 0.5, CAMERA[1] + EYE_HEIGHT, CAMERA[2] + 0.5)


def framing_points():
    """What the camera must show: the fence ring (with the gate arch), the whole design box and the pedestal."""
    pts = [(x, y, z) for x in (0, PLOT_X) for z in (0, PLOT_Z) for y in (0, 2.5)]
    pts += [(ENTRANCE[0] + dx, 5, 0) for dx in (-2, 3)]
    pts += [(x, y, z) for x in (HOUSE_AT[0], HOUSE_AT[0] + SX) for z in (HOUSE_AT[2], HOUSE_AT[2] + SZ)
            for y in (0, SY)]
    pts += [(PEDESTAL[0] + .5, 2.5, PEDESTAL[2] + .5)]
    return pts


def ground_boxes():
    x0, z0 = -HOUSE_AT[0], -HOUSE_AT[2]
    return [((x0, -1, z0), (x0 + PLOT_X, 0, z0 + PLOT_Z), (96, 140, 62))]


def plot_boxes():
    """Belt, pedestal, shop and fence-ring markers for the preview (not part of the design)."""
    hx, hz = HOUSE_AT[0], HOUSE_AT[2]
    out = belt_style.belt_boxes(belt_style.STYLE, BELT, ox=-hx, oz=-hz)
    px, pz = PEDESTAL[0] - hx, PEDESTAL[2] - hz
    out += [((px + .2, 0, pz + .2), (px + .8, .9, pz + .8), (230, 225, 210)),
            ((px + .25, 1.1, pz + .45), (px + .75, 1.6, pz + .55), (196, 140, 70))]
    sx, sz = SHOP[0] - hx, SHOP[2] - hz
    out.append(((sx + .3, 0, sz + .3), (sx + .7, 1.8, sz + .7), (90, 60, 160)))
    cx, cz = CLICKS[0] - hx, CLICKS[2] - hz
    out.append(((cx + .3, 0, cz + .3), (cx + .7, 1.8, cz + .7), (60, 150, 160)))
    return out


def fence_boxes():
    """The plot fence ring from houses/fence.yml (approximate shapes), in design coordinates."""
    st, s = fence_style.STYLE, 1 / 16
    col = {"spruce_fence": (115, 85, 52), "spruce_log": (62, 44, 26), "stone_bricks": (122, 121, 122),
           "bricks": (150, 82, 66), "chiseled_stone_bricks": (118, 117, 118), "lantern": (236, 178, 72),
           "waxed_copper_lantern": (120, 200, 120), "dark_oak_planks": (66, 44, 22), "dark_oak_slab": (78, 52, 28)}
    hx, hz = HOUSE_AT[0], HOUSE_AT[2]
    out = []

    def column(x, z, datas):
        for y, data in enumerate(datas):
            if data is None:
                continue
            b = parse_block_data(data)[0]
            x0, z0 = x - hx, z - hz
            if b.endswith("lantern"):
                out.append(((x0 + 5 * s, y, z0 + 5 * s), (x0 + 11 * s, y + 7 * s, z0 + 11 * s), col[b]))
            else:
                out.append(((x0, y, z0), (x0 + 1, y + (.5 if b.endswith("_slab") else 1), z0 + 1), col[b]))

    def rail(x, z, along_x):
        x0, z0, c = x - hx, z - hz, col["spruce_fence"]
        out.append(((x0 + 6 * s, 0, z0 + 6 * s), (x0 + 10 * s, 1, z0 + 10 * s), c))
        for y1, y2 in ((6 * s, 9 * s), (12 * s, 15 * s)):
            if along_x:
                out.append(((x0, y1, z0 + 7 * s), (x0 + 1, y2, z0 + 9 * s), c))
            else:
                out.append(((x0 + 7 * s, y1, z0), (x0 + 9 * s, y2, z0 + 1), c))

    g, ex = st["gate"], ENTRANCE[0]
    ring = [(x, 0) for x in range(PLOT_X)] + [(x, PLOT_Z - 1) for x in range(PLOT_X)]
    ring += [(0, z) for z in range(1, PLOT_Z - 1)] + [(PLOT_X - 1, z) for z in range(1, PLOT_Z - 1)]
    for x, z in ring:
        corner = x in (0, PLOT_X - 1) and z in (0, PLOT_Z - 1)
        along_x = z in (0, PLOT_Z - 1)
        k = x if along_x else z
        if corner:
            column(x, z, st["corner"] + [st["topper"]])
        elif z == 0 and x in (ex - 2, ex + 2):
            column(x, z, g["left"] if x < ex else g["right"])
        elif z == 0 and abs(x - ex) <= 1:
            column(x, z, g["arch"])
        elif k % st["post-every"] == 0 and not (z == 0 and abs(x - ex) <= 3) and 1 < k < (PLOT_X if along_x
                                                                                             else PLOT_Z) - 2:
            column(x, z, st["post"] + ([st["topper"]] if (k // st["post-every"]) % st["topper-every"] == 0 else []))
        else:
            rail(x, z, along_x)
    return out


def camera_view(boxes) -> Image.Image:
    """What the player sees from layout.camera (16:9, FOV 70), with the 4:3 crop marked."""
    hx, hz = HOUSE_AT[0], HOUSE_AT[2]
    ex, ey, ez = camera_eye()
    world = [((-40 - hx, -1, -40 - hz), (PLOT_X + 40 - hx, 0, PLOT_Z + 40 - hz), (88, 128, 58)),
             ((-hx, -1, -hz), (PLOT_X - hx, -0.02, PLOT_Z - hz), (100, 146, 64))] + boxes + fence_boxes()
    img = render_perspective(world, (ex - hx, ey, ez - hz), CAMERA_YAW, CAMERA_PITCH, CAMERA_FOV, (1280, 720))
    d = ImageDraw.Draw(img)
    w43 = int(720 * 4 / 3)
    x0 = (1280 - w43) // 2
    d.rectangle([x0, 0, x0 + w43 - 1, 719], outline=(255, 255, 255, 160), width=2)
    f = house_lib.font(16)
    d.text((10, 8), f"layout.camera {list(CAMERA)}, yaw {CAMERA_YAW}, pitch {CAMERA_PITCH}; 16:9, FOV {CAMERA_FOV}; "
           "white box = 4:3 screen", fill=(20, 20, 30), font=f)
    return img


def plot_sheet(chars: dict, palette: dict) -> Image.Image:
    cell = 18
    w, h = PLOT_X * cell, PLOT_Z * cell
    img = Image.new("RGBA", (w + 40, h + 175), (40, 44, 52, 255))
    d = ImageDraw.Draw(img)
    f = house_lib.font(14)
    top = {}
    for (x, y, z), c in chars.items():
        p = (x + HOUSE_AT[0], z + HOUSE_AT[2])
        if parse_block_data(palette[c])[0] != "air" and y >= top.get(p, (-1, None))[0]:
            top[p] = (y, c)
    ox, oy = 20, 30
    d.text((ox, 8), f"factory plot layout, top view (north up), 1 square = 1 block, plot-size {PLOT_X} x {PLOT_Z}",
           fill=(235, 235, 235), font=f)
    for gx in range(PLOT_X):
        for gz in range(PLOT_Z):
            px, py = ox + gx * cell, oy + gz * cell
            rgb = (96, 140, 62) if (gx + gz) % 2 else (90, 132, 58)
            if gx in (0, PLOT_X - 1) or gz in (0, PLOT_Z - 1):
                rgb = (115, 85, 52)
                if gz == 0 and abs(gx - ENTRANCE[0]) <= 1:
                    rgb = (200, 170, 110)
            if (gx, gz) in top:
                y, c = top[(gx, gz)]
                k = 0.55 + 0.45 * min(y, 12) / 12
                rgb = tuple(int(v * k) for v in colour(palette[c]))
            if (gx, gz) in BELT:
                rgb = (40, 40, 44)
            if (gx, gz) == (PEDESTAL[0], PEDESTAL[2]):
                rgb = (240, 220, 120)
            if (gx, gz) == (SHOP[0], SHOP[2]):
                rgb = (120, 90, 200)
            if (gx, gz) == (CLICKS[0], CLICKS[2]):
                rgb = (60, 150, 160)
            if (gx, gz) == (VIEWPOINT[0], VIEWPOINT[2]):
                rgb = (230, 80, 80)
            d.rectangle([px, py, px + cell - 1, py + cell - 1], fill=rgb, outline=(30, 34, 30))
    vx, vz = ox + VIEWPOINT[0] * cell + cell // 2, oy + VIEWPOINT[2] * cell + cell // 2
    d.line([vx, vz, vx, vz + 3 * cell], fill=(230, 80, 80), width=3)          # looking south (yaw 0)
    bx, by = ox + HOUSE_AT[0] * cell, oy + HOUSE_AT[2] * cell
    d.rectangle([bx, by, bx + SX * cell - 1, by + SZ * cell - 1], outline=(255, 255, 255), width=2)
    legend = [((40, 40, 44), f"belt {list(BELT_FROM)} -> {list(BELT_TO)} (5 cells)"),
              ((240, 220, 120), f"pedestal {list(PEDESTAL)}, yaw 180"),
              ((120, 90, 200), f"shop {list(SHOP)}, yaw 180"),
              ((60, 150, 160), f"clicks {list(CLICKS)}, yaw 180"),
              ((230, 80, 80), f"viewpoint {list(VIEWPOINT)}, yaw 0, pitch -14"),
              ((115, 85, 52), "fence: outer ring of plot-size"),
              ((200, 170, 110), f"entrance (3-wide gate) centre {list(ENTRANCE)}"),
              ((255, 255, 255), f"design box {SX} x {SY} x {SZ} at house-at {list(HOUSE_AT)}")]
    for i, (rgb, text) in enumerate(legend):
        lx, ly = ox + (i % 2) * (w // 2), oy + h + 14 + (i // 2) * 22
        d.rectangle([lx, ly, lx + 14, ly + 14], fill=rgb, outline=(20, 20, 20))
        d.text((lx + 22, ly), text, fill=(235, 235, 235), font=f)
    return img


def main() -> int:
    g = build()
    resolve_connections(g)
    chars, palette = to_palette(g)
    layers = house_lib.to_layers(chars, SIZE)
    blocks = house_lib.validate(chars, layers, palette, SIZE, ORIGIN, POINTS, SIGNS)
    header = [
        "# The `factory` cookie works. GENERATED by design/scripts/house_factory.py; do not edit by hand.",
        "# Spec and plot layout: design/factory.md (format: design/house-default.md, plus list points).",
        "# Axes: x = west->east, y = bottom->top, z = north->south. Front (door, sign, hatch) faces north (-Z).",
        f"# {blocks} blocks (not counting air/keep).",
    ]
    validate_layout(chars, palette)
    text = house_lib.write_yaml(header, layers, palette, SIZE, ORIGIN, POINTS, SIGNS) + "\n".join(layout_yaml()) + "\n"
    parsed = yaml.safe_load(text)
    assert parsed["layers"] == layers and parsed["size"] == list(SIZE)
    assert parsed["palette"] == palette and parsed["points"] == POINTS and parsed["signs"] == SIGNS
    assert parsed["layout"] == LAYOUT
    OUT_YML.parent.mkdir(parents=True, exist_ok=True)
    OUT_YML.write_text(text, encoding="utf-8")

    colours = {c: (colour(d) if parse_block_data(d)[0] != "air" else (255, 255, 255)) for c, d in palette.items()}
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    house_lib.layer_sheet(layers, SIZE, colours, "factory: layers bottom->top, x ->, z (north at top) v; "
                          "'.' = keep", cell=22, cols=5).save(OUT_DIR / "house_factory_layers.png")
    boxes = ground_boxes() + plot_boxes()
    for (x, y, z), data in sorted(g.items()):
        boxes += boxes_for(data, x, y, z)
    house_lib.iso_sheet(boxes, px_per_unit=22).save(OUT_DIR / "house_factory_iso.png")
    # a closer front 3/4 from the plot entrance (no plot ground, so the building fills the frame)
    near = [((-1, -1, -1), (SX + 1, 0, SZ + 1), (96, 140, 62))] + [bx_ for bx_ in boxes[1:]]
    render(near, yaw=200, pitch=22, px_per_unit=40).save(OUT_DIR / "house_factory_front.png")
    plot_sheet(chars, palette).save(OUT_DIR / "house_factory_plot.png")
    camera_view(boxes[1:]).save(OUT_DIR / "house_factory_camera.png")
    print(f"{OUT_YML.relative_to(ROOT)}: {blocks} blocks, {len(palette)} palette entries")
    print(f"previews in {OUT_DIR.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
