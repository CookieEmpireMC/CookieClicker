"""Plot decoration set: src/main/resources/houses/decor.yml (read by `/bakery plot setup`).

Outputs:
  src/main/resources/houses/decor.yml            (the file the plugin parses)
  design/scripts/previews/decor_plot.png         (top-down plot plan: decor, keep-out, factory, belt)
Run with `mise run assets`. Deterministic. Spec, format and keep-out rules: design/decor.md

Frame = layout: of houses/factory.yml: x west->east, z north->south, cells from the plot's min corner,
y 0 = first air layer above the ground.
"""
from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
import house_lib  # noqa: E402

OUT_YML = ROOT / "src/main/resources/houses/decor.yml"
OUT_PNG = ROOT / "design/scripts/previews/decor_plot.png"

PLOT_X, PLOT_Z = 31, 30            # layout.plot-size
BELT_X, BELT_Z = 18, (7, 11)

# keep-out rectangles (inclusive x0, z0, x1, z1; every height); derived in design/decor.md
KEEP_OUT = {
    "factory": (5, 9, 25, 24),     # design box x6..24 z10..23 plus 1 cell margin (door, hatch, belt start)
    "front": (12, 1, 23, 8),       # gate walk, click area, pedestal (18,6), clicks (16,6), shop (20,6) +2 cells, belt x18
}                                  # plus the fence ring: x in {0, 30} or z in {0, 29}

P = "minecraft:"
LOG = P + "spruce_log[axis=y]"
LANTERN = P + "lantern[hanging=false,waterlogged=false]"
BARREL = P + "barrel[facing=up,open=false]"
HAY = P + "hay_block[axis=y]"
HAYX = P + "hay_block[axis=x]"
LEAVES = P + "cherry_leaves[distance=7,persistent=true,waterlogged=false]"
OAKLEAVES = P + "oak_leaves[distance=7,persistent=true,waterlogged=false]"
WOOL, CARPET = P + "white_wool", P + "white_carpet"
FLOWERS = [P + f for f in ("poppy", "dandelion", "cornflower", "azure_bluet", "oxeye_daisy", "pink_tulip")]
POTS = [P + f for f in ("potted_poppy", "potted_dandelion", "potted_cornflower", "potted_azure_bluet",
                        "potted_oxeye_daisy", "potted_pink_tulip")]
NEEDS_GROUND = {"poppy", "dandelion", "cornflower", "azure_bluet", "oxeye_daisy", "pink_tulip", "wheat"}
BENCH_E = P + "spruce_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"
BENCH_W = P + "spruce_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]"

blocks: dict[tuple[int, int, int], str] = {}
clusters: dict[str, list[tuple[int, int, int]]] = {}
_current = ""


def cluster(name: str):
    global _current
    _current = name
    clusters[name] = []


def put(x: int, y: int, z: int, data: str):
    assert (x, y, z) not in blocks, f"duplicate {(x, y, z)}"
    blocks[(x, y, z)] = data
    clusters[_current].append((x, y, z))


def lamp(x, z):
    for y in range(3):
        put(x, y, z, LOG)
    put(x, 3, z, LANTERN)


def tree(x, z, trunk=4, leaves=LEAVES, log="cherry_log", r=2):
    for y in range(trunk):
        put(x, y, z, P + log + "[axis=y]")
    for y, rr in ((trunk - 2, r), (trunk - 1, r), (trunk, 1), (trunk + 1, 0)):
        for dx in range(-rr, rr + 1):
            for dz in range(-rr, rr + 1):
                if rr == 2 and abs(dx) == 2 and abs(dz) == 2:
                    continue                       # rounded corners
                if (x + dx, y, z + dz) in blocks:
                    continue                       # the trunk
                put(x + dx, y, z + dz, leaves)


def sack(x, z):
    put(x, 0, z, WOOL)
    put(x, 1, z, CARPET)


# --- clusters ------------------------------------------------------------------------------------------------------
cluster("path_edge_west")          # flower row along the west side of the walk, a lamp post halfway
for i, z in enumerate(range(2, 8)):
    if z == 4:
        lamp(11, z)
    else:
        put(11, 0, z, FLOWERS[i % len(FLOWERS)])
cluster("path_edge_east")
for i, z in enumerate(range(2, 8)):
    if z == 4:
        lamp(24, z)
    else:
        put(24, 0, z, FLOWERS[(i + 3) % len(FLOWERS)])

cluster("front_west_garden")       # cherry tree, bench facing the pedestal, potted plants
tree(3, 3)
for z in (5, 6, 7):
    put(8, 0, z, BENCH_E)
put(8, 0, 4, POTS[0])
put(8, 0, 8, POTS[2])
put(6, 0, 2, FLOWERS[1]); put(7, 0, 2, FLOWERS[2]); put(6, 0, 3, FLOWERS[4])
put(5, 0, 6, FLOWERS[3]); put(5, 0, 7, FLOWERS[0]); put(4, 0, 7, FLOWERS[5])

cluster("front_east_wheat")        # 3x3 wheat bed (centre = water so the farmland stays wet), scarecrow, hay
for x in (26, 27, 28):
    for z in (2, 3, 4):
        if (x, z) == (27, 3):
            put(x, 0, z, P + "water[level=0]")
        else:
            put(x, 0, z, P + "farmland[moisture=7]")
            put(x, 1, z, P + "wheat[age=7]")
put(29, 0, 6, LOG)
put(29, 1, 6, HAY)
put(29, 2, 6, P + "carved_pumpkin[facing=west]")
put(26, 0, 7, HAY)
put(27, 0, 7, HAYX)
put(26, 1, 7, HAY)
put(27, 0, 8, POTS[1])

cluster("west_store")              # crates and flour sacks west of the factory
for x, z in ((2, 12), (3, 12), (2, 13)):
    put(x, 0, z, BARREL)
put(2, 1, 12, BARREL)
sack(3, 13); sack(2, 14); sack(3, 14)
put(1, 0, 16, HAY); put(2, 0, 16, HAY); put(1, 1, 16, HAY)
put(3, 0, 17, POTS[4])
lamp(4, 11)
lamp(4, 22)

cluster("west_bench_garden")       # small garden with a bench further back
for z in (19, 20, 21):
    put(3, 0, z, BENCH_E)
put(3, 0, 18, POTS[3]); put(3, 0, 22, POTS[5])
for x in (1, 2):
    for z in (19, 20, 21):
        put(x, 0, z, FLOWERS[(x + z) % len(FLOWERS)])

cluster("east_yard")               # tree, crates and hay on the east side
tree(28, 13, r=1)
for x, z in ((27, 18), (28, 18), (28, 19)):
    put(x, 0, z, BARREL)
put(27, 1, 18, BARREL)
put(27, 0, 19, HAY)
sack(28, 20); sack(27, 21); sack(28, 21)
put(26, 0, 17, POTS[2])
lamp(26, 11)
lamp(26, 22)

cluster("east_bench")              # bench facing west towards the factory
for z in (15, 16):
    put(26, 0, z, BENCH_W)
put(26, 0, 14, POTS[0])

cluster("back_yard")               # hedge, log pile, trees, lamps behind the factory
for x in (7, 8, 9, 22, 23, 24):
    put(x, 0, 28, OAKLEAVES)
    if x in (8, 23):
        put(x, 1, 28, OAKLEAVES)
for x in (12, 13, 14, 15, 16):
    put(x, 0, 28, P + "spruce_log[axis=x]")
for x in (13, 14, 15):
    put(x, 1, 28, P + "spruce_log[axis=x]")
put(14, 2, 28, P + "spruce_log[axis=x]")
tree(3, 26, leaves=OAKLEAVES, log="oak_log")
tree(27, 26)
lamp(10, 26)
lamp(20, 26)
for i, x in enumerate((11, 12, 18, 19)):
    put(x, 0, 27, FLOWERS[i])


# --- validation ----------------------------------------------------------------------------------------------------
def keep_out(x: int, z: int) -> str | None:
    if x in (0, PLOT_X - 1) or z in (0, PLOT_Z - 1):
        return "ring"
    for name, r in KEEP_OUT.items():
        if r[0] <= x <= r[2] and r[1] <= z <= r[3]:
            return name
    return None


def validate():
    for (x, y, z), data in blocks.items():
        house_lib.check_block_data(data)
        assert 0 <= y <= 12, (x, y, z)
        k = keep_out(x, z)
        assert k is None, f"{(x, y, z)} {data} is inside keep-out '{k}'"
        b = house_lib.parse_block_data(data)[0]
        if b in NEEDS_GROUND:
            assert y == 0 or blocks.get((x, y - 1, z), "").startswith(P + "farmland"), f"{(x, y, z)} floats"
        if b == "lantern":
            assert (x, y - 1, z) in blocks, f"{(x, y, z)} lantern floats"
        assert not b.endswith("_fence"), "no fences (would need connection states)"
        if b == "water":
            assert y == 0 and all(blocks.get((x + dx, 0, z + dz), "").startswith(P + "farmland")
                                  for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))), "water must be enclosed"
    assert 120 <= len(blocks) <= 400


def write_yaml() -> str:
    lines = ["# Plot decoration set for /bakery plot setup. GENERATED by design/scripts/decor_style.py; do not edit by hand.",
             "# Format, coordinate frame and keep-out rules: design/decor.md",
             "# at = [x, y, z] in plot cells from the plot's min corner (x west->east, z north->south, y 0 = first air layer",
             "# above the ground; same frame as layout: in houses/factory.yml). block = full block data.",
             f"# {len(blocks)} blocks.", "blocks:"]
    for name, cells in clusters.items():
        lines.append(f"  # {name}")
        for pos in cells:
            lines.append(f'  - {{at: [{pos[0]}, {pos[1]}, {pos[2]}], block: "{blocks[pos]}"}}')
    return "\n".join(lines) + "\n"


def preview():
    s = 24
    img = Image.new("RGB", (PLOT_X * s, PLOT_Z * s), (96, 140, 62))
    d = ImageDraw.Draw(img)

    def rect(x0, z0, x1, z1, col):
        d.rectangle([x0 * s, z0 * s, (x1 + 1) * s - 1, (z1 + 1) * s - 1], fill=col)

    rect(*KEEP_OUT["front"], (118, 150, 76))
    rect(*KEEP_OUT["factory"], (140, 120, 110))
    rect(6, 10, 24, 23, (150, 82, 66))
    rect(BELT_X, BELT_Z[0], BELT_X, BELT_Z[1], (60, 60, 60))
    for x, z, c in ((18, 6, (240, 200, 80)), (16, 6, (80, 160, 240)), (20, 6, (80, 160, 240))):
        rect(x, z, x, z, c)
    rect(14, 0, 16, 0, (200, 170, 90))
    for i in range(PLOT_X):
        for z in (0, PLOT_Z - 1):
            if not (z == 0 and 14 <= i <= 16):
                rect(i, z, i, z, (115, 85, 52))
    for i in range(PLOT_Z):
        for x in (0, PLOT_X - 1):
            rect(x, i, x, i, (115, 85, 52))
    col = {"cherry_leaves": (232, 150, 190), "oak_leaves": (50, 110, 40), "log": (62, 44, 26),
           "lantern": (255, 200, 80), "barrel": (125, 92, 54), "hay": (210, 180, 60), "wool": (235, 235, 235),
           "carpet": (235, 235, 235), "farmland": (110, 70, 40), "wheat": (200, 180, 70), "water": (50, 90, 200),
           "stairs": (160, 120, 70), "pumpkin": (230, 130, 30)}
    top: dict[tuple[int, int], tuple[int, tuple]] = {}
    for (x, y, z), data in blocks.items():
        b = house_lib.parse_block_data(data)[0]
        c = (225, 60, 160)                       # flowers and pots
        for key, v in col.items():
            if key in b:
                c = v
        if (x, z) not in top or y >= top[(x, z)][0]:
            top[(x, z)] = (y, c)
    for (x, z), (_, c) in top.items():
        rect(x, z, x, z, c)
    for i in range(PLOT_X + 1):
        d.line([i * s, 0, i * s, PLOT_Z * s], fill=(70, 100, 50), width=1)
    for i in range(PLOT_Z + 1):
        d.line([0, i * s, PLOT_X * s, i * s], fill=(70, 100, 50), width=1)
    OUT_PNG.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT_PNG)


validate()
OUT_YML.write_text(write_yaml())
preview()
print(f"decor.yml: {len(blocks)} blocks in {len(clusters)} clusters")
