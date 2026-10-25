"""The `market` place (market village hub): a drystone village wall along the yard with a roofed timber lychgate, a notice board
at the gate, a cobbled square with an old stone well and a stepped market cross, the Recipe Book shelter (open timber, dark-oak
shingle pyramid roof, lectern), three striped market stalls (one is the ripe spot), a signpost and lanterns.

Outputs: src/main/resources/houses/market.yml, design/scripts/previews/place_market_*.png.
Run with `mise run assets`. Deterministic. Spec: design/countryside.md (Market), refs: design/refs-market.md.
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
from blocks import lg, plain as P, sl, st, stripped  # noqa: E402
from build import Build, Ramp, Wall, door, window  # noqa: E402

SX, SY, SZ, GROUND = 44, 16, 34, 4
SIZE = (SX, SY + GROUND, SZ)
WALL_Z = 11
BEAM = "dark_oak"
SHELTER = (8, 17, 12, 21)                          # post rectangle x0, z0, x1, z1
POINTS = {
    "viewpoint": {"at": [21, 0, 5], "yaw": 0, "pitch": 3},
    "yard": {"from": [15, 3], "to": [25, 9]},
    "hologram": [20, 1, 7],
    "panel": {"at": [23, 0, 7], "yaw": 180},
    "ripe": [25, 0, 7],
    "recipe_book": [10, 1, 19],
    "notice_board": {"at": [12, 0, 9], "yaw": 180},
}
SIGNS = [{"at": [36, 2, 14], "lines": ["", "Mill", "Cow Barn", ""]}, {"at": [34, 1, 14], "lines": ["Sugar Works", "Cocoa Grove", "Chicken Farm", ""]}]

PLASTER = Ramp(P("calcite"), [(P("diorite"), .2, None), (P("polished_diorite"), .12, "low")], seed=6, height=4, y0=1)
PLINTH = Ramp(P("cobblestone"), [(P("mossy_cobblestone"), .27, "low"), (P("andesite"), .15, None)], seed=4)
ROOF = Ramp("dark_oak", [("deepslate_tile", .1, None), ("cobbled_deepslate", .08, None)], seed=8, height=6, y0=4)
SQUARE = Ramp(P("cobblestone"), [(P("mossy_cobblestone"), .16, None), (P("andesite"), .16, None), (P("gravel"), .08, None)], seed=31)
RAMPS = {
    "plinth": ["cobblestone", "mossy_cobblestone", "andesite"],
    "roof": ["dark_oak_stairs", "deepslate_tile_stairs", "dark_oak_planks"],
    "plaster": ["calcite", "diorite", "polished_diorite"],
    "timber": ["dark_oak_log", "stripped_dark_oak_log", "dark_oak_planks"],
    "drystone": ["cobblestone_wall", "mossy_cobblestone_wall", "andesite_wall"],
}


def beam(axis: str) -> str:
    return stripped(BEAM, axis)


def square_cells(b: Build) -> set:
    import numpy as np
    from scipy.ndimage import gaussian_filter
    noise = gaussian_filter(np.random.default_rng(5).standard_normal((60, 40)), 1.6)
    noise = (noise - noise.mean()) / noise.std()
    cells = set()
    for x in range(3, 41):
        for z in range(12, 31):
            d = ((x - 22.0) / 19.0) ** 2 + ((z - 21.0) / 9.5) ** 2
            if d + 0.3 * noise[x, z] < 1.0:
                cells.add((x, z))
    return cells


def stall(b: Build, x0: int, z0: int, c1: str, c2: str, goods: str) -> None:
    """A 4 x 4 market stall facing west: fence posts, striped wool awning, valance carpets, counter and goods."""
    for (x, z) in ((x0, z0), (x0, z0 + 3), (x0 + 3, z0), (x0 + 3, z0 + 3)):
        for y in range(3):
            b.put(x, y, z, "@dark_oak_fence")
    for x in range(x0, x0 + 4):
        for z in range(z0, z0 + 4):
            b.put(x, 3, z, P(f"{c1 if (z - z0) % 2 == 0 else c2}_wool"))
    for z in range(z0, z0 + 4):
        b.put(x0 - 1, 3, z, P(f"{c1 if (z - z0) % 2 == 0 else c2}_wool"))
    for z in (z0 + 1, z0 + 2):
        b.put(x0, 0, z, sl("spruce", "bottom"))
        b.put(x0 + 1, 0, z, B.barrel() if goods == "barrels" else B.hay())
    b.put(x0 + 2, 0, z0 + 1, B.barrel())
    b.put(x0 + 2, 0, z0 + 2, P("pumpkin") if goods == "barrels" else P("white_wool"))
    b.put(x0 + 3, 1, z0 + 1, B.lantern(False)) if False else None
    b.put(x0 + 1, 2, z0 + 1, B.lantern(True))
    for x in range(x0 - 1, x0 + 4):
        for z in range(z0 - 1, z0 + 5):
            b.put(x, -1, z, SQUARE.pick(x, 0, z))
    b.notes.volumes.append({"name": f"stall{x0}_{z0}", "footprint": (x0, z0, x0 + 3, z0 + 3), "top": 4})


def cottage(b: Build, x0: int, z0: int, tag: str) -> None:
    """A 7 x 7 timber-frame cottage facing north onto the square: cobble plinth, plaster between dark-oak posts, dark-oak shingle
    gable roof (ridge along x), gable ends filled with plaster, a door, two shuttered lit windows and a flower box."""
    n = b.notes
    x1, z1 = x0 + 6, z0 + 6
    ring = [(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1) if x in (x0, x1) or z in (z0, z1)]
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            b.put(x, 0, z, PLINTH.pick(x, 0, z))
    for (x, z) in ring:
        post = (x in (x0, x1) and z in (z0, z1)) or (x - x0) % 3 == 0 and z in (z0, z1) or (z - z0) % 3 == 0 and x in (x0, x1)
        for y in (1, 2, 3):
            b.put(x, y, z, lg(BEAM, "y") if post else PLASTER.pick(x, y, z))
        b.put(x, 4, z, beam("x" if z in (z0, z1) else "z"))
    S.apron(b, x0 - 1, z0 - 3, x1 + 1, z0 - 1, G.COBBLE, force=True)
    pick = lambda x, y, z: ROOF.pick(x, y, z)                                      # noqa: E731
    roof = K.gable_roof_s(b, (x0 - 1, z0 - 1, x1 + 1, z1 + 1), 4, axis="x", field="dark_oak", eave="deepslate_tile",
                          rake="deepslate_tile", ridge=stripped("dark_oak", "x"), cap=sl("deepslate_tile"), name=f"cottage{tag}",
                          riser=P("dark_oak_planks"))
    for x in (x0, x1):
        K.fill_gable_wall(b, roof, [(x, z) for z in range(z0, z1 + 1)], 5, lambda x, y, z: PLASTER.pick(x, y, z))
    WN = Wall(b, "north", "east", (0, z0))
    WS = Wall(b, "south", "east", (0, z1))
    door(WN, x0 + 3, 1, mat="spruce", hood=False)
    b.put(x0 + 3, 0, z0 - 1, st("stone_brick", "south"))
    window(WN, x0 + 1, 2, height=2, shutters="spruce", box="potted_poppy", sill="spruce") if False else None
    window(WN, x0 + 5, 2, height=1, shutters="spruce", box="potted_cornflower", sill="spruce")
    window(WS, x0 + 2, 2, height=1, shutters="spruce", box="potted_azure_bluet", sill="spruce")
    window(Wall(b, "west", "south", (x0, 0)), z0 + 3, 2, height=1, shutters=None, box="potted_dandelion", sill="spruce")
    n.volumes.append({"name": f"cottage{tag}", "footprint": (x0, z0, x1, z1), "top": roof["ridge_y"] + 1})
    n.cottage_ring = getattr(n, "cottage_ring", []) + ring


def build() -> Build:
    b = Build((SX, SY, SZ), ground=GROUND)
    n = b.notes
    put, fill = b.put, b.fill
    G.lawn(b, SX, SZ)
    pick = lambda x, y, z: ROOF.pick(x, y, z)                                      # noqa: E731

    # ================= lychgate in the village wall =================
    G.drystone(b, [(x, WALL_Z) for x in list(range(2, 18)) + list(range(23, 42))], high=2)
    for x in (22, 23, 24):
        put(x, 2, WALL_Z, B.lantern(False)) if x == 23 else None
    for (x, z) in ((18, 11), (22, 11), (18, 13), (22, 13)):
        put(x, 0, z, PLINTH.pick(x, 0, z))
        fill(x, 1, z, x, 3, z, lg(BEAM, "y"))
    for x in range(18, 23):
        put(x, 4, 11, beam("x")); put(x, 4, 13, beam("x")); put(x, 4, 12, beam("x"))
    for z in (11, 12, 13):
        put(18, 4, z, beam("z")); put(22, 4, z, beam("z"))
    gate = K.hipped_roof(b, (17, 10, 23, 14), 4, {"W": 0, "E": 0, "N": None, "S": None}, field="dark_oak",
                         eave="deepslate_tile", rake="deepslate_tile", ridge=stripped("dark_oak", "z"), cap=sl("deepslate_tile"),
                         name="lychgate", form="gable", riser=P("dark_oak_planks"))
    for z in (11, 13):
        K.fill_gable_wall(b, gate, [(x, z) for x in range(18, 23)], 5, lambda x, y, z: P("dark_oak_planks"))
    put(20, 3, 12, B.lantern(True))
    for x in (19, 21):
        put(x, 3, 11, "@dark_oak_fence") if False else None
    put(18, 0, 12, sl("spruce", "bottom")); put(22, 0, 12, sl("spruce", "bottom"))
    n.volumes.append({"name": "lychgate", "footprint": (17, 10, 23, 14), "top": gate["ridge_y"] + 1})

    # ================= Recipe Book shelter =================
    x0, z0, x1, z1 = SHELTER
    ring = [(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1) if x in (x0, x1) or z in (z0, z1)]
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            put(x, 0, z, PLINTH.pick(x, 0, z))
    for (x, z) in ring:
        put(x, 4, z, beam("x" if z in (z0, z1) else "z"))
        if (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            fill(x, 1, z, x, 3, z, lg(BEAM, "y"))
        elif (x, z) not in ((10, z0), (10, z1)) and not (x == x1 and z == 19):      # balustrade; openings north, south, east
            put(x, 1, z, "@spruce_fence")
    for (x, z) in ((x0, 19), (x0, 18), (x0, 20)):
        put(x, 2, z, P("bookshelf"))
    put(10, 0, 19, PLINTH.pick(10, 0, 19))
    put(10, 3, 19, B.lantern(True)); put(10, 4, 19, beam("x"))
    put(11, 4, 19, beam("x")); put(9, 4, 19, beam("x"))
    roof = K.pyramid_roof(b, (x0 - 2, z0 - 2, x1 + 2, z1 + 2), 4, field="dark_oak", eave="deepslate_tile", rake="deepslate_tile",
                          ridge=P("dark_oak_planks"), cap=None, name="shelter", riser=P("dark_oak_planks"))
    put(10, roof["ridge_y"] + 1, 19, "@dark_oak_fence")
    put(10, roof["ridge_y"] + 2, 19, B.lantern(False))
    n.volumes.append({"name": "shelter", "footprint": (x0, z0, x1, z1), "top": roof["ridge_y"] + 3})
    n.plinth = {"blocks": RAMPS["plinth"], "courses": 1, "ring": ring}
    return b


def scenery(b: Build) -> None:
    n = b.notes
    put, rng = b.put, b.rng
    road = G.road(b, SX)
    yard = S.noisy_rect(b, 15, 3, 25, 9, wobble=1)
    S.ground(b, yard - road, G.COBBLE)
    sq = square_cells(b)
    S.ground(b, sq, SQUARE)
    S.ground(b, {(x, z) for z in range(10, 14) for x in range(18, 23)}, G.COBBLE, y=-1)
    # notice board at the gate, backdrop wall lanterns
    for x in (11, 14):
        fill = [put(x, y, 10, "@dark_oak_fence") for y in range(3)]
    for x in (12, 13):
        for y in (1, 2):
            put(x, y, 10, P("spruce_planks"))
    put(13, 1, 9, "minecraft:oak_trapdoor[facing=north,half=bottom,open=true,powered=false,waterlogged=false]")
    put(12, 2, 9, "minecraft:oak_trapdoor[facing=north,half=bottom,open=true,powered=false,waterlogged=false]")
    S.apron(b, 10, 9, 15, 10, G.COBBLE, force=True)
    put(24, 2, 11, B.lantern(False)); put(21, 2, 11, B.lantern(False)) if False else None
    S.lamp_post(b, 14, 3, 3)
    S.lamp_post(b, 26, 3, 3)
    # square: well, market cross, stalls, signpost, benches
    S.well(b, 31, 17)
    cx, cz = 22, 20
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            put(cx + dx, 0, cz + dz, sl("stone_brick", "bottom") if max(abs(dx), abs(dz)) == 2 else P("stone_bricks"))
    put(cx, 1, cz, P("mossy_stone_bricks"))
    for y in (2, 3):
        put(cx, y, cz, "@stone_brick_wall")
    put(cx - 1, 3, cz, "@mossy_stone_brick_wall"); put(cx + 1, 3, cz, "@mossy_stone_brick_wall")
    put(cx, 4, cz, "@stone_brick_wall"); put(cx, 5, cz, sl("stone_brick", "bottom"))
    stall(b, 27, 5, "red", "white", "barrels")
    stall(b, 27, 22, "blue", "white", "hay")
    stall(b, 34, 19, "yellow", "white", "barrels")
    for y in range(4):
        put(36, y, 14, lg(BEAM, "y"))
    put(35, 2, 14, B.wall_sign("spruce", "west")); put(37, 2, 14, B.wall_sign("spruce", "east"))
    SIGNS[:] = [{"at": [35, 2, 14], "lines": ["", "Mill", "Cow Barn", ""]}, {"at": [37, 2, 14], "lines": ["Sugar Works", "Cocoa Grove", "Chicken Farm", ""]}]
    S.apron(b, 34, 13, 38, 15, G.COBBLE, force=True)
    S.bench(b, 24, 25, facing="north", length=3)
    S.bench(b, 15, 22, facing="south", length=3)
    for (x, z) in ((16, 15), (38, 26), (14, 28)):
        S.lamp_post(b, x, z, 3)
    S.crates(b, [(5, 0, 14), (6, 0, 14), (5, 1, 14)], sacks=[(7, 0, 14)])
    cottage(b, 4, 25, "A")
    cottage(b, 13, 25, "B")
    S.tree(b, 40, 31, kind="oak", height=5, spread=2.5, y0=0)
    G.hedge(b, [(9, 14, 33), (17, 22, 33), (25, 30, 33), ("z", 14, 19, 43), ("z", 22, 27, 43)])
    S.ground(b, road, G.GRAVEL)
    n.front_yard = {"rect": (15, 3, 25, 9), "corridor": (18, 20), "max_y": 1}
    n.ground_region = (-1, -1, SX, SZ)
    n.ramps = {k: list(v) for k, v in RAMPS.items()}
    n.plinth["ring"] = n.plinth["ring"] + n.cottage_ring
    n.body_box = None
    n.wall_rows = (1, 3)


def previews(g: dict, out: Path) -> list:
    shots = {
        "eye": ((21.5, 1.62, 5.5), (21, 3, 20)),
        "front": ((22, 7, -9), (22, 3, 18)),
        "ne": ((56, 16, -4), (22, 2, 19)),
        "nw": ((-12, 16, -4), (22, 2, 19)),
        "back": ((22, 12, 46), (22, 2, 18)),
        "shelter": ((14, 3, 25), (10, 2, 19)),
        "square": ((36, 6, 29), (22, 1, 19)),
        "above": ("iso", 215, 52),
    }
    return place_views.place_previews("market", g, out, (0, 0, SX - 1, SZ - 1), shots)


def main() -> int:
    b = build()
    scenery(b)
    G.finish("market", b, points=POINTS, signs=SIGNS, size=SIZE, ground=GROUND, hero=False, previews=previews,
             size_range=(600, 2000))
    return 0


if __name__ == "__main__":
    sys.exit(main())
