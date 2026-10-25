"""The `mill` place: a stone watermill on a retaining wall out of its pond, with a breastshot wheel in a stone slot under the
jettied upper floor, a wing store, a staddle granary, a mill race and a fenced stone bridge, on a front yard facing the road.

Outputs:
  src/main/resources/houses/mill.yml              (place file: house format + points, ground-depth 4; FINAL orientation, road side NORTH)
  design/scripts/previews/place_mill_*.png         (textured previews; written even when the lint fails)
Run with `mise run assets`. Deterministic. Spec: design/countryside.md (Mill), refs: design/refs-mill.md.

Axes: x west -> east, y up (ground layers y -4..-1), z north -> south. The wheel is the item model cookieclicker:waterwheel
(design/waterwheel.md) displayed at points.wheel; the blocks inside the slot are its static fallback.
"""
from __future__ import annotations

import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
sys.path.insert(0, str(Path(__file__).resolve().parent))
import block_ids  # noqa: E402
import blocks as B  # noqa: E402
import build_lint  # noqa: E402
import farm_kits as K  # noqa: E402
import farm_lint  # noqa: E402
import house_lib  # noqa: E402
import mill_layout as ML  # noqa: E402
import place_lib  # noqa: E402
import place_views  # noqa: E402
import scenes as S  # noqa: E402
from blocks import AIR, lg, plain as P, sl, st, stripped  # noqa: E402
from build import Build, Ramp, Wall, door, window  # noqa: E402

OUT_YML = ROOT / "src/main/resources/houses/mill.yml"
OUT_DIR = ROOT / "design/scripts/previews"
SX, SY, SZ, GROUND, SIZE = ML.SX, ML.SY, ML.SZ, ML.GROUND, ML.SIZE
BEAM = "dark_oak"

# --- materials: every one a ramp of 3+ blocks (R6/R7) ------------------------------------------------------------------
STONE = Ramp(P("stone_bricks"), [(P("cracked_stone_bricks"), .13, None), (P("mossy_stone_bricks"), .18, "low"),
                                 (P("tuff_bricks"), .09, None)], seed=3)
PLINTH = Ramp(P("cobblestone"), [(P("mossy_cobblestone"), .27, "low"), (P("andesite"), .15, None)], seed=4)
PLASTER = Ramp(P("calcite"), [(P("diorite"), .18, None), (P("polished_diorite"), .12, "low")], seed=5, height=3, y0=5)
ROOF = Ramp("dark_oak", [], seed=8, height=14, y0=8)
FLOOR = Ramp(P("spruce_planks"), [(P("oak_planks"), .22, None), (P("dark_oak_planks"), .15, None)], seed=9)
COBBLE = Ramp(P("cobblestone"), [(P("mossy_cobblestone"), .16, None), (P("andesite"), .14, None)], seed=31)
GRAVEL = Ramp(P("gravel"), [(P("andesite"), .12, None)], seed=22)
PATH = Ramp(P("coarse_dirt"), [(P("gravel"), .3, None), (P("podzol[snowy=false]"), .12, None)], seed=33)
BANK = Ramp(P("coarse_dirt"), [(P("moss_block"), .3, None), (P("clay"), .15, None), (P("gravel"), .1, None)], seed=34)
BED = Ramp(P("clay"), [(P("gravel"), .3, None), (P("mud"), .2, None)], seed=35)
DIRT = Ramp(P("dirt"), [(P("coarse_dirt"), .08, None)], seed=36)
GRASS = B.grass()
RAMPS = {
    "stone": ["stone_bricks", "cracked_stone_bricks", "mossy_stone_bricks", "tuff_bricks"],
    "plinth": ["cobblestone", "mossy_cobblestone", "andesite"],
    "plaster": ["calcite", "diorite", "polished_diorite"],
    "timber": ["dark_oak_log", "stripped_dark_oak_log", "dark_oak_planks"],
    "roof": ["dark_oak_stairs", "deepslate_tile_stairs", "deepslate_tile_slab"],
    "floor": ["spruce_planks", "oak_planks", "dark_oak_planks"],
}


class Base:
    """Ramp-like: plinth blocks at and below y 0 (cobble, mossy at the waterline), stone bricks above."""

    def pick(self, x, y, z):
        return PLINTH.pick(x, y, z) if y <= 0 else STONE.pick(x, y, z)


BASE = Base()


def beam(axis: str) -> str:
    return stripped(BEAM, axis)


def wobble(n: int, lo: int, hi: int, rng, run=(3, 5)) -> list:
    out, off = [], rng.randint(lo, hi)
    left = 0
    for _ in range(n):
        if left <= 0:
            left = rng.randint(*run)
            off = rng.choice([o for o in range(lo, hi + 1) if o != off] or [off])
        out.append(off)
        left -= 1
    return out


def pond_cells(b: Build) -> set:
    """Pond A: one broad organic pond (an ellipse with noisy shore) from the retaining wall under the wheel out to the east
    edge, x 23..44, z 4..14 (z <= 12 east of the hall)."""
    import numpy as np
    from scipy.ndimage import gaussian_filter
    noise = gaussian_filter(np.random.default_rng(12).standard_normal((60, 40)), 1.6)
    noise = (noise - noise.mean()) / noise.std()
    cells = set()
    for x in range(23, 45):
        for z in range(3, 15):
            if x >= 34 and z > 12:
                continue
            d = ((x - 34.0) / 12.2) ** 2 + ((z - 10.4) / 7.4) ** 2
            if x <= 33 and z >= 11:
                d = min(d, 0.5)                                    # the part against the hall wall: always water
            if d + 0.28 * noise[x, z] < 1.0:
                cells.add((x, z))
    for x in range(25, 34):
        for z in range(11, 15):
            cells.add((x, z))
    for z in (8, 9):                                              # the tail race leaves through the east edge
        for x in range(40, 46):
            cells.add((x, z))
    return cells


def build():
    b = Build(SIZE, ground=GROUND)
    n = b.notes
    put, fill = b.put, b.fill
    rng = b.rng

    # ================= ground: height field, terrace east of the hall =================
    heights = {(x, z): -1 for x in range(SX) for z in range(SZ)}
    K.cut_and_fill(heights, (35, 13, 45, 30), 1, 1)
    K.height_field_ground(b, heights, top=Ramp(GRASS, []), sub=DIRT, y_floor=-GROUND)

    # ================= pond A =================
    pond = pond_cells(b)
    K.pond(b, pond, y_surface=-2, y_bed=-4, bed=BED, rim=BANK, lily=0.14)
    for (x, z) in pond:
        put(x, -1, z, AIR)
    for (x, z) in pond:                                          # a muddy shore ring of the bank cells
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, z + dz)
            if q not in pond and b.get(q[0], -1, q[1]) is not None and "grass" in (b.get(q[0], -1, q[1]) or "") \
                    and 0 <= q[0] < SX and 0 <= q[1] < SZ and rng.random() < .55:
                put(q[0], -1, q[1], BANK.pick(q[0], 0, q[1]))
    n.water_exits |= {(45, y, z) for y in (-3, -2) for z in (8, 9)}

    # retaining wall of the terrace towards the pond
    K.retaining_wall(b, 34, 13, 44, 13, -4, 1, ramp=BASE, coping="stone_brick")
    for y in range(-4, 2):
        put(34, y, 14, BASE.pick(34, y, 14))
    put(34, 2, 14, sl("stone_brick", "bottom"))

    # ================= hall: x 15..33, ground z 15..24, upper z 14..24 =================
    X0, X1 = ML.HALL_X
    ZG0, ZG1 = ML.HALL_ZG
    ZU0, ZU1 = ML.HALL_ZU
    ring_g = [(x, z) for x in range(X0, X1 + 1) for z in range(ZG0, ZG1 + 1) if x in (X0, X1) or z in (ZG0, ZG1)]
    for x in range(X0, X1 + 1):
        for z in range(ZG0, ZG1 + 1):
            edge = (x, z) in set(ring_g)
            put(x, 0, z, PLINTH.pick(x, 0, z) if edge else FLOOR.pick(x, 0, z))
            if edge:
                for y in (1, 2, 3):
                    put(x, y, z, STONE.pick(x, y, z))
                put(x, 4, z, STONE.pick(x, 4, z) if z == ZG0 else beam("x" if z == ZG1 else "z"))
            else:
                fill(x, 1, z, x, 8, z, AIR)
    # upper floor ring: north row z=14 (the jetty), south z=24, sides x=15/33
    ring_up = [(x, ZU0) for x in range(X0, X1 + 1)] + [(x, ZU1) for x in range(X0, X1 + 1)] + \
              [(X0, z) for z in range(ZU0 + 1, ZU1)] + [(X1, z) for z in range(ZU0 + 1, ZU1)]
    for (x, z) in ring_up:
        for y in (5, 6, 7):
            put(x, y, z, PLASTER.pick(x, y, z))
        put(x, 8, z, beam("x" if z in (ZU0, ZU1) else "z"))
    for z in range(ZU0, ZU0 + 1):
        pass
    for x in range(X0, X1 + 1):
        put(x, 5, ZU0, beam("x"))                                              # jetty bressumer
    for z in range(ZU0 + 1, ZG0):                                              # z=14 row over the old ground plane: nothing below
        pass
    for z in range(ZG0, ZU1 + 1):                                              # the upper side walls start over the ground wall
        pass
    # corner posts and bay posts (panels at most 3 wide)
    for (x, z) in ((X0, ZU0), (X1, ZU0), (X0, ZU1), (X1, ZU1)):
        for y in (6, 7):
            put(x, y, z, lg(BEAM, "y"))
    for z in (ZU0, ZU1):
        for x in (19, 23, 27, 31):
            for y in ((6, 7) if z == ZU1 else (6, 7)):
                put(x, y, z, lg(BEAM, "y"))
    for x in (X0, X1):
        for z in (17, 21):
            for y in (5, 6, 7):
                put(x, y, z, lg(BEAM, "y"))

    WNG = Wall(b, "north", "east", (0, ZG0))                                   # ground north plane (z=15)
    WNU = Wall(b, "north", "east", (0, ZU0))                                   # upper north plane (z=14)
    WSU = Wall(b, "south", "east", (0, ZU1))
    WEU = Wall(b, "east", "south", (X1, 0))
    WWU = Wall(b, "west", "south", (X0, 0))

    # ---- the wheel slot in the north ground wall (water, piers, lintel, static wheel)
    slot = K.wheel_slot(b, WNG, ML.SLOT_C, 0, radius=3, depth=3, ramp=BASE, y_floor=-4, y_top=4, pier=2, beam=BEAM)
    for x in range(ML.SLOT_C - 3, ML.SLOT_C + 4):                              # pit water, same body as the pond
        for z in (15, 16, 17):
            for y in (-3, -2):
                if (x, y, z) not in slot["fallback"]:
                    put(x, y, z, B.water())
    for x in range(ML.SLOT_C - 3, ML.SLOT_C + 4):
        for y in (-1, 0, 1, 2, 3):
            put(x, y, 14, None)
    # pilasters in front of the piers carry the jetty beam
    for x in (15, 24, 32, 33):
        for y in range(-4, 4):
            put(x, y, 14, BASE.pick(x, y, 14))
        put(x, 4, 14, P("chiseled_stone_bricks"))
    # door west part
    door(WNG, 17, 1, mat="spruce", hood=False)
    put(17, 0, 14, st("stone_brick", "south"))
    for x in (19, 21, 26, 28, 30):                                             # jetty brackets (inverted stairs)
        put(x, 4, 14, WNG.stair(BEAM, "top", "in"))
    put(19, 3, 14, B.lantern(True))
    window(WNG, 20, 2, width=2, height=2, shutters="spruce", box=None, sill="spruce", mullion=None)
    # ground frame piers on the north face west part: log posts either side of the window
    for y in (1, 2, 3):
        put(19, y, 15, lg(BEAM, "y")) if y == 2 else None
    # north upper windows and the hoist hatch above window 1
    for i, x in enumerate((18, 22, 26)):
        window(WNU, x, 6, height=2, shutters=None, box=None,
               sill="spruce")

    # ================= hall roof (steep, half-hip at the east end) =================
    roof_field = lambda x, y, z: ROOF.pick(x, y, z)                            # noqa: E731
    roof = K.half_hip_roof(b, (14, 13, 33, 25), ML.Y_EAVE, end="E", k=3, field="dark_oak", eave="deepslate_tile",
                           rake="deepslate_tile", ridge=stripped("dark_oak", "x"), cap=sl("deepslate_tile"),
                           name="hall", ramp_pick=roof_field, riser=P("dark_oak_planks"))
    # gable (west) and half-hip wall (east) infill
    K.fill_gable_wall(b, roof, [(X0, z) for z in range(ZU0, ZU1 + 1)], 9, lambda x, y, z: PLASTER.pick(x, y, z))
    K.fill_gable_wall(b, roof, [(X1, z) for z in range(ZU0, ZU1 + 1)], 9, lambda x, y, z: PLASTER.pick(x, y, z))
    n.volumes.append({"name": "hall", "footprint": (X0, ZU0, X1, ZU1), "top": roof["ridge_y"] + 1})

    # ---- dormers and the hoist gablet on the north slope
    oak = lambda x, y, z: P("dark_oak_planks")                               # noqa: E731
    K.dormer(b, roof, WNU, 18, 0, 9, wall_mat=oak, trim=BEAM, roof_mat="deepslate_tile",
             name="hoist", beam=True, hatch=True, h=3)
    for uc in (22, 26):
        K.dormer(b, roof, WNU, uc, 0, 9, wall_mat=oak, trim=BEAM, roof_mat="deepslate_tile",
                 name=f"dormer{uc}", h=3)

    return b, roof, slot, pond




def rest(b: Build, roof: dict, slot: dict, pond: set) -> dict:
    n = b.notes
    put, fill, rng = b.put, b.fill, b.rng
    X0, X1 = ML.HALL_X
    ZU0, ZU1 = ML.HALL_ZU
    WNG = Wall(b, "north", "east", (0, 15))
    WSG = Wall(b, "south", "east", (0, 24))
    WSU = Wall(b, "south", "east", (0, ML.HALL_ZU[1]))
    WEG = Wall(b, "east", "south", (X1, 0))
    WWG = Wall(b, "west", "south", (X0, 0))
    plaster = lambda x, y, z: PLASTER.pick(x, y, z)                    # noqa: E731

    # ---- hall: south facade (back door, windows), east end, west gable
    door(WSG, 20, 1, mat="spruce", hood=True)
    put(20, 0, 25, st("stone_brick", "north"))
    for x in (24, 28):
        window(WSG, x, 2, height=2, shutters="spruce", box=None, sill="spruce") if x == 24 else None
    for i, x in enumerate((17, 21, 25, 29)):
        window(WSG, x, 6, height=2, shutters=None, box=None, sill="spruce")
    for x in (29,):
        window(Wall(b, "north", "east", (0, ZU0)), x, 6, height=2, shutters=None, box=None, sill="spruce")
    window(WEG, 21, 2, height=2, shutters="spruce", box=None, sill="spruce")
    window(WEG, 19, 6, height=2, shutters=None, box=None, sill="spruce")
    window(WEG, 19, 10, height=1, shutters=None, box=None, sill="spruce")
    for z in (17, 21):                                                  # west gable windows above the wing roof
        window(WWG, z, 11, height=2, shutters=None, box=None, sill="spruce")
    for y in range(9, 15):                                              # king post
        if b.get(X0, y, 19) is not None and "plaster" not in b.block(X0, y, 19) and b.block(X0, y, 19) != "air":
            put(X0, y, 19, lg(BEAM, "y"))
    put(23, 2, 14, B.wall_sign("spruce", "north"))

    # ---- sluice tunnel through the east pier, channel, pond B, gate, bridge
    put(32, 0, 16, td_gate("west")); put(32, 0, 17, td_gate("west"))
    put(32, 1, 16, "@iron_bars"); put(32, 1, 17, "@iron_bars")
    chan = [(x, z) for x in range(33, 45) for z in (16, 17)]
    water = K.mill_race(b, chan, 0, -1, ramp=BASE, coping="stone_brick", coping_y=2)
    for z in (15, 18):
        put(34, 2, z, lg(BEAM, "y")); put(34, 3, z, lg(BEAM, "y"))
    for z in (15, 16, 17, 18):
        put(34, 4, z, beam("z"))
    put(34, 3, 16, "@iron_bars"); put(34, 3, 17, "@iron_bars")
    put(35, 3, 15, B.lever("wall", "east"))
    K.stone_bridge(b, 37, 14, 6, axis="z", y_deck=2, width=3, ramp=Ramp(P("stone_bricks"), [(P("mossy_stone_bricks"), .25, None), (P("cracked_stone_bricks"), .1, None)], seed=40),
                   open_under=frozenset((x, z) for x in range(37, 40) for z in (16, 17)))

    # ================= wing (store), in line with the hall, abutting its west gable =================
    WX0, WX1 = ML.WING_X
    WZ0, WZ1 = ML.WING_Z
    ring_w = {(x, z) for x in range(WX0, WX1 + 1) for z in range(WZ0, WZ1 + 1) if x == WX0 or z in (WZ0, WZ1) or x == WX1}
    for x in range(WX0, WX1 + 1):
        for z in range(WZ0, WZ1 + 1):
            if (x, z) in ring_w:
                put(x, 0, z, PLINTH.pick(x, 0, z))
                for y in (1, 2, 3):
                    put(x, y, z, STONE.pick(x, y, z))
                put(x, 4, z, beam("x" if z in (WZ0, WZ1) else "z"))
            else:
                put(x, 0, z, FLOOR.pick(x, 0, z))
                fill(x, 1, z, x, 4, z, AIR)
    WWN = Wall(b, "north", "east", (0, WZ0))
    WWS = Wall(b, "south", "east", (0, WZ1))
    WWW = Wall(b, "west", "south", (WX0, 0))
    for u in (10, 13):
        window(WWN, u, 2, height=2, shutters="spruce", box=None, sill="spruce")
    window(WWS, 11, 2, height=2, shutters=None, box=None, sill="spruce")
    door(WWW, 19, 1, mat="spruce", hood=True)
    put(7, 0, 19, st("stone_brick", "east"))
    wing = K.gable_roof_s(b, (WX0 - 1, WZ0 - 1, WX1, WZ1 + 1), ML.Y_EAVE_WING, axis="x", field="dark_oak", eave="deepslate_tile",
                          rake="deepslate_tile", ridge=stripped("dark_oak", "x"), cap=sl("deepslate_tile"), name="wing",
                          ramp_pick=lambda x, y, z: ROOF.pick(x, y, z), riser=P("dark_oak_planks"))
    K.fill_gable_wall(b, wing, [(WX0, z) for z in range(WZ0, WZ1 + 1)], 5, plaster)
    for y in range(5, 10):
        if b.block(WX0, y, 19) != "air":
            put(WX0, y, 19, lg(BEAM, "y"))
    n.volumes.append({"name": "wing", "footprint": (WX0, WZ0 - 1, WX1, WZ1 + 1), "top": wing["ridge_y"] + 1})

    # ================= sack shed (lean-to) on the south wall =================
    for x in (25, 28, 31):
        fill(x, 0, 27, x, 2, 27, lg(BEAM, "y"))
    for x in range(25, 32):
        put(x, 3, 27, beam("x"))
        for z, y in ((25, 5), (26, 4), (27, 3)):
            put(x, y, z, st("deepslate_tile" if z == 27 else "dark_oak", "north"))
    for z in (25, 26, 27):
        for x in (25, 31):
            if z != 27:
                fill(x, 0, z, x, 2, z, AIR)
    for x in (26, 27, 29, 30):
        put(x, 0, 27, "@stone_brick_wall")
    n.roofs.append({"name": "shed", "eave": [(x, 3, 27) for x in range(25, 32)],
                    "field": [(x, 4, 26) for x in range(25, 32)] + [(x, 5, 25) for x in range(25, 32)], "overhang": 1})
    n.roof_cells |= {(x, y, z) for x in range(25, 32) for (y, z) in ((3, 27), (4, 26), (5, 25))}
    n.roof_heights.add(3)
    for p in ((26, 0, 25), (26, 1, 25), (27, 0, 25), (30, 0, 25)):
        put(*p, B.barrel())
    for p in ((29, 0, 25), (30, 0, 26), (30, 1, 26), (29, 0, 26)):
        put(*p, P("white_wool"))
    put(29, 1, 25, P("white_carpet"))
    put(28, 3, 26, B.lantern(True))
    n.volumes.append({"name": "shed", "footprint": (25, 25, 31, 27), "top": 6})
    n.breaks.append({"name": "shed", "cell": (28, 4, 26)})
    K.dormer(b, roof, WSU, 21, 0, 9, wall_mat=lambda x, y, z: P("dark_oak_planks"), trim=BEAM, roof_mat="deepslate_tile", name="dormer_s", h=3)

    # ================= staddle granary (NW) with pyramid roof =================
    GX0, GX1 = ML.GRANARY_X
    GZ0, GZ1 = ML.GRANARY_Z
    stones = [(x, z) for x in (2, 5, 8) for z in (4, 7, 10) if (x, z) != (8, 7)]
    K.staddle_stones(b, stones)
    for x in range(GX0, GX1 + 1):
        for z in range(GZ0, GZ1 + 1):
            put(x, 2, z, FLOOR.pick(x, 2, z))
            if x in (GX0, GX1) or z in (GZ0, GZ1):
                for y in (3, 4):
                    put(x, y, z, FLOOR.pick(x, y, z))
                put(x, 5, z, beam("x" if z in (GZ0, GZ1) else "z"))
                if (x in (GX0, 5, GX1)) and (z in (GZ0, 7, GZ1)):
                    pass
            else:
                fill(x, 3, z, x, 5, z, AIR)
    for (x, z) in ((2, 4), (5, 4), (8, 4), (2, 7), (8, 6), (8, 8), (2, 10), (5, 10), (8, 10)):
        for y in (3, 4):
            put(x, y, z, lg(BEAM, "y"))
    for x in range(GX0, GX1 + 1):                                     # floor beams around
        for z in (GZ0, GZ1):
            put(x, 2, z, beam("x"))
    for z in range(GZ0, GZ1 + 1):
        for x in (GX0, GX1):
            put(x, 2, z, beam("z"))
    WGE = Wall(b, "east", "south", (GX1, 0))
    WGN = Wall(b, "north", "east", (0, GZ0))
    WGS = Wall(b, "south", "east", (0, GZ1))
    WGW = Wall(b, "west", "south", (GX0, 0))
    door(WGE, 7, 3, mat="spruce", hood=False)
    put(8, 2, 7, P("stone_bricks")); put(9, 0, 7, P("stone_bricks")); put(9, 1, 7, st("stone_brick", "west"))
    put(10, 0, 7, st("stone_brick", "west"))
    window(WGN, 4, 3, height=1, shutters=None, box=None, sill="spruce")
    window(WGS, 4, 3, height=1, shutters=None, box=None, sill="spruce")
    window(WGW, 7, 3, height=1, shutters=None, box=None, sill="spruce")
    gran = K.pyramid_roof(b, (GX0 - 1, GZ0 - 1, GX1 + 1, GZ1 + 1), 5, field="dark_oak", eave="deepslate_tile",
                          rake="deepslate_tile", ridge=P("dark_oak_planks"), cap=sl("deepslate_tile"), name="granary",
                          ramp_pick=lambda x, y, z: ROOF.pick(x, y, z), riser=P("dark_oak_planks"))
    n.volumes.append({"name": "granary", "footprint": (GX0, GZ0, GX1, GZ1), "top": gran["ridge_y"] + 1})
    return {"roof": roof, "wing": wing, "granary": gran}


def td_gate(facing: str) -> str:
    return B.td("spruce", facing, "bottom", True)


def scenery(b: Build, pond: set) -> None:
    """Ground, forecourt, path, beds, props, trees, hedges (all anchored scenes, D1-D7)."""
    n = b.notes
    put, rng = b.put, b.rng
    ZU0 = ML.HALL_ZU[0]
    # --- the road (3 wide, south edge wobbles), forecourt, path
    off = wobble(SX, 0, 1, rng)
    road = {(x, z) for x in range(SX) for z in range(0, 3 + off[x])}
    S.ground(b, road, GRAVEL)
    yard = S.noisy_rect(b, 11, 3, 21, 9, wobble=1)
    S.ground(b, yard - road, COBBLE)
    pathc = set()
    for z in range(10, 15):
        o = (0, 0, 1, 1, 0)[z - 10]
        for x in range(16 + o, 19 + o):
            pathc.add((x, z))
    S.ground(b, pathc - yard, COBBLE)
    # --- flower beds beside the path, under the windows
    S.flower_bed(b, {(x, z) for x in range(10, 15) for z in range(11, 15)} - pathc, density=.7)
    S.flower_bed(b, {(x, z) for x in range(19, 24) for z in range(11, 13)} - pathc, density=.7)
    # --- lamp posts at the yard corners, backdrop wall behind the panel
    S.lamp_post(b, 10, 3, 3)
    S.lamp_post(b, 22, 3, 3)
    for x in range(19, 24):
        put(x, 0, 10, "@stone_brick_wall" if x % 2 else "@mossy_stone_brick_wall")
    put(19, 1, 10, B.lantern(False)); put(23, 1, 10, B.lantern(False))
    S.apron(b, 18, 10, 24, 10, COBBLE, force=True)
    # --- sack cart (the ripe spot sits beside it), crates at the door
    for x in (9, 10):
        for z in (6, 7, 8):
            put(x, 0, z, sl("spruce", "bottom"))
    put(11, 0, 6, B.td("spruce", "west", "bottom", True)); put(11, 0, 8, B.td("spruce", "west", "bottom", True))
    put(9, 1, 7, P("white_wool")); put(10, 1, 7, P("white_wool")); put(9, 2, 7, P("white_carpet"))
    put(9, 1, 6, B.barrel())
    S.crates(b, [(19, 0, 14), (19, 1, 14)], sacks=[(21, 0, 14)])
    S.apron(b, 18, 13, 22, 14, COBBLE, force=True)
    # --- back garden and woodpile (south), garden steps
    S.veg_plot(b, 9, 28, 7, 5, ["wheat", "carrots", "potatoes"])
    S.woodpile(b, 18, 28, axis="x", length=5)
    S.bench(b, 22, 26, facing="north", length=3)
    # --- trees: oak by the granary, birch and oak on the terrace, willow-ish oak over the pond
    S.tree(b, 4, 15, kind="oak", height=5, spread=2.6, y0=0)
    S.tree(b, 3, 28, kind="birch", height=6, spread=2.4, y0=0)
    S.tree(b, 41, 28, kind="oak", height=5, spread=2.7, y0=2)
    S.tree(b, 29, 2, kind="oak", height=6, spread=3.0, y0=0)
    S.tree(b, 7, 31, kind="oak", height=5, spread=2.5, y0=0)
    # --- meadow tufts and flowers in clusters on the empty lawn (cells with two or more neighbours only)
    import numpy as np
    from scipy.ndimage import gaussian_filter
    field = gaussian_filter(np.random.default_rng(77).standard_normal((SX + 4, SZ + 4)), 1.8)
    field = (field - field.mean()) / field.std()
    taken = {(x, z) for (x, y, z) in b.g if y >= 0}
    ground_ok = lambda x, z: (x, -1, z) in b.g and "grass_block" in b.g[(x, -1, z)] and (x, 0, z) not in b.g  # noqa: E731
    tufts = {}
    for x in range(1, SX - 1):
        for z in range(3, SZ - 1):
            if ground_ok(x, z) and not (11 <= x <= 22 and z <= 14) and field[x, z] > 0.55 and (x, z) not in taken:
                tufts[(x, z)] = rng.random()
    for (x, z), r in sorted(tufts.items()):
        nb = sum(1 for dx in (-1, 0, 1) for dz in (-1, 0, 1) if (dx, dz) != (0, 0) and (x + dx, z + dz) in tufts)
        if nb >= 3:
            put(x, 0, z, P("short_grass" if r < .6 else ("fern" if r < .7 else
                           ("dandelion", "cornflower", "oxeye_daisy", "poppy", "azure_bluet")[int(r * 50) % 5])))
    # --- hedges along the box edge: segments of at most 6 with 2-3 cell gaps
    for (x0, x1, z) in ((1, 6, 34), (9, 14, 34), (17, 22, 34), (25, 30, 34), (33, 38, 34), (41, 44, 34)):
        for x in range(x0, x1 + 1):
            put(x, 0, z, B.leaves("oak")); put(x, 1, z, B.leaves("oak")) if (x + x0) % 3 else None
    for (z0, z1) in ((18, 23), (26, 31)):
        for z in range(z0, z1 + 1):
            put(0, 0, z, B.leaves("oak")); put(0, 1, z, B.leaves("oak")) if z % 3 else None
    # --- orchard on the terrace (quincunx), willow-like vines on the pond tree
    for (x, z) in K.orchard_grid(37, 25, 44, 31, 7, 0, seed=4):
        if 36 <= x <= 44 and 22 <= z <= 31 and (x, 2, z) not in b.g:
            S.tree(b, x, z, kind="oak", height=4, spread=1.8, y0=2)
    for (x, y, z), d in sorted(b.g.items()):
        if B.block_id(d) == "oak_leaves" and 24 <= x <= 35 and z <= 8 and y >= 3 and b.get(x, y - 1, z) is None and rng.random() < .5:
            put(x, y - 1, z, B.vine("up"))
    n.front_yard = {"rect": (11, 3, 21, 9), "corridor": (16, 18), "max_y": 1}
    n.ground_region = (-1, -1, SX, SZ)


SIGNS = [{"at": [23, 2, 14], "lines": ["", "The Mill", "", ""]}]


def finish_notes(b: Build) -> None:
    n = b.notes
    X0, X1 = ML.HALL_X
    n.ramps = {k: list(v) for k, v in RAMPS.items()}
    n.plinth = {"blocks": RAMPS["plinth"], "courses": 1,
                "ring": [(x, z) for x in range(X0, X1 + 1) for z in range(15, 25) if x in (X0, X1) or z in (15, 24)]}
    n.body_box = (X0, 14, X1, 24)
    n.wall_rows = (1, 8)
    n.exempt_windows = set()


def generate(write: bool = True) -> dict:
    b, roof, slot, pond = build()
    rest(b, roof, slot, pond)
    scenery(b, pond)
    finish_notes(b)
    g = dict(b.g)
    B.resolve(g)
    chars, palette = B.to_palette(g)
    size = (SX, SY + GROUND, SZ)
    layers = house_lib.to_layers(chars, size, GROUND)
    blocks = house_lib.validate(chars, layers, palette, size, (0, 0, 0), {}, SIGNS, ground=GROUND)
    absent = block_ids.missing({B.block_id(d) for d in palette.values()})
    assert not absent, f"block ids not in the 26.3 sources jar: {absent}"
    points = ML.POINTS
    place_lib.check_points(points, g, size, GROUND, wheel_required=True)
    try:
        build_lint.lint_house(g, b.notes, {}, palette)
        farm_lint.lint_place(g, b.notes, points, palette, hero=True, name="mill", water_frontage=(11, 3, 44, 14),
                             size=size, ground=GROUND)
    except build_lint.LintError:
        OUT_DIR.mkdir(parents=True, exist_ok=True)
        place_views.mill_previews(g, OUT_DIR)
        raise
    return {"g": g, "chars": chars, "palette": palette, "layers": layers, "blocks": blocks, "notes": b.notes}


def main() -> int:
    r = generate()
    header = [
        "# The `mill` place (countryside-builds). GENERATED by design/scripts/house_mill.py; do not edit by hand.",
        "# Spec: design/countryside.md (format: place files + points). FINAL orientation: the road side (yard) is NORTH (-Z).",
        "# Axes: x west->east, y bottom->top, z north->south. ground-depth 4: the first 4 layers are design y -4..-1.",
        f"# {r['blocks']} blocks (not counting air/keep).",
    ]
    size = (SX, SY + GROUND, SZ)
    text = place_lib.write_place_yaml(header, r["layers"], r["palette"], size, ML.POINTS, SIGNS, GROUND)
    parsed = yaml.safe_load(text)
    assert parsed["layers"] == r["layers"] and parsed["palette"] == r["palette"] and parsed["points"]["wheel"]["axis"] == "z"
    OUT_YML.parent.mkdir(parents=True, exist_ok=True)
    OUT_YML.write_text(text, encoding="utf-8")
    n_struct = sum(1 for (x, y, z), d in r["g"].items() if y >= 0 and B.block_id(d) not in ("air", "water"))
    print(f"{OUT_YML.relative_to(ROOT)}: {r['blocks']} blocks ({n_struct} above ground, non-water), {len(r['palette'])} palette entries")
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    place_views.mill_previews(r["g"], OUT_DIR)
    return 0


if __name__ == "__main__":
    sys.exit(main())
