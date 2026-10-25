"""The `cow_barn` place: a long barn with a STONE ground storey (a rhythm of arched stall openings, upside-down stair arches),
a wooden hayloft storey above (dark-oak frame, oak boards), ONE steep spruce roof with half-hip ends and four spruce dormers,
ONE big timber cross-gable in the middle (braced double doors, a loft door with a hoist beam and chain), a lower catslide aisle
at the back and a lean-to at the east end (stepped silhouette), and a paddock whose fence starts at the barn's west wall
(hay, trough, mud at the gate). The barn sits against a low terrace with meadow behind.

Outputs: src/main/resources/houses/cow_barn.yml, design/scripts/previews/place_cow_barn_*.png.
Run with `mise run assets`. Deterministic. Spec: design/countryside.md (Cow Barn), refs: design/refs-cow-barn.md.
Axes: x west -> east, y up (ground layers y -4..-1), z north -> south.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
import blocks as B  # noqa: E402
import farm_kits as K  # noqa: E402
import place_gen as G  # noqa: E402
import place_views  # noqa: E402
import scenes as S  # noqa: E402
from blocks import lg, plain as P, sl, st  # noqa: E402
from build import Build, Ramp, Wall, door, window  # noqa: E402

SX, SY, SZ, GROUND = 46, 18, 38, 4
SIZE = (SX, SY + GROUND, SZ)
BX0, BX1, BZ0, BZ1 = 10, 40, 17, 27       # main barn walls
WX0, WX1, WZ0 = 22, 28, 13                # cross-gable wing walls x, front wall z
UC = 25
Y_EAVE = 7
TRIM = "dark_oak"
POINTS = {
    "viewpoint": {"at": [24, 0, 5], "yaw": -6, "pitch": 4},
    "yard": {"from": [19, 3], "to": [29, 9]},
    "hologram": [23, 1, 7],
    "panel": {"at": [27, 0, 7], "yaw": 180},
    "ripe": [20, 0, 7],
}
SIGNS = [{"at": [22, 3, 12], "lines": ["", "Cow Barn", "", ""]}]

PLINTH = Ramp(P("cobblestone"), [(P("mossy_cobblestone"), .3, "low"), (P("andesite"), .15, None)], seed=4)
STONE = Ramp(P("stone_bricks"), [(P("mossy_stone_bricks"), .2, "low"), (P("cracked_stone_bricks"), .12, None)], seed=5, height=4, y0=1)
BOARD = Ramp(P("oak_planks"), [(P("dark_oak_planks"), .2, None), (P("birch_planks"), .1, None)], seed=6)
PADDOCK = Ramp(P("coarse_dirt"), [(P("mud"), .1, None), (P("podzol[snowy=false]"), .15, None), (P("dirt_path"), .1, None)], seed=37)
INNER = Ramp(P("coarse_dirt"), [(P("podzol[snowy=false]"), .3, None), (P("rooted_dirt"), .25, None), (P("dirt_path"), .2, None)], seed=19)
RAMPS = {
    "plinth": ["cobblestone", "mossy_cobblestone", "andesite"],
    "stone": ["stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks"],
    "board": ["oak_planks", "dark_oak_planks", "birch_planks"],
    "roof": ["spruce_stairs", "dark_oak_stairs", "spruce_planks"],
}
GATE_NS = "minecraft:spruce_fence_gate[facing=north,in_wall=false,open=false,powered=false]"
ARCH_X = {"N": [12, 17, 31, 36], "S": [12, 17, 22, 27, 32, 37]}


def beam(axis: str) -> str:
    return B.stripped(TRIM, axis)


def arch(b: Build, wall: Wall, u0: int, gate: bool = True) -> None:
    """A 3-wide, 3-high stall opening in a stone wall: upside-down stairs round the top corners, a stall gate across the bottom."""
    for du in range(3):
        for y in (1, 2, 3):
            wall.put(u0 + du, y, 0, "minecraft:air")
    wall.put(u0, 3, 0, wall.stair("stone_brick", "top", "along"))
    wall.put(u0 + 2, 3, 0, wall.stair("stone_brick", "top", "back"))
    for du in range(3):
        if gate:
            wall.put(u0 + du, 1, 0, "@spruce_fence" if du == 1 else "minecraft:air")
    if gate:
        wall.put(u0, 1, 0, wall.trapdoor("spruce", "bottom", True, "out"))
        wall.put(u0 + 2, 1, 0, wall.trapdoor("spruce", "bottom", True, "out"))
    x, y, z = wall.xyz(u0 + 1, 3, -2)
    b.put(x, y, z, B.lantern(True))


def build() -> Build:
    b = Build((SX, SY, SZ), ground=GROUND)
    n = b.notes
    put, fill = b.put, b.fill
    heights = {(x, z): -1 for x in range(SX) for z in range(SZ)}
    K.terrace(heights, (0, 34, SX - 1, SZ - 1), 0)
    G.lawn(b, SX, SZ, heights)
    K.retaining_wall(b, 0, 33, SX - 1, 33, -1, 0, ramp=PLINTH, coping="stone_brick")

    # ================= main barn: kerb, stone ground storey with arches, beam course, boarded loft =================
    ring = [(x, z) for x in range(BX0, BX1 + 1) for z in range(BZ0, BZ1 + 1) if x in (BX0, BX1) or z in (BZ0, BZ1)]
    for (x, z) in ring:
        put(x, 0, z, PLINTH.pick(x, 0, z))
        for y in range(1, 4):
            put(x, y, z, STONE.pick(x, y, z))
        put(x, 4, z, beam("x" if z in (BZ0, BZ1) else "z"))
    for x in range(BX0 + 1, BX1):                                      # interior floor: straw ground and the loft floor
        for z in range(BZ0 + 1, BZ1):
            put(x, -1, z, INNER.pick(x, 0, z))
            put(x, 4, z, P("spruce_planks"))
    posts_x = [10, 16, 21, 29, 34, 40]
    for z in (BZ0, BZ1):                                               # loft walls
        for x in range(BX0, BX1 + 1):
            for y in (5, 6):
                put(x, y, z, lg(TRIM, "y") if x in posts_x else BOARD.pick(x, y, z))
            put(x, 7, z, beam("x"))
    for x in (BX0, BX1):
        for z in range(BZ0, BZ1 + 1):
            for y in (5, 6):
                put(x, y, z, lg(TRIM, "y") if z in (BZ0, 22, BZ1) else BOARD.pick(x, y, z))
            put(x, 7, z, beam("z"))
    # main roof: steep spruce, half-hipped ends
    main = K.hipped_roof(b, (BX0 - 1, BZ0 - 1, BX1 + 1, BZ1 + 1), Y_EAVE, {"N": 0, "S": 0, "W": 3, "E": 3}, field="spruce",
                         eave=TRIM, rake=TRIM, ridge=beam("x"), cap=sl(TRIM), name="main", form="half-hip",
                         riser=P("spruce_planks"))
    for xin in (BX0, BX1):
        K.fill_gable_wall(b, main, [(xin, z) for z in range(BZ0, BZ1 + 1)], 8, lambda x, y, z: BOARD.pick(x, y, z))
    n.volumes.append({"name": "main", "footprint": (BX0, BZ0, BX1, BZ1), "top": main["ridge_y"] + 1})
    # arches along the long north side (and the back), pilasters between
    WN = Wall(b, "north", "east", (0, BZ0))
    for u0 in ARCH_X["N"]:
        arch(b, WN, u0)
    for x in (BX0, 15, 16, 20, 21, 29, 30, 34, 35, BX1):               # proud pilasters on the pier lines
        if x in (15, 16, 20, 21, 29, 30, 34, 35):
            continue
        put(x, 0, BZ0 - 1, PLINTH.pick(x, 0, BZ0 - 1))
        for y in (1, 2, 3):
            put(x, y, BZ0 - 1, STONE.pick(x, y, BZ0 - 1))
    for x in (13, 18, 32, 37):
        pass
    # four spruce dormers
    oak = lambda x, y, z: BOARD.pick(x, y, z)                           # noqa: E731
    for uc in (14, 18, 32, 36):
        K.dormer(b, main, WN, uc, 0, 8, wall_mat=oak, trim=TRIM, roof_mat="spruce", name=f"dormer{uc}", h=3, shutters=None)
    # back wall: arches into the aisle, a door, stone steps
    WS = Wall(b, "south", "east", (0, BZ1))
    for u0 in (12, 17, 32, 37):
        arch(b, WS, u0, gate=False)
    door(WS, 25, 1, mat="spruce", frame=TRIM, hood=False)
    # west end: paddock door (wide arch) and a loft hatch
    WW = Wall(b, "west", "south", (BX0, 0))
    for du in range(3):
        for y in (1, 2, 3):
            WW.put(21 + du, y, 0, "minecraft:air")
    WW.put(21, 3, 0, WW.stair("stone_brick", "top", "along")); WW.put(23, 3, 0, WW.stair("stone_brick", "top", "back"))
    WW.put(22, 1, 0, "@spruce_fence")
    door(WW, 22, 8, mat="spruce", frame=TRIM, hood=False)
    put(9, 7, 22, sl(TRIM, "top")); put(9, 7, 21, sl(TRIM, "top")); put(9, 7, 23, sl(TRIM, "top"))
    door(Wall(b, "east", "south", (BX1, 0)), 22, 8, mat="spruce", frame=TRIM, hood=False)

    # ================= cross-gable wing (timber): braced doors, loft door, hoist =================
    for z in range(WZ0, BZ0):
        for x in (WX0, WX1):
            put(x, 0, z, PLINTH.pick(x, 0, z))
            for y in range(1, 8):
                put(x, y, z, lg(TRIM, "y") if z in (WZ0, BZ0 - 1) else BOARD.pick(x, y, z))
            put(x, 8, z, beam("z"))
    WF = Wall(b, "north", "east", (0, WZ0))
    put(22, 3, 12, B.wall_sign("spruce", "north"))
    for x in range(WX0, WX1 + 1):
        put(x, 0, WZ0, PLINTH.pick(x, 0, WZ0))
        for y in (6, 7):
            put(x, y, WZ0, lg(TRIM, "y") if x in (WX0, WX1) else BOARD.pick(x, y, WZ0))
        put(x, 8, WZ0, beam("x"))
    K.barn_door_double(b, WF, UC, 1, width=5, height=4, leaf=lambda x, y, z: P("spruce_planks"), brace=P("dark_oak_planks"))
    for x in (WX0 + 1, WX1 - 1):
        put(x, 5, WZ0 - 1, B.lantern(True))
        put(x, 6, WZ0 - 1, st(TRIM, "south", "top"))
    for du in (-1, 1):                                                  # loft door: jambs, door, lintel
        for y in (6, 7):
            put(UC + du, y, WZ0, lg(TRIM, "y"))
    put(UC, 6, WZ0, B.dr("spruce", "north", "lower")); put(UC, 7, WZ0, B.dr("spruce", "north", "upper"))
    for x in range(UC - 1, UC + 2):
        put(x, 8, WZ0, beam("x"))
    wing = K.hipped_roof(b, (WX0 - 1, WZ0 - 1, WX1 + 1, BZ0 - 1), 8, {"W": 0, "E": 0, "N": None, "S": None}, field="spruce",
                         eave=TRIM, rake=TRIM, ridge=beam("z"), cap=sl(TRIM), name="wing", form="gable",
                         riser=P("spruce_planks"), clip=main)
    n.gables[:] = [g for g in n.gables if g["name"] != "wing:S"]
    K.fill_gable_wall(b, wing, [(x, WZ0) for x in range(WX0, WX1 + 1)], 9, lambda x, y, z: BOARD.pick(x, y, z))
    for y in (10,):                                                    # hoist beam out of the gable, chain and hook
        put(UC, y, WZ0 - 1, beam("z")); put(UC, y, WZ0 - 2, beam("z"))
    put(UC, 9, WZ0 - 2, B.chain("y")); put(UC, 8, WZ0 - 2, B.chain("y"))
    put(UC - 1, 9, WZ0 - 1, st(TRIM, "east", "top")); put(UC + 1, 9, WZ0 - 1, st(TRIM, "west", "top"))
    n.volumes.append({"name": "wing", "footprint": (WX0, WZ0, WX1, BZ0 - 1), "top": wing["ridge_y"] + 1})
    n.roof_heights |= {Y_EAVE, 8}

    # ================= lean-to at the east end and catslide aisle at the back =================
    east = K.catslide_roof(b, (BX1 + 1, BZ0, SX - 1, BZ1), "W", 5, field="spruce", eave=TRIM, name="east")
    for (x, z) in [(x, z) for x in range(BX1 + 1, SX - 1) for z in (BZ0 + 1, BZ1 - 1)] + [(SX - 2, z) for z in range(BZ0 + 1, BZ1)]:
        put(x, 0, z, PLINTH.pick(x, 0, z))
        for y in range(1, east["surface"][(x, z)]):
            put(x, y, z, STONE.pick(x, y, z))
    n.volumes.append({"name": "east", "footprint": (BX1 + 1, BZ0 + 1, SX - 2, BZ1 - 1), "top": 5})
    aisle = K.catslide_roof(b, (BX0 - 1, BZ1 + 1, BX1 + 1, BZ1 + 5), "N", 5, field="spruce", eave=TRIM, name="aisle")
    ring2 = [(x, BZ1 + 4) for x in range(BX0, BX1 + 1)] + [(x, z) for x in (BX0, BX1) for z in range(BZ1 + 1, BZ1 + 4)]
    for (x, z) in ring2:
        put(x, 0, z, PLINTH.pick(x, 0, z))
        for y in range(1, aisle["surface"][(x, z)]):
            put(x, y, z, STONE.pick(x, y, z))
    n.volumes.append({"name": "aisle", "footprint": (BX0, BZ1 + 1, BX1, BZ1 + 4), "top": 5})
    WA = Wall(b, "south", "east", (0, BZ1 + 4))
    for u0 in (13, 20, 27, 34):
        for du in range(2):
            for y in (1, 2):
                WA.put(u0 + du, y, 0, "minecraft:air")
        WA.put(u0, 1, 0, WA.trapdoor("spruce", "bottom", True, "out"))
    for x in range(BX0 + 1, BX1):
        for z in range(BZ1 + 1, BZ1 + 4):
            put(x, -1, z, INNER.pick(x, 0, z))
    n.roof_heights |= {3, 5}
    n.plinth = {"blocks": RAMPS["plinth"], "courses": 1, "ring": ring}
    return b


def scenery(b: Build) -> None:
    n = b.notes
    put, rng = b.put, b.rng
    road = G.road(b, SX)
    yard = S.noisy_rect(b, 19, 3, 29, 9, wobble=1)
    S.ground(b, yard - road, G.COBBLE)
    pathc = {(x, z) for z in range(10, 13) for x in range(23 + (z % 2), 27 + (z % 2) - 1)}
    S.ground(b, pathc - yard, G.GRAVEL)
    S.ground(b, {(x, z) for x in range(21, 30) for z in range(11, 13)}, G.COBBLE)
    S.flower_bed(b, {(x, z) for x in range(14, 21) for z in range(11, 13)}, density=.65)
    S.flower_bed(b, {(x, z) for x in range(31, 38) for z in range(11, 13)}, density=.65)
    S.lamp_post(b, 18, 10, 3)
    S.lamp_post(b, 31, 10, 3)
    put(21, 0, 12, B.barrel("up")); put(21, 1, 12, B.barrel("up")); put(20, 0, 12, B.barrel("up"))
    # paddock: fence starts at the barn's west wall, mud at the gate, trough, hay
    px0, pz0, pz1 = 1, BZ0, BZ1
    for x in range(px0, BX0):
        put(x, 0, pz0, "@spruce_fence"); put(x, 0, pz1, "@spruce_fence")
    for z in range(pz0, pz1 + 1):
        put(px0, 0, z, "@spruce_fence")
    for (x, z) in ((px0, pz0), (px0, pz1), (BX0 - 1, pz0), (BX0 - 1, pz1), (4, pz0), (6, pz0), (px0, 22)):
        put(x, 0, z, lg(TRIM, "y")); put(x, 1, z, "@dark_oak_fence"); put(x, 2, z, B.lantern(False))
    put(5, 0, pz0, GATE_NS)
    S.ground(b, {(x, z) for x in range(px0 + 1, BX0) for z in range(pz0 + 1, pz1)}, PADDOCK)
    S.ground(b, {(x, z) for x in range(4, 8) for z in range(14, 17)}, Ramp(P("coarse_dirt"), [(P("mud"), .25, None), (P("podzol[snowy=false]"), .2, None)], seed=11))
    G.trough(b, 3, 25, "x", 4)
    G.hay_rick(b, 2, 18, w=3, d=2, layers=2, axis="x")
    put(8, 0, 24, B.hay()); put(8, 0, 25, B.hay())
    S.tree(b, 4, 31, kind="oak", height=5, spread=2.4, y0=0)
    S.tree(b, 7, 12, kind="oak", height=5, spread=2.3, y0=0)
    # east: wagon of hay and the lean-to's cart yard
    S.ground(b, {(x, z) for x in range(41, 45) for z in range(12, 17)}, G.GRAVEL)
    G.hay_rick(b, 38, 12, w=4, d=3, layers=2, axis="x")
    S.woodpile(b, 38, 29, axis="x", length=3)
    S.tree(b, 43, 7, kind="oak", height=5, spread=2.3, y0=0)
    S.tree(b, 13, 5, kind="oak", height=5, spread=2.3, y0=0)
    S.tree(b, 35, 6, kind="birch", height=6, spread=2.1, y0=0)
    G.hedge(b, [(1, 6, 36), (9, 14, 36), (20, 26, 36), (33, 38, 36), (41, 44, 36)])
    G.meadow(b, SX, SZ, lambda x, z: 12 <= x <= 37 and z <= 13)
    S.ground(b, road, G.GRAVEL)
    n.front_yard = {"rect": (19, 3, 29, 9), "corridor": (24, 26), "max_y": 1}
    n.ground_region = (-1, -1, SX, SZ)
    n.ramps = {k: list(v) for k, v in RAMPS.items()}
    n.body_box = (BX0, BZ0, BX1, BZ1)
    n.wall_rows = (1, 3)


def previews(g: dict, out: Path) -> list:
    shots = {
        "eye": ((24.5, 1.62, 5.5), (25, 5, 15)),
        "front": ((25, 7, -9), (25, 6, 16)),
        "ne": ((60, 17, -6), (24, 4, 18)),
        "nw": ((-12, 17, -6), (22, 4, 18)),
        "back": ((50, 15, 52), (24, 4, 20)),
        "paddock": ((12, 7, 36), (5, 1, 22)),
        "above": ("iso", 215, 52),
    }
    return place_views.place_previews("cow_barn", g, out, (0, 0, SX - 1, SZ - 1), shots)


def main() -> int:
    b = build()
    scenery(b)
    G.finish("cow_barn", b, points=POINTS, signs=SIGNS, size=SIZE, ground=GROUND, hero=True, previews=previews,
             size_range=(1800, 3500))
    return 0


if __name__ == "__main__":
    sys.exit(main())
