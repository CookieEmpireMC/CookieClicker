"""Build lint: the style rules of design/style-guide.md ("Block structures") as asserts. A design that fails is not written.

Rule list, thresholds and how to read a failure: design/build-lint.md. Entry points:
    lint_house(g, notes, points, ...)  -> raises LintError listing every problem (g = {(x, y, z): block data}, design y)
    lint_decor(blocks, scenes, ...)    -> same, for houses/decor.yml (plot cells, ground at y -1/-2)
    lint_fence(style)
`notes` (Notes) is filled by the generator's kits while it builds (volumes, ramps, roofs, plinth, chimneys); the rest is
derived from the block grid itself (windows, depth planes, props, organic edges).
"""
from __future__ import annotations

from collections import deque
from dataclasses import dataclass, field

import blocks as B

DIRS6 = [(1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)]
LIGHT_WORDS = ("lantern", "shroomlight", "glowstone", "sea_lantern", "torch", "jack_o_lantern", "froglight", "copper_bulb")
PLANT_IDS = {"poppy", "dandelion", "cornflower", "azure_bluet", "oxeye_daisy", "allium", "pink_tulip", "red_tulip",
             "orange_tulip", "white_tulip", "lily_of_the_valley", "blue_orchid", "short_grass", "fern", "tall_grass",
             "large_fern", "rose_bush", "lilac", "peony", "sunflower", "wheat", "carrots", "potatoes", "beetroots",
             "sweet_berry_bush", "sugar_cane", "bush", "lily_pad", "dead_bush", "moss_carpet", "flowering_azalea",
             "azalea"}
PROP_WORDS = ("barrel", "hay_block", "composter", "cauldron", "carved_pumpkin", "pumpkin", "melon", "bell", "lectern",
              "campfire", "flower_pot", "potted_", "_wool", "_carpet", "lantern", "crafting_table", "bookshelf",
              "smoker", "furnace")
ORGANIC_GROUND = {"dirt_path", "gravel", "coarse_dirt", "podzol", "rooted_dirt", "moss_block", "mud", "packed_mud",
                  "clay", "cobblestone", "mossy_cobblestone", "andesite", "stone", "dirt", "tuff", "polished_andesite", "stone_bricks"}
DEPTH_IGNORE_WORDS = ("lantern", "potted_", "barrel", "hay_block", "wool", "carpet", "_fence", "campfire", "chain",
                      "_sign", "flower_pot", "composter", "cauldron", "bell", "lightning_rod", "azalea", "moss")
RAMP_EXEMPT_WORDS = ("air", "glass", "door", "lantern", "campfire", "sign", "carpet", "wool", "barrel", "pot",
                     "fence", "bars", "chain", "water", "magma", "shroomlight", "hay", "trapdoor", "grate",
                     "grass", "dirt", "farmland", "leaves", "flower", "tulip", "poppy", "dandelion", "cauldron",
                     "composter", "log", "wheat", "pumpkin", "bell", "_slab")


class LintError(AssertionError):
    pass


@dataclass
class Notes:
    """What the kits tell the lint about the design's intent (all checked against the grid where possible)."""
    volumes: list[dict] = field(default_factory=list)      # {name, footprint (x1,z1,x2,z2), top}
    ramps: dict[str, list[str]] = field(default_factory=dict)      # role -> block ids (light to dark)
    roofs: list[dict] = field(default_factory=list)        # {name, eave: [cells], field: [cells], overhang}
    plinth: dict | None = None                             # {blocks: [ids], courses: n, ring: [(x, z)]}
    chimneys: list[str] = field(default_factory=list)
    roof_cells: set = field(default_factory=set)
    body_box: tuple | None = None                          # (x1, z1, x2, z2) of the building body
    wall_rows: tuple = (1, 4)                              # y range inspected for depth planes
    exempt_windows: set = field(default_factory=set)       # any cell of a window the rules skip (rose window ...)
    seal: set = field(default_factory=set)                 # openings treated as closed for the inside/outside test
    front_yard: dict | None = None                         # {rect: (x1, z1, x2, z2), corridor: (x1, x2), max_y: n}
    ground_region: tuple | None = None                     # (x0, z0, x1, z1) exclusive box; edges on its border are not judged
    roof_info: list = field(default_factory=list)          # {name, form, rect, y_eave, ridge_y, cells}: every roof kit call
    roof_heights: set = field(default_factory=set)         # distinct eave / ridge / dormer / skirt heights (L-F9)
    gables: list = field(default_factory=list)             # {name, finish, rake: [cells], ...} free gables (L-F11)
    breaks: list = field(default_factory=list)             # dormers, gablets, ridge steps that break a big roof plane (L-F2)
    pents: int = 0
    bridges: int = 0
    wheel_fallback: set = field(default_factory=set)       # block cells of the static wheel (inside the display's volume)
    wheel_cells: set = field(default_factory=set)          # slot interior cells
    race_cells: set = field(default_factory=set)           # enclosed water of channels
    water_cells: set = field(default_factory=set)
    vents: set = field(default_factory=set)               # louvre / vent cells (not chimneys)
    big_doors: list = field(default_factory=list)          # {cells: [(x,y,z)], lintel: [(x,y,z)]}
    water_exits: set = field(default_factory=set)         # water cells allowed on the box border (stream joins)
    skip: set = field(default_factory=set)                 # rule names skipped on purpose (with a comment in the generator)


def bid(data: str | None) -> str:
    return "air" if data is None else B.block_id(data)


def is_light(data: str | None) -> bool:
    if data is None:
        return False
    b = bid(data)
    if b == "campfire":
        return B.props_of(data).get("lit") == "true"
    return any(w in b for w in LIGHT_WORDS)


def is_prop(b: str) -> bool:
    return b in PLANT_IDS or any(w in b for w in PROP_WORDS)


# --- windows ---------------------------------------------------------------------------------------
def _passable(b: str) -> bool:
    """Blocks the inside/outside flood fill may flow through: air and small things standing in front of windows."""
    return b == "air" or b.startswith("potted_") or b in PLANT_IDS or b.endswith(("_trapdoor", "lantern", "_wall_sign",
                                                                                 "campfire",
                                                                                 "_carpet")) or "barrel" in b


def _exterior(g: dict, lo, hi, seal: set) -> set:
    """Air cells reachable from outside the box (flood fill through air, not through anything else)."""
    seen = set()
    start = (lo[0] - 1, lo[1] - 1, lo[2] - 1)
    q = deque([start])
    seen.add(start)
    while q:
        x, y, z = q.popleft()
        for dx, dy, dz in DIRS6:
            n = (x + dx, y + dy, z + dz)
            if n in seen or not all(lo[i] - 1 <= n[i] <= hi[i] + 1 for i in range(3)):
                continue
            if n in seal or (n in g and not _passable(bid(g[n]))):
                continue
            seen.add(n)
            q.append(n)
    return seen


def windows(g: dict) -> list[list[tuple]]:
    cells = {p for p, d in g.items() if bid(d) in ("glass_pane",) or bid(d).endswith("_stained_glass_pane")}
    out, seen = [], set()
    for c in sorted(cells):
        if c in seen:
            continue
        comp, q = [], deque([c])
        seen.add(c)
        while q:
            p = q.popleft()
            comp.append(p)
            for dx, dy, dz in DIRS6:
                n = (p[0] + dx, p[1] + dy, p[2] + dz)
                if n in cells and n not in seen:
                    seen.add(n)
                    q.append(n)
        out.append(comp)
    return out


def check_windows(g: dict, notes: Notes, errors: list) -> None:
    pts = [p for p in g]
    lo = tuple(min(p[i] for p in pts) for i in range(3))
    hi = tuple(max(p[i] for p in pts) for i in range(3))
    ext = _exterior(g, lo, hi, notes.seal)
    solid = lambda p: p in g and B.sturdy(bid(g[p]))  # noqa: E731
    for comp in windows(g):
        if any(c in notes.exempt_windows for c in comp):
            continue
        xs = {c[0] for c in comp}
        zs = {c[2] for c in comp}
        name = f"window at {min(comp)}"
        if len(xs) > 1 and len(zs) > 1:
            continue                                   # a bay: judged by eye
        if len(zs) == 1 and len(xs) >= 1 and (len(xs) > 1 or (solid((min(xs) - 1, comp[0][1], comp[0][2]))
                                                              and solid((max(xs) + 1, comp[0][1], comp[0][2])))):
            normal = (0, 0, 1)
        else:
            normal = (1, 0, 0)
        out = None
        for sgn in (1, -1):
            d = (normal[0] * sgn, 0, normal[2] * sgn)
            if any((c[0] + d[0], c[1], c[2] + d[2]) in ext for c in comp):
                out = d
                break
        if out is None:
            errors.append(f"{name}: no outside (enclosed or sealed)")
            continue
        inn = (-out[0], 0, -out[2])
        lat = (1, 0, 0) if normal == (0, 0, 1) else (0, 0, 1)
        ymin = min(c[1] for c in comp)
        ymax = max(c[1] for c in comp)
        bottom = [c for c in comp if c[1] == ymin]

        def at(c, dx=0, dy=0, dz=0):
            return g.get((c[0] + dx, c[1] + dy, c[2] + dz))

        def sill_cell(c):
            for p in ((c[0] + out[0], c[1] - 1, c[2] + out[2]), (c[0], c[1] - 1, c[2]),
                      (c[0] + out[0], c[1], c[2] + out[2])):
                v = g.get(p)
                if v and (bid(v).endswith(("_trapdoor", "_slab", "_stairs")) or bid(v).startswith("potted_")
                          or bid(v).endswith("_wall")):
                    return True
            return False

        has_sill = sum(sill_cell(c) for c in bottom) >= max(1, len(bottom) // 2 + len(bottom) % 2) \
            if bottom else False
        box = any((bid(g.get((c[0] + out[0], c[1] + dy, c[2] + out[2]))).startswith(("potted_", "flowering_azalea"))
                   or bid(g.get((c[0] + out[0], c[1] + dy, c[2] + out[2]))) in PLANT_IDS)
                  for c in bottom for dy in (0, 1))
        # shutters: an open trapdoor beside each end of the window (in the wall plane or in front of it)
        ends = []
        for sgn in (1, -1):
            e = max(comp, key=lambda c: sgn * (c[0] * lat[0] + c[2] * lat[2]))
            ends.append([(e[0] + sgn * lat[0] + k * out[0], c_y, e[2] + sgn * lat[2] + k * out[2])
                         for k in (0, 1) for c_y in range(ymin, ymax + 1)])
        def shut(cells):
            return any(g.get(p) and bid(g[p]).endswith("_trapdoor") and B.props_of(g[p]).get("open") == "true"
                       for p in cells)
        shutters = shut(ends[0]) and shut(ends[1])
        lit = False
        for c in comp:
            for k in range(1, 4):
                for l in range(-2, 3):
                    for dy in range(-3, 4):
                        p = (c[0] + inn[0] * k + lat[0] * l, c[1] + dy, c[2] + inn[2] * k + lat[2] * l)
                        if is_light(g.get(p)):
                            lit = True
        if not has_sill:
            errors.append(f"{name}: needs a sill (trapdoor, slab or stair under it)")
        if not lit:
            errors.append(f"{name}: needs a lit interior (lantern, campfire, shroomlight ...) within 3 blocks behind it")


# --- depth planes ----------------------------------------------------------------------------------
def check_depth(g: dict, notes: Notes, errors: list, max_run: int = 4) -> None:
    if notes.body_box is None:
        return
    x1, z1, x2, z2 = notes.body_box
    y1, y2 = notes.wall_rows
    solid = {p: d for p, d in g.items() if bid(d) != "air" and not any(w in bid(d) for w in DEPTH_IGNORE_WORDS)}
    sides = {
        "north": (range(x1, x2 + 1), lambda u, y: [(u, y, z) for z in range(z1 - 3, z2 + 1)]),
        "south": (range(x1, x2 + 1), lambda u, y: [(u, y, z) for z in range(z2 + 3, z1 - 1, -1)]),
        "west": (range(z1, z2 + 1), lambda u, y: [(x, y, u) for x in range(x1 - 3, x2 + 1)]),
        "east": (range(z1, z2 + 1), lambda u, y: [(x, y, u) for x in range(x2 + 3, x1 - 1, -1)]),
    }
    for side, (cols, ray) in sides.items():
        sig = []
        for u in cols:
            col = []
            for y in range(y1, y2 + 1):
                depth = None
                for i, p in enumerate(ray(u, y)):
                    if p in solid:
                        depth = i
                        break
                col.append(depth)
            sig.append(tuple(col))
        run, worst, start = 1, 1, 0
        for i in range(1, len(sig)):
            run = run + 1 if sig[i] == sig[i - 1] else 1
            if run > worst:
                worst, start = run, i - run + 1
        if worst > max_run:
            errors.append(f"depth: the {side} facade is flat for {worst} columns in a row (from column {start}); "
                          f"add a post, reveal, bay, buttress or shutters at least every {max_run} blocks")


# --- ramps, roofs, plinth, chimney, volumes ---------------------------------------------------------
def check_structure(g: dict, notes: Notes, points: dict, errors: list) -> None:
    ids = [bid(d) for p, d in g.items() if p[1] >= 0]                 # building materials; the ground layers are judged by R15
    count: dict[str, int] = {}
    for b in ids:
        count[b] = count.get(b, 0) + 1
    # volumes
    vols = notes.volumes
    if len(vols) < 2:
        errors.append(f"volumes: {len(vols)} found, at least 2 needed (R1)")
    else:
        keys = {(v["footprint"], v["top"]) for v in vols}
        if len(keys) < 2:
            errors.append("volumes: the volumes must differ in footprint or height")
        if len({v["top"] for v in vols}) < 2:
            errors.append("volumes: give the volumes at least two different roof heights (R2)")
    # ramps
    ramped = set()
    for role, members in notes.ramps.items():
        ramped |= set(members)
        tot = sum(count.get(m, 0) for m in members) or 1
        present = [m for m in members if count.get(m, 0) >= 1 and count.get(m, 0) / tot >= 0.02]
        if len(present) < 3:
            errors.append(f"ramp '{role}': only {len(present)} of {members} are used (each >= 1 block and >= 2% of the ramp); a material needs "
                          "a ramp of at least 3 blocks (R6)")
            continue
        total = sum(count.get(m, 0) for m in members)
        top = max(members, key=lambda m: count.get(m, 0))
        if count[top] / total > 0.93:
            errors.append(f"ramp '{role}': {top} is {count[top] / total:.0%} of the ramp; the variants are noise (R7: 7-60%)")
        if count[top] / total < 0.33:
            errors.append(f"ramp '{role}': no dominant block ({top} only {count[top] / total:.0%}); a ramp has a base block")
        for m in present:
            if m != top and count[m] / total > 0.45:
                errors.append(f"ramp '{role}': {m} is {count[m] / total:.0%}, an even splat (R7: variants 10-30%)")
    for b, n in sorted(count.items()):
        if n >= 30 and b not in ramped and not any(w in b for w in RAMP_EXEMPT_WORDS):
            errors.append(f"material {b} is used {n} times but belongs to no ramp (R6)")
    # roofs
    if not notes.roofs:
        errors.append("roofs: none registered")
    for r in notes.roofs:
        if r["overhang"] < 1:
            errors.append(f"roof {r['name']}: eave overhang {r['overhang']} < 1 (R8)")
        eave = {bid(g.get(c)) for c in r["eave"]}
        fld = {bid(g.get(c)) for c in r["field"]}
        if "air" in eave or "air" in fld:
            errors.append(f"roof {r['name']}: registered roof cells that are air")
        if len(eave | fld) < 2 or not (eave - fld or fld - eave):
            errors.append(f"roof {r['name']}: eave and roof field need 2 different materials (R8), got {sorted(eave | fld)}")
    # plinth
    pl = notes.plinth
    if pl is None:
        errors.append("plinth: not registered (R12)")
    else:
        if not 1 <= pl["courses"] <= 2:
            errors.append(f"plinth: {pl['courses']} courses, must be 1-2 (R12)")
        ring = pl["ring"]
        good = sum(1 for (x, z) in ring if all(bid(g.get((x, y, z))) in pl["blocks"] or bid(g.get((x, y, z))) == "air"
                                              or bid(g.get((x, y, z))).endswith(("_door", "_stairs", "_slab", "_bars"))
                                              for y in range(pl["courses"])))
        if ring and good / len(ring) < 0.85:
            errors.append(f"plinth: only {good}/{len(ring)} ring cells are plinth blocks")
        above = sum(1 for (x, z) in ring if bid(g.get((x, pl["courses"], z))) not in pl["blocks"]
                    and bid(g.get((x, pl["courses"], z))) != "air")
        if ring and above / len(ring) < 0.5:
            errors.append("plinth: the wall above the plinth is the same material (the plinth must read as a base)")
    # chimney
    if len(notes.chimneys) > 1:
        errors.append(f"chimney: {len(notes.chimneys)} registered; at most ONE per building (R11): {notes.chimneys}")
    for key in ("smoke", "steam"):
        v = points.get(key)
        if v is not None and isinstance(v[0], list) and len(v) > 1:
            errors.append(f"points.{key} has {len(v)} entries; at most one (R11)")
    if notes.roof_cells:
        errors += chimney_components(g, notes)


def chimney_components(g: dict, notes: Notes) -> list[str]:
    top = {}
    for (x, y, z), d in g.items():
        if bid(d) != "air" and y >= 0:
            if (x, z) not in top or y > top[(x, z)][0]:
                top[(x, z)] = (y, bid(d))
    roof_h = {}
    for (x, y, z) in notes.roof_cells:
        roof_h[(x, z)] = max(roof_h.get((x, z), -1), y)
    marked = set()
    for (x, z), (y, b) in top.items():
        if any(w in b for w in ("lantern", "fence", "campfire", "chain", "rod", "slab", "stairs")) and False:
            continue
        near = [roof_h[(x + dx, z + dz)] for dx in range(-2, 3) for dz in range(-2, 3) if (x + dx, z + dz) in roof_h]
        if not near:
            continue
        if y >= max(near) + 2 and not any(w in b for w in ("lantern", "fence", "chain", "rod", "log")) \
                and (x, y, z) not in notes.roof_cells:
            marked.add((x, z))
    comps, seen = 0, set()
    for c in sorted(marked):
        if c in seen:
            continue
        comps += 1
        q = deque([c])
        seen.add(c)
        while q:
            x, z = q.popleft()
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    n = (x + dx, z + dz)
                    if n in marked and n not in seen:
                        seen.add(n)
                        q.append(n)
    return [f"chimney: {comps} stacks rise above the roofline (R11 allows exactly one)"] if comps > 1 else []


# --- front yard, props, organic edges -------------------------------------------------------------------------------
def check_front_yard(g: dict, notes: Notes, errors: list) -> None:
    fy = notes.front_yard
    if not fy:
        return
    x1, z1, x2, z2 = fy["rect"]
    cx1, cx2 = fy["corridor"]
    for (x, y, z), d in g.items():
        if bid(d) == "air" or not (x1 <= x <= x2 and z1 <= z <= z2):
            continue
        if cx1 <= x <= cx2 and 0 <= y <= 2 and not bid(d).startswith(("potted_",)) and bid(d) not in PLANT_IDS:
            errors.append(f"front yard: {bid(d)} at {(x, y, z)} blocks the 3-wide corridor to the door (R19)")
        elif y > fy.get("max_y", 1) and not (bid(d).endswith("lantern") or "fence" in bid(d)):
            errors.append(f"front yard: {bid(d)} at {(x, y, z)} stands in the clear yard (R19)")


def check_props(g: dict, errors: list) -> None:
    """D1: no lone props. A prop needs a non-prop block (structure or ground treatment) or 2 more props within 1."""
    for p, d in g.items():
        b = bid(d)
        if not is_prop(b) or b == "air":
            continue
        near_struct, near_prop = False, 0
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    if (dx, dy, dz) == (0, 0, 0):
                        continue
                    n = g.get((p[0] + dx, p[1] + dy, p[2] + dz))
                    if n is None or bid(n) == "air":
                        continue
                    nb = bid(n)
                    if is_prop(nb):
                        near_prop += 1
                    elif nb != "grass_block":
                        near_struct = True
        if not near_struct and near_prop < 2:
            errors.append(f"lone prop: {b} at {p} (D1: every prop is anchored to a wall, path, bed or other props)")


def check_organic(g: dict, errors: list, max_run: int = 7, region: tuple | None = None, extra_inside=None) -> None:
    """R15: ground treatments (paths, gravel, coarse dirt, beds ...) and hedges never run straight for more than
    `max_run` blocks along their edge."""
    mask, built = set(), set()
    for (x, y, z), d in g.items():
        b = bid(d)
        if y in (-1, -2) and b in ORGANIC_GROUND and b != "grass_block":
            mask.add((x, z))
        if y >= 0 and b != "air" and not is_prop(b) and not b.endswith("leaves") and "fence" not in b:
            built.add((x, z))
    inside = mask | built | set(extra_inside or ())
    xs = [p[0] for p in mask] or [0]
    zs = [p[1] for p in mask] or [0]
    for name, (dx, dz) in (("north", (0, -1)), ("south", (0, 1)), ("west", (-1, 0)), ("east", (1, 0))):
        edge = [(x, z) for (x, z) in mask if (x + dx, z + dz) not in inside
                and (region is None or (region[0] < x + dx < region[2] and region[1] < z + dz < region[3]))]
        es = set(edge)
        for (x, z) in sorted(es):
            prev = (x - 1, z) if dz else (x, z - 1)
            if prev in es:
                continue
            n, cur = 1, (x + 1, z) if dz else (x, z + 1)
            while cur in es:
                n += 1
                cur = (cur[0] + (1 if dz else 0), cur[1] + (0 if dz else 1))
            if n > max_run:
                errors.append(f"organic edge: a ground treatment runs straight {n} blocks along its {name} edge "
                              f"from {(x, z)} (R15 allows {max_run})")
    # hedges / leaf rows
    leaves = {(x, z) for (x, y, z), d in g.items() if y >= 0 and bid(d).endswith("_leaves") and (x, y - 1, z) in g
              and "log" not in bid(g[(x, y - 1, z)])}
    for axis in (0, 1):
        for p in sorted(leaves):
            prev = (p[0] - 1, p[1]) if axis == 0 else (p[0], p[1] - 1)
            if prev in leaves:
                continue
            n, cur = 1, p
            while True:
                cur = (cur[0] + 1, cur[1]) if axis == 0 else (cur[0], cur[1] + 1)
                if cur not in leaves:
                    break
                n += 1
            if n > max_run:
                errors.append(f"organic edge: a hedge runs straight {n} blocks from {p} (R15)")


# --- entry points ------------------------------------------------------------------------------------------------
def check_ids_against_client_jar(palette: dict, errors: list) -> None:
    """If the client jar is available: every block id exists in 26.3 and every state name/value is a real one."""
    try:
        import block_models as bm
    except ImportError:
        return
    if not bm.available():
        return
    for c, data in palette.items():
        block, props = bm.parse(data)
        if block in ("air", "water"):
            continue
        bs = bm.blockstate(block)
        if bs is None:
            errors.append(f"block {block} has no blockstate in the 26.3 client jar (wrong id?)")
            continue
        if "variants" in bs:
            keys = [dict(p.split("=") for p in k.split(",")) if k else {} for k in bs["variants"]]
            known = {}
            for k in keys:
                for n, v in k.items():
                    known.setdefault(n, set()).add(v)
            for n, v in props.items():
                if n in known and v not in known[n]:
                    errors.append(f"{data}: value {n}={v} not in the jar's variants {sorted(known[n])}")


def lint_house(g: dict, notes: Notes, points: dict, palette: dict | None = None) -> None:
    errors: list[str] = []
    g = {p: d for p, d in g.items() if bid(d) != "air" or True}
    if "windows" not in notes.skip:
        check_windows(g, notes, errors)
    if "depth" not in notes.skip:
        check_depth(g, notes, errors)
    check_structure(g, notes, points, errors)
    check_front_yard(g, notes, errors)
    check_props(g, errors)
    check_organic(g, errors, region=notes.ground_region)
    if palette:
        check_ids_against_client_jar(palette, errors)
    if errors:
        raise LintError("build lint failed (design/build-lint.md):\n  - " + "\n  - ".join(sorted(set(errors))))


def lint_decor(blocks: dict, scenes: list[dict], plot_cells: set | None = None, palette: dict | None = None,
               extra_inside=None) -> None:
    """blocks: {(x, y, z): data} plot cells (ground at y -1/-2); scenes: [{name, cells, anchor, ground_cells}]."""
    errors: list[str] = []
    check_props(blocks, errors)
    check_organic(blocks, errors, extra_inside=extra_inside)
    for s in scenes:
        cells = s["cells"]
        types = {bid(blocks[c]) for c in cells if c in blocks}
        above = [c for c in cells if c[1] >= 0]
        ground = [c for c in cells if c[1] < 0]
        if not s.get("anchor"):
            errors.append(f"scene {s['name']}: no anchor (D1)")
        if not ((len(cells) >= 5 and len(types) >= 3) or len(ground) >= 9):
            errors.append(f"scene {s['name']}: {len(cells)} blocks / {len(types)} types; needs >= 5 blocks and 3 types "
                          "or a 9-cell ground patch (D2)")
    if errors:
        raise LintError("decor lint failed (design/build-lint.md):\n  - " + "\n  - ".join(sorted(set(errors))))


def lint_fence(style: dict) -> None:
    errors = []
    gaps = style.get("post-spacing")
    if not gaps:
        errors.append("fence: post-spacing missing (an even post-every rhythm looks like a grid, R16)")
    elif len(set(gaps)) < 3 or max(gaps) - min(gaps) < 2:
        errors.append(f"fence: post-spacing {gaps} is too regular (R16): use at least 3 different gaps")
    tg = style.get("topper-spacing")
    if tg and len(set(tg)) < 2:
        errors.append("fence: topper-spacing needs at least 2 different values")
    if errors:
        raise LintError("fence lint failed:\n  - " + "\n  - ".join(errors))
