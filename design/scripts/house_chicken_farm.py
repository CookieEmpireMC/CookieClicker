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
    # gable pop-hole + louvre on the west gable
    WWg = Wall(b, "west", "south", (12, 0))
    for dz in (-1, 0, 1):
    # door on the north (yard) wall
    WN = Wall(b, "north", "east", (0, 17))
    door(WN, 15, 4, mat="spruce", frame=TRIM, hood=False)
    put(12, 5, 16, B.wall_sign("spruce", "north"))
    # windows
    pass
    window(Wall(b, "south", "east", (0, 21)), 16, 5, width=1, height=1, shutters=None, sill="spruce")
    window(Wall(b, "west", "south", (12, 0)), 20, 5, width=1, height=1, shutters="spruce", sill="spruce")
    window(Wall(b, "east", "south", (18, 0)), 20, 5, width=1, height=1, shutters="spruce", sill="spruce")
    # ramp from the yard gate to the deck, rails
    for i, z in enumerate(range(12, 16)):
        put(15, i, z, st("spruce", "north"))
    for z in (12, 13, 14, 15):
        for x in (14, 16):
            y = z - 12
            put(x, y + 1, z, "@spruce_fence")
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
    for dx, f in ((-1, "west"), (1, "east")):
        put(cx + dx, ry + 1, cz, B.td("spruce", f, "bottom", True))
        n.vents.add((cx + dx, ry + 1, cz))
    put(cx, ry + 2, cz, st("dark_oak", "east"))
    put(cx, ry + 3, cz, "@dark_oak_fence")
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
    put(27, fy, 18, "@dark_oak_fence")
    put(27, fy + 1, 18, B.lantern(False))
    n.roof_heights.add(fy)
    n.volumes.append({"name": "coop_b", "footprint": (25, 16, 29, 20), "top": roofb["ridge_y"] + 1})
    WNb = Wall(b, "north", "east", (0, 16))
    put(27, 3, 16, B.dr("spruce", "north", "lower")); put(27, 4, 16, B.dr("spruce", "north", "upper"))
    for i, z in enumerate(range(13, 16)):                           # ramp up to the little coop
        put(27, i, z, st("spruce", "north"))
    window(Wall(b, "south", "east", (0, 20)), 26, 3, width=1, height=1, shutters=None, sill="spruce")
    window(Wall(b, "east", "south", (29, 0)), 18, 3, width=1, height=1, shutters="spruce", sill="spruce")
    # nest boxes on b's west wall
    K.nest_box_row(Wall(b, "west", "south", (25, 0)), 17, 3, 2)
    put(26, 0, 19, B.cauldron_water(3))
    put(28, 0, 18, B.barrel("north"))

    n.plinth = {"blocks": RAMPS["plinth"], "courses": 1, "ring": foot}
    return b


def scenery(b: Build) -> None:
    n = b.notes
    put, rng = b.put, b.rng
    road = G.road(b, SX)
    yard = S.noisy_rect(b, 11, 3, 21, 9, wobble=1)
    S.ground(b, yard - road, Ramp(P("coarse_dirt"), [(P("podzol[snowy=false]"), .12, None), (P("dirt_path"), .12, None)], seed=44))
    S.ground(b, {(x, z) for z in (10, 11) for x in range(14, 18)} | {(x, 11 + (x % 2)) for x in range(8, 24)}, G.GRAVEL)
    # pad under coop A and a cobble skirt
    S.ground(b, {(x, z) for x in range(10, 21) for z in range(15, 24)}, KERB)
    S.ground(b, {(x, z) for x in range(24, 31) for z in range(15, 22)}, KERB)
    # run fence: one fence around both coops, shared line at x=22
    def line(cells, lantern_at=()):
        for i, c in enumerate(cells):
            put(c[0], 0, c[1], "@spruce_fence")
    cap = lambda x, z: (put(x, 0, z, "@dark_oak_fence"), put(x, 1, z, B.lantern(False)))      # noqa: E731
    def edge(cells, post_every):
        for i, (x, z) in enumerate(cells):
            put(x, 0, z, "@spruce_fence")
        for (x, z) in cells[::post_every]:
    x0, x1, x2, z0, z1 = 7, 22, 32, 10, 26
    edge([(x, z0) for x in range(x0, x1 + 1)], 5)
    edge([(x, z1) for x in range(x0, x1 + 1)], 4)
    edge([(x0, z) for z in range(z0 + 1, z1)], 4)
    edge([(x1, z) for z in range(z0 + 1, z1)], 3)
    edge([(x, z0 + 2) for x in range(x1 + 1, x2 + 1)], 4)
    edge([(x2, z) for z in range(z0 + 3, z1 - 1)], 4)
    edge([(x, z1 - 2) for x in range(x1 + 1, x2)], 4)
    for (x, z) in ((7, 10), (22, 10), (7, 26), (22, 26), (32, 12), (32, 24), (22, 12)):
        put(x, 0, z, lg(TRIM, "y")); put(x, 1, z, "@dark_oak_fence"); put(x, 2, z, B.lantern(False))
    put(15, 0, z0, GATE_NS)
    put(22, 0, 18, GATE_EW)
    put(x0, 0, 17, GATE_EW)
    # run ground, hay feed piles, trough, a few tufts
    S.ground(b, {(x, z) for x in range(x0 + 1, x1) for z in range(z0 + 1, z1)} - {(x, z) for x in range(10, 21) for z in range(15, 24)}, RUN)
    S.ground(b, {(x, z) for x in range(x1 + 1, x2) for z in range(z0 + 3, z1 - 1)} - {(x, z) for x in range(24, 31) for z in range(15, 22)}, RUN)
    put(9, 0, 14, B.hay()); put(9, 0, 15, B.hay()); put(10, 0, 14, sl("spruce", "bottom"))
    put(20, 0, 24, B.hay()); put(21, 0, 24, B.hay()); put(21, 0, 23, B.hay())
    put(30, 0, 24, B.hay())
    G.trough(b, 12, 25, "x", 4)
    for (x, z) in ((8, 20), (9, 22), (17, 25), (22 - 2, 12), (23, 25), (31, 14), (26, 23), (30, 22)):
        if (x, 0, z) not in b.g:
            put(x, 0, z, P("short_grass"))
    # orchard overlapping the run edge, hedge, haystack outside
    S.tree(b, 34, 18, kind="oak", height=5, spread=2.4, y0=0)
    S.tree(b, 3, 20, kind="oak", height=4, spread=2.2, y0=0)
    S.veg_plot(b, 25, 28, 6, 4, ["carrots", "potatoes", "wheat"])
    for (tx, tz) in ((36, 12), (36, 24), (31, 30), (6, 30), (2, 12)):
        S.tree(b, tx, tz, kind="oak", height=4, spread=2.1, y0=0)
    S.woodpile(b, 3, 24, axis="z", length=4)
    G.hedge(b, [(1, 6, 30), (22, 28, 30), ("z", 25, 30, 38)])
    G.meadow(b, SX, SZ, lambda x, z: 10 <= x <= 22 and z <= 12)
    S.ground(b, road, G.GRAVEL)
    n.front_yard = {"rect": (11, 3, 21, 9), "corridor": (14, 16), "max_y": 1}
    n.ground_region = (-1, -1, SX, SZ)
    n.ramps = {k: list(v) for k, v in RAMPS.items()}
    n.body_box = (11, 16, 19, 22)
    n.wall_rows = (4, 6)


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
             size_range=(900, 1400))
    return 0


if __name__ == "__main__":
    sys.exit(main())
