"""Shared helpers for house design generators (design/scripts/house_*.py).

A design is built as a grid {(x, y, z): char} plus a palette {char: block data}. This module:
- checks block data strings against 26.3 vanilla property schemas (check_block_data),
- runs the structural checks every design must pass (validate): bounds, smoke/door/sign points,
  support for doors/hanging/standing/wall-mounted blocks, pane/bars/fence connections against the
  neighbours, straight stairs, no gravity blocks, volume < house.max-blocks,
- writes the YAML (write_yaml) and renders previews (layer_sheet, iso_sheet).
Format spec: design/house-default.md (+ the list points in design/factory.md).
Axes: x = west -> east, y = bottom -> top, z = north -> south.
"""
from __future__ import annotations

import re

from PIL import Image, ImageDraw, ImageFont

from voxel_render import render

KEEP = "."
MAX_VOLUME = 40000          # config.yml house.max-blocks (design box volume)

# --- block data validation -------------------------------------------------------------------
BLOCK_DATA_RE = re.compile(r"^minecraft:[a-z0-9_]+(\[[a-z0-9_]+=[a-z0-9_]+(,[a-z0-9_]+=[a-z0-9_]+)*\])?$")
BOOL = {"true", "false"}
HORIZONTAL = {"north", "south", "west", "east"}
ALL_DIRS = HORIZONTAL | {"up", "down"}
CONNECT4 = {d: BOOL for d in ("east", "north", "south", "west", "waterlogged")}
# property schemas (26.3 vanilla, checked against the paper-api 26.3 Material/BlockData classes)
SCHEMAS = [
    (lambda b: b.endswith("_stairs"), {"facing": HORIZONTAL, "half": {"top", "bottom"},
     "shape": {"straight", "inner_left", "inner_right", "outer_left", "outer_right"},
     "waterlogged": BOOL}),
    (lambda b: b.endswith("_slab"), {"type": {"top", "bottom", "double"}, "waterlogged": BOOL}),
    (lambda b: b.endswith("_door"), {"facing": HORIZONTAL, "half": {"upper", "lower"},
     "hinge": {"left", "right"}, "open": BOOL, "powered": BOOL}),
    (lambda b: b.endswith("_trapdoor"), {"facing": HORIZONTAL, "half": {"top", "bottom"}, "open": BOOL,
     "powered": BOOL, "waterlogged": BOOL}),
    (lambda b: b.endswith("glass_pane") or b.endswith("_bars") or b.endswith("_fence"), CONNECT4),
    (lambda b: b.endswith("_log") or b == "hay_block", {"axis": {"x", "y", "z"}}),
    (lambda b: b.endswith("_hanging_sign") and "_wall_" not in b,
     {"attached": BOOL, "rotation": {str(i) for i in range(16)}, "waterlogged": BOOL}),
    (lambda b: b.endswith("_wall_sign") and "hanging" not in b, {"facing": HORIZONTAL, "waterlogged": BOOL}),
    (lambda b: b.endswith("lantern"), {"hanging": BOOL, "waterlogged": BOOL}),
    (lambda b: b.endswith("_chain"), {"axis": {"x", "y", "z"}, "waterlogged": BOOL}),
    (lambda b: b.endswith("lightning_rod"), {"facing": ALL_DIRS, "powered": BOOL, "waterlogged": BOOL}),
    (lambda b: b == "campfire", {"facing": HORIZONTAL, "lit": BOOL, "signal_fire": BOOL, "waterlogged": BOOL}),
    (lambda b: b == "barrel", {"facing": ALL_DIRS, "open": BOOL}),
    (lambda b: b == "composter", {"level": {str(i) for i in range(9)}}),
    (lambda b: b.endswith("copper_grate"), {"waterlogged": BOOL}),
    (lambda b: b.startswith("potted_"), {}),
    (lambda b: b in {"air", "stone_bricks", "mossy_stone_bricks", "chiseled_stone_bricks", "spruce_planks",
                     "dark_oak_planks", "white_terracotta", "terracotta", "bricks", "magma_block", "shroomlight",
                     "waxed_cut_copper", "white_wool", "white_carpet", "glass"}, {}),
]
GRAVITY = {"sand", "red_sand", "gravel", "anvil", "dragon_egg", "pointed_dripstone", "scaffolding"}
# full cubes with sturdy faces (panes, bars and fences connect to them; they carry anything)
SOLID = {"stone_bricks", "mossy_stone_bricks", "chiseled_stone_bricks", "spruce_planks", "dark_oak_planks",
         "white_terracotta", "terracotta", "bricks", "magma_block", "shroomlight", "waxed_cut_copper",
         "white_wool", "glass", "barrel", "hay_block", "waxed_copper_grate"}
LOGS = lambda b: b.endswith("_log")  # noqa: E731
# neighbours a pane/bars/fence is known NOT to connect to (anything else that is not solid fails the check)
NO_CONNECT = {"air"}
DIRS = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0),
        "up": (0, 1, 0), "down": (0, -1, 0)}
OPPOSITE = {"north": "south", "south": "north", "west": "east", "east": "west", "up": "down", "down": "up"}


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


def is_solid(block: str) -> bool:
    return block in SOLID or LOGS(block)


def family(block: str) -> str | None:
    if block.endswith("glass_pane") or block.endswith("_bars"):
        return "pane"
    if block.endswith("_fence") and "nether" not in block:
        return "wood_fence"
    return None


# --- structural checks -------------------------------------------------------------------------
def validate(g: dict, layers: list[list[str]], palette: dict, size: tuple, origin: tuple,
             points: dict, signs: list, smoke_keys=("chimney", "smoke")) -> int:
    """Every check the cottage ran, generalised from block data. Returns the block count (no air/keep)."""
    sx, sy, sz = size
    for c, data in palette.items():
        assert len(c) == 1 and c not in (KEEP, " "), c
        check_block_data(data)
    assert len(layers) == sy and all(len(l) == sz and all(len(r) == sx for r in l) for l in layers)
    for layer in layers:
        for row in layer:
            for c in row:
                assert c == KEEP or c in palette, f"unknown char {c!r}"
    inside = lambda p: all(0 <= p[i] < size[i] for i in range(3))  # noqa: E731
    assert inside(origin)

    def plist(v):
        return v if isinstance(v[0], list) else [v]

    for name, v in points.items():
        for p in plist(v):
            assert inside(p), f"point {name} {p} out of bounds"
    blk = {c: parse_block_data(d) for c, d in palette.items()}
    at = lambda x, y, z: g.get((x, y, z), KEEP)  # noqa: E731

    def bid(x, y, z):
        c = at(x, y, z)
        return "air" if c == KEEP else blk[c][0]

    def props(x, y, z):
        c = at(x, y, z)
        return {} if c == KEEP else blk[c][1]

    for key in smoke_keys:
        for p in plist(points.get(key, [[]])) if key in points else []:
            assert bid(*p) == "air" and is_solid(bid(p[0], p[1] - 1, p[2])), f"{key} {p} must sit on its stack"
    for p in plist(points.get("steam", [[]])) if "steam" in points else []:
        assert bid(*p) == "air" and bid(p[0], p[1] - 1, p[2]) != "air", f"steam {p} must sit on its vent"
    if "door" in points:
        b, pr = bid(*points["door"]), props(*points["door"])
        assert b.endswith("_door") and pr["half"] == "lower", "door point must be the lower door half"
    for s in signs:
        assert inside(s["at"]) and "sign" in bid(*s["at"]) and len(s["lines"]) <= 4, s

    for (x, y, z), c in g.items():
        b, pr = blk[c]
        below, above = bid(x, y - 1, z), bid(x, y + 1, z)
        ceiling_ok = is_solid(above) or above.endswith("_stairs") and props(x, y + 1, z)["half"] == "bottom" \
            or above.endswith("_slab") and props(x, y + 1, z)["type"] != "top" or above.endswith("_chain")
        if b.endswith("_door"):
            if pr["half"] == "lower":
                up = props(x, y + 1, z)
                assert above == b and up["half"] == "upper" and up["facing"] == pr["facing"], (x, y, z)
                assert is_solid(below), f"door {(x, y, z)} needs a floor"
            else:
                assert below == b and props(x, y - 1, z)["half"] == "lower", (x, y, z)
        if b.endswith("lantern"):
            if pr["hanging"] == "true":
                assert ceiling_ok, f"hanging lantern needs a ceiling {(x, y, z)}"
            else:
                assert below != "air", f"standing lantern needs a floor {(x, y, z)}"
        if b.endswith("_hanging_sign") and "_wall_" not in b:
            assert ceiling_ok, f"hanging sign needs a ceiling {(x, y, z)}"
        if b.endswith("_chain") and pr["axis"] == "y":
            assert ceiling_ok, f"chain needs a ceiling {(x, y, z)}"
        if b.endswith("_wall_sign"):
            dx, dy, dz = DIRS[OPPOSITE[pr["facing"]]]
            assert is_solid(bid(x + dx, y + dy, z + dz)), f"wall sign {(x, y, z)} needs a wall behind it"
        if b == "white_carpet":
            assert below != "air", f"carpet needs a floor {(x, y, z)}"
        # panes/bars/fences: connection flags must match the neighbours (outside / keep = no connection)
        fam = family(b)
        if fam:
            for d in HORIZONTAL:
                dx, _, dz = DIRS[d]
                nb = bid(x + dx, y, z + dz)
                if is_solid(nb) or family(nb) == fam:
                    want = True
                elif nb in NO_CONNECT or nb.endswith("_door") or nb.endswith("_wall_sign") \
                        or nb.endswith("lantern") or nb.startswith("potted_") or nb == "campfire":
                    want = False
                elif fam == "wood_fence" and nb.endswith("_fence_gate"):
                    want = True
                else:
                    raise AssertionError(f"{b} {(x, y, z)}: neighbour {nb} {d} has no known connection rule")
                assert (pr[d] == "true") == want, f"{b} {(x, y, z)} {d}: {pr[d]} vs neighbour {nb!r}"
        # straight stairs: no stair with a perpendicular facing (same half) in front of / behind them
        if b.endswith("_stairs"):
            assert pr["shape"] == "straight", (x, y, z)
            for d in (pr["facing"], OPPOSITE[pr["facing"]]):
                dx, _, dz = DIRS[d]
                nb = bid(x + dx, y, z + dz)
                if nb.endswith("_stairs"):
                    np_ = props(x + dx, y, z + dz)
                    perp = {np_["facing"], pr["facing"]} not in ({"north", "south"}, {"east", "west"}) \
                        and np_["facing"] != pr["facing"]
                    if perp and np_["half"] == pr["half"]:
                        raise AssertionError(f"stair {(x, y, z)} would bend: neighbour {d} faces {np_['facing']}")
    assert sx * sy * sz < MAX_VOLUME
    return sum(1 for c in g.values() if blk[c][0] != "air")


def to_layers(g: dict, size: tuple) -> list[list[str]]:
    sx, sy, sz = size
    return [["".join(g.get((x, y, z), KEEP) for x in range(sx)) for z in range(sz)] for y in range(sy)]


def _q(s: str) -> str:
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


def _vec(v) -> str:
    if isinstance(v[0], list):
        return "[" + ", ".join(_vec(p) for p in v) + "]"
    return f"[{v[0]}, {v[1]}, {v[2]}]"


def write_yaml(header: list[str], layers, palette: dict, size: tuple, origin: tuple, points: dict,
               signs: list) -> str:
    out = list(header) + [
        f"size: [{size[0]}, {size[1]}, {size[2]}]",
        f"origin: [{origin[0]}, {origin[1]}, {origin[2]}]",
        "palette:",
    ]
    out += [f"  {_q(c)}: {_q(d)}" for c, d in palette.items()]
    out.append("layers:")
    for y, layer in enumerate(layers):
        out.append(f"  # y={y}")
        out.append("  - [" + ", ".join(_q(r) for r in layer) + "]")
    out.append("points:")
    out += [f"  {k}: {_vec(v)}" for k, v in points.items()]
    out.append("signs:")
    for s in signs:
        out.append(f"  - at: {_vec(s['at'])}")
        out.append("    lines: [" + ", ".join(_q(l) for l in s["lines"]) + "]")
    return "\n".join(out) + "\n"


# --- previews ----------------------------------------------------------------------------------
def font(size: int):
    try:
        return ImageFont.load_default(size=size)
    except TypeError:
        return ImageFont.load_default()


def layer_sheet(layers, size: tuple, colours: dict, title: str, cell: int = 30, cols: int = 4) -> Image.Image:
    sx, _, sz = size
    pad, head = 16, 24
    lw, lh = sx * cell, sz * cell
    rows = (len(layers) + cols - 1) // cols
    img = Image.new("RGBA", (cols * (lw + pad) + pad, rows * (lh + head + pad) + pad + 22), (40, 44, 52, 255))
    d = ImageDraw.Draw(img)
    f, small = font(16), font(14)
    d.text((pad, 6), title, fill=(230, 230, 230), font=small)
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
                rgb = colours[c] if c != "_" else (30, 32, 38)
                d.rectangle([x0, y0, x0 + cell - 1, y0 + cell - 1], fill=rgb, outline=(20, 20, 24))
                lum = 0.3 * rgb[0] + 0.59 * rgb[1] + 0.11 * rgb[2]
                d.text((x0 + cell / 2, y0 + cell / 2), c, fill=(0, 0, 0) if lum > 120 else (245, 245, 245),
                       font=f, anchor="mm")
    return img


def iso_sheet(boxes, views=((215, 28), (145, 28), (35, 28), (180, 8)), px_per_unit: int = 36,
              background=(58, 64, 76, 255)) -> Image.Image:
    """Two views on top, the rest below (same layout as the cottage sheet)."""
    imgs = [render(boxes, yaw=yaw, pitch=pitch, px_per_unit=px_per_unit) for yaw, pitch in views]
    pad = 12
    top, bottom = imgs[:2], imgs[2:]
    w = max(sum(v.width for v in top) + (len(top) + 1) * pad, sum(v.width for v in bottom) + (len(bottom) + 1) * pad)
    h = max(v.height for v in top) + max(v.height for v in bottom) + 3 * pad
    out = Image.new("RGBA", (w, h), background)
    x = pad
    for v in top:
        out.alpha_composite(v, (x, pad))
        x += v.width + pad
    x = pad
    y2 = pad * 2 + max(v.height for v in top)
    for v in bottom:
        out.alpha_composite(v, (x, y2))
        x += v.width + pad
    return out
