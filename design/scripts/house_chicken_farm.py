"""The `chicken_farm` place: a stone coop with coped gable parapets, a stepped buttress, a steep clay-tile roof, dark shutters,
nest boxes and a pop hole with ramp; a square dovecote with a rat ledge and a louvre cupola; a run fenced with stone pillars and
hurdles; haystack, orchard tree and a terraced backdrop with retaining walls.

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
from blocks import lg, plain as P, sl, st  # noqa: E402
from build import Build, Ramp, Wall, door, window  # noqa: E402

SX, SY, SZ, GROUND = 40, 16, 34, 4
SIZE = (SX, SY + GROUND, SZ)
CX0, CX1, CZ0, CZ1 = 10, 20, 13, 19          # coop walls
DX0, DX1, DZ0, DZ1 = 25, 29, 13, 17          # dovecote walls
Y_EAVE = 4
POINTS = {
    "viewpoint": {"at": [15, 0, 5], "yaw": -10, "pitch": 3},
    "yard": {"from": [12, 3], "to": [22, 9]},
    "hologram": [16, 1, 7],
    "panel": {"at": [19, 0, 7], "yaw": 180},
    "ripe": [12, 0, 8],
}
SIGNS = [{"at": [14, 2, 11], "lines": ["", "Chicken Farm", "", ""]}]

WALL = Ramp(P("stone"), [(P("andesite"), .22, None), (P("stone_bricks"), .16, None)], seed=3)
PLINTH = Ramp(P("cobblestone"), [(P("mossy_cobblestone"), .27, "low"), (P("cobbled_deepslate"), .12, None)], seed=4)
ROOF = Ramp("brick", [("mud_brick", .24, None), ("resin_brick", .05, None)], seed=8, height=8, y0=4)
RUN = Ramp(P("coarse_dirt"), [(P("podzol[snowy=false]"), .25, None), (P("mud"), .12, None), (P("dirt_path"), .12, None)], seed=37)
RAMPS = {
    "wall": ["stone", "andesite", "stone_bricks"],
    "plinth": ["cobblestone", "mossy_cobblestone", "cobbled_deepslate"],
    "roof": ["brick_stairs", "mud_brick_stairs", "resin_brick_stairs", "dark_oak_stairs", "bricks"],
}
GATE_W = "minecraft:spruce_fence_gate[facing=east,in_wall=false,open=false,powered=false]"


def build() -> Build:
    b = Build((SX, SY, SZ), ground=GROUND)
    n = b.notes
    put, fill = b.put, b.fill
    pick = lambda x, y, z: ROOF.pick(x, y, z)                                      # noqa: E731
    heights = {(x, z): -1 for x in range(SX) for z in range(SZ)}
    K.terrace(heights, (32, 18, 39, 33), 0)
    K.terrace(heights, (36, 24, 39, 33), 1)
    G.lawn(b, SX, SZ, heights)
    K.retaining_wall(b, 31, 18, 31, 33, -1, 0, ramp=PLINTH, coping="stone_brick")
    K.retaining_wall(b, 35, 24, 35, 33, -1, 1, ramp=PLINTH, coping="stone_brick")
    K.retaining_wall(b, 32, 18, 39, 18, -1, 0, ramp=PLINTH, coping="stone_brick")

    # ================= coop =================
    ring = [(x, z) for x in range(CX0, CX1 + 1) for z in range(CZ0, CZ1 + 1) if x in (CX0, CX1) or z in (CZ0, CZ1)]
    for (x, z) in ring:
        put(x, 0, z, PLINTH.pick(x, 0, z))
        for y in range(1, 4):
            put(x, y, z, WALL.pick(x, y, z))
        put(x, 4, z, WALL.pick(x, 4, z)) if x in (CX0, CX1) else put(x, 4, z, B.stripped("dark_oak", "x"))
    for x in range(CX0 + 1, CX1):
        for z in range(CZ0 + 1, CZ1):
            put(x, 0, z, PLINTH.pick(x, 0, z))
    roof = K.hipped_roof(b, (CX0 + 1, CZ0 - 1, CX1 - 1, CZ1 + 1), Y_EAVE, {"N": 0, "S": 0, "W": None, "E": None},
                         field="brick", eave="dark_oak", rake="dark_oak", ridge=P("stone_bricks"), cap=sl("stone_brick"),
                         name="coop", form="gable", ramp_pick=pick, riser=P("bricks"))
    WWg = Wall(b, "west", "south", (CX0, 0))
    WEg = Wall(b, "east", "south", (CX1, 0))
    for wl, xin in ((WWg, CX0 + 1), (WEg, CX1 - 1)):
        prof = lambda u, xin=xin: roof["surface"][(xin, u)]                        # noqa: E731
        K.coped_gable(b, wl, CZ0, CZ1, 5, lambda u, prof=prof: prof(u) + 1, cap="stone_brick", body=None)
        for u in range(CZ0, CZ1 + 1):
            for y in range(5, prof(u) + 1):
                wl.put(u, y, 0, WALL.pick(*wl.xyz(u, y, 0)))
    K.vent_slits(WWg, 15, 6, 2)
    K.vent_slits(WEg, 15, 6, 2)
    n.volumes.append({"name": "coop", "footprint": (CX0, CZ0, CX1, CZ1), "top": roof["ridge_y"] + 1})
    K.nest_box_row(WWg, 15, 1, 4)
    K.buttress(b, WWg, CZ0, 3, ramp=WALL)
    # pilasters (depth rhythm) on the walls
    for x in (CX0, 14, 19, CX1):
        for y in range(0, 4):
            put(x, y, CZ0 - 1, PLINTH.pick(x, y, CZ0 - 1) if y == 0 else WALL.pick(x, y, CZ0 - 1))
    for x in (CX0, 14, 17, CX1):
        for y in range(0, 4):
            put(x, y, CZ1 + 1, PLINTH.pick(x, y, CZ1 + 1) if y == 0 else WALL.pick(x, y, CZ1 + 1))
    for z in (15, 16, 17):
        for y in range(0, 4):
            put(CX1 + 1, y, z, PLINTH.pick(CX1 + 1, y, z) if y == 0 else WALL.pick(CX1 + 1, y, z))
    WN = Wall(b, "north", "east", (0, CZ0))
    WS = Wall(b, "south", "east", (0, CZ1))
    door(WN, 12, 1, mat="spruce", hood=True)
    put(12, 0, CZ0 - 1, st("stone_brick", "south"))
    window(WN, 16, 2, width=2, height=1, shutters="dark_oak", box="potted_cornflower", sill="spruce")
    door(WS, 12, 1, mat="spruce", hood=False)
    window(WS, 18, 2, width=1, height=1, shutters="dark_oak", box="potted_poppy", sill="spruce")
    put(14, 2, 11, B.wall_sign("spruce", "north"))
    WS.put(15, 1, 0, "@iron_bars")                                                  # pop hole with its ramp
    WS.put(15, 0, 1, WS.stair("spruce", "bottom", "in"))
    WS.put(15, 0, 2, sl("spruce", "bottom"))

    # ================= dovecote =================
    ring_d = [(x, z) for x in range(DX0, DX1 + 1) for z in range(DZ0, DZ1 + 1) if x in (DX0, DX1) or z in (DZ0, DZ1)]
    for (x, z) in ring_d:
        put(x, 0, z, PLINTH.pick(x, 0, z))
        for y in range(1, 7):
            put(x, y, z, WALL.pick(x, y, z))
        put(x, 7, z, B.stripped("dark_oak", "x" if z in (DZ0, DZ1) else "z"))
    for x in range(DX0 - 1, DX1 + 2):                                               # rat ledge
        for z in range(DZ0 - 1, DZ1 + 2):
            if (x in (DX0 - 1, DX1 + 1) or z in (DZ0 - 1, DZ1 + 1)) and (x, z) != (27, DZ0 - 1):
                put(x, 3, z, sl("stone_brick", "top"))
    WDN = Wall(b, "north", "east", (0, DZ0))
    WDS = Wall(b, "south", "east", (0, DZ1))
    WDW = Wall(b, "west", "south", (DX0, 0))
    WDE = Wall(b, "east", "south", (DX1, 0))
    door(WDN, 27, 1, mat="spruce", hood=False)
    put(27, 0, DZ0 - 1, st("stone_brick", "south"))
    for wl, us in ((WDN, (25, 29)), (WDS, (25, 27, 29)), (WDW, (14, 16)), (WDE, (14, 16))):
        for u in us:
            for y in (4, 5):
                wl.put(u, y, 1, wl.trapdoor("dark_oak", "bottom", True))
    dr = K.pyramid_roof(b, (DX0 - 1, DZ0 - 1, DX1 + 1, DZ1 + 1), 7, field="brick", eave="dark_oak", rake="dark_oak",
                        ridge=P("stone_bricks"), cap=None, name="dovecote", ramp_pick=pick, riser=P("bricks"))
    cx, cz, cy = 27, 15, dr["ridge_y"] + 1
    put(cx, cy, cz, "@dark_oak_fence")
    put(cx, cy + 1, cz, B.lantern(False))
    for dx, dz, f in ((-1, 0, "west"), (1, 0, "east"), (0, -1, "north"), (0, 1, "south")):
        put(cx + dx, cy, cz + dz, f"minecraft:spruce_trapdoor[facing={f},half=bottom,open=true,powered=false,waterlogged=false]")
        n.vents.add((cx + dx, cy, cz + dz))
    n.volumes.append({"name": "dovecote", "footprint": (DX0, DZ0, DX1, DZ1), "top": cy + 2})
    n.roof_heights.add(cy)
    return b


def scenery(b: Build) -> None:
    n = b.notes
    put, rng = b.put, b.rng
    road = G.road(b, SX)
    yard = S.noisy_rect(b, 12, 3, 22, 9, wobble=1)
    S.ground(b, yard - road, Ramp(P("coarse_dirt"), [(P("dirt_path"), .3, None), (P("gravel"), .2, None)], seed=44))
    S.ground(b, {(x, z) for z in range(10, 13) for x in range(11 + z % 2, 14 + z % 2)}, G.GRAVEL)
    S.flower_bed(b, {(x, z) for x in range(15, 21) for z in range(11, 13)}, density=.6)
    for x in range(18, 23):
        put(x, 0, 10, "@stone_brick_wall" if x % 2 else "@mossy_stone_brick_wall")
    put(18, 1, 10, B.lantern(False)); put(22, 1, 10, B.lantern(False))
    S.apron(b, 17, 10, 23, 10, G.COBBLE, force=True)
    S.lamp_post(b, 11, 3, 3)
    S.lamp_post(b, 23, 3, 3)
    put(10, 0, 8, B.hay()); put(10, 0, 9, B.hay()); put(10, 1, 9, B.hay()); put(10, 0, 7, B.barrel())
    # run: stone pillars + spruce fence, hurdle divider, trough, feed
    x0, x1, z0, z1 = 9, 23, 22, 30
    S.ground(b, {(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)}, RUN)
    K.stone_pillar_fence(b, [(x, z0) for x in range(x0, x1 + 1)], every=4)
    K.stone_pillar_fence(b, [(x, z1) for x in range(x0, x1 + 1)], every=4)
    K.stone_pillar_fence(b, [(x0, z) for z in range(z0 + 1, z1)], every=4)
    K.stone_pillar_fence(b, [(x1, z) for z in range(z0 + 1, z1)], every=4)
    put(x0, 0, 26, GATE_W)
    put(12, 0, z0, "minecraft:spruce_fence_gate[facing=north,in_wall=false,open=false,powered=false]")
    K.hurdle_fence(b, [(x, 26) for x in range(13, 21)])
    G.trough(b, 18, 28, "x", 3)
    put(11, 0, 28, B.hay()); put(12, 0, 28, B.hay()); put(11, 0, 29, B.hay())
    S.scarecrow(b, 21, 24)
    # terraces: haystack, apple tree, vine-less hedges
    G.hay_rick(b, 32, 26, w=3, d=4, layers=2, axis="z")
    S.tree(b, 34, 22, kind="oak", height=4, spread=2.0, y0=1)
    G.hedge(b, [(1, 6, 32), (17, 22, 32)])
    taken = lambda x, z: (11 <= x <= 23 and z <= 9)                                 # noqa: E731
    G.meadow(b, SX, SZ, taken, seed=91, thresh=.7)
    n.front_yard = {"rect": (12, 3, 22, 9), "corridor": (11, 13), "max_y": 1}
    n.ground_region = (-1, -1, SX, SZ)
    n.ramps = {k: list(v) for k, v in RAMPS.items()}
    n.plinth = {"blocks": RAMPS["plinth"], "courses": 1,
                "ring": [(x, z) for x in range(CX0, CX1 + 1) for z in range(CZ0, CZ1 + 1) if x in (CX0, CX1) or z in (CZ0, CZ1)]}
    n.body_box = (CX0, CZ0, CX1, CZ1)
    n.wall_rows = (1, 3)


def previews(g: dict, out: Path) -> list:
    shots = {
        "eye": ((15.5, 1.62, 5.5), (19, 4, 15)),
        "front": ((19, 6, -8), (19, 4, 16)),
        "ne": ((52, 15, -4), (20, 3, 17)),
        "nw": ((-10, 15, -4), (18, 3, 17)),
        "back": ((46, 14, 46), (18, 3, 17)),
        "dove": ((30, 4, 6), (27, 5, 15)),
        "run": ((6, 8, 36), (16, 1, 25)),
        "above": ("iso", 215, 52),
    }
    return place_views.place_previews("chicken_farm", g, out, (0, 0, SX - 1, SZ - 1), shots)


def main() -> int:
    b = build()
    scenery(b)
    G.finish("chicken_farm", b, points=POINTS, signs=SIGNS, size=SIZE, ground=GROUND, hero=False, previews=previews)
    return 0


if __name__ == "__main__":
    sys.exit(main())
