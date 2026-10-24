"""Scene kits for yards and plots (style guide D1-D7): each function builds one anchored group on a Build and registers it
as a scene (b.scene(name, anchor) first). Ground treatments are written at y -1 / -2 (the ground layers), things that
stand at y >= 0. Soil is always placed under plants: nothing depends on the unknown ground.
"""
from __future__ import annotations

import blocks as B
from blocks import AIR, lg, plain as P, sl, st
from build import Build, Ramp

FLOWERS = ["poppy", "dandelion", "cornflower", "azure_bluet", "oxeye_daisy", "allium", "pink_tulip",
           "lily_of_the_valley"]
PODZOL, COARSE, ROOTED = P("podzol[snowy=false]"), P("coarse_dirt"), P("rooted_dirt")
PATH = Ramp(P("dirt_path"), [(P("gravel"), .22, None), (P("coarse_dirt"), .14, None), (P("cobblestone"), .09, None)],
            seed=21)
GRAVEL = Ramp(P("gravel"), [(P("coarse_dirt"), .25, None), (P("dirt_path"), .12, None), (P("cobblestone"), .08, None)],
              seed=22)


def noisy_rect(b: Build, x0: int, z0: int, x1: int, z1: int, wobble: int = 1, seed_shift: int = 0) -> set:
    """Cells of a rectangle whose edge is pushed in or out by up to `wobble` cells in runs of 2-4 cells (R15)."""
    cells = set()
    rng = b.rng
    for x in range(x0 - wobble, x1 + wobble + 1):
        for z in range(z0 - wobble, z1 + wobble + 1):
            dx = max(x0 - x, 0, x - x1)
            dz = max(z0 - z, 0, z - z1)
            d = max(dx, dz)
            if d == 0:
                cells.add((x, z))
    # wobble the four sides
    for side in range(4):
        run = 0
        for i in range(((x1 - x0) if side < 2 else (z1 - z0)) + 3):
            if run <= 0:
                run, off = rng.randint(2, 4), rng.choice((-1, 0, 0, 1))
            run -= 1
            for k in range(1, wobble + 1):
                if off >= k:
                    if side == 0:
                        cells.add((x0 - 1 + i, z0 - k))
                    elif side == 1:
                        cells.add((x0 - 1 + i, z1 + k))
                    elif side == 2:
                        cells.add((x0 - k, z0 - 1 + i))
                    else:
                        cells.add((x1 + k, z0 - 1 + i))
            if off < 0:
                for k in range(1, -off + 1):
                    if side == 0:
                        cells.discard((x0 - 1 + i, z0 + k - 1))
                    elif side == 1:
                        cells.discard((x0 - 1 + i, z1 - k + 1))
                    elif side == 2:
                        cells.discard((x0 + k - 1, z0 - 1 + i))
                    else:
                        cells.discard((x1 - k + 1, z0 - 1 + i))
    return {(x, z) for (x, z) in cells if x0 - wobble <= x <= x1 + wobble and z0 - wobble <= z <= z1 + wobble}


def ground(b: Build, cells, ramp: Ramp, y: int = -1) -> None:
    for (x, z) in sorted(cells):
        b.put(x, y, z, ramp.pick(x, 0, z))


def flower_bed(b: Build, cells, kinds=FLOWERS, density: float = .78, seed: int = 0) -> None:
    """Podzol / coarse dirt / rooted dirt bed (y -1) with flowers, ferns and tall grass tufts on it (y 0)."""
    rng = b.rng
    for (x, z) in sorted(cells):
        r = rng.random()
        b.put(x, -1, z, PODZOL if r < .6 else (COARSE if r < .85 else ROOTED))
        if rng.random() < density:
            k = rng.choice(kinds)
            b.put(x, 0, z, P(k))
        elif rng.random() < .5:
            b.put(x, 0, z, P("fern" if rng.random() < .5 else "short_grass"))


def woodpile(b: Build, x: int, z: int, axis: str = "x", length: int = 5) -> None:
    """Horizontal logs in a pyramid (length, length-2, ... high), a chopping block beside it (R18 woodpile)."""
    for layer in range((length + 1) // 2):
        for i in range(layer, length - layer):
            cx, cz = (x + i, z) if axis == "x" else (x, z + i)
            b.put(cx, layer, cz, lg("spruce" if (i + layer) % 3 else "oak", axis))
    cx, cz = (x + length + 1, z) if axis == "x" else (x, z + length + 1)
    b.put(cx, 0, cz, lg("oak", "y"))


def lamp_post(b: Build, x: int, z: int, height: int = 3) -> None:
    for y in range(height):
        b.put(x, y, z, "@dark_oak_fence")
    b.put(x, height, z, B.lantern(False))


def bench(b: Build, x: int, z: int, facing: str = "south", length: int = 3, along: str = "x") -> None:
    for i in range(length):
        cx, cz = (x + i, z) if along == "x" else (x, z + i)
        b.put(cx, 0, cz, st("spruce", facing))
    for i in (0, length - 1):
        cx, cz = (x + i, z) if along == "x" else (x, z + i)
        b.put(cx, -1, cz, P("coarse_dirt"))


def crates(b: Build, cells: list, sacks: list = ()) -> None:
    for (x, y, z) in cells:
        b.put(x, y, z, B.barrel())
    for (x, y, z) in sacks:
        b.put(x, y, z, P("white_wool"))
        b.put(x, y + 1, z, P("white_carpet"))


def hay(b: Build, cells: list) -> None:
    for (x, y, z, ax) in cells:
        b.put(x, y, z, B.hay(ax))


def scarecrow(b: Build, x: int, z: int, facing: str = "south") -> None:
    b.put(x, 0, z, lg("dark_oak", "y"))
    b.put(x, 1, z, B.hay("y"))
    b.put(x - 1, 1, z, P("stripped_dark_oak_log[axis=x]"))
    b.put(x + 1, 1, z, P("stripped_dark_oak_log[axis=x]"))
    b.put(x, 2, z, P(f"carved_pumpkin[facing={facing}]"))
    b.put(x, -1, z, P("coarse_dirt"))


def veg_plot(b: Build, x0: int, z0: int, w: int, d: int, crops: list, seed: int = 0) -> None:
    """Farmland rows with a water channel through the middle (enclosed), crops ripe or growing (y -1 farmland, y 0 crop)."""
    mid = x0 + w // 2
    for x in range(x0, x0 + w):
        for z in range(z0, z0 + d):
            if x == mid and z0 < z < z0 + d - 1 and (z - z0) % 2 == 1:
                b.put(x, -1, z, P("water[level=0]"))
                continue
            b.put(x, -1, z, B.farmland(7))
            crop = crops[(x - x0) % len(crops)]
            b.put(x, 0, z, B.crop(crop, 7 if crop != "beetroots" else 3))
    for x in range(x0 - 1, x0 + w + 1):
        for z in (z0 - 1, z0 + d):
            b.put(x, -1, z, P("coarse_dirt"))
    for z in range(z0, z0 + d):
        for x in (x0 - 1, x0 + w):
            b.put(x, -1, z, P("coarse_dirt"))


def well(b: Build, x: int, z: int) -> None:
    """3 x 3 stone ring around enclosed water (ground layers), two dark oak posts, a small stair roof."""
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if (dx, dz) == (0, 0):
                b.put(x, -1, z, P("water[level=0]"))
                b.put(x, -2, z, P("water[level=0]"))
            else:
                b.put(x + dx, -1, z + dz, P("cobblestone"))
                b.put(x + dx, 0, z + dz, P("mossy_cobblestone") if (dx + dz) % 2 else P("cobblestone"))
    for dz in (-1, 1):
        b.put(x - 1, 0, z + dz, P("stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
              if False else P("mossy_cobblestone"))
    for dx in (-1, 1):
        for y in (1, 2):
            b.put(x + dx, y, z, lg("dark_oak", "y"))
        b.put(x + dx, 3, z, B.stripped("dark_oak", "z"))
    for dz in (-1, 0, 1):
        b.put(x - 1, 3, z + dz, B.stripped("dark_oak", "z"))
        b.put(x + 1, 3, z + dz, B.stripped("dark_oak", "z"))
    for dz in (-1, 0, 1):
        b.put(x, 4, z + dz, sl("dark_oak"))
    for dx in (-1, 1):
        for dz in (-1, 0, 1):
            b.put(x + dx, 4, z + dz, st("spruce", "east" if dx < 0 else "west"))
    b.put(x, 2, z, B.chain("y"))
    b.put(x, 1, z, B.barrel())
    b.g[(x, 3, z)] = P("dark_oak_planks")


def tree(b: Build, x: int, z: int, kind: str = "oak", height: int = 5, spread: float = 2.6, bend: int = 0) -> None:
    """A custom tree: slightly bent trunk, 3-4 canopy blobs with noisy edges, podzol roots (R20). Leaves persistent."""
    rng = b.rng
    log = f"{kind}_log"
    b.put(x, -1, z, PODZOL)
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        if rng.random() < .6:
            b.put(x + dx, -1, z + dz, ROOTED if rng.random() < .5 else PODZOL)
    tx, tz = x, z
    for y in range(height):
        b.put(tx, y, tz, f"minecraft:{log}[axis=y]")
        if bend and y in (2, 3):
            tx += bend if y == 2 else 0
    blobs = [(tx, height, tz, spread)]
    for _ in range(3):
        blobs.append((tx + rng.randint(-2, 2), height - 1 + rng.randint(0, 1), tz + rng.randint(-2, 2),
                      spread - 0.6 - rng.random() * .6))
    for (bx, by, bz, r) in blobs:
        for dx in range(-3, 4):
            for dy in range(-2, 3):
                for dz in range(-3, 4):
                    dd = (dx * dx + dz * dz) / (r * r) + (dy * dy) / ((r * .8) ** 2)
                    if dd + rng.random() * .35 < 1.0:
                        p = (bx + dx, by + dy, bz + dz)
                        if p not in b.g and 0 <= p[1] < b.size[1] and 0 <= p[0] < b.size[0] and 0 <= p[2] < b.size[2]:
                            b.put(*p, B.leaves(kind))
    b.put(tx, height, tz, f"minecraft:{log}[axis=y]")
    for dy in range(1, 3):                                                 # a branch stub for character
        bx = tx + (1 if rng.random() < .5 else -1)
        if (bx, height - dy, tz) not in b.g:
            b.put(bx, height - dy, tz, f"minecraft:{log}[axis=x]")
