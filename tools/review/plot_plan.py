"""Plans a whole review plot the way `/bakery plot setup` does, in pure Python (no server).

A Python re-implementation of the plugin's planning (house/design/HouseDesignParser, DecorKeepOut, FenceStyle,
FenceLayout and house/setup/SetupPlanner) for a flat review world:
- the house design at `layout.house-at`, including its `ground-depth` layers below the house box;
- the plot-wide decor (`houses/decor.yml`) minus the keep-out columns (ground decor at y -1/-2 has the smaller
  ground keep-out);
- the fence on the layout's outer ring, gate at `layout.entrance`, `post-spacing` / `topper-spacing` rhythm.
Every `*_leaves` block is made `persistent=true`, as the plugin does. Coordinates out of `plan()` are world blocks.
The belt frame, stations and buildings are not placed (they are entities or plugin-only).
If the Java planner changes, change this file with it.
"""
import math
import os
import re

import yaml

MAX_GROUND_DEPTH = 2
DECOR_MIN_Y = -2
DECOR_MAX_Y = 16
MARGIN = 1
FRONT_MARGIN_X = 3
FRONT_MARGIN_Z = 2
AIR = "minecraft:air"
DIRS = ((0, -1), (1, 0), (0, 1), (-1, 0))  # N, E, S, W
FACES = ("north", "east", "south", "west")


# ---------------------------------------------------------------- block data helpers

def block_id(data):
    return data.split("[", 1)[0]


def with_props(data, props):
    """Block data with `props` (dict) set, keeping the others."""
    name, _, rest = data.partition("[")
    current = {}
    if rest:
        for part in rest.rstrip("]").split(","):
            if "=" in part:
                k, v = part.split("=", 1)
                current[k.strip()] = v.strip()
    current.update(props)
    return name + ("[" + ",".join(f"{k}={v}" for k, v in current.items()) + "]" if current else "")


def design_block(data):
    """What the plugin places for a design block: leaves are always persistent."""
    if block_id(data).endswith("_leaves"):
        return with_props(data, {"persistent": "true"})
    return data


# ---------------------------------------------------------------- house design

class Design:
    def __init__(self, name, raw):
        self.name = name
        self.sx, self.sy, self.sz = (int(v) for v in raw["size"])
        self.ground = int(raw.get("ground-depth") or 0)
        if not 0 <= self.ground <= MAX_GROUND_DEPTH or self.ground >= self.sy:
            raise ValueError(f"{name}: ground-depth must be 0..{MAX_GROUND_DEPTH} and less than size y")
        self.origin = [int(v) for v in raw.get("origin") or (0, 0, 0)]
        self.palette = {str(k): str(v).strip() for k, v in raw["palette"].items()}
        self.layers = raw["layers"]
        self.points = raw.get("points") or {}
        self.layout = raw.get("layout")

    def cells(self):
        """(x, design y, z, block data) for every non-keep cell, deepest layer first; ground cells have y < 0."""
        for layer, rows in enumerate(self.layers):
            for z, row in enumerate(rows):
                for x, ch in enumerate(row):
                    if ch in ". ":
                        continue
                    if ch not in self.palette:
                        raise ValueError(f"{self.name}: layer {layer} row {z} uses {ch!r}, not in the palette")
                    yield x, layer - self.ground, z, self.palette[ch]

    def to_world(self, at, x, y, z):
        return at[0] + x - self.origin[0], at[1] + y - self.origin[1], at[2] + z - self.origin[2]

    def house_height(self):
        return self.sy - self.ground


def load_yaml(path):
    with open(path, encoding="utf-8") as f:
        return yaml.safe_load(f)


# ---------------------------------------------------------------- layout

def floor_cell(at):
    return [math.floor(float(v)) for v in at]


class Layout:
    def __init__(self, raw):
        self.size_x, self.size_z = (int(v) for v in raw["plot-size"])
        self.house_at = [int(v) for v in raw["house-at"]]
        self.entrance = [int(v) for v in raw["entrance"]]
        self.pedestal = raw["pedestal"]
        self.shop = raw["shop"]
        self.clicks = raw.get("clicks")
        self.viewpoint = raw.get("viewpoint")
        belt = raw.get("belt") or {}
        self.belt_from = belt.get("from")
        self.belt_to = belt.get("to")


# ---------------------------------------------------------------- decor keep-out (house/design/DecorKeepOut)

def _between(ax, az, bx, bz, margin):
    return (min(ax, bx) - margin, min(az, bz) - margin, max(ax, bx) + margin, max(az, bz) + margin)


def footprints_from_slots_file(path):
    """Building id -> footprint side, read from the generator's comments in building-slots.yml (0 when missing)."""
    out = {}
    if not os.path.exists(path):
        return out
    with open(path, encoding="utf-8") as f:
        for line in f:
            s = line.strip()
            if s.startswith("#") and "footprint square" in s:
                ident = s[1:].split(":", 1)[0].strip()
                try:
                    out[ident] = float(s.split("footprint square", 1)[1].split("x", 1)[0])
                except ValueError:
                    pass
    return out


class KeepOut:
    def __init__(self, design, layout, slots=None, footprints=None):
        self.size_x, self.size_z = layout.size_x, layout.size_z
        self.areas = []
        self.ground_areas = []
        cx, _, cz = design.to_world(layout.house_at, 0, 0, 0)
        self.areas.append(("the factory", _between(cx, cz, cx + design.sx - 1, cz + design.sz - 1, MARGIN)))
        self.ground_areas.append(("the house design box",
                                  _between(cx, cz, cx + design.sx - 1, cz + design.sz - 1, 0)))
        ex, ez = layout.entrance
        min_x = max_x = ex
        min_z = max_z = ez
        spots = [layout.pedestal, layout.shop] + ([layout.clicks] if layout.clicks else [])
        for s in spots:
            c = floor_cell(s["at"])
            min_x, max_x = min(min_x, c[0]), max(max_x, c[0])
            min_z, max_z = min(min_z, c[2]), max(max_z, c[2])
        self.areas.append(("the walk, pedestal and panels", (min_x - FRONT_MARGIN_X, min_z - FRONT_MARGIN_Z,
                                                             max_x + FRONT_MARGIN_X, max_z + FRONT_MARGIN_Z)))
        door = design.points.get("door")
        if door is not None:
            d = design.to_world(layout.house_at, *door)
            self.areas.append(("the walk to the door", _between(ex, ez, d[0], d[2], 0)))
        if layout.belt_from and layout.belt_to:
            f, t = layout.belt_from, layout.belt_to
            self.areas.append(("the belt", _between(f[0], f[2], t[0], t[2], MARGIN)))
            self.ground_areas.append(("the belt", _between(f[0], f[2], t[0], t[2], 0)))
        footprints = footprints or {}
        for ident, lst in (slots or {}).items():
            h = footprints.get(ident, 0.0) / 2
            for slot in lst or []:
                sx, _, sz = floor_cell(slot["at"])
                lo = (lambda c: c if h <= 0 else math.floor(c + 0.5 - h))
                hi = (lambda c: c if h <= 0 else math.ceil(c + 0.5 + h) - 1)
                self.areas.append(("a building slot", (lo(sx), lo(sz), hi(sx), hi(sz))))

    def reason(self, x, z, ground=False):
        if x < 0 or z < 0 or x >= self.size_x or z >= self.size_z:
            return "outside the plot"
        if x in (0, self.size_x - 1) or z in (0, self.size_z - 1):
            return "the fence ring"
        for why, (a, b, c, d) in (self.ground_areas if ground else self.areas):
            if a <= x <= c and b <= z <= d:
                return why
        return None


def decor_blocks(raw, keep_out, problems):
    """Plot-cell decor blocks [(x, y, z, data)] the plugin would place; skipped ones add a problem."""
    out, seen = [], set()
    for i, e in enumerate((raw or {}).get("blocks") or []):
        at = e.get("at") if isinstance(e, dict) else None
        block = e.get("block") if isinstance(e, dict) else None
        if not (isinstance(at, list) and len(at) == 3 and isinstance(block, str) and block.strip()):
            problems.append(f"decor blocks[{i}] malformed; skipped")
            continue
        x, y, z = (int(v) for v in at)
        if not DECOR_MIN_Y <= y < DECOR_MAX_Y or (x, y, z) in seen:
            problems.append(f"decor blocks[{i}] y {y} out of range or cell used twice; skipped")
            continue
        seen.add((x, y, z))
        why = keep_out.reason(x, z, ground=y < 0)
        if why:
            problems.append(f"decor {x} {y} {z} ({block}) lies in a keep-out cell ({why}); skipped")
            continue
        out.append((x, y, z, block.strip()))
    return out


# ---------------------------------------------------------------- fence (house/design/FenceStyle + FenceLayout)

def _column(raw, null_is_air=False):
    if isinstance(raw, str):
        return [raw.strip()]
    return [AIR if (v is None and null_is_air) else str(v).strip() for v in raw]


def _spacing(raw, list_key, every_key, fallback):
    if raw.get(list_key) is not None:
        return [int(v) for v in raw[list_key]]
    every = raw.get(every_key)
    every = fallback if every is None else int(every)
    return [every] if every > 0 else []


class FenceStyle:
    def __init__(self, raw):
        self.post = _column(raw["post"])
        self.rail = str(raw["rail"]).strip()
        self.corner = _column(raw["corner"]) if raw.get("corner") is not None else self.post
        self.post_spacing = _spacing(raw, "post-spacing", "post-every", 4) or [4]
        self.topper = str(raw["topper"]).strip() if raw.get("topper") else None
        self.topper_spacing = _spacing(raw, "topper-spacing", "topper-every", 1 if self.topper else 0)
        gate = raw.get("gate") or {}
        self.gate_width = int(gate.get("width", 3))
        self.gate_left = _column(gate["left"])
        self.gate_right = _column(gate["right"]) if gate.get("right") is not None else self.gate_left
        self.arch = _column(gate["arch"], True) if gate.get("arch") is not None else [AIR, AIR]

    @staticmethod
    def _on_cycle(gaps, n):
        m = n % sum(gaps)
        p = 0
        for g in gaps:
            if p == m:
                return True
            p += g
        return False

    def post_at(self, offset):
        return self._on_cycle(self.post_spacing, offset)

    def post_index(self, offset):
        total = sum(self.post_spacing)
        cycles = offset // total
        rest = offset - cycles * total
        count = cycles * len(self.post_spacing)
        p = 0
        for g in self.post_spacing:
            if p >= rest:
                break
            count += 1
            p += g
        return count

    def topped_post(self, index):
        return bool(self.topper_spacing) and self._on_cycle(self.topper_spacing, index)

    def column(self, role):
        return {"POST": self.post, "CORNER": self.corner, "GATE_LEFT": self.gate_left,
                "GATE_RIGHT": self.gate_right, "ARCH": self.arch}.get(role, [self.rail])


def fence_blocks(style, min_x, min_z, max_x, max_z, y, gate_x, gate_z, house_contains):
    """World blocks [(x, y, z, data)] of a rectangular plot's fence on flat ground at `y`."""
    def inside(x, z):
        return min_x <= x <= max_x and min_z <= z <= max_z

    border = [(x, z) for z in range(min_z, max_z + 1) for x in range(min_x, max_x + 1)
              if any(not inside(x + dx, z + dz) for dx, dz in DIRS)]
    if not border:
        return []
    bset = set(border)
    centre = min(border, key=lambda k: (k[0] - gate_x) ** 2 + (k[1] - gate_z) ** 2)  # first wins on ties
    opening, seen, queue = [], {centre}, [centre]
    while queue and len(opening) < style.gate_width:
        k = queue.pop(0)
        opening.append(k)
        for dx, dz in DIRS:
            n = (k[0] + dx, k[1] + dz)
            if n in bset and n not in seen:
                seen.add(n)
                queue.append(n)
    open_set = set(opening)
    out_dir = next(((dx, dz) for dx, dz in DIRS if not inside(centre[0] + dx, centre[1] + dz)), DIRS[0])
    left_x, left_z = -out_dir[1], out_dir[0]
    pillars = {}
    for k in opening:
        for dx, dz in DIRS:
            n = (k[0] + dx, k[1] + dz)
            if n in bset and n not in open_set and n not in pillars:
                dot = (n[0] - centre[0]) * left_x + (n[1] - centre[1]) * left_z
                pillars[n] = "GATE_LEFT" if dot >= 0 else "GATE_RIGHT"
    roles = {}
    for k in border:
        if k in open_set or k in pillars:
            continue
        x, z = k
        n, s = (x, z - 1) in bset, (x, z + 1) in bset
        e, w = (x + 1, z) in bset, (x - 1, z) in bset
        if e and w and not n and not s:
            roles[k] = "POST" if style.post_at(x - min_x) else "RAIL"
        elif n and s and not e and not w:
            roles[k] = "POST" if style.post_at(z - min_z) else "RAIL"
        else:
            roles[k] = "CORNER"
    for k, r in list(roles.items()):
        if r == "POST" and any((k[0] + dx, k[1] + dz) in pillars
                               or roles.get((k[0] + dx, k[1] + dz)) == "CORNER" for dx, dz in DIRS):
            roles[k] = "RAIL"
    tall = {k: len(style.column(r)) if r != "RAIL" else 1 for k, r in roles.items()}
    tall.update({k: len(style.column(r)) for k, r in pillars.items()})
    blocks = []

    def add(x, yy, z, data):
        if not house_contains(x, yy, z):
            blocks.append((x, yy, z, data))

    for (x, z), r in roles.items():
        if r == "RAIL":
            faces = {FACES[i]: True for i, (dx, dz) in enumerate(DIRS) if (x + dx, z + dz) in tall}
            add(x, y, z, _connect(style.rail, faces))
            continue
        col = style.column(r)
        for level, data in enumerate(col):
            add(x, y + level, z, data)
        if style.topper and _topped(style, r, x, z, min_x, min_z, bset):
            add(x, y + len(col), z, style.topper)
    for (x, z), r in pillars.items():
        for level, data in enumerate(style.column(r)):
            add(x, y + level, z, data)
    for x, z in opening:
        for level, data in enumerate(style.arch):
            if data != AIR:
                add(x, y + level, z, data)
    return blocks


def _topped(style, role, x, z, min_x, min_z, bset):
    if role == "CORNER":
        return True
    if role != "POST":
        return False
    east_west = (x + 1, z) in bset and (x - 1, z) in bset
    return style.topped_post(style.post_index(x - min_x if east_west else z - min_z))


def _connect(data, faces):
    """Rail block data with its connections set (fences/panes/bars: true; walls: low); others unchanged."""
    ident = block_id(data)
    if ident.endswith("_wall"):
        return with_props(data, {f: "low" for f in faces})
    if ident.endswith(("_fence", "_pane")) or ident == "minecraft:iron_bars":
        return with_props(data, {f: "true" for f in faces})
    return data


# ---------------------------------------------------------------- the whole plot

class PlotPlan:
    """World blocks and camera anchors of one review plot."""

    def __init__(self, design, layout, blocks, box, y0, ax, az, problems):
        self.design = design
        self.layout = layout
        self.blocks = blocks          # {(x, y, z): data}, later writers win like the plugin's change list
        self.box = box                # (min_x, min_z, max_x, max_z) of the plot / house-only area
        self.y0 = y0
        self.ax = ax
        self.az = az
        self.problems = problems

    def top(self):
        return max((p[1] for p in self.blocks), default=self.y0)


def plan(houses_dir, design_name, at):
    """Plans `design_name` with the plot's min corner at `at` = (x, y0, z); y0 is the first air layer."""
    ax, y0, az = at
    design = Design(design_name, load_yaml(os.path.join(houses_dir, design_name + ".yml")))
    problems, blocks = [], {}
    if not design.layout:
        house_at = (ax, y0, az)  # place-only design: its region minimum on the review corner
        for x, y, z, data in design.cells():
            blocks[design.to_world(house_at, x, y, z)] = design_block(data)
        box = (ax, az, ax + design.sx - 1, az + design.sz - 1)
        return PlotPlan(design, None, blocks, box, y0, ax, az, problems)
    layout = Layout(design.layout)
    house_at = (ax + layout.house_at[0], y0 + layout.house_at[1], az + layout.house_at[2])
    for x, y, z, data in design.cells():
        blocks[design.to_world(house_at, x, y, z)] = design_block(data)
    base = design.to_world(house_at, 0, 0, 0)

    def house_contains(x, y, z):
        return (base[0] <= x < base[0] + design.sx and base[1] <= y < base[1] + design.house_height()
                and base[2] <= z < base[2] + design.sz)

    max_x, max_z = ax + layout.size_x - 1, az + layout.size_z - 1
    fence_path = os.path.join(houses_dir, "fence.yml")
    if os.path.exists(fence_path):
        style = FenceStyle(load_yaml(fence_path))
        for x, y, z, data in fence_blocks(style, ax, az, max_x, max_z, y0, ax + layout.entrance[0],
                                          az + layout.entrance[1], house_contains):
            blocks[(x, y, z)] = design_block(data)
    decor_path = os.path.join(houses_dir, "decor.yml")
    if os.path.exists(decor_path):
        slots_path = os.path.join(houses_dir, "building-slots.yml")
        slots = (load_yaml(slots_path) or {}).get("slots") if os.path.exists(slots_path) else None
        keep_out = KeepOut(design, layout, slots, footprints_from_slots_file(slots_path))
        for x, y, z, data in decor_blocks(load_yaml(decor_path), keep_out, problems):
            blocks[(ax + x, y0 + y, az + z)] = design_block(data)
    return PlotPlan(design, layout, blocks, (ax, az, max_x, max_z), y0, ax, az, problems)


# ---------------------------------------------------------------- cameras

def look(cam, target):
    """Minecraft yaw/pitch (degrees) from `cam` toward `target`: yaw 0 = south (+z), 90 = west; pitch + = down."""
    dx, dy, dz = (target[i] - cam[i] for i in range(3))
    yaw = math.degrees(math.atan2(-dx, dz))
    pitch = math.degrees(math.atan2(-dy, math.hypot(dx, dz)))
    return yaw, pitch


EYE = 1.62


def shots(p):
    """{shot: (x, y, z, yaw, pitch)} for 'eye' (layout viewpoint), '34' (3/4 overview) and 'door' (close-up)."""
    if not p.layout:
        return _place_only_shots(p)
    min_x, min_z, max_x, max_z = p.box
    sx, sz = max_x - min_x + 1, max_z - min_z + 1
    centre = (min_x + sx / 2, p.y0 + 3, min_z + sz / 2)
    if p.layout:
        ex, ez = p.ax + p.layout.entrance[0] + 0.5, p.az + p.layout.entrance[1] + 0.5
    else:
        ex, ez = centre[0], max_z + 8  # place-only design: look at it from the south
    out = {}
    if p.layout and p.layout.viewpoint:
        v = p.layout.viewpoint
        at = [float(c) for c in v["at"]]
        out["eye"] = (p.ax + at[0] + 0.5, p.y0 + at[1] + EYE, p.az + at[2] + 0.5, float(v.get("yaw", 0)),
                      float(v.get("pitch", 0)))
    else:
        cam = (ex, p.y0 + EYE, ez)
        out["eye"] = cam + look(cam, centre)
    span = max(sx, sz, 12)
    side_x = 1 if ex >= centre[0] else -1
    side_z = 1 if ez >= centre[2] else -1
    cam = (centre[0] + side_x * 0.7 * span, p.y0 + 0.55 * span, centre[2] + side_z * 0.7 * span)
    out["34"] = cam + look(cam, centre)
    door = p.design.points.get("door")
    if door is not None:
        house_at = ((p.ax + p.layout.house_at[0], p.y0 + p.layout.house_at[1], p.az + p.layout.house_at[2])
                    if p.layout else (p.ax, p.y0, p.az))
        dx, dy, dz = p.design.to_world(house_at, *door)
        target = (dx + 0.5, dy + 1.0, dz + 0.5)
        vx, vz = ex - target[0], ez - target[2]
        n = math.hypot(vx, vz) or 1.0
        cam = (target[0] + 6 * vx / n, dy + EYE + 0.5, target[2] + 6 * vz / n)
        out["door"] = cam + look(cam, target)
    return out


FACINGS = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
FOV = 70.0  # Chunky's vertical field of view in the review scenes (review_render.scene)


def door_facing(design):
    """Horizontal unit vector the design's front faces: the `facing` of the block at points.door; north if unknown."""
    door = design.points.get("door")
    if door is not None:
        x, y, z = (int(v) for v in door)
        try:
            ch = design.layers[y + design.ground][z][x]
        except (IndexError, TypeError):
            ch = None
        m = re.search(r"facing=(\w+)", design.palette.get(ch, "")) if ch else None
        if m and m.group(1) in FACINGS:
            return FACINGS[m.group(1)]
    return FACINGS["north"]


def _place_only_shots(p):
    """Shots for a design without a layout, framed on the house box (the plot box is the house box here)."""
    min_x, min_z, max_x, max_z = p.box
    sx, sz, sy = max_x - min_x + 1, max_z - min_z + 1, p.design.house_height()
    cx, cz = min_x + sx / 2, min_z + sz / 2
    centre = (cx, p.y0 + sy / 2, cz)
    fx, fz = door_facing(p.design)
    door = p.design.points.get("door")
    target = None
    if door is not None:
        dx, dy, dz = p.design.to_world((p.ax, p.y0, p.az), *door)
        target = (dx + 0.5, dy + 1.0, dz + 0.5)
    out = {}
    dist = 1.3 * max(sx, sz)
    cam = (cx + fx * dist, p.y0 + 1.6, cz + fz * dist)
    out["eye"] = cam + look(cam, target or centre)
    # front-left as seen by someone in front of the house looking at it; 20 degrees up, far enough that a
    # sphere around the box plus 3 blocks fits the vertical field of view
    hx, hz = fx - fz, fz + fx
    n = math.hypot(hx, hz)
    radius = math.sqrt((sx + 6) ** 2 + (sy + 6) ** 2 + (sz + 6) ** 2) / 2
    d = radius / math.sin(math.radians(FOV / 2))
    up = math.radians(20)
    cam = (centre[0] + hx / n * d * math.cos(up), centre[1] + d * math.sin(up),
           centre[2] + hz / n * d * math.cos(up))
    out["34"] = cam + look(cam, centre)
    if target is not None:
        cam = (target[0] + 4 * fx, dy + EYE, target[2] + 4 * fz)
        out["door"] = cam + look(cam, target)
    return out
