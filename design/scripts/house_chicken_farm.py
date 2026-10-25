"""The `chicken_farm` place: ONE wooden farmyard cluster. A big oak-plank coop raised on dark log stilts over a mossy-cobble kerb
(plank ramp to a small door, nest composters and a water cauldron under the floor), a steep spruce roof with a louvre cupola
and a weathervane; a smaller second coop with a pyramid roof in its own pen next door; one run fence that hugs both coops (spruce
fence, log posts with lantern caps, gates), coarse-dirt run ground with hay feed piles and a trough; an orchard tree overlapping
the edge; the yard and the road on the north side.

Outputs: src/main/resources/houses/chicken_farm.yml, design/scripts/previews/place_chicken_farm_*.png.
Run with `mise run assets`. Deterministic. Spec: design/countryside.md (Chicken Farm), refs: design/refs-chicken-farm.md.
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
from blocks import lg, plain as P, sl, st, td  # noqa: E402
from build import Build, Ramp, Wall, door, window  # noqa: E402

SX, SY, SZ, GROUND = 40, 17, 34, 4
SIZE = (SX, SY + GROUND, SZ)
POINTS = {
    "viewpoint": {"at": [15, 0, 5], "yaw": -8, "pitch": 4},
    "yard": {"from": [11, 3], "to": [21, 9]},
    "hologram": [16, 1, 7],
    "panel": {"at": [19, 0, 7], "yaw": 180},
    "ripe": [12, 0, 8],
}
SIGNS = [{"at": [12, 5, 16], "lines": ["", "Chicken Farm", "", ""]}]

BOARD = Ramp(P("oak_planks"), [(P("birch_planks"), .2, None), (P("jungle_planks"), .1, None)], seed=3)
KERB = Ramp(P("cobblestone"), [(P("mossy_cobblestone"), .3, "low"), (P("andesite"), .15, None)], seed=4)
RUN = Ramp(P("coarse_dirt"), [(P("podzol[snowy=false]"), .25, None), (P("rooted_dirt"), .12, None), (P("dirt_path"), .14, None)], seed=37)
RAMPS = {
    "board": ["oak_planks", "birch_planks", "jungle_planks"],
    "plinth": ["cobblestone", "mossy_cobblestone", "andesite"],
    "roof": ["spruce_stairs", "dark_oak_stairs", "spruce_planks"],
}
GATE_NS = "minecraft:spruce_fence_gate[facing=north,in_wall=false,open=false,powered=false]"
GATE_EW = "minecraft:spruce_fence_gate[facing=east,in_wall=false,open=false,powered=false]"
TRIM = "dark_oak"
FLOOR_A = 3          # floor block y of coop A (stilts 3 high)
FLOOR_B = 2


def post(b: Build, x: int, z: int, y0: int, y1: int) -> None:
    for y in range(y0, y1 + 1):
        b.put(x, y, z, lg(TRIM, "y"))


def build() -> Build:
    b = Build((SX, SY, SZ), ground=GROUND)
    n = b.notes
    put, fill = b.put, b.fill
    G.lawn(b, SX, SZ)

    # ============ coop A: floor x11..19 z16..22 at y3, inset plank walls x12..18 z17..21 (y4..6), outer posts ============
    AX0, AX1, AZ0, AZ1 = 11, 19, 16, 22
    for x in range(AX0, AX1 + 1):
        for z in range(AZ0, AZ1 + 1):
            put(x, FLOOR_A, z, BOARD.pick(x, 3, z))
    posts = [(11, 16), (19, 16), (11, 22), (19, 22), (11, 19), (19, 19), (15, 22)]
    foot = []
    for (x, z) in posts:
        put(x, 0, z, KERB.pick(x, 0, z))
        post(b, x, z, 1, 6)
        foot.append((x, z))
    for z in (16, 22):                                                # low kerb between the stilts (sides and back), open at the front
        for x in range(AX0, AX1 + 1):
            if z == 22 or (x in (12, 18)):
                put(x, 0, z, KERB.pick(x, 0, z))
    for x in (AX0, AX1):
        for z in range(AZ0, AZ1 + 1):
            put(x, 0, z, KERB.pick(x, 0, z))
    for x in range(AX0, AX1 + 1):                                    # brace rail under the floor
        for z in (AZ0, AZ1):
            if b.get(x, 1, z) is None:
                put(x, 2, z, B.stripped(TRIM, "x"))
    for z in range(AZ0, AZ1 + 1):
        for x in (AX0, AX1):
            if b.get(x, 1, z) is None:
                put(x, 2, z, B.stripped(TRIM, "z"))
    # walls (inset one block): oak boards, dark frame posts at the inset corners and every 3rd
    for x in range(12, 19):
        for z in (17, 21):
            for y in range(4, 7):
                put(x, y, z, lg(TRIM, "y") if x in (12, 18) else BOARD.pick(x, y, z))
    for z in range(17, 22):
        for x in (12, 18):
            for y in range(4, 7):
                put(x, y, z, lg(TRIM, "y") if z in (17, 21) else BOARD.pick(x, y, z))
    for x in range(12, 19):                                          # plate row
        for z in (17, 21):
            put(x, 7, z, B.stripped(TRIM, "x"))
    for z in range(17, 22):
        put(12, 7, z, B.stripped(TRIM, "z")); put(18, 7, z, B.stripped(TRIM, "z"))
    for z in (16, 22):                                               # outer plate on the posts
        for x in range(AX0, AX1 + 1):
            if x in (11, 13, 15, 17, 19):
                put(x, 7, z, B.stripped(TRIM, "x"))
    roof = K.hipped_roof(b, (10, 15, 20, 23), 7, {"N": 0, "S": 0, "W": None, "E": None}, field="spruce", eave="dark_oak",
                         rake="dark_oak", ridge=B.stripped(TRIM, "x"), cap=sl("dark_oak"), name="coop", form="gable",
                         riser=P("spruce_planks"))
    for xin in (12, 18):                                             # gable infill, boards over a frame
        K.fill_gable_wall(b, roof, [(xin, z) for z in range(17, 22)], 7, lambda x, y, z: BOARD.pick(x, y, z))
        for z in range(17, 22):
            if b.get(xin, 7, z) is not None and z in (17, 21):
                pass
    n.volumes.append({"name": "coop", "footprint": (AX0, AZ0, AX1, AZ1), "top": roof["ridge_y"] + 1})
    # door on the north (yard) wall
    WN = Wall(b, "north", "east", (0, 17))
    door(WN, 15, 4, mat="spruce", frame=TRIM, hood=False)
    put(12, 5, 16, B.wall_sign("spruce", "north"))
    # windows
    pass
    G.window_sill(b, Wall(b, "west", "south", (12, 0)), 20, 5)
    G.window_sill(b, Wall(b, "east", "south", (18, 0)), 20, 5)
    for x in (12, 13, 14, 16, 17):                                    # ONE row of 5 nest hatches under a slab strip (south wall)
        put(x, 4, 22, B.td("spruce", "south", "bottom", True))
        put(x, 5, 22, sl("spruce", "bottom"))
        put(x, 4, 21, B.hay())
    # ramp from the yard gate to the deck, rails
    for i, z in enumerate(range(12, 16)):
        put(15, i, z, st("spruce", "north"))
    for x in (12, 13, 14, 16, 17, 18):                                # deck railing in front of the door wall
        put(x, 4, 16, "@spruce_fence")
    for x in (13, 17):
        put(x, 6, 16, st("dark_oak", "south", "top")); put(x, 5, 16, B.lantern(True))
    # nest composters, cauldron and a feed trough under the floor
    for x in (13, 14, 16, 17):
        put(x, 0, 21, "minecraft:composter[level=%d]" % (3 + x % 3))
    put(12, 0, 20, B.cauldron_water(3))
    put(18, 0, 20, B.barrel("north"))
    put(15, 0, 18, sl("spruce", "bottom")); put(14, 0, 18, sl("spruce", "bottom")); put(16, 0, 18, sl("spruce", "bottom"))
    put(15, 2, 19, B.lantern(True))
    put(15, 3, 19, B.stripped(TRIM, "x"))
    # louvre cupola on the ridge
    ry = roof["ridge_y"]
    cx, cz = 15, 19
    put(cx, ry + 1, cz, P("oak_planks"))
    for dx in (-1, 1):
        put(cx + dx, ry + 1, cz, sl("spruce", "bottom"))
        n.vents.add((cx + dx, ry + 1, cz))
    put(cx, ry + 2, cz, st("dark_oak", "east"))
    put(cx, ry + 3, cz, "@spruce_fence")
    put(cx, ry + 4, cz, B.lantern(False))
    n.roof_cells.add((cx, ry + 2, cz))
    n.roof_heights |= {ry + 3}

    # ============ coop B: a small hut, floor y2, walls y3..4 on the plank ring x25..29 z16..20, pyramid roof ============
    for x in range(25, 30):
        for z in range(16, 21):
            ring = x in (25, 29) or z in (16, 20)
            put(x, FLOOR_B, z, BOARD.pick(x, 2, z))
            if ring:
                if (x, z) in ((25, 16), (29, 16), (25, 20), (29, 20), (27, 20), (25, 18), (29, 18)):
                    post(b, x, z, 1, 4)
                    put(x, 0, z, KERB.pick(x, 0, z))
                    foot.append((x, z))
                else:
                    for y in (3, 4):
                        put(x, y, z, BOARD.pick(x, y, z))
    for x in range(26, 29):
        put(x, 5, 16, B.stripped(TRIM, "x")); put(x, 5, 20, B.stripped(TRIM, "x"))
    for z in range(17, 20):
        put(25, 5, z, B.stripped(TRIM, "z")); put(29, 5, z, B.stripped(TRIM, "z"))
    for x in (25, 29):
        for z in (16, 20):
            put(x, 5, z, B.stripped(TRIM, "x"))
    roofb = K.pyramid_roof(b, (24, 15, 30, 21), 5, field="spruce", eave="dark_oak", rake="dark_oak", ridge=P("spruce_planks"),
                           cap=None, name="coop_b", riser=P("spruce_planks"))
    fy = roofb["ridge_y"] + 1
    put(27, fy, 18, "@spruce_fence")
    put(27, fy + 1, 18, B.lantern(False))
    n.roof_heights.add(fy)
    n.volumes.append({"name": "coop_b", "footprint": (25, 16, 29, 20), "top": roofb["ridge_y"] + 1})
    WNb = Wall(b, "north", "east", (0, 16))
    put(27, 3, 16, B.dr("spruce", "north", "lower")); put(27, 4, 16, B.dr("spruce", "north", "upper"))
    for i, z in enumerate(range(13, 16)):                           # ramp up to the little coop
        put(27, i, z, st("spruce", "north"))
    G.window_sill(b, Wall(b, "south", "east", (0, 20)), 26, 3)
    G.window_sill(b, Wall(b, "east", "south", (29, 0)), 18, 3)
    put(26, 0, 19, B.cauldron_water(3))
    put(28, 0, 18, B.barrel("north"))

    n.plinth = {"blocks": RAMPS["plinth"], "courses": 1, "ring": foot}
    return b


def scenery(b: Build) -> None:
    n = b.notes
    put, rng = b.put, b.rng
    road = G.road(b, SX)
    yard = S.noisy_rect(b, 11, 3, 21, 9, wobble=1)
    S.ground(b, yard - road, G.COBBLE)
    # the run: an irregular rounded outline, scratched dirt in the front half fading into grass at the back
    run = G.blob(b, 5, 10, 31, 31, cut=5, wob=2)
    edge = G.perimeter(run)
    scratch = Ramp(P("coarse_dirt"), [(P("podzol[snowy=false]"), .22, None), (P("dirt_path"), .2, None), (P("rooted_dirt"), .14, None)], seed=37)
    for (x, z) in sorted(run):
        t = (z - 10) / 21.0 + rng.random() * .25
        if (x, z) in G_PAD:
            continue
        if t < .6:
            put(x, -1, z, scratch.pick(x, 0, z))
        elif t < .78:
            put(x, -1, z, P("coarse_dirt") if rng.random() < .35 else P("grass_block[snowy=false]"))
    # path from the yard to the gate
    S.ground(b, {(x, z) for z in (10, 11) for x in range(14, 18)}, G.GRAVEL)
    S.ground(b, {(x, z) for x in range(10, 21) for z in range(15, 24)} | {(x, z) for x in range(24, 31) for z in range(15, 22)}, KERB)
    # fence: capped stone pillars every 4 with a spruce rail between; double gate between taller pillars
    gate_x = (15, 16)
    skip = {(x, 10) for x in range(14, 18)}
    for i, (x, z) in enumerate(sorted(edge, key=lambda c: (c[1], c[0]))):
        if (x, z) in skip:
            continue
        put(x, 0, z, "@spruce_fence")
    ring_sorted = sorted(edge, key=lambda c: (c[1], c[0]))
    cx0, cz0 = 18.0, 20.0
    import math
    kept = []
    for (x, z) in sorted(edge - skip, key=lambda c: math.atan2(c[1] - cz0, c[0] - cx0)):
        if all(math.hypot(x - kx, z - kz) >= 4 for kx, kz in kept) and abs(x - 15.5) + abs(z - 10) > 3:
            kept.append((x, z))
    for (x, z) in kept:
        G.gate_pillar(b, x, z, 2, lantern=False)
    for gx in (14, 17):
        G.gate_pillar(b, gx, 10, 3, lantern=(gx == 14))
    put(15, 0, 10, GATE_NS); put(16, 0, 10, GATE_NS)
    # berry-bush hedge outside the long north-west side
    for x in range(6, 13):
        if (x, 9) not in yard and b.get(x, 0, 9) is None:
            put(x, 0, 9, P("sweet_berry_bush[age=3]"))
            put(x, -1, 9, P("podzol[snowy=false]"))
    # run furniture
    for x in (21, 22, 23):                                           # feeder
        put(x, 0, 25, st("spruce", "south"))
    put(22, -1, 25, P("coarse_dirt"))
    put(8, 0, 17, B.cauldron_water(3)); put(9, 0, 17, B.cauldron_water(3))                 # two waterers
    put(8, -1, 18, P("cobblestone")); put(9, -1, 18, P("cobblestone"))
    for x in range(21, 24):                                          # dust bath, ringed with cobble slabs
        for z in range(13, 16):
            put(x, -1, z, P("packed_mud") if (x + z) % 3 else P("coarse_dirt"))
    for x in range(20, 25):
        for z in (12, 16):
            put(x, 0, z, sl("cobblestone", "bottom"))
    for z in (13, 14, 15):
        put(20, 0, z, sl("cobblestone", "bottom")); put(24, 0, z, sl("cobblestone", "bottom"))
    # lean-to shelter 5 x 3 on spruce posts
    for x in range(8, 13):
        for z in range(25, 28):
            put(x, 0 if False else -1, z, P("coarse_dirt"))
    for (x, z) in ((8, 25), (12, 25), (8, 27), (12, 27)):
        put(x, 0, z, "@spruce_fence"); put(x, 1, z, "@spruce_fence")
    for x in range(8, 13):
        put(x, 2, 25, st("spruce", "south")); put(x, 2, 26, sl("spruce", "bottom")); put(x, 2, 27, sl("spruce", "bottom"))
    put(10, 0, 27, B.hay()); put(11, 0, 27, B.hay()); put(11, 0, 26, B.hay())
    n.roof_cells |= {(x, 2, z) for x in range(8, 13) for z in (25, 26, 27)}
    # haystack and scarecrow in the grass half, a shade tree in the back
    for (x, z) in ((16, 29), (17, 29), (16, 30), (17, 30)):
        put(x, 0, z, B.hay())
    put(16, 1, 29, B.hay()); put(17, 1, 29, B.hay()); put(16, 1, 30, B.hay())
    S.scarecrow(b, 25, 27)
    S.tree(b, 24, 30, kind="oak", height=5, spread=2.4, y0=0)
    # outside the run: one orchard row on the east, a veg plot, meadow
    S.veg_plot(b, 34, 24, 4, 4, ["carrots", "potatoes", "wheat"])
    for (tx, tz) in ((36, 13), (36, 19)):
        S.tree(b, tx, tz, kind="oak", height=4, spread=2.1, y0=0)
    S.ground(b, road, G.GRAVEL)
    n.front_yard = {"rect": (11, 3, 21, 9), "corridor": (14, 16), "max_y": 1}
    n.ground_region = (-1, -1, SX, SZ)
    n.ramps = {k: list(v) for k, v in RAMPS.items()}
    n.body_box = (11, 16, 19, 22)
    n.wall_rows = (4, 6)
    n.pen = {"building": [(x, z) for x in range(11, 20) for z in range(16, 23)] + [(x, z) for x in range(25, 30) for z in range(16, 21)],
             "cells": sorted(run)}
    n.props = [("yard", "gate", True), ("run", "haystack", True), ("run", "shelter", False), ("run", "dust bath", False)]


G_PAD = {(x, z) for x in range(10, 21) for z in range(15, 24)} | {(x, z) for x in range(24, 31) for z in range(15, 22)}


def previews(g: dict, out: Path) -> list:
    shots = {
        "eye": ((15.5, 1.62, 5.5), (16, 4, 17)),
        "front": ((17, 7, -8), (17, 4, 18)),
        "ne": ((48, 15, -4), (20, 3, 18)),
        "nw": ((-10, 15, -4), (18, 3, 18)),
        "back": ((44, 14, 44), (18, 3, 18)),
        "run": ((20, 9, 40), (16, 2, 20)),
        "above": ("iso", 215, 52),
    }
    return place_views.place_previews("chicken_farm", g, out, (0, 0, SX - 1, SZ - 1), shots)


def main() -> int:
    b = build()
    scenery(b)
    G.finish("chicken_farm", b, points=POINTS, signs=SIGNS, size=SIZE, ground=GROUND, hero=False, previews=previews,
             size_range=(900, 1500), trapdoor_budget=8)
    return 0


if __name__ == "__main__":
    sys.exit(main())
