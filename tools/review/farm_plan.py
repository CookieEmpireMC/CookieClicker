"""Plans the countryside designs for the review server the way `/bakery farm setup` does (farm/FarmPlan.java).

- A place file (`mill`, `cow_barn`, `sugar_works`, `cocoa_grove`, `chicken_farm`, `market`: a house design with
  structured `points:`) is placed alone, its cell [0, 0, 0] (first layer above its ground) on the review corner.
- `countryside` is placed with its origin cell on the review corner (the farm region's min corner), then every
  `places.<p>.at` place over it, in file order (later blocks win, like the plugin's paste).
Ground-depth up to 4 (HouseDesignParser.MAX_FARM_GROUND_DEPTH). Every `*_leaves` block is made persistent.
If the Java planner changes, change this file with it.
"""
import math
import os

import plot_plan
from plot_plan import EYE, FOV, Design, PlotPlan, design_block, load_yaml, look

MAX_FARM_GROUND_DEPTH = 4
COUNTRYSIDE = "countryside"
WHEEL_DISTANCE = 8.0   # along the axle: a 7-block wheel fills the 70 degree view
PANEL_DISTANCE = 6.0


def is_farm_design(name, raw):
    """True for the countryside and for place files (their viewpoint is a {at, yaw, pitch} map)."""
    points = raw.get("points") or {}
    return name == COUNTRYSIDE or "places" in raw or isinstance(points.get("viewpoint"), dict)


class FarmPlan(PlotPlan):
    """A PlotPlan without a layout, plus the placed places: [(Design, world of its cell [0, 0, 0])]."""

    def __init__(self, design, blocks, box, y0, ax, az, problems, places, countryside):
        super().__init__(design, None, blocks, box, y0, ax, az, problems)
        self.places = places
        self.countryside = countryside


def _box_of(design, base):
    """(min_x, min_z, max_x, max_z) of a design whose cell [0, 0, 0] is at world `base`."""
    min_x, min_z = base[0], base[2]
    return min_x, min_z, min_x + design.sx - 1, min_z + design.sz - 1


def _union(a, b):
    return min(a[0], b[0]), min(a[1], b[1]), max(a[2], b[2]), max(a[3], b[3])


def _paste(blocks, design, base):
    for x, y, z, data in design.cells():
        blocks[(base[0] + x, base[1] + y, base[2] + z)] = design_block(data)


def plan(houses_dir, name, at):
    """Plans `name` with the farm region's min corner (or a lone place's cell [0, 0, 0]) at `at` = (x, y0, z)."""
    ax, y0, az = at
    raw = load_yaml(os.path.join(houses_dir, name + ".yml"))
    design = Design(name, raw, MAX_FARM_GROUND_DEPTH)
    problems, blocks, places = [], {}, []
    countryside = name == COUNTRYSIDE or bool(raw.get("places"))
    # the countryside's origin cell lands on the region corner; a place's cell [0, 0, 0] lands on its target
    base = design.to_world(at, 0, 0, 0) if countryside else tuple(at)
    _paste(blocks, design, base)
    box = _box_of(design, base)
    if not countryside:
        places.append((design, base))
        return FarmPlan(design, blocks, box, y0, ax, az, problems, places, False)
    if base[0] < ax or base[2] < az:
        problems.append(f"the countryside's origin {design.origin} puts it west or north of the region's min "
                        "corner; use origin x and z 0")
    land = box
    for pname, spec in (raw.get("places") or {}).items():
        path = os.path.join(houses_dir, f"{pname}.yml")
        if not os.path.exists(path):
            problems.append(f"places.{pname}: houses/{pname}.yml does not exist; skipped")
            continue
        place = Design(pname, load_yaml(path), MAX_FARM_GROUND_DEPTH)
        cell = [int(v) for v in (spec or {}).get("at") or ()]
        if len(cell) != 3:
            problems.append(f"places.{pname}.at must be [x, y, z]; skipped")
            continue
        target = design.to_world(at, *cell)
        b = _box_of(place, target)
        if b[0] < land[0] or b[1] < land[1] or b[2] > land[2] or b[3] > land[3]:
            problems.append(f"place {pname} sticks out of the countryside")
        _paste(blocks, place, target)
        places.append((place, target))
        box = _union(box, b)
    return FarmPlan(design, blocks, box, y0, ax, az, problems, places, True)


# ---------------------------------------------------------------- cameras

def _cell(base, c):
    return base[0] + int(c[0]), base[1] + int(c[1]), base[2] + int(c[2])


def _spot(raw):
    """{at, yaw, pitch} or a bare [x, y, z] as (cell, yaw, pitch)."""
    if isinstance(raw, dict):
        return raw.get("at"), float(raw.get("yaw", 0)), float(raw.get("pitch", 0))
    return raw, 0.0, 0.0


def _overview(centre, sx, sy, sz, front):
    """The 3/4 shot of a box: front-left of `front`, 20 degrees up, the box plus 3 blocks inside the view."""
    fx, fz = front
    hx, hz = fx - fz, fz + fx
    n = math.hypot(hx, hz) or 1.0
    radius = math.sqrt((sx + 6) ** 2 + (sy + 6) ** 2 + (sz + 6) ** 2) / 2
    d = radius / math.sin(math.radians(FOV / 2))
    up = math.radians(20)
    cam = (centre[0] + hx / n * d * math.cos(up), centre[1] + d * math.sin(up),
           centre[2] + hz / n * d * math.cos(up))
    return cam + look(cam, centre)


def _above(p):
    """Straight down on the whole box, high enough that its longer side fits the vertical view (plus 10%)."""
    min_x, min_z, max_x, max_z = p.box
    sx, sz = max_x - min_x + 1, max_z - min_z + 1
    d = 1.1 * (max(sx, sz) / 2) / math.tan(math.radians(FOV / 2))
    return (min_x + sx / 2, p.y0 + p.design.house_height() + d, min_z + sz / 2, 0.0, 90.0)


def _side(v):
    return 1.0 if v >= 0 else -1.0


def shots(p):
    """{shot: (x, y, z, yaw, pitch)}: countryside 'above' and '34'; a place 'eye' (its viewpoint), '34' and
    'detail' (the mill's wheel, else the upgrade panel with the yard behind)."""
    min_x, min_z, max_x, max_z = p.box
    sx, sz = max_x - min_x + 1, max_z - min_z + 1
    if p.countryside:
        sy = p.design.house_height()
        centre = (min_x + sx / 2, p.y0 + sy / 2, min_z + sz / 2)
        return {"above": _above(p), "34": _overview(centre, sx, sy, sz, (0, 1))}
    design, base = p.places[0]
    points = design.points
    out = {}
    sy = design.house_height()
    centre = (min_x + sx / 2, p.y0 + sy / 2, min_z + sz / 2)
    front = (0.0, -1.0)
    view = points.get("viewpoint")
    vp = None
    if view is not None:
        at, yaw, pitch = _spot(view)
        c = _cell(base, at)
        vp = (c[0] + 0.5, c[1] + EYE, c[2] + 0.5)
        out["eye"] = vp + (yaw, pitch)
        dx, dz = vp[0] - centre[0], vp[2] - centre[2]
        n = math.hypot(dx, dz)
        if n > 0:
            front = (dx / n, dz / n)
    else:
        out["eye"] = plot_plan._place_only_shots(p)["eye"]
    out["34"] = _overview(centre, sx, sy, sz, front)
    wheel = points.get("wheel")
    panel = points.get("panel")
    if isinstance(wheel, dict) and wheel.get("at") is not None:
        c = _cell(base, wheel["at"])
        target = (c[0] + 0.5, c[1] + 0.5, c[2] + 0.5)
        ax_x, ax_z = (1.0, 0.0) if str(wheel.get("axis", "x")) == "x" else (0.0, 1.0)
        ref = vp or (centre[0] + front[0] * sx, centre[1], centre[2] + front[1] * sz)
        along = _side((ref[0] - target[0]) * ax_x + (ref[2] - target[2]) * ax_z)
        across = _side((ref[0] - target[0]) * ax_z + (ref[2] - target[2]) * ax_x)
        cam = (target[0] + ax_x * along * WHEEL_DISTANCE + ax_z * across * 3,
               target[1] + 2.0,
               target[2] + ax_z * along * WHEEL_DISTANCE + ax_x * across * 3)
        out["detail"] = cam + look(cam, target)
    elif panel is not None:
        at, yaw, _ = _spot(panel)
        c = _cell(base, at)
        target = (c[0] + 0.5, c[1] + 1.0, c[2] + 0.5)
        # Minecraft yaw: 0 faces south (+z), 90 west (-x); the camera stands where the panel faces
        fx, fz = -math.sin(math.radians(yaw)), math.cos(math.radians(yaw))
        cam = (target[0] + fx * PANEL_DISTANCE, target[1] + 1.2, target[2] + fz * PANEL_DISTANCE)
        out["detail"] = cam + look(cam, target)
    return out
