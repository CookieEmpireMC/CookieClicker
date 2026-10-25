"""The `cow_barn` place: a long medieval threshing barn whose clay-tile roof is the dominant mass (half-hipped ends, catslide
outshut at the back), ONE stepped (corbie) gable over a wide cart entrance with braced double doors and a fence gate, three
small dormers (one with the hoist and a hay bale), a dairy lean-to, a paddock with a drystone wall and a trough, a hay rick.

Outputs:
  src/main/resources/houses/cow_barn.yml          (place file: house format + points, ground-depth 4; FINAL orientation, road NORTH)
  design/scripts/previews/place_cow_barn_*.png    (textured previews; written even when the lint fails)
Run with `mise run assets`. Deterministic. Spec: design/countryside.md (Cow Barn), refs: design/refs-cow-barn.md.
Axes: x west -> east, y up (ground layers y -4..-1), z north -> south.
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
import blocks as B  # noqa: E402
import farm_kits as K  # noqa: E402
import place_gen as G  # noqa: E402
import place_views  # noqa: E402
import scenes as S  # noqa: E402
from blocks import lg, plain as P, sl, st, stripped  # noqa: E402
from build import Build, Ramp, Wall, door, window  # noqa: E402

SX, SY, SZ, GROUND = 48, 18, 36, 4
SIZE = (SX, SY + GROUND, SZ)
BX0, BX1 = 8, 32                    # barn body x
FZ, BZ = 14, 26                     # front wall z, back wall z
PZ = 11                             # porch front (parapet) plane
UC = 20                             # porch / door centre column
Y_EAVE = 5
BEAM = "dark_oak"

YARD = ((14, 3), (24, 9))
POINTS = {
    "viewpoint": {"at": [18, 0, 5], "yaw": -12, "pitch": 3},
    "yard": {"from": list(YARD[0]), "to": list(YARD[1])},
    "hologram": [19, 1, 7],
    "panel": {"at": [22, 0, 7], "yaw": 180},
    "ripe": [14, 0, 7],
}
SIGNS = [{"at": [17, 2, PZ - 1], "lines": ["", "Cow Barn", "", ""]}]

MASON = Ramp(P("stone_bricks"), [(P("cracked_stone_bricks"), .2, None), (P("mossy_stone_bricks"), .26, "low")], seed=3)
PLINTH = Ramp(P("cobblestone"), [(P("mossy_cobblestone"), .27, "low"), (P("andesite"), .15, None)], seed=4)
BOARD = Ramp(P("spruce_planks"), [(P("oak_planks"), .2, None), (P("dark_oak_planks"), .12, None)], seed=5)
PLASTER = Ramp(P("calcite"), [(P("diorite"), .2, None), (P("polished_diorite"), .12, "low")], seed=6, height=6, y0=1)
ROOF = Ramp("brick", [("mud_brick", .24, None), ("resin_brick", .04, None)], seed=8, height=8, y0=5)
FLOOR = Ramp(P("spruce_planks"), [(P("oak_planks"), .22, None), (P("dark_oak_planks"), .15, None)], seed=9)
COARSE = Ramp(P("coarse_dirt"), [(P("podzol[snowy=false]"), .3, None), (P("gravel"), .12, None), (P("mud"), .12, None)], seed=33)
PADDOCK = Ramp(P("coarse_dirt"), [(P("mud"), .25, None), (P("podzol[snowy=false]"), .15, None), (P("dirt_path"), .1, None)], seed=37)
RAMPS = {
    "masonry": ["stone_bricks", "cracked_stone_bricks", "mossy_stone_bricks"],
    "plinth": ["cobblestone", "mossy_cobblestone", "andesite"],
    "board": ["spruce_planks", "oak_planks", "dark_oak_planks"],
    "timber": ["dark_oak_log", "stripped_dark_oak_log", "dark_oak_planks"],
    "plaster": ["calcite", "diorite", "polished_diorite"],
    "roof": ["brick_stairs", "mud_brick_stairs", "resin_brick_stairs", "dark_oak_stairs", "bricks"],
    "drystone": ["cobblestone_wall", "mossy_cobblestone_wall", "andesite_wall"],
}
GATE = "minecraft:spruce_fence_gate[facing=north,in_wall=false,open=false,powered=false]"


def beam(axis: str) -> str:
    return stripped(BEAM, axis)


def drop(b: Build, cells) -> None:
    for c in cells:
        b.g.pop(c, None)
        b.notes.roof_cells.discard(c)
    for r in b.notes.roofs:
        r["eave"] = [c for c in r["eave"] if c in b.g]
        r["field"] = [c for c in r["field"] if c in b.g]


def build() -> Build:
    b = Build(SIZE[:1] + (SY,) + SIZE[2:], ground=GROUND)
    n = b.notes
    put, fill, rng = b.put, b.fill, b.rng
    G.lawn(b, SX, SZ)

    # ================= body: plinth, board-and-batten walls on a dark log frame =================
    ring = [(x, z) for x in range(BX0, BX1 + 1) for z in range(FZ, BZ + 1) if x in (BX0, BX1) or z in (FZ, BZ)]
    posts_x = list(range(BX0, BX1 + 1, 4))                                        # 8 12 16 20 24 28 32
    posts_z = [FZ, 18, 22, BZ]
    for (x, z) in ring:
        put(x, 0, z, PLINTH.pick(x, 0, z))
    for x in range(BX0, BX1 + 1):                                                 # front and back walls
        for z, ytop in ((FZ, 4), (BZ, 2)):
            if z == FZ and 17 <= x <= 23:
                continue
            for y in range(1, ytop + 1):
                if x in posts_x:
                    put(x, y, z, lg(BEAM, "y"))
                elif (x - BX0) % 4 == 2:
                    put(x, y, z, P("stripped_spruce_log[axis=y]"))
                else:
                    put(x, y, z, BOARD.pick(x, y, z))
            put(x, ytop + 1, z, beam("x"))
    for x in posts_x:                                                             # proud posts under the eaves
        if not 17 <= x <= 23:
            put(x, 0, FZ - 1, PLINTH.pick(x, 0, FZ - 1))
            fill(x, 1, FZ - 1, x, 4, FZ - 1, lg(BEAM, "y"))
        put(x, 0, BZ + 1, PLINTH.pick(x, 0, BZ + 1))
        fill(x, 1, BZ + 1, x, 2, BZ + 1, lg(BEAM, "y"))
    for z in posts_z:
        for x in (BX0 - 1, BX1 + 1):
            put(x, 0, z, PLINTH.pick(x, 0, z))
            fill(x, 1, z, x, 4 if z <= 22 else 2, z, lg(BEAM, "y"))

    # ================= main roof (half-hipped ends) and catslide outshut =================
    pick = lambda x, y, z: ROOF.pick(x, y, z)                                      # noqa: E731
    main = K.hipped_roof(b, (BX0 - 1, FZ - 1, BX1 + 1, 23), Y_EAVE, {"N": 0, "S": 0, "W": 3, "E": 3}, field="brick",
                         eave="dark_oak", rake="dark_oak", ridge=P("stone_bricks"), cap=sl("stone_brick"),
                         name="nave", form="half-hip", ramp_pick=pick, riser=P("bricks"))
    cat = K.catslide_roof(b, (BX0 - 1, 23, BX1 + 1, 27), "N", Y_EAVE, field=pick, eave="dark_oak", name="outshut")
    surface = dict(main["surface"])
    surface.update({k: v for k, v in cat["surface"].items()})
    for side_x, outward in ((BX0, "west"), (BX1, "east")):                          # end walls: boards below, brick gable above
        for z in range(FZ, BZ + 1):
            top = surface.get((side_x, z))
            if top is None:
                continue
            for y in range(1, top):
                if b.get(side_x, y, z) is not None:
                    continue
                if y <= 4:
                    put(side_x, y, z, P("stripped_spruce_log[axis=y]") if (z - FZ) % 4 == 2 else BOARD.pick(side_x, y, z))
                elif y == 5 and z <= 22:
                    put(side_x, y, z, beam("z"))
                else:
                    put(side_x, y, z, MASON.pick(side_x, y, z))
        for z in posts_z:
            put(side_x, 5, z, beam("z")) if z <= 22 and b.get(side_x, 5, z) is None else None
    WW = Wall(b, "west", "south", (BX0, 0))
    WE = Wall(b, "east", "south", (BX1, 0))
    K.vent_slits(WW, 16, 7, 3)
    K.vent_slits(WE, 16, 7, 3)
    for wl in (WW, WE):                                                            # louvre slits keep their stone surround
        for u in (15, 21):
            for y in (7, 8):
                if wl.get(u, y, 0) is not None and B.block_id(wl.get(u, y, 0)) == "air":
                    pass
    n.volumes.append({"name": "nave", "footprint": (BX0, FZ, BX1, 22), "top": main["ridge_y"] + 1})
    n.volumes.append({"name": "outshut", "footprint": (BX0, 23, BX1, BZ), "top": 4})

    # ================= cart porch: piers, lintel, gate, braced doors, corbie gable, cross-gable roof =================
    WF = Wall(b, "north", "east", (0, FZ))
    WP = Wall(b, "north", "east", (0, PZ))
    for du in (-3, 3):
        for z in (PZ, PZ + 1, PZ + 2):
            x = UC + du
            put(x, 0, z, PLINTH.pick(x, 0, z))
            for y in range(1, 5):
                put(x, y, z, MASON.pick(x, y, z))
            put(x, 5, z, beam("z"))
    for du in range(-3, 4):
        WP.put(UC + du, 5, 0, WP.log(BEAM, "along", True))
        for z in (PZ + 1, PZ + 2):
            put(UC + du, 0, z, PLINTH.pick(UC + du, 0, z))
    for du in range(-2, 3):
        put(UC + du, 0, PZ, PLINTH.pick(UC + du, 0, PZ))
    for du in (-2, -1, 1, 2):
        WP.put(UC + du, 1, 0, GATE)
    WP.put(UC, 1, 0, "@spruce_fence")
    WP.put(UC, 2, 0, "@spruce_fence")
    K.barn_door_double(b, WF, UC, 1, width=5, height=4, leaf=lambda x, y, z: P("spruce_planks"),
                       brace=P("dark_oak_planks"))
    for du in (-2, 2):
        WF.put(UC + du, 4, 1, B.lantern(True))
    K.corbie_gable(b, WP, UC, 3, 6, {3: 7, 2: 9, 1: 10, 0: 11}, body=lambda x, y, z: MASON.pick(x, y, z))
    for du in (-3, 3):                                                             # pier tops carry the first step
        for y in (6, 7):
            if WP.get(UC + du, y, 0) is None:
                WP.put(UC + du, y, 0, MASON.pick(*WP.xyz(UC + du, y, 0)))
    for du in (-1, 1):                                                             # slits in the gable
        for y in (7, 8):
            WP.put(UC + du, y, 0, "@iron_bars")
    WP.put(UC, 7, 0, P("stripped_spruce_log[axis=y]"))
    before = {c for c in main["cells"]}
    porch = K.hipped_roof(b, (UC - 4, PZ + 1, UC + 4, 17), Y_EAVE, {"W": 0, "E": 0, "N": None, "S": None}, field="brick",
                          eave="dark_oak", rake="dark_oak", ridge=P("stone_bricks"), cap=sl("stone_brick"),
                          name="porch", form="gable", ramp_pick=pick, riser=P("bricks"), clip=main)
    n.gables[:] = [g for g in n.gables if g["name"] not in ("porch:S", "porch:N")]
    drop(b, [(x, Y_EAVE, FZ - 1) for x in range(UC - 3, UC + 4)] + [(x, Y_EAVE, FZ - 1) for x in range(UC - 3, UC + 4)])
    for du in range(-2, 3):
        for z in (PZ + 1, PZ + 2):
            put(UC + du, 5, z, beam("x"))
    n.volumes.append({"name": "porch", "footprint": (UC - 3, PZ, UC + 3, FZ), "top": 12})
    for x in range(UC - 2, UC + 3):                                                # paved porch floor
        for z in (PZ + 1, PZ + 2):
            put(x, -1, z, G.COBBLE.pick(x, 0, z))
    put(17, 2, PZ - 1, B.wall_sign("spruce", "north"))

    # ================= dormers on the front slope (one with the hoist and a hay bale) =================
    roof_mat = "brick"
    oak = lambda x, y, z: P("dark_oak_planks")                                    # noqa: E731
    for uc in (12, 27, 31):
        hoist = uc == 27
        K.dormer(b, main, WF, uc, 0, 6, wall_mat=oak, trim=BEAM, roof_mat=roof_mat, name=f"dormer{uc}",
                 beam=hoist, hatch=hoist, h=3)
        if hoist:
            WF.put(uc, 6 + 3 - 2, 2, B.hay())                                      # the bale hangs on the chain

    # ================= back wall: stall half-doors, muck hatches =================
    WB = Wall(b, "south", "east", (0, BZ))
    for x in (11, 17, 23, 29):
        door(WB, x, 1, mat="spruce", hood=False, lintel=False, jambs=False)
        put(x, 3, BZ, beam("x"))
    for x in (14, 20, 26):
        WB.put(x, 2, 1, WB.trapdoor("spruce", "bottom", True))
        WB.put(x, 1, 1, B.hay())
    n.roof_heights.add(Y_EAVE)

    # ================= dairy lean-to on the east gable =================
    DX0, DX1, DZ0, DZ1 = 33, 39, 17, 21
    dcat = K.catslide_roof(b, (DX0, DZ0 - 1, DX1 + 1, DZ1 + 1), "W", 7, field=pick, eave="dark_oak", name="dairy")
    ds = dcat["surface"]
    ring_d = [(x, z) for x in range(DX0, DX1 + 1) for z in range(DZ0, DZ1 + 1) if x == DX1 or z in (DZ0, DZ1)]
    for (x, z) in ring_d:
        put(x, 0, z, PLINTH.pick(x, 0, z))
        for y in range(1, ds[(x, z)]):
            put(x, y, z, lg(BEAM, "y") if (x in (DX0 + 3, DX1) and z in (DZ0, DZ1)) or (x == DX0 and z in (DZ0, DZ1)) else PLASTER.pick(x, y, z))
    for x in range(DX0, DX1 + 1):
        for z in range(DZ0 + 1, DZ1):
            put(x, 0, z, FLOOR.pick(x, 0, z))
    for z in range(DZ0, DZ1 + 1):
        put(DX1 + 1, 0, z, PLINTH.pick(DX1 + 1, 0, z)) if False else None
    for x in range(DX0, DX1 + 1):
        for z, w in ((DZ0, "n"), (DZ1, "s")):
            top = ds[(x, z)]
            if top - 1 >= 1:
                put(x, top - 1, z, beam("x")) if b.block(x, top - 1, z) != "air" else None
    WDN = Wall(b, "north", "east", (0, DZ0))
    WDS = Wall(b, "south", "east", (0, DZ1))
    window(WDN, 34, 2, shutters="spruce", box="potted_cornflower", sill="spruce")
    door(WDN, 37, 1, mat="spruce", hood=False)
    window(WDS, 35, 2, shutters="spruce", box="potted_azure_bluet", sill="spruce")
    window(Wall(b, "east", "south", (DX1, 0)), 19, 2, shutters=None, box="potted_poppy", sill="spruce")
    put(36, 0, DZ0 - 1, B.barrel()); put(36, 1, DZ0 - 1, P("iron_bars")) if False else None
    put(38, 0, DZ0 - 1, B.barrel()); put(38, 1, DZ0 - 1, B.barrel())
    put(34, 8, 19, "minecraft:dark_oak_trapdoor[facing=east,half=bottom,open=true,powered=false,waterlogged=false]")
    put(34, 8, 20, "minecraft:dark_oak_trapdoor[facing=west,half=bottom,open=true,powered=false,waterlogged=false]")
    put(34, 9, 19, sl("dark_oak", "bottom")); put(34, 9, 20, sl("dark_oak", "bottom"))
    n.vents |= {(34, 8, 19), (34, 8, 20)}
    n.volumes.append({"name": "dairy", "footprint": (DX0, DZ0, DX1, DZ1), "top": 8})
    return b


def scenery(b: Build) -> None:
    n = b.notes
    put, rng = b.put, b.rng
    road = G.road(b, SX)
    yard = S.noisy_rect(b, 14, 3, 24, 9, wobble=1)
    S.ground(b, yard - road, G.COBBLE)
    # path to the gate, front-of-barn beds, backdrop for the panel
    pathc = {(x, z) for z in range(10, 14) for x in range(18 + (z % 2), 21 + (z % 2))}
    S.ground(b, pathc - yard, G.GRAVEL)
    S.flower_bed(b, {(x, z) for x in range(9, 16) for z in range(11, 13)}, density=.65)
    S.flower_bed(b, {(x, z) for x in range(25, 32) for z in range(11, 13)}, density=.65)
    for x in range(22, 26):
        put(x, 0, 10, "@stone_brick_wall" if x % 2 else "@mossy_stone_brick_wall")
    put(22, 1, 10, B.lantern(False)); put(25, 1, 10, B.lantern(False))
    S.apron(b, 21, 10, 26, 10, G.COBBLE, force=True)
    S.lamp_post(b, 13, 3, 3)
    S.lamp_post(b, 25, 3, 3)
    # vines between the posts on the front wall
    for x in (9, 10, 14, 15, 25, 26, 29, 30):
        for y in range(1, 2 + rng.randint(2, 3)):
            put(x, y, FZ - 1, B.vine("south"))
    # paddock: drystone wall, trough, feeder, trampled ground
    pad = {(x, z) for x in range(2, 13) for z in range(5, 13)}
    S.ground(b, {c for c in pad if rng.random() < .5}, PADDOCK)
    G.drystone(b, [(1, z) for z in range(4, 13)] + [(x, 4) for x in range(1, 13)] + [(x, 13) for x in range(1, 8)]
               + [(x, 13) for x in (9, 10, 11)] + [(13, z) for z in list(range(4, 7)) + list(range(8, 13))], high=2)
    put(13, 0, 7, "minecraft:spruce_fence_gate[facing=east,in_wall=false,open=false,powered=false]")
    put(13, -1, 7, P("coarse_dirt")); put(14, -1, 7, P("coarse_dirt")); put(12, -1, 7, P("mud"))
    G.trough(b, 3, 5, "x", 4)
    put(2, 0, 11, B.hay()); put(2, 0, 10, B.hay()); put(3, 0, 11, B.hay())
    S.tree(b, 5, 8, kind="oak", height=5, spread=2.4, y0=0)
    # east side: hay rick, haycart, milk churns
    G.hay_rick(b, 36, 5, w=6, d=3, layers=3)
    for dx in (0, 1, 2):
        for dz in (0, 1):
            put(30 + dx, 0, 8 + dz, sl("spruce", "bottom"))
    for (x, z) in ((30, 7), (32, 10)):
        put(x, 0, z, B.hay())
    put(30, 1, 8, B.hay()); put(31, 1, 8, B.hay()); put(31, 1, 9, B.hay())
    for z in (7, 10):
        put(31, 0, z, "minecraft:spruce_trapdoor[facing=north,half=bottom,open=true,powered=false,waterlogged=false]")
    S.apron(b, 29, 6, 33, 10, G.GRAVEL, force=True)
    S.woodpile(b, 40, 12, axis="x", length=4)
    S.bench(b, 42, 18, facing="west", length=2, along="z")
    # south: pasture trees, a field wall, hedges
    for (x, z, k) in ((12, 32, "oak"), (36, 32, "oak"), (46, 22, "oak"), (2, 22, "birch")):
        S.tree(b, x, z, kind=k, height=5 if k == "oak" else 6, spread=2.6 if k == "oak" else 2.2, y0=0)
    G.drystone(b, [(x, 32) for x in range(25, 35)] + [(34, z) for z in range(29, 33)], high=1)
    G.hedge(b, [(1, 6, 34), (9, 14, 34), (17, 22, 34), (25, 30, 34), (33, 38, 34), (41, 46, 34), ("z", 20, 24, 0), ("z", 27, 31, 0)])
    taken = lambda x, z: (14 <= x <= 24 and z <= 9) or (x <= 13 and z <= 13)         # noqa: E731
    G.meadow(b, SX, SZ, taken, seed=77)
    n.front_yard = {"rect": (14, 3, 24, 9), "corridor": (18, 20), "max_y": 1}
    n.ground_region = (-1, -1, SX, SZ)
    n.ramps = {k: list(v) for k, v in RAMPS.items()}
    n.plinth = {"blocks": RAMPS["plinth"], "courses": 1,
                "ring": [(x, z) for x in range(BX0, BX1 + 1) for z in range(FZ, BZ + 1) if x in (BX0, BX1) or z in (FZ, BZ)]}
    n.body_box = (BX0, FZ, BX1, BZ)
    n.wall_rows = (1, 3)


def previews(g: dict, out: Path) -> list:
    shots = {
        "eye": ((18.5, 1.62, 5.5), (22, 6, 15)),
        "front": ((20, 6, -9), (20, 5, 16)),
        "ne": ((58, 16, -6), (22, 4, 17)),
        "nw": ((-12, 16, -6), (20, 4, 17)),
        "back": ((50, 14, 50), (20, 4, 17)),
        "porch": ((20.5, 3.0, 5.5), (20.5, 3, 14)),
        "dairy": ((46, 6, 10), (36, 3, 19)),
        "paddock": ((13, 5, 17), (6, 1, 8)),
        "above": ("iso", 215, 52),
    }
    return place_views.place_previews("cow_barn", g, out, (0, 0, SX - 1, SZ - 1), shots)


def main() -> int:
    b = build()
    scenery(b)
    G.finish("cow_barn", b, points=POINTS, signs=SIGNS, size=SIZE, ground=GROUND, hero=True, previews=previews)
    return 0


if __name__ == "__main__":
    sys.exit(main())
