"""Kits for the countryside places (docs/research/farm-buildings.md sections 9 and 10b), on top of build.py.

Every kit is a plain function over a `Build` (grid + lint notes), takes its materials as arguments, and is deterministic:
variation comes from the Build's seeded rng or from explicit seeds. Status per kit is in KITS at the bottom
("done" = used by a shipped place and tested, "simple" = tested, small, "stub" = raises NotImplementedError, test skipped).

Axes as everywhere: x west -> east, y up, z north -> south. Roof kits work in the rectangle (x0, z0, x1, z1) of the roof's
outermost cells (overhang included); stairs face the HIGH side (the ridge), like the game.
"""
from __future__ import annotations

import math

import blocks as B
from blocks import AIR, DIRS, OPP, lg, plain as P, sl, st, stripped, td
from build import Build, Ramp, Wall

COMPASS = {"N": "north", "S": "south", "W": "west", "E": "east"}
PITCH_STEEP = (0, 1, 3, 4, 6, 7, 9, 10, 12, 13, 15, 16)      # y rise per level, about 56 degrees (1.5 per step)
PITCH_45 = tuple(range(0, 16))


# --- roofs ---------------------------------------------------------------------------------------------------------------
def roof_level(rect: tuple, ks: dict, x: int, z: int) -> tuple[int, str]:
    """(level, side) of a roof cell. ks maps a side (N, S, W, E) to None (that side does not slope = a gable end), 0
    (slopes to its edge = eave or full hip) or k > 0 (half-hip: the wall rises k levels before the hip begins)."""
    x0, z0, x1, z1 = rect
    d = {"N": z - z0, "S": z1 - z, "W": x - x0, "E": x1 - x}
    best, side = 10 ** 6, "N"
    for s in ("N", "S", "W", "E"):
        k = ks.get(s)
        if k is not None and d[s] + k < best:
            best, side = d[s] + k, s
    return best, side


def hipped_roof(b: Build, rect: tuple, y_eave: int, ks: dict, *, field: str, eave: str, rake: str, ridge: str,
                cap: str | None, pitch=PITCH_STEEP, name: str = "roof", form: str = "hip", ramp_pick=None,
                riser: str | None = None, overhang: int = 1) -> dict:
    """One solver for gable, half-hip and hip roofs: level = min over sloping sides of (distance to the edge + k).
    Stairs of `field` (or ramp_pick(x, y, z)); eave rows in `eave`, gable-end rake rows in `rake`; the highest level is
    the ridge (a `ridge` block with a `cap` slab on top). Steep pitches (default 1.5 per step) fill the 2-block risers
    with a full block of `riser` (the field block id). Returns {'surface': {(x, z): y of the top roof block},
    'ridge_y', 'lmax', 'rect', 'y': pitch table, 'cells': set}."""
    x0, z0, x1, z1 = rect
    lv = {(x, z): roof_level(rect, ks, x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)}
    lmax = max(v[0] for v in lv.values())
    assert lmax < len(pitch), "pitch table too short"
    surface, cells, eave_c, field_c = {}, set(), [], []
    riser = riser or f"minecraft:{field}_planks"
    for (x, z), (L, side) in sorted(lv.items()):
        is_ridge = L == lmax
        y = y_eave + pitch[max(L - 1, 0) if is_ridge else L]
        if is_ridge:
            b.put(x, y, z, ridge)
            if cap:
                b.put(x, y + 1, z, cap)
            surface[(x, z)] = y + (1 if cap else 0)
            cells.add((x, y, z))
            continue
        d = {"N": z - z0, "S": z1 - z, "W": x - x0, "E": x1 - x}
        is_rake = any(ks[s] is None and d[s] == 0 for s in ks)
        is_eave = any(ks[s] == 0 and d[s] == 0 for s in ks)
        mat = field
        if ramp_pick:
            mat = ramp_pick(x, y, z)
        if is_eave:
            mat = eave
        if is_rake:
            mat = rake
        b.put(x, y, z, st(mat, OPP[COMPASS[side]]))
        if L > 0 and pitch[L] - pitch[L - 1] == 2:
            b.put(x, y - 1, z, riser)
        surface[(x, z)] = y
        cells.add((x, y, z))
        (eave_c if is_eave or is_rake else field_c).append((x, y, z))
    b.notes.roof_cells |= cells
    b.notes.roofs.append({"name": name, "eave": eave_c, "field": field_c, "overhang": overhang})
    ridge_y = y_eave + pitch[max(lmax - 1, 0)]
    b.notes.roof_info.append({"name": name, "form": form, "rect": rect, "y_eave": y_eave, "ridge_y": ridge_y + 1,
                              "cells": cells})
    b.notes.roof_heights |= {y_eave, ridge_y + 1}
    for s, k in ks.items():
        if k is None:
            b.notes.gables.append({"name": f"{name}:{s}", "side": s, "rect": rect, "finish": "bargeboard",
                                   "rake": [c for c in cells if _on_side(c, rect, s)], "field": field,
                                   "rake_mat": rake})
    return {"surface": surface, "ridge_y": ridge_y, "lmax": lmax, "rect": rect, "y": pitch, "cells": cells,
            "levels": {p: v[0] for p, v in lv.items()}}


def _on_side(c: tuple, rect: tuple, s: str) -> bool:
    x0, z0, x1, z1 = rect
    return {"N": c[2] == z0, "S": c[2] == z1, "W": c[0] == x0, "E": c[0] == x1}[s]


def fill_gable_wall(b: Build, roof: dict, cells: list, y_from: int, pick, *, side_plane: bool = True) -> int:
    """Wall infill under a roof at the given wall-plane cells [(x, z)]: from y_from up to the roof's stair row - 1."""
    n = 0
    for (x, z) in cells:
        top = roof["surface"].get((x, z))
        if top is None:
            continue
        for y in range(y_from, top):
            if b.get(x, y, z) is None:
                b.put(x, y, z, pick(x, y, z))
                n += 1
    return n


def half_hip_roof(b: Build, rect: tuple, y_eave: int, *, end: str, k: int = 3, axis: str | None = None, **kw) -> dict:
    """A gable roof whose `end` side (N, S, W or E) is a half-hip with its wall rising k levels first. `axis` is the
    ridge direction ('x' or 'z'); default: the longer side of the rectangle."""
    ridge_axis_x = (axis == "x") if axis else (rect[2] - rect[0]) >= (rect[3] - rect[1])
    sides = ("N", "S") if ridge_axis_x else ("W", "E")
    gables = ("W", "E") if ridge_axis_x else ("N", "S")
    ks = {sides[0]: 0, sides[1]: 0, gables[0]: None, gables[1]: None}
    assert end in gables
    ks[end] = k
    return hipped_roof(b, rect, y_eave, ks, form="half-hip", **kw)


def gable_roof_s(b: Build, rect: tuple, y_eave: int, axis: str | None = None, **kw) -> dict:
    ridge_axis_x = (axis == "x") if axis else (rect[2] - rect[0]) >= (rect[3] - rect[1])
    ks = {"N": 0, "S": 0, "W": None, "E": None} if ridge_axis_x else {"W": 0, "E": 0, "N": None, "S": None}
    return hipped_roof(b, rect, y_eave, ks, form="gable", **kw)


def pyramid_roof(b: Build, rect: tuple, y_eave: int, **kw) -> dict:
    """Four-sided hip over a (near) square: a pyramid. rect sides must be odd and equal for a single peak."""
    return hipped_roof(b, rect, y_eave, {"N": 0, "S": 0, "W": 0, "E": 0}, form="pyramid", **kw)


def roof_height(roof: dict, x: int, z: int) -> int | None:
    return roof["surface"].get((x, z))


# --- small roofs and ledges ----------------------------------------------------------------------------------------------
def pent_skirt(b: Build, wall: Wall, u0: int, u1: int, y: int, mat: str, *, depth: int = 1, edge: str | None = None) -> None:
    """A lean-to ledge ('skirt') of stairs on a wall between floors: `depth` rows of stairs rising to the wall,
    u0..u1 along the wall, bottom-left row at height y (the stair sits at y, its high side at the wall). Registers a
    roof height for L-F9."""
    for u in range(u0, u1 + 1):
        for w in range(1, depth + 1):
            wall.put(u, y + (depth - w), w, wall.stair(mat, "bottom", "in"))
            if w == depth and edge:
                wall.put(u, y + (depth - w), w, wall.stair(edge, "bottom", "in"))
    for u in range(u0, u1 + 1):
        for w in range(1, depth + 1):
            b.notes.roof_cells.add(wall.xyz(u, y + (depth - w), w))
    b.notes.roof_heights.add(y)
    b.notes.roof_info.append({"name": f"skirt@{y}", "form": "pent", "rect": None, "y_eave": y, "ridge_y": y + depth,
                              "cells": set()})
    b.notes.pents += 1


def dormer(b: Build, roof: dict, wall: Wall, uc: int, front_w: int, y0: int, *, wall_mat, trim: str, roof_mat: str,
           glass: str = "@glass_pane", hatch: bool = False, h: int = 3, depth: int = 12, name: str = "dormer",
           beam: bool = False, light: bool = True, box: str | None = "potted_poppy") -> None:
    """A 3-wide gabled dormer standing on the main roof. `wall` frame: u along the facade, w outward; the front plane is
    w = front_w, the first wall row y0. Cheek columns uc-1/uc+1 are wall_mat(x, y, z) for h rows, the middle column is a
    2-high window (or an open hatch: hatch=True) with a trim lintel, the roof is a stair on each cheek at y0 + h and a
    trim ridge log in the middle at y0 + h. It runs back from the front plane until the main roof surface reaches that
    height. beam=True adds the hoist: a trim beam two cells out of the ridge, a chain and a barrel on it."""
    yr = y0 + h
    done = set()
    for w in range(front_w, front_w - depth, -1):
        for du in (-1, 0, 1):
            x, _, z = wall.xyz(uc + du, y0, w)
            surf = roof["surface"].get((x, z))
            if du in done or (surf is not None and surf >= yr):
                done.add(du)                              # the main roof has risen to the dormer's roof: this column ends
                continue
            for yy in range(y0, yr + 2):
                if (x, yy, z) in b.g:
                    b.g.pop((x, yy, z))
                    b.notes.roof_cells.discard((x, yy, z))
            if du == 0:
                if w == front_w:
                    for yy in (y0, y0 + 1):
                        wall.put(uc, yy, w, AIR if hatch else glass)
                    for yy in range(y0 + 2, yr):
                        wall.put(uc, yy, w, wall.log(trim, "along", True))
                wall.put(uc, yr, w, wall.log(trim, "depth", True))
            else:
                for yy in range(y0, yr):
                    wall.put(uc + du, yy, w, wall_mat(x, yy, z))
                wall.put(uc + du, yr, w, wall.stair(roof_mat, "bottom", "along" if du < 0 else "back"))
                b.notes.roof_cells.add((x, yr, z))
    if light:
        x, _, z = wall.xyz(uc, y0, front_w - 2)
        if b.block(x, y0 - 1, z) == "air":
            b.put(x, y0 - 1, z, sl("spruce", "top"))
        b.put(x, y0, z, B.lantern(False))
        if hatch:
            b.put(*wall.xyz(uc, y0, front_w - 1), B.hay())
    if beam:
        for w in (front_w + 1, front_w + 2):
            wall.put(uc, yr, w, wall.log(trim, "depth", True))
        wall.put(uc, yr - 1, front_w + 2, B.chain("y"))
        wall.put(uc, yr - 2, front_w + 2, B.barrel())
    if box and not hatch:
        wall.put(uc, y0, front_w + 1, P(box))
    if hatch:
        b.notes.seal |= {wall.xyz(uc, y0, front_w), wall.xyz(uc, y0 + 1, front_w)}
    for r in b.notes.roofs:                                   # the dormer replaced some roof cells
        r["eave"] = [c for c in r["eave"] if b.block(*c) != "air"]
        r["field"] = [c for c in r["field"] if b.block(*c) != "air"]
    b.notes.breaks.append({"name": name, "cell": wall.xyz(uc, y0, front_w)})
    b.notes.roof_heights.add(yr)


def coped_gable(b: Build, wall: Wall, u0: int, u1: int, y_from: int, profile, cap: str = "stone_brick",
                body: str | None = None) -> None:
    """A parapet gable: the gable wall rises one block above the roof plane and is capped with slabs. `profile(u)` gives
    the height of the first roof block at column u in this wall's plane; the parapet stands at profile(u) (one above the
    roof stairs' top) in `body` (a block data string) and slab caps on top."""
    for u in range(u0, u1 + 1):
        y = profile(u)
        if body:
            wall.put(u, y, 0, body)
        wall.put(u, y + (1 if body else 0), 0, sl(cap, "bottom"))
    b.notes.gables.append({"name": "coped", "finish": "coping", "rake": [wall.xyz(u, profile(u), 0) for u in range(u0, u1 + 1)]})


# --- walls and parts --------------------------------------------------------------------------------------------------------
def staddle_stones(b: Build, cells: list, *, y_ground: int = -1, stem: str = "stone_brick", cap: str = "stone_brick",
                   base: str = "mossy_cobblestone") -> None:
    """Mushroom stones: a base stone (in the ground), a wall-post stem and a top-half slab cap. The floor beams of the
    building go on top. cells = [(x, z)]; the base block replaces the ground block at y_ground."""
    for (x, z) in cells:
        b.put(x, y_ground, z, P(base))
        b.put(x, y_ground + 1, z, f"@{stem}_wall")
        b.put(x, y_ground + 2, z, sl(cap, "top"))


def retaining_wall(b: Build, x0: int, z0: int, x1: int, z1: int, y_low: int, y_top: int, *, ramp: Ramp,
                   coping: str | None = "stone_brick", batter: bool = False) -> int:
    """A straight stone wall (one cell thick, along x or z) from y_low to y_top, courses taken from `ramp`; a coping of
    slabs (top-half) or None. Returns the block count. Used both under water (pond bed to terrace) and for terraces."""
    assert x0 == x1 or z0 == z1
    n = 0
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            for y in range(y_low, y_top + 1):
                b.put(x, y, z, ramp.pick(x, y, z))
                n += 1
            if coping:
                b.put(x, y_top + 1, z, sl(coping, "bottom"))
    return n


def stone_bridge(b: Build, x0: int, z0: int, length: int, *, axis: str, y_deck: int, mat: str = "stone_brick",
                 width: int = 3, lamp: bool = True, ramp: Ramp | None = None, open_under: frozenset = frozenset()) -> list:
    """A little fenced stone bridge: `length` cells along `axis` ('x' or 'z') from (x0, z0), `width` across, the deck of
    stair-ramped ends (the first and last cell are stairs rising to the deck), slabs on the deck, stone-brick walls as the
    parapet on both edges with a lantern on the 2 end posts. Returns the deck cells (x, z)."""
    along = (1, 0) if axis == "x" else (0, 1)
    across = (0, 1) if axis == "x" else (1, 0)
    deck = []
    face_up = "east" if axis == "x" else "south"
    face_dn = OPP[face_up]
    for i in range(length):
        for j in range(width):
            x, z = x0 + along[0] * i + across[0] * j, z0 + along[1] * i + across[1] * j
            deck.append((x, z))
            if i == 0:
                b.put(x, y_deck, z, st(mat, face_up))
            elif i == length - 1:
                b.put(x, y_deck, z, st(mat, face_dn))
            else:
                b.put(x, y_deck, z, (ramp.pick(x, y_deck, z) if ramp else P(f"{mat}s")))
            # arch under the deck: a stone-brick course below the middle
            if 0 < i < length - 1 and (x, z) not in open_under:
                b.put(x, y_deck - 1, z, P("mossy_stone_bricks") if (i + j) % 3 == 0 else P("stone_bricks"))
    for i in range(1, length - 1):
        for j in (0, width - 1):
            x, z = x0 + along[0] * i + across[0] * j, z0 + along[1] * i + across[1] * j
            b.put(x, y_deck + 1, z, f"@{mat}_wall")
    if lamp:
        for i in (1, length - 2):
            for j in (0, width - 1):
                x, z = x0 + along[0] * i + across[0] * j, z0 + along[1] * i + across[1] * j
                b.put(x, y_deck + 2, z, B.lantern(False))
    b.notes.bridges += 1
    return deck


def vent_slits(wall: Wall, u0: int, y: int, count: int, *, mat: str = "iron_bars") -> None:
    """A diaper row of 1x2 ventilation slits in a brick/plaster gable (bars), every second column."""
    for i in range(count):
        for dy in (0, 1):
            wall.put(u0 + 2 * i, y + dy, 0, f"@{mat}")


def board_and_batten(wall: Wall, u0: int, u1: int, y0: int, y1: int, *, board, batten, every: int = 3) -> None:
    """Vertical board cladding with a batten column every `every` cells."""
    for u in range(u0, u1 + 1):
        for y in range(y0, y1 + 1):
            wall.put(u, y, 0, batten(u, y) if (u - u0) % every == 0 else board(u, y))


def nest_box_row(wall: Wall, u0: int, y: int, count: int, *, mat: str = "spruce") -> None:
    """A row of open-trapdoor nest boxes on a wall (hay carpet in each)."""
    for i in range(count):
        wall.put(u0 + i, y, 1, wall.trapdoor(mat, "bottom", True))
        wall.put(u0 + i, y, 0, B.hay())


def hurdle_fence(b: Build, cells: list, *, post: str = "oak_fence", panel: str = "spruce") -> None:
    """A woven hurdle run along cells [(x, z)]: fence posts with trapdoor panels at every other cell."""
    for i, (x, z) in enumerate(cells):
        b.put(x, 0, z, f"@{post}")
        if i % 2:
            b.put(x, 1, z, td(panel, "north", "bottom", True))


def stone_pillar_fence(b: Build, cells: list, *, every: int = 4, pillar: str = "cobblestone_wall", fence: str = "spruce_fence") -> None:
    """Stone pillars (2 high) every `every` cells with wooden fence between."""
    for i, (x, z) in enumerate(cells):
        if i % every == 0:
            b.put(x, 0, z, f"@{pillar}")
            b.put(x, 1, z, f"@{pillar}")
        else:
            b.put(x, 0, z, f"@{fence}")


def buttress(b: Build, wall: Wall, u: int, y_top: int, *, ramp: Ramp, steps: int = 3) -> None:
    """A stepped buttress against a wall: `steps` stepping blocks out from the wall, each lower and deeper."""
    for s in range(steps):
        for y in range(0, y_top - 2 * s):
            wall.put(u, y, 1 + s, ramp.pick(*wall.xyz(u, y, 1 + s)))
        wall.put(u, y_top - 2 * s, 1 + s, wall.stair("stone_brick", "bottom", "in"))


def awning(b: Build, wall: Wall, u0: int, u1: int, y: int, *, colours=("red", "white"), depth: int = 2) -> None:
    """A striped market awning: stairs would be wrong in wool colours, so alternate coloured carpets over fence rails."""
    for u in range(u0, u1 + 1):
        for w in range(1, depth + 1):
            wall.put(u, y, w, P(f"{colours[u % len(colours)]}_carpet"))
    for u in (u0, u1):
        wall.put(u, y - 1, depth, "@dark_oak_fence")


def drying_rack(b: Build, x: int, z: int, length: int, *, axis: str = "x") -> None:
    for i in range(length):
        cx, cz = (x + i, z) if axis == "x" else (x, z + i)
        b.put(cx, 0, cz, "@jungle_fence" if i % 3 == 0 else None)
        b.put(cx, 1, cz, "@jungle_fence" if i % 3 == 0 else P("brown_carpet"))


def orchard_grid(x0: int, z0: int, x1: int, z1: int, spacing: int = 5, jitter: int = 1, seed: int = 1) -> list:
    """Quincunx tree positions (every second row shifted by half a spacing), jittered by up to `jitter`."""
    import random
    rng = random.Random(seed)
    out = []
    for r, z in enumerate(range(z0, z1 + 1, spacing - 1)):
        shift = (spacing // 2) if r % 2 else 0
        for x in range(x0 + shift, x1 + 1, spacing):
            out.append((x + rng.randint(-jitter, jitter), z + rng.randint(-jitter, jitter)))
    return out


def furrow_field(b: Build, x0: int, z0: int, w: int, d: int, crops: list, *, along: str = "z") -> None:
    """Ridge-and-furrow: beds 2 wide alternating farmland (crop) and a coarse dirt furrow, crops in patches."""
    for x in range(x0, x0 + w):
        for z in range(z0, z0 + d):
            i = (x - x0) if along == "z" else (z - z0)
            if i % 3 == 2:
                b.put(x, -1, z, P("coarse_dirt"))
            else:
                b.put(x, -1, z, B.farmland(7))
                crop = crops[((x - x0) // 3 + (z - z0) // 5) % len(crops)]
                b.put(x, 0, z, B.crop(crop, 7 if crop != "beetroots" else 3))


# --- water: wheel slot, mill race, ponds ----------------------------------------------------------------------------------
def wheel_slot(b: Build, wall: Wall, uc: int, y_axle: int, *, radius: int = 3, depth: int = 3, ramp: Ramp, y_floor: int,
               y_top: int, pier: int = 2, beam: str = "dark_oak", fallback: bool = True, y_water_floor: int = -1) -> dict:
    """The stone slot for a face-on waterwheel in the wall frame: w = 0 is the pier face (the facade), the slot runs
    `depth` cells into the wall (w = 0 .. -(depth - 1)), the back wall at w = -depth. Interior u uc-radius..uc+radius
    (2 * radius + 1 wide), `pier` cells of stone on each side from y_floor to y_top, a stone floor under the pit, a beam
    lintel across the top row (y_top), the axle bearing in the back wall, and (fallback) a static wheel of fences and
    open trapdoors on the centre plane w = -1 (the item display replaces it; only the part above y_water_floor).
    Returns {'axle': (x, y, z) of the axle centre cell, 'interior': set of interior cells (all y), 'fallback': set of cells}.
    The wheel's axle cell is (uc, y_axle, w = -1)."""
    interior = set()
    lo, hi = uc - radius, uc + radius
    for w in range(0, -depth, -1):
        for u in range(lo, hi + 1):
            for y in range(y_floor + 1, y_top):
                interior.add(wall.xyz(u, y, w))
    for (x, y, z) in interior:
        b.put(x, y, z, AIR)
    for w in range(0, -depth - 1, -1):
        for y in range(y_floor, y_top + 1):
            for u in list(range(lo - pier, lo)) + list(range(hi + 1, hi + 1 + pier)):
                wall.put(u, y, w, ramp.pick(*wall.xyz(u, y, w)))
            if w == -depth:
                for u in range(lo, hi + 1):
                    wall.put(u, y, w, ramp.pick(*wall.xyz(u, y, w)))
    for u in range(lo, hi + 1):
        for w in range(0, -depth, -1):
            wall.put(u, y_floor, w, ramp.pick(*wall.xyz(u, y_floor, w)))
            wall.put(u, y_top, w, wall.log(beam, "along", True))
    wall.put(uc, y_axle, -depth, wall.log("dark_oak", "depth", True))
    wall.put(uc, y_axle, -depth + 1 if depth > 1 else -depth, None)
    fb = set()
    if fallback:
        for dx in range(-radius, radius + 1):
            for dy in range(-radius, radius + 1):
                r = math.hypot(dx, dy)
                y = y_axle + dy
                if y < y_water_floor:
                    continue
                cell = wall.xyz(uc + dx, y, -1)
                if (dx, dy) == (0, 0):
                    wall.put(uc, y, -1, wall.log("dark_oak", "depth", True))
                    fb.add(cell)
                elif r <= 2.1 and (dx == 0 or dy == 0):
                    wall.put(uc + dx, y, -1, "@dark_oak_fence")
                    fb.add(cell)
                elif 2.6 <= r <= 3.4:
                    wall.put(uc + dx, y, -1, wall.trapdoor("spruce", "bottom", True, "in"))
                    fb.add(cell)
    b.notes.wheel_fallback |= fb
    b.notes.wheel_cells |= interior
    return {"axle": wall.xyz(uc, y_axle, -1), "interior": interior, "fallback": fb}


def mill_race(b: Build, cells: list, y_water: int, y_floor: int, *, ramp: Ramp, coping: str | None = "stone_brick",
              coping_y: int | None = None) -> set:
    """A stone-lined channel: cells = [(x, z)] of water (all at y_water, still); floor at y_floor, side walls on every
    non-water neighbour from y_floor to y_water + 1 with a slab coping. Returns the water cells."""
    water = set(cells)
    wcells = set()
    for (x, z) in water:
        b.put(x, y_floor, z, ramp.pick(x, y_floor, z))
        for y in range(y_floor + 1, y_water + 1):
            b.put(x, y, z, B.water())
            wcells.add((x, y, z))
    for (x, z) in water:
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, z + dz)
            if n in water:
                continue
            for y in range(y_floor, y_water + 1):
                b.put(n[0], y, n[1], ramp.pick(n[0], y, n[1]))
            cy = y_water + 1 if coping_y is None else coping_y
            if coping and b.get(n[0], cy, n[1]) is None:
                b.put(n[0], cy, n[1], sl(coping, "bottom"))
    for (x, y, z) in wcells:
        if y == y_water and b.get(x, y_water + 1, z) is not None:
            b.put(x, y_water + 1, z, AIR)
    b.notes.race_cells |= wcells
    return wcells


def pond(b: Build, cells: set, y_surface: int, y_bed: int, *, bed: Ramp, rim: Ramp, lily: float = 0.08,
         shelf: int = 1) -> set:
    """Water for the (x, z) `cells`: the bed at y_bed, water from y_bed + 1 up to y_surface, a shallow shelf (one block
    higher bed) in cells within `shelf` of the shore, a rim ramp on the shore cells (y_surface), lily pads at random.
    Returns the water cells (x, y, z)."""
    out = set()
    shore = set()
    for (x, z) in cells:
        edge = any((x + dx, z + dz) not in cells for dx in (-1, 0, 1) for dz in (-1, 0, 1))
        yb = y_bed + (1 if edge else 0)
        for y in range(y_bed, yb + 1):
            b.put(x, y, z, bed.pick(x, y, z))
        for y in range(yb + 1, y_surface + 1):
            b.put(x, y, z, B.water())
            out.add((x, y, z))
        if edge:
            shore.add((x, z))
        elif b.rng.random() < lily:
            b.put(x, y_surface + 1, z, P("lily_pad"))
    b.notes.water_cells |= out
    return out


def height_field_ground(b: Build, heights: dict, *, top: Ramp, sub: Ramp, y_floor: int) -> None:
    """Terraces and cut-and-fill: heights {(x, z): y of the surface block}; fills y_floor..h with `sub`, and the top block
    from `top`. Cells missing from the dict are left alone."""
    for (x, z), h in heights.items():
        for y in range(y_floor, h):
            b.put(x, y, z, sub.pick(x, y, z))
        b.put(x, h, z, top.pick(x, h, z))


def terrace(heights: dict, rect: tuple, level: int) -> None:
    """Raise the height field to `level` over rect (x0, z0, x1, z1)."""
    for x in range(rect[0], rect[2] + 1):
        for z in range(rect[1], rect[3] + 1):
            heights[(x, z)] = level


def cut_and_fill(heights: dict, rect: tuple, level: int, slope: int = 1) -> None:
    """Level `rect` to `level` and blend the heights outside it down/up to their old value with a slope of `slope` blocks
    per cell (a platform with a ramp-like skirt)."""
    x0, z0, x1, z1 = rect
    for (x, z), h in list(heights.items()):
        dx = max(x0 - x, 0, x - x1)
        dz = max(z0 - z, 0, z - z1)
        d = max(dx, dz)
        if d == 0:
            heights[(x, z)] = level
        else:
            target = level + (1 if h > level else -1) * 0
            heights[(x, z)] = max(min(h, level + d * slope), level - d * slope) if h != level else level


# --- not built yet (run 2) ---------------------------------------------------------------------------------------------
def _todo(name: str):
    def f(*a, **k):
        raise NotImplementedError(f"{name}: planned for the countryside-builds run 2 (docs/research/farm-buildings.md section 9)")
    f.__name__ = name
    return f


catslide_roof = _todo("catslide_roof")
round_building = _todo("round_building")
cone_roof = _todo("cone_roof")
hay_roll_thatch = _todo("hay_roll_thatch")
corbie_gable = _todo("corbie_gable")
eyebrow_dormer = _todo("eyebrow_dormer")
barn_door_double = _todo("barn_door_double")

KITS = {
    "done (Mill, tested)": ["hipped_roof", "half_hip_roof", "gable_roof_s", "pyramid_roof", "pent_skirt", "dormer (+hoist gablet)",
                            "coped_gable", "staddle_stones", "retaining_wall", "stone_bridge", "wheel_slot", "mill_race", "pond",
                            "height_field_ground", "terrace", "cut_and_fill"],
    "simple (tested, small)": ["vent_slits", "board_and_batten", "nest_box_row", "hurdle_fence", "stone_pillar_fence",
                               "buttress", "awning", "drying_rack", "orchard_grid", "furrow_field"],
    "stub (run 2)": ["catslide_roof", "round_building", "cone_roof", "hay_roll_thatch", "corbie_gable", "eyebrow_dormer",
                     "barn_door_double"],
}
