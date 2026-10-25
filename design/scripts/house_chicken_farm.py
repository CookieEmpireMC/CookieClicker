"""The `chicken_farm` place: one farmyard cluster. A stone coop with coped gable parapets, a stepped buttress, a steep clay-tile roof,
dark shutters, nest boxes and a pop hole with ramp; a square dovecote with a rat ledge and a louvre cupola at the corner of the
run, linked to the coop by a low wall; a run that wraps around the coop's south side, fenced with stone pillars and hurdles;
a bending path from the yard; a terraced orchard backdrop with retaining walls that touch the dovecote.

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
CX0, CX1, CZ0, CZ1 = 8, 18, 14, 20           # coop walls
DX0, DX1, DZ0, DZ1 = 22, 26, 18, 22          # dovecote walls
Y_EAVE = 4
POINTS = {
    "viewpoint": {"at": [15, 0, 5], "yaw": -10, "pitch": 3},
    "yard": {"from": [12, 3], "to": [22, 9]},
    "hologram": [16, 1, 7],
    "panel": {"at": [19, 0, 7], "yaw": 180},
    "ripe": [12, 0, 8],
}
SIGNS = [{"at": [12, 2, 12], "lines": ["", "Chicken Farm", "", ""]}]

WALL = Ramp(P("stone"), [(P("andesite"), .22, None), (P("stone_bricks"), .16, None)], seed=3)
PLINTH = Ramp(P("cobblestone"), [(P("mossy_cobblestone"), .27, "low"), (P("cobbled_deepslate"), .12, None)], seed=4)
RUN = Ramp(P("coarse_dirt"), [(P("podzol[snowy=false]"), .25, None), (P("mud"), .12, None), (P("dirt_path"), .12, None)], seed=37)
RAMPS = {
    "wall": ["stone", "andesite", "stone_bricks"],
    "plinth": ["cobblestone", "mossy_cobblestone", "cobbled_deepslate"],
    "roof": ["brick_stairs", "deepslate_tile_stairs", "bricks", "deepslate_tiles"],
}
GATE_E = "minecraft:spruce_fence_gate[facing=east,in_wall=false,open=false,powered=false]"


def build() -> Build:
    b = Build((SX, SY, SZ), ground=GROUND)
    n = b.notes
    put, fill = b.put, b.fill
    heights = {(x, z): -1 for x in range(SX) for z in range(SZ)}
    K.terrace(heights, (29, 12, 39, 33), 0)
    K.terrace(heights, (33, 22, 39, 33), 1)
    G.lawn(b, SX, SZ, heights)
    K.retaining_wall(b, 28, 12, 28, 33, -1, 0, ramp=PLINTH, coping="stone_brick")
    K.retaining_wall(b, 32, 22, 32, 33, -1, 1, ramp=PLINTH, coping="stone_brick")
    K.retaining_wall(b, 29, 12, 39, 12, -1, 0, ramp=PLINTH, coping="stone_brick")

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
                         field="brick", eave="deepslate_tile", rake="deepslate_tile", ridge=P("deepslate_tiles"),
                         cap=sl("deepslate_tile"), name="coop", form="gable", riser=P("bricks"))
    WWg = Wall(b, "west", "south", (CX0, 0))
    WEg = Wall(b, "east", "south", (CX1, 0))
    for wl, xin in ((WWg, CX0 + 1), (WEg, CX1 - 1)):
        prof = lambda u, xin=xin: roof["surface"][(xin, u)]                        # noqa: E731
        K.coped_gable(b, wl, CZ0, CZ1, 5, lambda u, prof=prof: prof(u) + 1, cap="stone_brick", body=None)
        for u in range(CZ0, CZ1 + 1):
            for y in range(5, prof(u) + 1):
                wl.put(u, y, 0, WALL.pick(*wl.xyz(u, y, 0)))
    K.vent_slits(WWg, 16, 6, 2)
    K.vent_slits(WEg, 16, 6, 2)
    n.volumes.append({"name": "coop", "footprint": (CX0, CZ0, CX1, CZ1), "top": roof["ridge_y"] + 1})
    K.nest_box_row(WWg, 16, 1, 4)
    K.buttress(b, WWg, CZ0, 3, ramp=WALL)
    for x in (CX0, 12, 17, CX1):                                                   # pilasters (depth rhythm)
        for y in range(0, 4):
            put(x, y, CZ0 - 1, PLINTH.pick(x, y, CZ0 - 1) if y == 0 else WALL.pick(x, y, CZ0 - 1))
    for x in (CX0, 12, 14, CX1):
        for y in range(0, 4):
            put(x, y, CZ1 + 1, PLINTH.pick(x, y, CZ1 + 1) if y == 0 else WALL.pick(x, y, CZ1 + 1))
    for z in (16, 17, 18):
        for y in range(0, 4):
            put(CX1 + 1, y, z, PLINTH.pick(CX1 + 1, y, z) if y == 0 else WALL.pick(CX1 + 1, y, z))
    WN = Wall(b, "north", "east", (0, CZ0))
    WS = Wall(b, "south", "east", (0, CZ1))
    door(WN, 10, 1, mat="spruce", hood=False)
    put(10, 0, CZ0 - 1, st("stone_brick", "south"))
    window(WN, 14, 2, width=2, height=1, shutters="dark_oak", box=None, sill="spruce")
    put(12, 2, 12, B.wall_sign("spruce", "north"))
    door(WS, 10, 1, mat="spruce", hood=False)
    window(WS, 16, 2, width=1, height=1, shutters="dark_oak", box=None, sill="spruce")
    WS.put(13, 1, 0, "@iron_bars")                                                  # pop hole with its ramp
    WS.put(13, 0, 1, WS.stair("spruce", "bottom", "in"))
    WS.put(13, 0, 2, sl("spruce", "bottom"))

    # ================= dovecote at the corner of the run =================
    ring_d = [(x, z) for x in range(DX0, DX1 + 1) for z in range(DZ0, DZ1 + 1) if x in (DX0, DX1) or z in (DZ0, DZ1)]
    for (x, z) in ring_d:
        put(x, 0, z, PLINTH.pick(x, 0, z))
        for y in range(1, 7):
            put(x, y, z, WALL.pick(x, y, z))
        put(x, 7, z, B.stripped("dark_oak", "x" if z in (DZ0, DZ1) else "z"))
    for x in range(DX0 - 1, DX1 + 2):                                               # rat ledge
        for z in range(DZ0 - 1, DZ1 + 2):
            if (x in (DX0 - 1, DX1 + 1) or z in (DZ0 - 1, DZ1 + 1)) and (x, z) != (24, DZ0 - 1):
                put(x, 3, z, sl("stone_brick", "top"))
    WDN = Wall(b, "north", "east", (0, DZ0))
    WDS = Wall(b, "south", "east", (0, DZ1))
    WDW = Wall(b, "west", "south", (DX0, 0))
    WDE = Wall(b, "east", "south", (DX1, 0))
    door(WDN, 24, 1, mat="spruce", hood=False)
    put(24, 0, DZ0 - 1, st("stone_brick", "south"))
    for wl, us in ((WDN, (22, 26)), (WDS, (22, 24, 26)), (WDW, (19, 21)), (WDE, (19, 21))):
        for u in us:
            for y in (4, 5):
                wl.put(u, y, 1, wl.trapdoor("dark_oak", "bottom", True))
    dr = K.pyramid_roof(b, (DX0 - 1, DZ0 - 1, DX1 + 1, DZ1 + 1), 7, field="brick", eave="deepslate_tile", rake="deepslate_tile",
                        ridge=P("deepslate_tiles"), cap=None, name="dovecote", riser=P("bricks"))
    cx, cz, cy = 24, 20, dr["ridge_y"] + 1
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
    put = b.put
    road = G.road(b, SX)
    yard = S.noisy_rect(b, 12, 3, 22, 9, wobble=1)
    S.ground(b, yard - road, Ramp(P("coarse_dirt"), [(P("podzol[snowy=false]"), .1, None)], seed=44))
    # a path that bends from the yard to the coop door, on to the run gate and the dovecote
    path = {(x, z) for z in (10, 11) for x in range(14, 17)} | {(x, z) for x in range(9, 17) for z in (12, 13)}
    path |= {(x, 18 + dz) for x in range(19, 22) for dz in (0, 1)} | {(19, z) for z in range(14, 18)}
    S.ground(b, path - {(x, z) for x in range(CX0, CX1 + 1) for z in range(CZ0, CZ1 + 1)}, G.GRAVEL)
    S.flower_bed(b, {(x, z) for x in range(17, 22) for z in (11, 12)}, density=.6)
    for x in range(18, 23):
        put(x, 0, 10, "@stone_brick_wall" if x % 2 else "@mossy_stone_brick_wall")
    put(18, 1, 10, B.lantern(False)); put(22, 1, 10, B.lantern(False))
    S.apron(b, 17, 10, 23, 10, G.COBBLE, force=True)
    S.lamp_post(b, 11, 3, 3)
    S.lamp_post(b, 23, 3, 3)
    put(10, 0, 8, B.hay()); put(10, 0, 9, B.hay()); put(10, 1, 9, B.hay())
    # run wraps the coop's south side: stone pillars + spruce fence, hurdle divider, trough, feed
    x0, x1, z0, z1 = 6, 20, 22, 30
    S.ground(b, {(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)}, RUN)
    K.stone_pillar_fence(b, [(x, z1) for x in range(x0, x1 + 1)], every=4)
    K.stone_pillar_fence(b, [(x0, z) for z in range(21, z1)], every=4)
    K.stone_pillar_fence(b, [(x1, z) for z in range(21, z1)], every=4)
    for x in (7, 19):
        put(x, 0, 21, "@spruce_fence")
    put(x0, 0, 26, GATE_E)
    K.hurdle_fence(b, [(x, 26) for x in range(9, 17)])
    G.trough(b, 14, 28, "x", 3)
    put(8, 0, 28, B.hay()); put(9, 0, 28, B.hay()); put(8, 0, 29, B.hay())
    S.scarecrow(b, 17, 24)
    # low wall links the dovecote ledge to the run's east fence
    G.drystone(b, [(21, 24), (21, 25)], high=1)
    G.drystone(b, [(20, 17), (21, 17)], high=1)
    # terraced orchard touching the dovecote; haystack, trees overlapping the edge
    G.hay_rick(b, 33, 14, w=3, d=4, layers=2, axis="z")
    S.tree(b, 31, 19, kind="oak", height=4, spread=2.0, y0=1)
    S.tree(b, 36, 27, kind="oak", height=4, spread=2.0, y0=2)
    G.hedge(b, [(1, 6, 32), (9, 14, 32), ("z", 12, 17, 1)])
    S.ground(b, road, G.GRAVEL)
    n.front_yard = {"rect": (12, 3, 22, 9), "corridor": (14, 16), "max_y": 1}
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
        "dove": ((34, 5, 8), (24, 5, 19)),
        "run": ((4, 8, 38), (14, 1, 25)),
        "above": ("iso", 215, 52),
    }
    return place_views.place_previews("chicken_farm", g, out, (0, 0, SX - 1, SZ - 1), shots)


def main() -> int:
    b = build()
    scenery(b)
    G.finish("chicken_farm", b, points=POINTS, signs=SIGNS, size=SIZE, ground=GROUND, hero=False, previews=previews,
             size_range=(600, 1400))
    return 0


if __name__ == "__main__":
    sys.exit(main())
