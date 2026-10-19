"""The `default` bakery cottage: a block list file for `/bakery house place default`.

Outputs:
  src/main/resources/houses/default.yml           (the design file the plugin parses)
  design/scripts/previews/house_default_layers.png (top view of every layer)
  design/scripts/previews/house_default_iso.png    (orthographic 3/4 views, front and back)
Run with `mise run assets` (or `python design/scripts/house_default.py` from the repo root).
Deterministic. Spec and file format: design/house-default.md

Design axes: x = west -> east (0..8), y = bottom -> top (0..6), z = north -> south (0..6).
The front (door, windows, sign) faces north (-Z).
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

import yaml
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
from voxel_render import render  # noqa: E402

OUT_YML = ROOT / "src/main/resources/houses/default.yml"
OUT_DIR = ROOT / "design/scripts/previews"

SX, SY, SZ = 9, 7, 7
ORIGIN = (0, 0, 0)
KEEP = "."

# char -> block data string (Minecraft 26.3 ids and states, every property spelled out)
PALETTE = {
    "_": "minecraft:air",
    "S": "minecraft:stone_bricks",
    "M": "minecraft:mossy_stone_bricks",
    "s": "minecraft:stone_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]",
    "F": "minecraft:spruce_planks",
    "P": "minecraft:spruce_log[axis=y]",
    "X": "minecraft:stripped_spruce_log[axis=x]",
    "Z": "minecraft:stripped_spruce_log[axis=z]",
    "W": "minecraft:white_terracotta",
    "B": "minecraft:bricks",
    "-": "minecraft:glass_pane[east=true,north=false,south=false,waterlogged=false,west=true]",
    "|": "minecraft:glass_pane[east=false,north=true,south=true,waterlogged=false,west=false]",
    "d": "minecraft:oak_door[facing=north,half=lower,hinge=left,open=false,powered=false]",
    "D": "minecraft:oak_door[facing=north,half=upper,hinge=left,open=false,powered=false]",
    "v": "minecraft:dark_oak_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]",
    "^": "minecraft:dark_oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]",
    "=": "minecraft:dark_oak_slab[type=bottom,waterlogged=false]",
    "L": "minecraft:lantern[hanging=true,waterlogged=false]",
    "H": "minecraft:spruce_hanging_sign[attached=false,rotation=8,waterlogged=false]",
    "r": "minecraft:potted_red_tulip",
    "o": "minecraft:potted_orange_tulip",
}

# --- block data validation -------------------------------------------------------------------
BLOCK_DATA_RE = re.compile(r"^minecraft:[a-z0-9_]+(\[[a-z0-9_]+=[a-z0-9_]+(,[a-z0-9_]+=[a-z0-9_]+)*\])?$")
BOOL = {"true", "false"}
HORIZONTAL = {"north", "south", "west", "east"}
# property schemas (26.3 vanilla), matched by block id suffix/name
SCHEMAS = [
    (lambda b: b.endswith("_stairs"), {"facing": HORIZONTAL, "half": {"top", "bottom"},
     "shape": {"straight", "inner_left", "inner_right", "outer_left", "outer_right"},
     "waterlogged": BOOL}),
    (lambda b: b.endswith("_slab"), {"type": {"top", "bottom", "double"}, "waterlogged": BOOL}),
    (lambda b: b.endswith("_door"), {"facing": HORIZONTAL, "half": {"upper", "lower"},
     "hinge": {"left", "right"}, "open": BOOL, "powered": BOOL}),
    (lambda b: b.endswith("glass_pane"), {d: BOOL for d in ("east", "north", "south", "west", "waterlogged")}),
    (lambda b: b.endswith("_log"), {"axis": {"x", "y", "z"}}),
    (lambda b: b.endswith("_hanging_sign") and "_wall_" not in b,
     {"attached": BOOL, "rotation": {str(i) for i in range(16)}, "waterlogged": BOOL}),
    (lambda b: b == "lantern", {"hanging": BOOL, "waterlogged": BOOL}),
    (lambda b: b in {"air", "stone_bricks", "mossy_stone_bricks", "spruce_planks", "white_terracotta",
                     "bricks", "potted_red_tulip", "potted_orange_tulip"}, {}),
]
GRAVITY = {"sand", "red_sand", "gravel", "anvil", "dragon_egg", "pointed_dripstone", "scaffolding"}
FULL_SOLID = {"S", "M", "F", "P", "X", "Z", "W", "B"}   # full cubes with sturdy faces
PANE = {"-", "|"}


def parse_block_data(data: str) -> tuple[str, dict[str, str]]:
    if not BLOCK_DATA_RE.match(data):
        raise ValueError(f"bad block data syntax: {data}")
    block, _, rest = data[len("minecraft:"):].partition("[")
    props = dict(p.split("=") for p in rest.rstrip("]").split(",")) if rest else {}
    return block, props


def check_block_data(data: str) -> None:
    block, props = parse_block_data(data)
    if block.removesuffix("_concrete_powder") != block or block in GRAVITY:
        raise ValueError(f"gravity block not allowed: {data}")
    for match, schema in SCHEMAS:
        if match(block):
            if set(props) != set(schema):
                raise ValueError(f"{data}: properties {sorted(props)} != expected {sorted(schema)}")
            for k, v in props.items():
                if v not in schema[k]:
                    raise ValueError(f"{data}: {k}={v} not in {sorted(schema[k])}")
            return
    raise ValueError(f"no schema for block {block}; add one before using it")


# --- the design --------------------------------------------------------------------------------
def build() -> dict[tuple[int, int, int], str]:
    g: dict[tuple[int, int, int], str] = {}

    def put(x, y, z, c):
        assert 0 <= x < SX and 0 <= y < SY and 0 <= z < SZ, (x, y, z)
        g[(x, y, z)] = c

    body_x, body_z = range(1, 8), range(1, 6)          # walls on x=1,7 and z=1,5
    inner_x, inner_z = range(2, 7), range(2, 5)

    # y0: stone plinth (visible, the house sits on the ground) + spruce floor + front step
    mossy = {(1, 1), (2, 5), (7, 4), (6, 1)}
    for x in body_x:
        for z in body_z:
            edge = x in (1, 7) or z in (1, 5)
            put(x, 0, z, ("M" if (x, z) in mossy else "S") if edge else "F")
    put(4, 0, 0, "s")                                   # step up to the door
    put(3, 0, 0, "r"); put(5, 0, 0, "o")               # potted tulips either side (pots need no support)
    put(8, 0, 3, "B")                                   # chimney base

    # y1..y3: timber-framed walls, interior air
    for y in (1, 2, 3):
        for x in body_x:
            for z in body_z:
                if x in inner_x and z in inner_z:
                    put(x, y, z, "_")
                elif x in (1, 7) and z in (1, 5):
                    put(x, y, z, "P")                   # corner posts
                elif y == 3:
                    put(x, y, z, "X" if z in (1, 5) else "Z")   # top beam
                else:
                    put(x, y, z, "W")
    # front (z=1): windows at x=2,6 (y2), door at x=4
    put(2, 2, 1, "-"); put(6, 2, 1, "-")
    put(4, 1, 1, "d"); put(4, 2, 1, "D")
    # back (z=5): windows at x=3,5
    put(3, 2, 5, "-"); put(5, 2, 5, "-")
    # west gable wall (x=1): window at z=3
    put(1, 2, 3, "|")
    # east gable wall (x=7): chimney breast at z=3, windows either side
    for y in (1, 2, 3):
        put(7, y, 3, "B")
    put(7, 2, 2, "|"); put(7, 2, 4, "|")

    # roof: shallow stair/slab pitch over x=0..8 (one block overhang all round)
    profile = {0: (3, "v"), 1: (4, "="), 2: (4, "v"), 3: (5, "="), 4: (4, "^"), 5: (4, "="), 6: (3, "^")}
    for x in range(SX):
        for z, (y, c) in profile.items():
            put(x, y, z, c)
    # gables (y4, z3 under the ridge) + attic air
    put(1, 4, 3, "F")
    put(7, 4, 3, "B")
    for x in inner_x:
        put(x, 4, 3, "_")

    # chimney: brick stack outside the east gable, through the roof overhang
    for y in range(0, 6):
        put(8, y, 3, "B")

    # porch: lantern and hanging sign under the front eave, either side of the door
    put(3, 2, 0, "L")
    put(5, 2, 0, "H")
    return g


POINTS = {"chimney": [8, 6, 3], "door": [4, 1, 1]}
SIGNS = [{"at": [5, 2, 0], "lines": ["", "Bakery", "", ""]}]


# --- structural checks -------------------------------------------------------------------------
def validate(g: dict, layers: list[list[str]]) -> int:
    for c, data in PALETTE.items():
        assert len(c) == 1 and c != KEEP, c
        check_block_data(data)
    assert len(layers) == SY and all(len(l) == SZ and all(len(r) == SX for r in l) for l in layers)
    for layer in layers:
        for row in layer:
            for c in row:
                assert c == KEEP or c in PALETTE, f"unknown char {c!r}"
    inside = lambda p: all(0 <= p[i] < (SX, SY, SZ)[i] for i in range(3))  # noqa: E731
    assert inside(ORIGIN)
    for name, p in POINTS.items():
        assert inside(p), f"point {name} {p} out of bounds"
    at = lambda x, y, z: g.get((x, y, z), KEEP)  # noqa: E731
    cx, cy, cz = POINTS["chimney"]
    assert at(cx, cy, cz) in (KEEP, "_") and at(cx, cy - 1, cz) == "B", "chimney point must sit on the stack"
    assert at(*POINTS["door"]) == "d"
    for s in SIGNS:
        assert inside(s["at"]) and at(*s["at"]) == "H" and len(s["lines"]) <= 4

    for (x, y, z), c in g.items():
        # support from inside the design only
        if c == "d":
            assert at(x, y + 1, z) == "D" and at(x, y - 1, z) in FULL_SOLID, (x, y, z)
        if c in ("L", "H"):
            assert at(x, y + 1, z) in FULL_SOLID | {"v", "^", "s", "="}, f"hanging block needs a ceiling {(x, y, z)}"
        # glass pane connections must match the neighbours (outside / keep = no connection)
        if c in PANE:
            _, props = parse_block_data(PALETTE[c])
            for d, (dx, dz) in {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}.items():
                n = at(x + dx, y, z + dz)
                want = n in FULL_SOLID or n in PANE
                assert (props[d] == "true") == want, f"pane {(x, y, z)} {d}: {props[d]} vs neighbour {n!r}"
        # straight stairs: no perpendicular stair in front of / behind them (that would bend the shape)
        if c in ("v", "^", "s"):
            for dz in (-1, 1):
                n = at(x, y, z + dz)
                if n in ("v", "^", "s"):
                    raise AssertionError(f"stair {(x, y, z)} has a stair neighbour along its facing axis")
    blocks = sum(1 for c in g.values() if c != "_")
    assert blocks < 2000
    return blocks


def to_layers(g: dict) -> list[list[str]]:
    return [["".join(g.get((x, y, z), KEEP) for x in range(SX)) for z in range(SZ)] for y in range(SY)]


def write_yaml(layers: list[list[str]], blocks: int) -> str:
    q = lambda s: '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'  # noqa: E731
    out = [
        "# The `default` bakery cottage. GENERATED by design/scripts/house_default.py; do not edit by hand.",
        "# Format and coordinates: design/house-default.md",
        "# Axes: x = west->east, y = bottom->top, z = north->south. Front (door) faces north (-Z).",
        f"# {blocks} blocks (not counting air/keep).",
        f"size: [{SX}, {SY}, {SZ}]",
        f"origin: [{ORIGIN[0]}, {ORIGIN[1]}, {ORIGIN[2]}]",
        "palette:",
    ]
    out += [f"  {q(c)}: {q(d)}" for c, d in PALETTE.items()]
    out.append("layers:")
    for y, layer in enumerate(layers):
        out.append(f"  # y={y}")
        out.append("  - [" + ", ".join(q(r) for r in layer) + "]")
    out.append("points:")
    out += [f"  {k}: [{v[0]}, {v[1]}, {v[2]}]" for k, v in POINTS.items()]
    out.append("signs:")
    for s in SIGNS:
        out.append(f"  - at: [{s['at'][0]}, {s['at'][1]}, {s['at'][2]}]")
        out.append("    lines: [" + ", ".join(q(l) for l in s["lines"]) + "]")
    return "\n".join(out) + "\n"


# --- previews ----------------------------------------------------------------------------------
# Approximate average colours of the vanilla textures, for previews only.
COLOURS = {
    "S": (122, 121, 122), "M": (108, 121, 94), "s": (122, 121, 122), "F": (115, 85, 52),
    "P": (62, 44, 26), "X": (118, 90, 58), "Z": (118, 90, 58), "W": (209, 178, 161),
    "B": (150, 82, 66), "-": (178, 214, 226), "|": (178, 214, 226), "d": (140, 110, 64),
    "D": (140, 110, 64), "v": (66, 44, 22), "^": (66, 44, 22), "=": (78, 52, 28),
    "L": (236, 178, 72), "H": (115, 85, 52), "_": (255, 255, 255),
    "r": (200, 60, 48), "o": (232, 130, 40),
}


def boxes_for(c: str, x: int, y: int, z: int):
    col = COLOURS[c]
    b = lambda x1, y1, z1, x2, y2, z2, rgb=col: ((x + x1, y + y1, z + z1), (x + x2, y + y2, z + z2), rgb)  # noqa: E731
    if c == "_":
        return []
    if c == "=":
        return [b(0, 0, 0, 1, .5, 1)]
    if c in ("v", "s"):
        return [b(0, 0, 0, 1, .5, 1), b(0, .5, .5, 1, 1, 1)]
    if c == "^":
        return [b(0, 0, 0, 1, .5, 1), b(0, .5, 0, 1, 1, .5)]
    if c == "-":
        return [b(0, 0, 7 / 16, 1, 1, 9 / 16)]
    if c == "|":
        return [b(7 / 16, 0, 0, 9 / 16, 1, 1)]
    if c in ("d", "D"):
        return [b(0, 0, 13 / 16, 1, 1, 1)]
    if c == "L":
        return [b(5 / 16, 1 / 16, 5 / 16, 11 / 16, 8 / 16, 11 / 16),
                b(7 / 16, 8 / 16, 7 / 16, 9 / 16, 1, 9 / 16, (60, 60, 66))]
    if c in ("r", "o"):
        return [b(5 / 16, 0, 5 / 16, 11 / 16, 6 / 16, 11 / 16, (142, 76, 56)),
                b(6 / 16, 6 / 16, 6 / 16, 10 / 16, 12 / 16, 10 / 16)]
    if c == "H":
        return [b(1 / 16, 0, 7 / 16, 15 / 16, 10 / 16, 9 / 16),
                b(3 / 16, 10 / 16, 7.5 / 16, 4 / 16, 1, 8.5 / 16, (60, 60, 66)),
                b(12 / 16, 10 / 16, 7.5 / 16, 13 / 16, 1, 8.5 / 16, (60, 60, 66))]
    return [b(0, 0, 0, 1, 1, 1)]


def ground_boxes():
    """A patch of grass around the house (preview only, not part of the design)."""
    return [((-2, -1, -3), (SX + 2, 0, SZ + 2), (96, 140, 62))]


def font(size: int):
    try:
        return ImageFont.load_default(size=size)
    except TypeError:
        return ImageFont.load_default()


def layer_sheet(layers) -> Image.Image:
    cell, pad, head = 30, 16, 24
    lw, lh = SX * cell, SZ * cell
    cols = 4
    rows = (len(layers) + cols - 1) // cols
    img = Image.new("RGBA", (cols * (lw + pad) + pad, rows * (lh + head + pad) + pad + 22), (40, 44, 52, 255))
    d = ImageDraw.Draw(img)
    f, small = font(16), font(14)
    d.text((pad, 6), "default cottage: layers bottom->top, x ->, z (north at top) v; '.' = keep", fill=(230, 230, 230), font=small)
    for y, layer in enumerate(layers):
        ox = pad + (y % cols) * (lw + pad)
        oy = pad + 22 + (y // cols) * (lh + head + pad)
        d.text((ox, oy), f"y={y}", fill=(240, 240, 240), font=f)
        for z, row in enumerate(layer):
            for x, c in enumerate(row):
                x0, y0 = ox + x * cell, oy + head + z * cell
                if c == KEEP:
                    shade = (70, 76, 88) if (x + z) % 2 else (62, 67, 78)
                    d.rectangle([x0, y0, x0 + cell - 1, y0 + cell - 1], fill=shade)
                    continue
                rgb = COLOURS[c] if c != "_" else (30, 32, 38)
                d.rectangle([x0, y0, x0 + cell - 1, y0 + cell - 1], fill=rgb, outline=(20, 20, 24))
                lum = 0.3 * rgb[0] + 0.59 * rgb[1] + 0.11 * rgb[2]
                d.text((x0 + cell / 2, y0 + cell / 2), c, fill=(0, 0, 0) if lum > 120 else (245, 245, 245),
                       font=f, anchor="mm")
    return img


def iso_sheet(g: dict) -> Image.Image:
    boxes = ground_boxes()
    for (x, y, z), c in sorted(g.items()):
        boxes += boxes_for(c, x, y, z)
    views = [render(boxes, yaw=yaw, pitch=pitch, px_per_unit=36)
             for yaw, pitch in ((215, 28), (145, 28), (35, 28), (180, 8))]
    pad = 12
    w = sum(v.width for v in views[:2]) + 3 * pad
    h = max(v.height for v in views[:2]) + max(v.height for v in views[2:]) + 3 * pad
    out = Image.new("RGBA", (w, h), (58, 64, 76, 255))
    x = pad
    for v in views[:2]:
        out.alpha_composite(v, (x, pad))
        x += v.width + pad
    x = pad
    y2 = pad * 2 + max(v.height for v in views[:2])
    for v in views[2:]:
        out.alpha_composite(v, (x, y2))
        x += v.width + pad
    return out


def main() -> int:
    g = build()
    layers = to_layers(g)
    blocks = validate(g, layers)
    text = write_yaml(layers, blocks)
    parsed = yaml.safe_load(text)
    assert parsed["layers"] == layers and parsed["size"] == [SX, SY, SZ]
    assert parsed["palette"] == PALETTE and parsed["points"] == POINTS and parsed["signs"] == SIGNS
    OUT_YML.parent.mkdir(parents=True, exist_ok=True)
    OUT_YML.write_text(text, encoding="utf-8")
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    layer_sheet(layers).save(OUT_DIR / "house_default_layers.png")
    iso_sheet(g).save(OUT_DIR / "house_default_iso.png")
    print(f"{OUT_YML.relative_to(ROOT)}: {blocks} blocks")
    print(f"previews in {OUT_DIR.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
