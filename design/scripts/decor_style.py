"""Plot decoration set: src/main/resources/houses/decor.yml (read by `/bakery plot setup`).

Outputs:
  src/main/resources/houses/decor.yml            (the file the plugin parses)
  design/scripts/previews/decor_plot.png         (textured plan + 3/4 views of the whole plot)
Run with `mise run assets`. Deterministic. Spec, format and keep-out rules: design/decor.md

Frame = layout: of houses/factory.yml: x west->east, z north->south, cells from the plot's min corner,
y 0 = first air layer above the ground, y -1 / -2 = the ground's top block and the block under it.
Scenes over the WHOLE plot (D1-D7): lanes ringing the factory, plazas, yards under the building slots, flower beds,
a well, a veg garden, woodpile, trees on a mound; ground treatments everywhere (R13/R14), props only where the keep-outs
allow (y >= 0: not in the front zone, the factory margin, the slot footprints or the fence ring).
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
sys.path.insert(0, str(Path(__file__).resolve().parent))
import blocks as B  # noqa: E402
import build_lint  # noqa: E402
import building_slots  # noqa: E402
import design_views  # noqa: E402
import plot_views  # noqa: E402
import factory_layout as FL  # noqa: E402
import house_lib  # noqa: E402
import scenes as S  # noqa: E402
from blocks import plain as P  # noqa: E402
from build import Build, Ramp  # noqa: E402

OUT_YML = ROOT / "src/main/resources/houses/decor.yml"
OUT_DIR = ROOT / "design/scripts/previews"
PLOT_X, PLOT_Z = FL.PLOT_X, FL.PLOT_Z
HOUSE_BOX = (6, 10, 24, 23)                     # design box x6..24 z10..23 (ground there belongs to the house design)
BELT = set(FL.BELT)

# y >= 0 keep-outs (inclusive rectangles): factory margin and the front zone; plus the ring and the slot zones
KEEP_OUT = {"factory": (5, 9, 25, 24), "front": (12, 1, 23, 8)}
SLOT_ZONE = building_slots.zone_cells(0.35)


def keep_out(x: int, z: int) -> str | None:
    if x in (0, PLOT_X - 1) or z in (0, PLOT_Z - 1):
        return "ring"
    if (x, z) in SLOT_ZONE:
        return "building slot"
    for name, r in KEEP_OUT.items():
        if r[0] <= x <= r[2] and r[1] <= z <= r[3]:
            return name
    return None


def ground_ok(x: int, z: int) -> bool:
    return (1 <= x <= PLOT_X - 2 and 1 <= z <= PLOT_Z - 2
            and not (HOUSE_BOX[0] <= x <= HOUSE_BOX[2] and HOUSE_BOX[1] <= z <= HOUSE_BOX[3]) and (x, z) not in BELT)


class Decor(Build):
    """A Build whose put() enforces the plot rules: ground layers skip forbidden cells, props must obey the keep-outs."""

    def put(self, x, y, z, data):
        if data is None:
            return
        if y < 0:
            if not ground_ok(x, z):
                return
        elif y > 10:
            raise AssertionError(f"too high {(x, y, z)}")
        else:
            k = keep_out(x, z)
            assert k is None, f"{(x, y, z)} {data} is inside keep-out '{k}'"
        super().put(x, y, z, data)

    def can(self, x, y, z) -> bool:
        return y < 0 and ground_ok(x, z) or y >= 0 and keep_out(x, z) is None and 1 <= x <= 29 and 1 <= z <= 28


b = Decor((PLOT_X, 12, PLOT_Z), ground=2)
rng = b.rng

COBBLE = Ramp(P("cobblestone"), [(P("mossy_cobblestone"), .22, None), (P("andesite"), .16, None),
                                 (P("gravel"), .06, None)], seed=31)
YARD = Ramp(P("dirt_path"), [(P("coarse_dirt"), .22, None)], seed=32)


# --- ground helpers -----------------------------------------------------------------------------------------------
def lane(points: list, hl=(1, 1), hr=(1, 2), jitter: int = 1) -> set:
    """A wandering lane along axis-aligned waypoints: the centre shifts by 1 every 3-5 cells, widths vary (R15)."""
    cells = set()
    for (x0, z0), (x1, z1) in zip(points, points[1:]):
        along_z = x0 == x1
        n = abs(z1 - z0) if along_z else abs(x1 - x0)
        step = 1 if (z1 > z0 if along_z else x1 > x0) else -1
        off, run, a, c = 0, 0, rng.randint(*hl), rng.randint(*hr)
        for i in range(n + 1):
            if run <= 0:
                run = rng.randint(3, 5)
                off = rng.choice([o for o in range(-jitter, jitter + 1) if o != off] or [off]) if rng.random() < .9 else off
                a, c = rng.randint(*hl), rng.randint(*hr)
            run -= 1
            pos = (z0 if along_z else x0) + step * i
            for k in range(-a, c + 1):
                cells.add((x0 + off + k, pos) if along_z else (pos, z0 + off + k))
    return cells


def blob(cx: float, cz: float, rx: float, rz: float, noise: float = .35) -> set:
    cells = set()
    for x in range(int(cx - rx) - 2, int(cx + rx) + 3):
        for z in range(int(cz - rz) - 2, int(cz + rz) + 3):
            d = ((x - cx) / rx) ** 2 + ((z - cz) / rz) ** 2
            if d + (rng.random() - .5) * noise * 2 < 1.0:
                cells.add((x, z))
    return cells


def _noise_field():
    import numpy as np
    from scipy.ndimage import gaussian_filter
    f = gaussian_filter(np.random.default_rng(5).standard_normal((PLOT_X + 4, PLOT_Z + 4)), 1.3)
    return (f - f.mean()) / f.std()


NOISE = _noise_field()


def fringe(cells: set, thr: float = .35) -> set:
    """A soft coarse-dirt transition just outside a treatment: neighbouring cells where a smooth noise field is high, so
    the edge gains clusters of 2-4 cells, never lone squares."""
    out = set()
    for (x, z) in cells:
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, z + dz)
            if n not in cells and NOISE[n[0] % NOISE.shape[0], n[1] % NOISE.shape[1]] > thr:
                out.add(n)
    return out - cells


def paint(cells: set, ramp: Ramp, fr: float = .75) -> None:
    S.ground(b, cells, ramp)
    for (x, z) in sorted(fringe(cells, fr)):
        if b.get(x, -1, z) is None:
            b.put(x, -1, z, P("coarse_dirt"))


# --- scenes: ground first (so props sit on what they belong to), then things that stand ---------------------------------
def ellipse(cx: float, cz: float, rx: float, rz: float, deg: float = 24.0) -> set:
    """A slightly rotated ellipse: its flat sides stair-step every 3-5 cells instead of running straight (R15)."""
    import math
    ca, sa = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    out = set()
    for x in range(int(cx - rx) - 2, int(cx + rx) + 3):
        for z in range(int(cz - rx) - 2, int(cz + rx) + 3):
            u, v = (x - cx) * ca + (z - cz) * sa, -(x - cx) * sa + (z - cz) * ca
            if (u / rx) ** 2 + (v / rz) ** 2 <= 1.0:
                out.add((x, z))
    return out


STONEB = P("stone_bricks")
W3 = dict(hl=(1, 1), hr=(1, 1))                       # every track is 3 wide; only its centre wanders

b.scene("main_walk", "gate")
walk = lane([(15, 1), (15, 9)], **W3, jitter=1)
for (x, z) in sorted(walk):                           # stone near the house and the plaza, fading into the dirt track
    cobble = min(1.0, max(0.0, (z - 2) / 6)) + 0.22 * NOISE[x, z] > 0.55
    b.put(x, -1, z, (COBBLE if cobble else S.PATH).pick(x, 0, z))

b.scene("pedestal_plaza", "pedestal")
plaza = ellipse(18, 5.5, 4.2, 2.8)
rim = {c for c in plaza if any((c[0] + dx, c[1] + dz) not in plaza for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
for (x, z) in sorted(plaza):
    if (x, z) in rim and (x, z) not in walk:
        b.put(x, -1, z, STONEB)                       # a defined stone-brick rim
    else:
        b.put(x, -1, z, COBBLE.pick(x, 0, z))

b.scene("baker_yard", "slots")
S.ground(b, ellipse(7, 3.8, 4.4, 3.0) | lane([(10, 4), (13, 4)], **W3), S.PATH)

b.scene("portal_yard", "slots")
S.ground(b, ellipse(27, 4, 2.5, 2.5) | lane([(22, 4), (26, 4)], **W3), S.PATH)

b.scene("ring_lane", "factory")
ring = lane([(4, 9), (4, 25)], **W3) | lane([(4, 25), (27, 25)], **W3) | lane([(27, 25), (27, 9)], **W3)
S.ground(b, ring, S.PATH)

b.scene("shipping_yard", "slots")
S.ground(b, ellipse(2, 13.5, 2.0, 3.4) | ellipse(2, 21, 2.0, 3.0), S.PATH)
b.scene("oven_yard", "slots")
S.ground(b, ellipse(27, 16.5, 1.9, 5.4), S.PATH)
b.scene("belt_yard", "slots")
S.ground(b, ellipse(12, 26, 5.0, 1.9), S.PATH)

# flower beds along the fence and at the paths (podzol, flowers, ferns)
b.scene("gate_bed_west", "fence")
cells = {(x, z) for x in (10, 11) for z in range(1, 6)} | {(9, 1), (10, 6)}
cells = {(x, z) for (x, z) in cells if b.can(x, 0, z) or True}
S.flower_bed(b, {c for c in cells if b.can(c[0], 0, c[1])})
b.scene("gate_bed_east", "fence")
S.flower_bed(b, {(x, z) for x in range(24, 30) for z in (1, 2)} | {(24, 3), (24, 4), (29, 3), (25, 3)}
             - {c for c in SLOT_ZONE}, kinds=S.FLOWERS[2:] + S.FLOWERS[:2])

b.scene("well_yard", "well")
S.well(b, 2, 5)
S.flower_bed(b, {(x, z) for x in (1, 2, 3) for z in (1, 2, 3)} | {(1, 8), (2, 8), (3, 8), (2, 9), (1, 9)})
for (x, z) in sorted(blob(2, 5, 2.6, 2.6)):
    if b.get(x, -1, z) is None and abs(x - 2) <= 1 and abs(z - 5) <= 1:
        pass

# the west lane store: crates, sacks and hay along x = 4, a lamp at the corner
b.scene("west_store", "lane")
S.crates(b, [(4, 0, 13), (4, 1, 13), (4, 0, 14)], sacks=[(4, 0, 16)])
S.hay(b, [(4, 0, 17, "x"), (4, 0, 19, "z")])
b.put(4, 0, 18, P("potted_allium")) if False else None

b.scene("east_beds", "fence")
for z0 in (11, 16, 20):
    S.flower_bed(b, {(29, z) for z in range(z0, z0 + 3)} | {(28, z0 + 1)}, kinds=["fern", "lily_of_the_valley",
                                                                                "allium", "cornflower"], density=.7)
b.scene("oven_store", "fence")
S.hay(b, [(29, 0, 14, "x"), (29, 1, 14, "x"), (29, 0, 15, "z")])
S.crates(b, [(29, 0, 18), (29, 1, 18)], sacks=[(29, 0, 19)])

b.scene("back_garden", "garden")
S.veg_plot(b, 17, 26, 6, 2, ["wheat", "carrots", "potatoes"])
S.scarecrow(b, 24, 27, facing="west")

b.scene("back_orchard", "tree")
mound = {(x, z) for x in range(25, 30) for z in range(25, 29) if (x - 27) ** 2 + (z - 27) ** 2 + rng.random() * 1.2 < 5.2}
for (x, z) in sorted(mound):
    if b.can(x, 0, z):
        b.put(x, -1, z, B.grass())
        b.put(x, 0, z, B.grass())
S.tree(b, 27, 27, "oak", height=4, spread=1.9, y0=1, ok=b.can)
S.tree(b, 21, 28, "birch", height=4, spread=1.5, y0=0, ok=b.can) if False else None
b.scene("woodpile", "tree")
S.woodpile(b, 20, 28, "x", 3)

# --- validation / output ------------------------------------------------------------------------------------------------
def validate() -> None:
    inside = {(x, z) for x in range(0, PLOT_X) for z in range(0, PLOT_Z) if ground_ok(x, z) is False}
    for (x, y, z), data in b.g.items():
        house_lib.check_block_data(data) if not B.is_connect(data) else None
        blk = B.block_id(data)
        if y >= 0:
            assert keep_out(x, z) is None, f"{(x, y, z)} {data}"
        if blk == "water":
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                n = b.g.get((x + dx, y, z + dz))
                assert n is not None, f"water at {(x, y, z)} is not enclosed"
        if blk in house_lib.PLANTS:
            below = b.g.get((x, y - 1, z))
            assert below is None and y > 0 or below is not None and B.block_id(below) in house_lib.SOIL | {"water"} \
                or blk == "lily_pad", f"{blk} {(x, y, z)} has no own soil: {below}"
        if blk == "lantern":
            assert (x, y - 1, z) in b.g, f"{(x, y, z)} lantern floats"


def write_yaml(g: dict) -> str:
    owner = {}
    for sc in b.scenes:
        for c in sc["cells"]:
            owner[c] = sc["name"]
    order = []
    seen = set()
    for sc in b.scenes:
        if sc["name"] in seen:
            continue
        seen.add(sc["name"])
        order.append(sc["name"])
    lines = ["# Plot decoration set for /bakery plot setup. GENERATED by design/scripts/decor_style.py; do not edit by hand.",
             "# Format, coordinate frame and keep-out rules: design/decor.md",
             "# at = [x, y, z] in plot cells from the plot's min corner (x west->east, z north->south, y 0 = first air layer",
             "# above the ground; y -1 / -2 = the ground's top block / the one under it; same frame as layout: in houses/factory.yml).",
             "# block = full block data.", f"# {len(g)} blocks.", "blocks:"]
    for name in order:
        cells = sorted((c for c, o in owner.items() if o == name and c in g), key=lambda c: (c[1], c[2], c[0]))
        if not cells:
            continue
        lines.append(f"  # {name}")
        for (x, y, z) in cells:
            lines.append(f'  - {{at: [{x}, {y}, {z}], block: "{g[(x, y, z)]}"}}')
    return "\n".join(lines) + "\n"


def drop_loose_ground(min_size: int = 6) -> None:
    """No isolated ground squares anywhere: ground treatments in connected groups under `min_size` cells are removed
    unless a bed, well, garden, lamp or woodpile scene owns them."""
    ground = {"dirt_path", "gravel", "coarse_dirt", "podzol", "rooted_dirt", "cobblestone", "mossy_cobblestone", "andesite"}
    keep = {"gate_bed_west", "gate_bed_east", "well_yard", "west_store", "east_beds", "oven_store", "back_garden",
            "back_orchard", "woodpile"}
    owner = {}
    for sc in b.scenes:
        for c in sc["cells"]:
            owner[c] = sc["name"]
    cells = {(x, z) for (x, y, z), d in b.g.items() if y == -1 and B.block_id(d) in ground}
    seen = set()
    for c in sorted(cells):
        if c in seen:
            continue
        comp, q = [], [c]
        seen.add(c)
        while q:
            x, z = q.pop()
            comp.append((x, z))
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                n = (x + dx, z + dz)
                if n in cells and n not in seen:
                    seen.add(n)
                    q.append(n)
        if len(comp) < min_size and not any(owner.get((x, -1, z)) in keep for (x, z) in comp):
            for (x, z) in comp:
                del b.g[(x, -1, z)]


def main() -> int:
    drop_loose_ground()
    g = dict(b.g)
    B.resolve(g)
    validate()
    inside = {(x, z) for x in range(PLOT_X) for z in range(PLOT_Z)
              if x in (0, PLOT_X - 1) or z in (0, PLOT_Z - 1)
              or (HOUSE_BOX[0] <= x <= HOUSE_BOX[2] and HOUSE_BOX[1] <= z <= HOUSE_BOX[3]) or (x, z) in BELT}
    scenes = [{"name": s["name"], "cells": [c for c in s["cells"] if c in g], "anchor": s["anchor"]} for s in b.scenes]
    scenes = [s for s in scenes if s["cells"]]
    try:
        build_lint.lint_decor(g, scenes, extra_inside=inside)
    except build_lint.LintError:
        plot_views.plot_previews(g, OUT_DIR)
        raise
    text = write_yaml(g)
    OUT_YML.write_text(text, encoding="utf-8")
    print(f"decor.yml: {len(g)} blocks in {len(scenes)} scenes")
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    plot_views.plot_previews(g, OUT_DIR)
    return 0


if __name__ == "__main__":
    sys.exit(main())
