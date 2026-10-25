"""Shared generator steps for the countryside places (Cow Barn, Chicken Farm, Market): road, lawn, meadow, hedges, drystone
walls, hay, troughs, and `finish()` = block-id check, points check, lints, YAML writer, previews.
The Mill (house_mill.py) predates this module and keeps its own copy of the steps."""
from __future__ import annotations

from pathlib import Path

import yaml

import block_ids
import blocks as B
import build_lint
import farm_kits
import farm_lint
import house_lib
import place_lib
import scenes as S
from blocks import plain as P, sl
from build import Build, Ramp

ROOT = Path(__file__).resolve().parents[3]
OUT_DIR = ROOT / "design/scripts/previews"
HOUSES = ROOT / "src/main/resources/houses"

COBBLE = Ramp(P("cobblestone"), [(P("mossy_cobblestone"), .16, None), (P("andesite"), .14, None)], seed=31)
GRAVEL = Ramp(P("gravel"), [(P("andesite"), .12, None)], seed=22)
DIRT = Ramp(P("dirt"), [(P("coarse_dirt"), .08, None)], seed=36)
GRASS = B.grass()
DRYSTONE = ["cobblestone_wall", "mossy_cobblestone_wall", "andesite_wall"]


def wobble(n: int, lo: int, hi: int, rng, run=(3, 5)) -> list:
    out, off = [], rng.randint(lo, hi)
    left = 0
    for _ in range(n):
        if left <= 0:
            left = rng.randint(*run)
            off = rng.choice([o for o in range(lo, hi + 1) if o != off] or [off])
        out.append(off)
        left -= 1
    return out


def lawn(b: Build, sx: int, sz: int, heights: dict | None = None) -> None:
    h = heights if heights is not None else {(x, z): -1 for x in range(sx) for z in range(sz)}
    farm_kits.height_field_ground(b, h, top=Ramp(GRASS, []), sub=DIRT, y_floor=-b.ground)


def road(b: Build, sx: int, ramp: Ramp = GRAVEL) -> set:
    """The 3-wide gravel road along the north edge (its south edge wobbles by one cell)."""
    off = wobble(sx, 0, 1, b.rng)
    cells = {(x, z) for x in range(sx) for z in range(0, 3 + off[x])}
    S.ground(b, cells, ramp)
    return cells


def drystone(b: Build, cells: list, y: int = 0, high: int = 1) -> None:
    """A dry-stone wall (cobblestone / mossy / andesite wall mix) over `cells`, `high` blocks tall."""
    for (x, z) in cells:
        r = b.rng.random()
        name = DRYSTONE[0] if r < .62 else (DRYSTONE[1] if r < .86 else DRYSTONE[2])
        for dy in range(high):
            b.put(x, y + dy, z, f"@{name}")


def trough(b: Build, x: int, z: int, axis: str = "x", length: int = 3) -> None:
    """A trough: water cauldrons at both ends, spruce slabs between, on coarse dirt."""
    for i in range(length):
        cx, cz = (x + i, z) if axis == "x" else (x, z + i)
        b.put(cx, 0, cz, sl("spruce", "bottom") if 0 < i < length - 1 else B.cauldron_water(3))
        b.put(cx, -1, cz, P("coarse_dirt"))


def hay_rick(b: Build, x: int, z: int, w: int = 6, d: int = 3, layers: int = 3, axis: str = "x") -> None:
    """A stacked hay rick: layers of hay blocks, each shorter at both ends, on coarse dirt."""
    for y in range(layers):
        for i in range(y, w - y):
            for j in range(d):
                cx, cz = (x + i, z + j) if axis == "x" else (x + j, z + i)
                b.put(cx, y, cz, B.hay("x" if axis == "x" else "z"))
    for i in range(w):
        for j in range(d):
            cx, cz = (x + i, z + j) if axis == "x" else (x + j, z + i)
            b.put(cx, -1, cz, P("coarse_dirt"))


def meadow(b: Build, sx: int, sz: int, skip, seed: int = 77, thresh: float = .55, z0: int = 3) -> None:
    """Short grass, ferns and flower clusters on empty lawn (cells with 3+ neighbours only)."""
    import numpy as np
    from scipy.ndimage import gaussian_filter
    rng = b.rng
    field = gaussian_filter(np.random.default_rng(seed).standard_normal((sx + 4, sz + 4)), 1.8)
    field = (field - field.mean()) / field.std()
    taken = {(x, z) for (x, y, z) in b.g if y >= 0}
    ok = lambda x, z: (x, -1, z) in b.g and "grass_block" in b.g[(x, -1, z)] and (x, 0, z) not in b.g  # noqa: E731
    tufts = {}
    for x in range(1, sx - 1):
        for z in range(z0, sz - 1):
            if ok(x, z) and not skip(x, z) and field[x, z] > thresh and (x, z) not in taken:
                tufts[(x, z)] = rng.random()
    chosen = set(tufts)
    while True:
        keep = {c for c in chosen if sum(1 for dx in (-1, 0, 1) for dz in (-1, 0, 1)
                                          if (dx, dz) != (0, 0) and (c[0] + dx, c[1] + dz) in chosen) >= 3}
        if keep == chosen:
            break
        chosen = keep
    for (x, z) in sorted(chosen):
        r = tufts[(x, z)]
        b.put(x, 0, z, P("short_grass" if r < .6 else ("fern" if r < .7 else
                         ("dandelion", "cornflower", "oxeye_daisy", "poppy", "azure_bluet")[int(r * 50) % 5])))


def hedge(b: Build, segments: list, kind: str = "oak") -> None:
    """Leaf hedges: (x0, x1, z) runs along x or ("z", z0, z1, x) runs along z, at most 6 cells each; a second layer on two
    cells of three."""
    for s in segments:
        if s[0] == "z":
            _, z0, z1, x = s
            for z in range(z0, z1 + 1):
                b.put(x, 0, z, B.leaves(kind))
                if z % 3:
                    b.put(x, 1, z, B.leaves(kind))
        else:
            x0, x1, z = s
            for x in range(x0, x1 + 1):
                b.put(x, 0, z, B.leaves(kind))
                if (x + x0) % 3:
                    b.put(x, 1, z, B.leaves(kind))


def finish(name: str, b: Build, *, points: dict, signs: list, size: tuple, ground: int, hero: bool, previews,
           water_frontage=None, size_range: tuple | None = None, trapdoor_budget: int = 12) -> dict:
    """Resolve the grid, validate the palette and the points, run the lints (previews are written even when a lint fails),
    write houses/<name>.yml. Returns {'blocks', 'above', 'palette', 'g'}."""
    g = dict(b.g)
    B.resolve(g)
    chars, palette = B.to_palette(g)
    layers = house_lib.to_layers(chars, size, ground)
    blocks = house_lib.validate(chars, layers, palette, size, (0, 0, 0), {}, signs, ground=ground)
    absent = block_ids.missing({B.block_id(d) for d in palette.values()})
    assert not absent, f"block ids not in the 26.3 sources jar: {absent}"
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    try:
        place_lib.check_points(points, g, size, ground)
        build_lint.lint_house(g, b.notes, {}, palette)
        farm_lint.lint_place(g, b.notes, points, palette, hero=hero, name=name, water_frontage=water_frontage, size=size,
                             ground=ground, size_range=size_range, trapdoor_budget=trapdoor_budget)
    except (build_lint.LintError, AssertionError):
        previews(g, OUT_DIR)
        raise
    header = [
        f"# The `{name}` place (countryside-builds). GENERATED by design/scripts/house_{name}.py; do not edit by hand.",
        "# Spec: design/countryside.md (format: place files + points). FINAL orientation: the road side (yard) is NORTH (-Z).",
        "# Axes: x west->east, y bottom->top, z north->south. ground-depth 4: the first 4 layers are design y -4..-1.",
        f"# {blocks} blocks (not counting air/keep).",
    ]
    text = place_lib.write_place_yaml(header, layers, palette, size, points, signs, ground)
    parsed = yaml.safe_load(text)
    assert parsed["layers"] == layers and parsed["palette"] == palette
    out = HOUSES / f"{name}.yml"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(text, encoding="utf-8")
    above = sum(1 for (x, y, z), d in g.items() if y >= 0 and B.block_id(d) not in ("air", "water"))
    print(f"houses/{name}.yml: {blocks} blocks ({above} above ground, non-water), {len(palette)} palette entries, "
          f"volume {size[0] * size[1] * size[2]}")
    previews(g, OUT_DIR)
    return {"blocks": blocks, "above": above, "palette": palette, "g": g}


# --- organic pens (run 2c) ------------------------------------------------------------------------------------------------
def blob(b: Build, x0: int, z0: int, x1: int, z1: int, cut: int = 3, wob: int = 1) -> set:
    """Cells of a rounded, irregular rectangle: corners chamfered by `cut`, every side pushed in by up to `wob` in runs."""
    rng = b.rng
    cells = set()
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if min(x - x0, x1 - x) + min(z - z0, z1 - z) >= cut:
                cells.add((x, z))
    for side in range(4):
        n = (x1 - x0 + 1) if side < 2 else (z1 - z0 + 1)
        for i, off in enumerate(wobble(n, 0, wob, rng)):
            for k in range(off):
                c = [(x0 + i, z0 + k), (x0 + i, z1 - k), (x0 + k, z0 + i), (x1 - k, z0 + i)][side]
                cells.discard(c)
    return cells


def perimeter(cells: set) -> set:
    return {(x, z) for (x, z) in cells
            if any((x + dx, z + dz) not in cells for dx in (-1, 0, 1) for dz in (-1, 0, 1) if (dx, dz) != (0, 0))}


def drystone_wall(b: Build, cells, high: int = 2, boulder: float = .12, skip=frozenset()) -> None:
    """Low drystone wall (cobble / mossy / andesite wall pieces) along `cells`: 1-2 high in runs of 4, a missing top here and
    there, and boulders (full cobblestone / mossy / andesite blocks) built into the course."""
    rng = b.rng
    for (x, z) in sorted(cells):
        if (x, z) in skip:
            continue
        r = rng.random()
        h = 2 if high >= 2 and ((x // 4 + z // 4) % 3) else 1
        if r < boulder:
            b.put(x, 0, z, P(("cobblestone", "mossy_cobblestone", "andesite")[int(r * 100) % 3]))
            if h == 2 and rng.random() < .5:
                b.put(x, 1, z, sl("cobblestone", "bottom"))
            continue
        name = DRYSTONE[0] if r < .62 else (DRYSTONE[1] if r < .88 else DRYSTONE[2])
        b.put(x, 0, z, f"@{name}")
        if h == 2 and rng.random() > .18:
            b.put(x, 1, z, f"@{name}")


def hedge_bank(b: Build, cells, skip=frozenset()) -> None:
    """A hedge on a low stone bank: a mossy/plain cobblestone bank at y0, leaves 2 high on top (third layer on some cells)."""
    rng = b.rng
    for (x, z) in sorted(cells):
        if (x, z) in skip:
            continue
        b.put(x, 0, z, P("mossy_cobblestone" if rng.random() < .4 else "cobblestone"))
        b.put(x, 1, z, B.leaves("oak"))
        b.put(x, 2, z, B.leaves("oak")) if rng.random() < .75 else None


def window_sill(b: Build, wall, u: int, y: int, width: int = 1, height: int = 1, light: bool = True) -> None:
    """A window with a slab sill (no trapdoors) and a lit room."""
    from build import window
    window(wall, u, y, width=width, height=height, shutters=None, sill=None, box=None, light=light)
    for i in range(width):
        wall.put(u + i, y - 1, 1, sl("spruce", "top"))


def gate_pillar(b: Build, x: int, z: int, high: int = 2, lantern: bool = False) -> None:
    """A full-block stone pillar with a stone-brick slab cap (or a lantern on top)."""
    for y in range(high):
        r = b.rng.random()
        b.put(x, y, z, P("cobblestone" if r < .6 else ("mossy_cobblestone" if r < .85 else "andesite")))
    if lantern:
        b.put(x, high, z, B.lantern(False))
    else:
        b.put(x, high, z, sl("stone_brick", "bottom"))
