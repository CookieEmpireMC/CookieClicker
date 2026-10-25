"""The `cow_barn` place: a dairy farm. A long barn with a STONE ground storey (a rhythm of arched stall openings), a boarded
hayloft in a dark-oak frame, ONE steep spruce roof with half-hip ends and four dormers, ONE timber cross-gable (braced double
doors, loft door, hoist), a lean-to at the east end; a muddy fold-yard (15 x 10) behind the barn with a sunken stone trough and a
3-bay open-fronted shelter shed (hay rack over a trough); a big irregular pasture (drystone walls, hedge on a stone bank, a
step stile, worn ground fading into grass); a roadside milk-churn stand; a drystone wall along the road.

Outputs: src/main/resources/houses/cow_barn.yml, design/scripts/previews/place_cow_barn_*.png.
Run with `mise run assets`. Deterministic. Spec: design/countryside.md section 12, refs: design/refs-cow-barn.md,
research: docs/research/farm-details.md.
Axes: x west -> east, y up (ground layers y -2..-1), z north -> south.
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
import blocks as B  # noqa: E402
import farm_kits as K  # noqa: E402
import place_gen as G  # noqa: E402
import place_views  # noqa: E402
import scenes as S  # noqa: E402
from blocks import lg, plain as P, sl, st  # noqa: E402
from build import Build, Ramp, Wall, door  # noqa: E402

SX, SY, SZ, GROUND = 60, 14, 40, 2
SIZE = (SX, SY + GROUND, SZ)
BX0, BX1, BZ0, BZ1 = 16, 42, 15, 23       # main barn walls
WX0, WX1, WZ0 = 26, 32, 12                # cross-gable wing walls x, front wall z
UC = 29
Y_EAVE = 6
TRIM = "dark_oak"
POINTS = {
    "viewpoint": {"at": [28, 0, 5], "yaw": -4, "pitch": 4},
    "yard": {"from": [24, 3], "to": [34, 9]},
    "hologram": [26, 1, 7],
    "panel": {"at": [31, 0, 7], "yaw": 180},
    "ripe": [24, 0, 6],
}
SIGNS = [{"at": [26, 3, 11], "lines": ["", "Cow Barn", "", ""]}]

PLINTH = Ramp(P("cobblestone"), [(P("mossy_cobblestone"), .3, "low"), (P("andesite"), .15, None)], seed=4)
STONE = Ramp(P("stone_bricks"), [(P("mossy_stone_bricks"), .2, "low"), (P("cracked_stone_bricks"), .12, None)], seed=5, height=4, y0=1)
BOARD = Ramp(P("oak_planks"), [(P("dark_oak_planks"), .3, None), (P("spruce_planks"), .0001, None)], seed=6)
INNER = Ramp(P("coarse_dirt"), [(P("podzol[snowy=false]"), .3, None), (P("rooted_dirt"), .25, None), (P("dirt_path"), .2, None)], seed=19)
RAMPS = {
    "plinth": ["cobblestone", "mossy_cobblestone", "andesite"],
    "stone": ["stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks"],
    "board": ["oak_planks", "dark_oak_planks", "spruce_planks"],
    "roof": ["spruce_stairs", "dark_oak_stairs", "spruce_planks"],
    "drystone": ["cobblestone_wall", "mossy_cobblestone_wall", "andesite_wall"],
}
GATE_NS = "minecraft:spruce_fence_gate[facing=north,in_wall=false,open=false,powered=false]"
GATE_EW = "minecraft:spruce_fence_gate[facing=east,in_wall=false,open=false,powered=false]"
ARCH_N = [18, 22, 34, 38]
ARCH_S = [17, 21, 25, 36]
FOLD = (16, 24, 30, 33)                   # fold-yard interior x0, z0, x1, z1


def beam(axis: str) -> str:
    return B.stripped(TRIM, axis)


def arch(b: Build, wall: Wall, u0: int, gate: bool = True) -> None:
    """A 3-wide, 3-high stall opening: upside-down stairs round the top corners; with `gate`, a low stone half-wall on both sides
    of one stall gate."""
    for du in range(3):
        for y in (1, 2, 3):
            wall.put(u0 + du, y, 0, "minecraft:air")
    wall.put(u0, 3, 0, wall.stair("stone_brick", "top", "along"))
    wall.put(u0 + 2, 3, 0, wall.stair("stone_brick", "top", "back"))
    if gate:
        wall.put(u0, 1, 0, P("stone_bricks")); wall.put(u0 + 2, 1, 0, P("stone_bricks"))
        wall.put(u0 + 1, 1, 0, GATE_NS)
    x, y, z = wall.xyz(u0 + 1, 3, -2)
    b.put(x, y, z, B.lantern(True))


def build() -> Build:
    b = Build((SX, SY, SZ), ground=GROUND)
    n = b.notes
    put, fill = b.put, b.fill
    heights = {(x, z): -1 for x in range(SX) for z in range(SZ)}
    G.lawn(b, SX, SZ, heights)

    # ================= main barn: kerb, stone ground storey with arches, beam course, boarded loft =================
    ring = [(x, z) for x in range(BX0, BX1 + 1) for z in range(BZ0, BZ1 + 1) if x in (BX0, BX1) or z in (BZ0, BZ1)]
    for (x, z) in ring:
        put(x, 0, z, PLINTH.pick(x, 0, z))
        for y in range(1, 4):
            put(x, y, z, STONE.pick(x, y, z))
        put(x, 4, z, beam("x" if z in (BZ0, BZ1) else "z"))
    for x in range(BX0 + 1, BX1):
        for z in range(BZ0 + 1, BZ1):
            put(x, -1, z, INNER.pick(x, 0, z))
            put(x, 4, z, P("oak_planks"))
    posts_x = [16, 21, 25, 33, 37, 42]
    for z in (BZ0, BZ1):
        for x in range(BX0, BX1 + 1):
            put(x, 5, z, lg(TRIM, "y") if x in posts_x else BOARD.pick(x, 5, z))
            put(x, 6, z, beam("x"))
    for x in (BX0, BX1):
        for z in range(BZ0, BZ1 + 1):
            put(x, 5, z, lg(TRIM, "y") if z in (BZ0, 19, BZ1) else BOARD.pick(x, 5, z))
            put(x, 6, z, beam("z"))
    main = K.hipped_roof(b, (BX0 - 1, BZ0 - 1, BX1 + 1, BZ1 + 1), Y_EAVE, {"N": 0, "S": 0, "W": 3, "E": 3}, field="spruce",
                         eave=TRIM, rake=TRIM, ridge=beam("x"), cap=sl(TRIM), name="main", form="half-hip",
                         riser=P("spruce_planks"))
    for xin in (BX0, BX1):
        K.fill_gable_wall(b, main, [(xin, z) for z in range(BZ0, BZ1 + 1)], 7, lambda x, y, z: BOARD.pick(x, y, z))
    n.volumes.append({"name": "main", "footprint": (BX0, BZ0, BX1, BZ1), "top": main["ridge_y"] + 1})
    WN = Wall(b, "north", "east", (0, BZ0))
    for u0 in ARCH_N:
        arch(b, WN, u0)
    for x in posts_x:                                                    # proud pilasters on the pier lines
        put(x, 0, BZ0 - 1, PLINTH.pick(x, 0, BZ0 - 1))
        for y in (1, 2, 3):
            put(x, y, BZ0 - 1, STONE.pick(x, y, BZ0 - 1))
    oak = lambda x, y, z: BOARD.pick(x, y, z)                             # noqa: E731
    for uc in (19, 23, 36, 40):
        K.dormer(b, main, WN, uc, 0, 7, wall_mat=oak, trim=TRIM, roof_mat="spruce", name=f"dormer{uc}", h=3, shutters=None)
    WS = Wall(b, "south", "east", (0, BZ1))
    for u0 in ARCH_S:
        arch(b, WS, u0, gate=False)
    door(WS, 31, 1, mat="spruce", frame=TRIM, hood=False)

    # ================= cross-gable wing (timber): braced doors, loft door, hoist =================
    for z in range(WZ0, BZ0):
        for x in (WX0, WX1):
            put(x, 0, z, PLINTH.pick(x, 0, z))
            for y in range(1, 7):
                put(x, y, z, lg(TRIM, "y") if z in (WZ0, BZ0 - 1) else BOARD.pick(x, y, z))
            put(x, 7, z, beam("z"))
    WF = Wall(b, "north", "east", (0, WZ0))
    for x in range(WX0, WX1 + 1):
        put(x, 0, WZ0, PLINTH.pick(x, 0, WZ0))
        for y in (5, 6):
            put(x, y, WZ0, lg(TRIM, "y") if x in (WX0, WX1) else BOARD.pick(x, y, WZ0))
        put(x, 7, WZ0, beam("x"))
    put(26, 3, 11, B.wall_sign("spruce", "north"))
    K.barn_door_double(b, WF, UC, 1, width=5, height=3, leaf=lambda x, y, z: P("spruce_planks"), brace=P("dark_oak_planks"))
    for du in (-1, 1):
        for y in (5, 6):
            put(UC + du, y, WZ0, lg(TRIM, "y"))
    put(UC, 5, WZ0, B.dr("spruce", "north", "lower")); put(UC, 6, WZ0, B.dr("spruce", "north", "upper"))
    for x in (WX0 + 1, WX1 - 1):
        put(x, 4, WZ0 - 1, st(TRIM, "south", "top")); put(x, 3, WZ0 - 1, B.lantern(True))
    wing = K.hipped_roof(b, (WX0 - 1, WZ0 - 1, WX1 + 1, BZ0 - 1), 7, {"W": 0, "E": 0, "N": None, "S": None}, field="spruce",
                         eave=TRIM, rake=TRIM, ridge=beam("z"), cap=sl(TRIM), name="wing", form="gable",
                         riser=P("spruce_planks"), clip=main)
    n.gables[:] = [g for g in n.gables if g["name"] != "wing:S"]
    K.fill_gable_wall(b, wing, [(x, WZ0) for x in range(WX0, WX1 + 1)], 8, lambda x, y, z: BOARD.pick(x, y, z))
    put(UC, 9, WZ0 - 1, beam("z")); put(UC, 9, WZ0 - 2, beam("z"))     # hoist beam, chain and hook
    put(UC, 8, WZ0 - 2, B.chain("y")); put(UC, 7, WZ0 - 2, B.chain("y"))
    n.volumes.append({"name": "wing", "footprint": (WX0, WZ0, WX1, BZ0 - 1), "top": wing["ridge_y"] + 1})
    n.roof_heights |= {Y_EAVE, 7}

    # ================= lean-to at the east end =================
    east = K.catslide_roof(b, (BX1 + 1, BZ0, BX1 + 5, BZ1), "W", 5, field="spruce", eave=TRIM, name="east")
    cells = [(x, z) for x in range(BX1 + 1, BX1 + 4) for z in (BZ0 + 1, BZ1 - 1)] + [(BX1 + 4, z) for z in range(BZ0 + 1, BZ1)]
    for (x, z) in cells:
        put(x, 0, z, PLINTH.pick(x, 0, z))
        for y in range(1, east["surface"][(x, z)]):
            put(x, y, z, STONE.pick(x, y, z))
    n.volumes.append({"name": "east", "footprint": (BX1 + 1, BZ0 + 1, BX1 + 4, BZ1 - 1), "top": 5})

    # ================= 3-bay open-fronted shelter shed on the south side of the fold-yard =================
    sx0, sx1 = 18, 28
    shed = K.catslide_roof(b, (sx0 - 1, 34, sx1 + 1, 38), "S", 6, field="spruce", eave=TRIM, name="shed")
    for x in range(sx0, sx1 + 1):                                        # back wall, stone below, boards above
        put(x, 0, 37, PLINTH.pick(x, 0, 37))
        for y in range(1, shed["surface"][(x, 37)]):
            put(x, y, 37, STONE.pick(x, y, 37) if y <= 3 else BOARD.pick(x, y, 37))
    for x in (sx0, sx1):                                                 # end walls follow the roof
        for z in (35, 36):
            put(x, 0, z, PLINTH.pick(x, 0, z))
            for y in range(1, shed["surface"][(x, z)]):
                put(x, y, z, STONE.pick(x, y, z) if y <= 3 else BOARD.pick(x, y, z))
    for x in (sx0, 21, 25, sx1):                                         # front posts
        for y in range(0, 4):
            put(x, y, 35, lg(TRIM, "y"))
    for x in range(sx0, sx1 + 1):
        put(x, 4, 35, beam("x"))
    for x in range(sx0 + 1, sx1):                                        # trough and hay rack
        put(x, 0, 36, sl("spruce", "bottom") if x not in (sx0 + 1, sx1 - 1) else B.cauldron_water(3))
        put(x, -1, 36, P("coarse_dirt")); put(x, -1, 35, P("coarse_dirt"))
    for x in (19, 23, 27):
        put(x, 1, 36, "@spruce_fence")
    for x in range(19, 28, 2):
        put(x, 2, 36, B.hay())
    put(22, 3, 35, B.lantern(True))
    n.volumes.append({"name": "shed", "footprint": (sx0, 34, sx1, 37), "top": 6})
    n.roof_heights |= {4, 6}
    n.plinth = {"blocks": RAMPS["plinth"], "courses": 1, "ring": ring}
    return b


def lamp(b: Build, x: int, z: int, height: int = 3) -> None:
    for y in range(height):
        b.put(x, y, z, "@spruce_fence")
    b.put(x, height, z, B.lantern(False))


def scenery(b: Build) -> None:
    n = b.notes
    put, rng = b.put, b.rng
    road = G.road(b, SX)
    yard = S.noisy_rect(b, 24, 3, 34, 9, wobble=1)
    S.ground(b, yard - road, G.COBBLE)
    S.ground(b, {(x, z) for z in range(10, 13) for x in range(27, 32)} - {(x, z) for x in range(WX0, WX1 + 1) for z in range(12, 15)}, G.GRAVEL)
    S.ground(b, {(x, z) for x in range(25, 34) for z in range(10, 11)}, G.COBBLE)
    S.flower_bed(b, {(x, z) for x in range(19, 25) for z in range(11, 13)}, density=.65)
    S.flower_bed(b, {(x, z) for x in range(34, 40) for z in range(11, 13)}, density=.65)
    lamp(b, 23, 10)
    lamp(b, 35, 10)
    put(25, 0, 10, B.barrel("up")); put(25, 1, 10, B.barrel("up")); put(24, 0, 10, B.barrel("up"))

    # ---- roadside: drystone wall with a step stile, milk-churn stand at the yard
    wall_cells = [(x, 5) for x in list(range(1, 21)) + list(range(41, 59)) if x not in (50, 51)]
    G.drystone_wall(b, wall_cells, high=2)
    put(50, 0, 5, P("cobblestone")); put(51, 0, 5, sl("cobblestone", "bottom"))
    put(50, 0, 4, st("cobblestone", "south")); put(50, 0, 6, st("cobblestone", "north"))
    put(51, 0, 4, sl("cobblestone", "bottom")); put(51, 0, 6, sl("cobblestone", "bottom"))
    for x in range(36, 39):                                              # churn stand
        for z in (4, 5):
            put(x, -1, z, P("stone_bricks"))
    for x in range(36, 39):
        put(x, 0, 4, B.barrel("up"))
    lamp(b, 39, 4, 2)

    # ---- fold-yard: drystone walls, double field gate to the pasture, ground worn around trough, gate and shed
    fx0, fz0, fx1, fz1 = FOLD
    wall = [(fx0 - 1, z) for z in range(fz0, fz1 + 2)] + [(fx1 + 1, z) for z in range(fz0, fz1 + 2)]
    wall += [(x, 34) for x in (15, 16, 17, 29, 30, 31)]
    gate_cells = {(31, 28), (31, 29)}
    G.drystone_wall(b, [c for c in wall if c not in gate_cells | {(31, 27), (31, 30)}], high=2)
    for gz, lant in ((27, True), (30, False)):
        G.gate_pillar(b, 31, gz, 3, lantern=lant)
    for c in gate_cells:
        put(c[0], 0, c[1], GATE_EW)
    trough = [(x, z) for x in range(22, 26) for z in (29, 30)]
    rim = {(x, z) for x in range(21, 27) for z in range(28, 32)} - set(trough)
    for (x, z) in trough:
        put(x, -2, z, P("stone_bricks")); put(x, -1, z, B.water())
    for (x, z) in rim:
        put(x, -1, z, P("stone_bricks"))
    for x in range(22, 26):
        put(x, 0, 28, sl("stone_brick", "bottom")); put(x, 0, 31, sl("stone_brick", "bottom"))
    for z in range(fz0, fz1 + 1):
        for x in range(fx0, fx1 + 1):
            if (x, z) in rim or (x, z) in trough or b.get(x, 0, z) is not None:
                continue
            d = min(math.hypot(x - 23.5, z - 29.5) - 2, math.hypot(x - 31, z - 28.5), (33 - z) * 1.2 + 0)
            r = rng.random()
            if d < 3:
                blk = P("packed_mud") if r < .3 else (P("coarse_dirt") if r < .8 else P("dirt_path"))
            elif d < 6:
                blk = P("coarse_dirt") if r < .6 else (P("packed_mud") if r < .7 else P("dirt_path"))
            else:
                blk = P("coarse_dirt") if r < .4 else (P("podzol[snowy=false]") if r < .7 else P("dirt_path"))
            put(x, -1, z, blk)
    put(17, 0, 25, B.hay()); put(17, 0, 26, B.hay()); put(18, 0, 25, B.hay())

    # ---- pasture: irregular outline, worn ground fading into grass, drystone wall north, hedge on a stone bank elsewhere
    pasture = G.blob(b, 32, 25, 58, 38, cut=5, wob=2)
    edge = {c for c in G.perimeter(pasture) if c[0] > 32}
    dry = {c for c in edge if c[1] <= 29 or c[0] <= 38}
    G.drystone_wall(b, dry, high=2)
    G.hedge_bank(b, edge - dry)
    for (x, z) in sorted(pasture):
        if b.get(x, 0, z) is not None or b.get(x, -1, z) is None:
            continue
        d = math.hypot(x - 32, z - 28.5)
        r = rng.random()
        if d < 4:
            put(x, -1, z, P("packed_mud") if r < .5 else P("coarse_dirt"))
        elif d < 8 and r < .6:
            put(x, -1, z, P("coarse_dirt") if r < .35 else P("dirt_path"))
    for x in range(33, 47):                                              # the worn trail, 2 wide
        for z in (28 + (x // 5) % 2, 29 + (x // 5) % 2):
            put(x, -1, z, P("dirt_path") if rng.random() < .7 else P("coarse_dirt"))
    G.meadow(b, SX, SZ, lambda x, z: (x, z) not in pasture or x <= 44 or (x, z) in edge, thresh=.1)
    S.tree(b, 49, 33, kind="oak", height=5, spread=2.6, y0=0)
    S.tree(b, 57, 28, kind="oak", height=5, spread=2.2, y0=0)

    # ---- west and back: hedge, woodpile, hay rick against the shed, orchard trees
    S.woodpile(b, 13, 17, axis="z", length=4)
    G.hay_rick(b, 30, 35, w=3, d=3, layers=2, axis="x")
    for (tx, tz) in ((8, 14), (9, 30), (46, 8)):
        S.tree(b, tx, tz, kind="oak", height=5, spread=2.3, y0=0)
    S.ground(b, road, G.GRAVEL)
    n.front_yard = {"rect": (24, 3, 34, 9), "corridor": (28, 30), "max_y": 1}
    n.ground_region = (-1, -1, SX, SZ)
    n.ramps = {k: list(v) for k, v in RAMPS.items()}
    n.body_box = (BX0, WZ0, BX1, BZ1)
    n.wall_rows = (1, 3)
    n.pen = {"building": [(x, z) for x in range(BX0, BX1 + 1) for z in range(BZ0, BZ1 + 1)]
             + [(x, z) for x in range(WX0, WX1 + 1) for z in range(WZ0, BZ0)],
             "cells": [(x, z) for x in range(fx0, fx1 + 1) for z in range(fz0, fz1 + 1)] + sorted(pasture)}
    n.props = [("yard", "churn stand", True), ("fold-yard", "sunken trough", True), ("shed", "hay rack", True),
               ("pasture", "oak", True)]


def previews(g: dict, out: Path) -> list:
    shots = {
        "eye": ((28.5, 1.62, 5.5), (29, 5, 15)),
        "front": ((29, 8, -9), (29, 5, 16)),
        "ne": ((72, 17, -6), (30, 3, 20)),
        "nw": ((-14, 17, -6), (26, 3, 20)),
        "fold": ((12, 8, 40), (24, 1, 30)),
        "pasture": ((34, 9, 42), (46, 1, 31)),
        "churn": ((38, 4, 9), (37, 1, 4)),
        "above": ("iso", 215, 52),
    }
    return place_views.place_previews("cow_barn", g, out, (0, 0, SX - 1, SZ - 1), shots)


def main() -> int:
    b = build()
    scenery(b)
    G.finish("cow_barn", b, points=POINTS, signs=SIGNS, size=SIZE, ground=GROUND, hero=False, previews=previews,
             size_range=(1800, 3600), trapdoor_budget=12)
    return 0


if __name__ == "__main__":
    sys.exit(main())
