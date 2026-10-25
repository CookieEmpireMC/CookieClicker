"""Farm-place lint L-F1..L-F11 (docs/research/farm-buildings.md section 10), on top of build_lint (R1-R21).

    lint_place(g, notes, points, palette, hero=True, name="mill", water_frontage=(x0, z0, x1, z1), roof_share=None)

g = {(x, y, z): block data} (design y, resolved), notes = the Build's Notes, points = the place's `points`. A failing place
is not written (LintError lists every problem). Each rule is also a function that returns a list of messages, so the tests
can call it on tiny grids. Rule text: design/build-lint.md ("Farm places").
"""
from __future__ import annotations

import math
from collections import deque

import blocks as B
from build_lint import LintError, Notes, ORGANIC_GROUND, bid

ROOF_FORMS = {"gable", "half-hip", "hip", "catslide", "cross-gable", "cone", "pyramid"}
HERO_BLOCKS = (1500, 3500)
SMALL_BLOCKS = (600, 1200)
YARD_MIN = (11, 7)
WATER_FRONTAGE_MIN = 0.25
STAIR_SHARE_MIN = 0.15


def _is_air(d) -> bool:
    return d is None or bid(d) == "air"


def lf1_volumes(notes: Notes, hero: bool) -> list[str]:
    """Hero buildings: at least two volumes with a roof, each at least 20% of the summed footprints, differing in ridge
    height or axis."""
    if not hero:
        return []
    vols = [v for v in notes.volumes if v.get("footprint")]
    areas = [(v["footprint"][2] - v["footprint"][0] + 1) * (v["footprint"][3] - v["footprint"][1] + 1) for v in vols]
    total = sum(areas) or 1
    big = [v for v, a in zip(vols, areas) if a / total >= 0.20]
    if len(big) < 2:
        return [f"L-F1 volumes: {len(big)} roof volume(s) cover >= 20% of the footprint, need 2 "
                f"({[(v['name'], round(a / total, 2)) for v, a in zip(vols, areas)]})"]
    if len({v["top"] for v in big}) < 2:
        return ["L-F1 volumes: the big volumes all have the same ridge height"]
    return []


def lf2_roof_forms(g: dict, notes: Notes) -> list[str]:
    errs = []
    forms = {r["form"] for r in notes.roof_info if r["form"] in ROOF_FORMS}
    if len(forms) < 2:
        errs.append(f"L-F2 roof diversity: forms used {sorted(forms)}, need at least 2 of {sorted(ROOF_FORMS)}")
    cells = {p: g[p] for p in notes.roof_cells if p in g and bid(g[p]).endswith("_stairs")}
    by_xz: dict = {}
    for (x, y, z), d in cells.items():
        by_xz.setdefault((x, z, B.props_of(d)["facing"]), []).append(y)
    seen = set()
    for key in sorted(by_xz):
        if key in seen:
            continue
        comp, q = [], deque([key])
        seen.add(key)
        while q:
            k = q.popleft()
            comp.append(k)
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                n = (k[0] + dx, k[1] + dz, k[2])
                if n in by_xz and n not in seen:
                    seen.add(n)
                    q.append(n)
        xs = [k[0] for k in comp]
        zs = [k[1] for k in comp]
        w, d = max(xs) - min(xs) + 1, max(zs) - min(zs) + 1
        if w > 7 and d > 7:
            near = [b for b in notes.breaks if min(xs) - 2 <= b["cell"][0] <= max(xs) + 2
                    and min(zs) - 2 <= b["cell"][2] <= max(zs) + 2]
            if not near:
                errs.append(f"L-F2 roof plane: a {w} x {d} roof plane facing {comp[0][2]} at {min(xs), min(zs)} has no "
                            "dormer, gablet or ridge step (planes over 7 x 7 need a break)")
    return errs


def lf3_yard(g: dict, points: dict, notes: Notes) -> list[str]:
    errs = []
    yd = points["yard"]
    x0, x1 = sorted((yd["from"][0], yd["to"][0]))
    z0, z1 = sorted((yd["from"][1], yd["to"][1]))
    w, d = x1 - x0 + 1, z1 - z0 + 1
    if not ((w >= YARD_MIN[0] and d >= YARD_MIN[1]) or (d >= YARD_MIN[0] and w >= YARD_MIN[1])):
        errs.append(f"L-F3 yard: {w} x {d} is smaller than {YARD_MIN[0]} x {YARD_MIN[1]}")
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            ground = bid(g.get((x, -1, z)))
            if ground in ("water", "air") or ground.endswith("leaves"):
                errs.append(f"L-F3 yard: ground at {(x, z)} is {ground}")
            edge = x in (x0, x1) or z in (z0, z1)
            for y in range(0, 6):
                p = (x, y, z)
                if _is_air(g.get(p)):
                    continue
                b = bid(g[p])
                if edge and y <= 2:
                    continue                                   # props and fences stand on the rim only
                errs.append(f"L-F3 yard: {b} at {p} stands in the clear yard")
    for k in ("hologram", "ripe"):
        p = points[k]
        if not (x0 - 1 <= p[0] <= x1 + 1 and z0 - 1 <= p[2] <= z1 + 1):
            errs.append(f"L-F3 yard: {k} {p} is not inside or next to the yard")
    p = points["panel"]["at"]
    if not (x0 - 1 <= p[0] <= x1 + 1 and z0 - 1 <= p[2] <= z1 + 1):
        errs.append(f"L-F3 yard: panel {p} is not inside or next to the yard")
    vp = points["viewpoint"]["at"]
    if not (x0 - 2 <= vp[0] <= x1 + 2 and z0 - 2 <= vp[2] <= z1 + 2):
        errs.append(f"L-F3 yard: viewpoint {vp} is not at the yard")
    return errs


def water_cells(g: dict) -> set:
    return {p for p, d in g.items() if bid(d) == "water"}


def lf4_clearance(g: dict, points: dict, notes: Notes, size: tuple, ground: int) -> list[str]:
    errs = []
    if "wheel" in points:
        ax, ay, az = points["wheel"]["at"]
        axis = points["wheel"]["axis"]
        allowed = notes.wheel_fallback
        for (x, y, z), d in g.items():
            if _is_air(d) or bid(d) == "water" or (x, y, z) in allowed:
                continue
            dx, dy, dz = x - ax, y - ay, z - az
            along, ra, rb = (dz, dx, dy) if axis == "z" else (dx, dz, dy)
            if abs(along) <= 1 and math.hypot(ra, rb) <= 3.6 and (ra, rb) != (0, 0):
                errs.append(f"L-F4 clearance: {bid(d)} at {(x, y, z)} is inside the waterwheel's disc")
    sx, sy, sz = size
    for (x, y, z) in sorted(water_cells(g)):
        if y < -ground:
            continue
        if (x, y, z) in notes.water_exits:
            continue
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, -1, 0)):
            n = (x + dx, y + dy, z + dz)
            if not (0 <= n[0] < sx and 0 <= n[2] < sz):
                errs.append(f"L-F4 water: water at {(x, y, z)} reaches the edge of the box (declare it in water_exits)")
            elif n[1] >= -ground and (n not in g or _is_air(g[n])):
                errs.append(f"L-F4 water: water at {(x, y, z)} is open towards {(dx, dy, dz)} (water must be enclosed)")
            if len(errs) > 20:
                return errs
    for (x, y, z), d in g.items():
        if bid(d) == "cocoa":
            ok = False
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if bid(g.get((x + dx, y, z + dz))) == "jungle_log":
                    ok = True
            if not ok:
                errs.append(f"L-F4 cocoa: pod at {(x, y, z)} has no jungle log beside it")
    return errs


def lf5_doors(g: dict, notes: Notes) -> list[str]:
    errs = []
    for door in notes.big_doors:
        cells = door["cells"]
        if len(cells) >= 3 and not all(not _is_air(g.get(c)) for c in door.get("lintel", [])):
            errs.append(f"L-F5 door: the {len(cells)}-wide door at {cells[0]} has no lintel one block wider each side")
    return errs


def lf6_ground(g: dict, notes: Notes) -> list[str]:
    errs = []
    top = {(x, z): bid(d) for (x, y, z), d in g.items() if y == -1 and _is_air(g.get((x, 0, z)))}
    for (x, z), b in sorted(top.items()):
        if b not in ORGANIC_GROUND or b == "grass_block":
            continue
        if all(top.get((x + i, z + j)) == b for i in range(5) for j in range(5)):
            errs.append(f"L-F6 ground: a 5 x 5 patch of {b} from {(x, z)} (mix a second block into every patch)")
            break
    surface = max((y for (x, y, z), d in g.items() if bid(d) == "water"), default=None)
    if surface is not None:
        mask = {(x, z) for (x, y, z), d in g.items() if bid(d) == "water" and y == surface}
        for name, (dx, dz) in (("north", (0, -1)), ("south", (0, 1)), ("west", (-1, 0)), ("east", (1, 0))):
            edge = {(x, z) for (x, z) in mask if (x + dx, z + dz) not in mask
                    and bid(g.get((x + dx, surface, z + dz))) in ("dirt", "coarse_dirt", "mud", "clay", "moss_block",
                                                                    "grass_block", "gravel", "air")}
            for (x, z) in sorted(edge):
                prev = (x - 1, z) if dz else (x, z - 1)
                if prev in edge:
                    continue
                n, cur = 1, ((x + 1, z) if dz else (x, z + 1))
                while cur in edge:
                    n += 1
                    cur = (cur[0] + (1 if dz else 0), cur[1] + (0 if dz else 1))
                if n > 7:
                    errs.append(f"L-F6 ground: the pond's {name} shore runs straight for {n} cells from {(x, z)} (max 7)")
    return errs


def lf7_vents(g: dict, notes: Notes) -> list[str]:
    errs = []
    if len(notes.chimneys) > 1:
        errs.append(f"L-F7 chimney: {len(notes.chimneys)} chimneys registered, at most 1")
    for (x, y, z), d in g.items():
        if bid(d) == "campfire" and B.props_of(d).get("lit") == "true" and y >= 0:
            above = [bid(g.get((x, yy, z))) for yy in range(y + 1, y + 12)]
            vent = any((x, yy, z) in notes.vents for yy in range(y + 1, y + 12))
            if not vent and any(a != "air" for a in above):
                errs.append(f"L-F7 vent: the campfire at {(x, y, z)} is covered and has no louvre or open roof above it")
    return errs


def mean_luminance(block: str) -> float | None:
    try:
        import block_models as bm
        if not bm.available():
            return None
        q = bm.block_quads(f"minecraft:{block}")[0]
        arr, _ = bm.texture(q["tex"])
        px = arr[arr[..., 3] > 8][:, :3].astype(float)
        return float((0.299 * px[:, 0] + 0.587 * px[:, 1] + 0.114 * px[:, 2]).mean())
    except Exception:
        return None


def _ramp_lum(g: dict, members: list[str]) -> float | None:
    count = {m: 0 for m in members}
    for (x, y, z), d in g.items():
        if y >= 0 and bid(d) in count:
            count[bid(d)] += 1
    tot = sum(count.values())
    if not tot:
        return None
    acc = 0.0
    for m, c in count.items():
        if c:
            lum = mean_luminance(m)
            if lum is None:
                return None
            acc += lum * c
    return acc / tot


def lf8_contrast(g: dict, notes: Notes) -> list[str]:
    errs = []
    r = notes.ramps
    for a, b, need, label in (("timber", "plaster", 40.0, "frame vs infill"), ("roof", "stone", 20.0, "roof vs walls")):
        if a in r and b in r:
            la, lb = _ramp_lum(g, r[a]), _ramp_lum(g, r[b])
            if la is not None and lb is not None and abs(la - lb) < need:
                errs.append(f"L-F8 contrast: {label} differ by only {abs(la - lb):.0f} luminance (need {need:.0f})")
    scenery = ("leaves", "grass", "dirt", "vine", "fern", "mud", "clay", "moss", "lily", "flower", "tulip", "poppy", "dandelion",
               "cornflower", "daisy", "azure", "farmland", "wheat", "carrots", "potatoes", "gravel", "path")
    solid = [bid(d) for (x, y, z), d in g.items() if y >= 0 and bid(d) != "air" and bid(d) != "water"
             and not any(w in bid(d) for w in scenery)]
    stairs = sum(1 for b in solid if b.endswith("_stairs"))
    if solid and stairs / len(solid) < STAIR_SHARE_MIN:
        errs.append(f"L-F8 stairs: {stairs / len(solid):.0%} of the blocks are stairs, need {STAIR_SHARE_MIN:.0%}")
    return errs


def lf9_roof_heights(notes: Notes, roof_share: tuple | None = None, share_value: float | None = None) -> list[str]:
    errs = []
    if len(notes.roof_heights) < 3:
        errs.append(f"L-F9 roof heights: {sorted(notes.roof_heights)}; hero buildings need roofs at 3 or more heights")
    if roof_share and share_value is not None and not roof_share[0] <= share_value <= roof_share[1]:
        errs.append(f"L-F9 roof share: {share_value:.0%} of the facade is roof, need {roof_share[0]:.0%}-{roof_share[1]:.0%}")
    return errs


def lf10_water(g: dict, frontage: tuple | None) -> list[str]:
    if not frontage:
        return []
    x0, z0, x1, z1 = frontage
    cols = {(x, z) for (x, y, z), d in g.items() if bid(d) == "water" and x0 <= x <= x1 and z0 <= z <= z1}
    share = len(cols) / ((x1 - x0 + 1) * (z1 - z0 + 1))
    if share < WATER_FRONTAGE_MIN:
        return [f"L-F10 water: {share:.0%} of the frontage {frontage} is water, need {WATER_FRONTAGE_MIN:.0%}"]
    return []


def lf11_gables(g: dict, notes: Notes) -> list[str]:
    errs = []
    field_ids = {bid(g[c]) for r in notes.roofs for c in r["field"] if c in g}
    for gb in notes.gables:
        rake = [c for c in gb.get("rake", []) if c in g]
        if not rake:
            errs.append(f"L-F11 gable {gb['name']}: no rake cells")
            continue
        if gb["finish"] == "bargeboard":
            ids = {bid(g[c]) for c in rake if bid(g[c]).endswith("_stairs")}
            if not ids or ids <= field_ids and len(ids) == len(field_ids):
                errs.append(f"L-F11 gable {gb['name']}: the rake row uses the same stairs as the field (no bargeboard)")
        elif gb["finish"] == "coping":
            if not all(bid(g[c]).endswith("_slab") for c in rake):
                errs.append(f"L-F11 gable {gb['name']}: coping cells are not slabs")
        elif gb["finish"] == "corbie":
            pass
        else:
            errs.append(f"L-F11 gable {gb['name']}: unknown finish {gb['finish']}")
    return errs


def _family(block: str) -> str:
    f = block.removesuffix("_stairs").removesuffix("_slab")
    return f[:-1] if f.endswith("s") else f


def lf12_roof_families(g: dict, notes: Notes) -> list[str]:
    """Every roof field uses one block family (stairs, slabs, risers), the trim (eave and rake rows) one other family."""
    field = {_family(bid(g[c])) for r in notes.roofs for c in r["field"] if c in g and bid(g[c]).endswith(("_stairs", "_slab"))}
    trim = {_family(bid(g[c])) for r in notes.roofs for c in r["eave"] if c in g and bid(g[c]).endswith(("_stairs", "_slab"))}
    errs = []
    if len(field) > 1:
        errs.append(f"L-F12 roof: the roof field mixes {len(field)} block families {sorted(field)}; use one")
    if len(trim) > 1:
        errs.append(f"L-F12 roof: the trim (eave and rake rows) mixes {len(trim)} block families {sorted(trim)}; use one")
    return errs


def ld1_gates(g: dict) -> list[str]:
    """Every fence gate stands between two full blocks (posts or pillars), never beside a bare fence piece or air."""
    errs = []
    for (x, y, z), d in g.items():
        if not bid(d).endswith("_fence_gate"):
            continue
        along = (1, 0) if B.props_of(d).get("facing") in ("north", "south") else (0, 1)
        for s in (1, -1):
            n = g.get((x + along[0] * s, y, z + along[1] * s))
            nb = bid(n) if n else "air"
            if nb == "air" or nb.endswith(("_fence", "_trapdoor", "_slab", "_stairs")):
                errs.append(f"L-D1 gate: the gate at {(x, y, z)} is not flanked by a full-block post ({nb})")
    return errs


def ld2_trapdoors(g: dict, budget: int) -> list[str]:
    errs = []
    cells = {p for p, d in g.items() if bid(d).endswith("_trapdoor") and p[1] >= 0}
    if len(cells) > budget:
        errs.append(f"L-D2 trapdoors: {len(cells)} trapdoors, budget {budget}")
    for (x, y, z) in cells:
        for dx, dz in ((1, 0), (0, 1)):
            run = 1
            while (x + dx * run, y, z + dz * run) in cells:
                run += 1
            if run >= 6 and (x - dx, y, z - dz) not in cells:
                errs.append(f"L-D2 trapdoors: a row of {run} trapdoors at {(x, y, z)}")
    return errs


def ld3_fence_family(g: dict) -> list[str]:
    fam = {bid(d) for p, d in g.items() if p[1] >= 0 and bid(d).endswith("_fence")}
    return [f"L-D3 fence family: {sorted(fam)}; one fence block family per place"] if len(fam) > 1 else []


def ld4_race_open(g: dict, notes: Notes) -> list[str]:
    errs = []
    for (x, y, z) in notes.race_cells:
        if not _is_air(g.get((x, y + 1, z))):
            errs.append(f"L-D4 race: {bid(g[(x, y + 1, z)])} sits over the race water at {(x, y, z)}")
    return errs


def ld5_flume(g: dict, notes: Notes) -> list[str]:
    errs = []
    for (x, y, z) in notes.flume:
        ok = any(not _is_air(g.get((x, y - k, z))) and (x, y - k, z) not in notes.flume for k in range(1, 4))
        ok = ok or any(not _is_air(g.get((x + dx, y, z + dz))) and (x + dx, y, z + dz) not in notes.flume
                       for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if not ok:
            errs.append(f"L-D5 flume: the flume block at {(x, y, z)} has no support within 3 blocks")
    return errs


def ld6_pen(g: dict, notes: Notes) -> list[str]:
    pen = notes.pen
    if not pen:
        return []
    errs = []
    area, build = len(set(pen["cells"])), len(set(pen["building"]))
    if build and area < 1.3 * build:
        errs.append(f"L-D6 pen: the fenced yard is {area} cells, the building {build}; need at least 1.3x")
    kinds = {bid(g[(x, -1, z)]) for (x, z) in pen["cells"] if (x, -1, z) in g}
    if len(kinds) < 2:
        errs.append(f"L-D6 pen: the pen ground has {len(kinds)} block type(s), need 2 or more")
    return errs


def ld7_props(notes: Notes) -> list[str]:
    errs = []
    zones: dict = {}
    for zone, name, hero in notes.props:
        zones.setdefault(zone, []).append((name, hero))
    for zone, items in zones.items():
        heroes = [n for n, h in items if h]
        if len(heroes) > 1:
            errs.append(f"L-D7 props: zone {zone} has {len(heroes)} hero props {heroes}")
    return errs


def lf_size(g: dict, hero: bool, size_range: tuple | None = None) -> list[str]:
    n = sum(1 for (x, y, z), d in g.items() if y >= 0 and bid(d) not in ("air", "water"))
    lo, hi = size_range or (HERO_BLOCKS if hero else SMALL_BLOCKS)
    if not lo <= n <= hi:
        return [f"size: {n} blocks above ground, expected {lo}-{hi}"]
    return []


def lint_place(g: dict, notes: Notes, points: dict, palette: dict | None = None, *, hero: bool = True, name: str = "place",
               water_frontage: tuple | None = None, roof_share: tuple | None = None, size: tuple | None = None,
               ground: int = 4, size_range: tuple | None = None, strict_roofs: bool = True, trapdoor_budget: int | None = None) -> None:
    errs: list[str] = []
    errs += lf1_volumes(notes, hero)
    errs += lf2_roof_forms(g, notes)
    errs += lf3_yard(g, points, notes)
    errs += lf4_clearance(g, points, notes, size or (10 ** 6, 0, 10 ** 6), ground)
    errs += lf5_doors(g, notes)
    errs += lf6_ground(g, notes)
    errs += lf7_vents(g, notes)
    errs += lf8_contrast(g, notes)
    errs += lf9_roof_heights(notes, roof_share)
    errs += lf10_water(g, water_frontage)
    errs += lf11_gables(g, notes)
    if strict_roofs:
        errs += lf12_roof_families(g, notes)
    errs += ld1_gates(g) + ld4_race_open(g, notes)
    if trapdoor_budget is not None:                      # the animal places (L-D2, L-D3)
        errs += ld2_trapdoors(g, trapdoor_budget) + ld3_fence_family(g)
    errs += ld5_flume(g, notes) + ld6_pen(g, notes) + ld7_props(notes)
    errs += lf_size(g, hero, size_range)
    if errs:
        raise LintError(f"farm lint failed for {name} (design/build-lint.md, L-F rules):\n  - " + "\n  - ".join(sorted(set(errs))))
