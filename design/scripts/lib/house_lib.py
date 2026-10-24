"""Shared helpers for house design generators (design/scripts/house_*.py).

A design is built as a grid {(x, y, z): char} plus a palette {char: block data}. This module:
- checks block data strings against 26.3 vanilla property schemas (check_block_data),
- runs the structural checks every design must pass (validate): bounds, smoke/door/sign points,
  support for doors/hanging/standing/wall-mounted blocks and plants, connection flags and stair shapes
  (they must equal what blocks.resolve() computes = what the game computes), no gravity blocks, volume,
- writes the YAML (write_yaml) and renders the flat-colour previews (layer_sheet, iso_sheet); the textured previews
  are in design_views.py.
Format spec: design/house-default.md (+ the list points in design/factory.md, ground-depth in house-default.md).
Axes: x = west -> east, y = bottom -> top, z = north -> south. With `ground` > 0 the first `ground` layers lie below
design y 0 (grid y -ground..-1); points/signs/origin always use design y (0 = the first air layer above the ground).
"""
from __future__ import annotations

import re

from PIL import Image, ImageDraw, ImageFont

import blocks as B
from voxel_render import render

KEEP = "."
MAX_VOLUME = 40000          # config.yml house.max-blocks (design box volume)

# --- block data validation -------------------------------------------------------------------
BLOCK_DATA_RE = re.compile(r"^minecraft:[a-z0-9_]+(\[[a-z0-9_]+=[a-z0-9_]+(,[a-z0-9_]+=[a-z0-9_]+)*\])?$")
BOOL = {"true", "false"}
HORIZONTAL = {"north", "south", "west", "east"}
ALL_DIRS = HORIZONTAL | {"up", "down"}
CONNECT4 = {d: BOOL for d in ("east", "north", "south", "west", "waterlogged")}
WALL4 = {**{d: {"none", "low", "tall"} for d in ("east", "north", "south", "west")}, "up": BOOL, "waterlogged": BOOL}
# property schemas (26.3 vanilla; names and values cross-checked with the client jar's blockstates by build_lint)
SCHEMAS = [
    (lambda b: b.endswith("_stairs"), {"facing": HORIZONTAL, "half": {"top", "bottom"},
     "shape": {"straight", "inner_left", "inner_right", "outer_left", "outer_right"},
     "waterlogged": BOOL}),
    (lambda b: b.endswith("_slab"), {"type": {"top", "bottom", "double"}, "waterlogged": BOOL}),
    (lambda b: b.endswith("_door"), {"facing": HORIZONTAL, "half": {"upper", "lower"},
     "hinge": {"left", "right"}, "open": BOOL, "powered": BOOL}),
    (lambda b: b.endswith("_trapdoor"), {"facing": HORIZONTAL, "half": {"top", "bottom"}, "open": BOOL,
     "powered": BOOL, "waterlogged": BOOL}),
    (lambda b: b.endswith("_fence_gate"), {"facing": HORIZONTAL, "in_wall": BOOL, "open": BOOL, "powered": BOOL}),
    (lambda b: b.endswith("glass_pane") or b.endswith("_bars") or (b.endswith("_fence") and "gate" not in b),
     CONNECT4),
    (lambda b: b.endswith("_wall") and "sign" not in b, WALL4),
    (lambda b: (b.endswith("_log") or b.endswith("_wood") or b == "hay_block" or b.endswith("_stem")
                or b == "bone_block" or b.startswith(("basalt", "polished_basalt"))), {"axis": {"x", "y", "z"}}),
    (lambda b: b.endswith("_hanging_sign") and "_wall_" not in b,
     {"attached": BOOL, "rotation": {str(i) for i in range(16)}, "waterlogged": BOOL}),
    (lambda b: b.endswith("_wall_sign") and "hanging" not in b, {"facing": HORIZONTAL, "waterlogged": BOOL}),
    (lambda b: b.endswith("lantern"), {"hanging": BOOL, "waterlogged": BOOL}),
    (lambda b: b.endswith("_chain"), {"axis": {"x", "y", "z"}, "waterlogged": BOOL}),
    (lambda b: b.endswith("lightning_rod"), {"facing": ALL_DIRS, "powered": BOOL, "waterlogged": BOOL}),
    (lambda b: b == "campfire", {"facing": HORIZONTAL, "lit": BOOL, "signal_fire": BOOL, "waterlogged": BOOL}),
    (lambda b: b == "barrel", {"facing": ALL_DIRS, "open": BOOL}),
    (lambda b: b == "composter", {"level": {str(i) for i in range(9)}}),
    (lambda b: b == "water_cauldron", {"level": {"1", "2", "3"}}),
    (lambda b: b.endswith("copper_grate"), {"waterlogged": BOOL}),
    (lambda b: b.startswith("potted_"), {}),
    (lambda b: b.endswith("_leaves"), {"distance": {str(i) for i in range(1, 8)}, "persistent": BOOL,
     "waterlogged": BOOL}),
    (lambda b: b in ("grass_block", "podzol", "mycelium"), {"snowy": BOOL}),
    (lambda b: b == "farmland", {"moisture": {str(i) for i in range(8)}}),
    (lambda b: b in ("wheat", "carrots", "potatoes"), {"age": {str(i) for i in range(8)}}),
    (lambda b: b == "beetroots", {"age": {"0", "1", "2", "3"}}),
    (lambda b: b == "sweet_berry_bush", {"age": {"0", "1", "2", "3"}}),
    (lambda b: b == "sugar_cane", {"age": {str(i) for i in range(16)}}),
    (lambda b: b == "water", {"level": {str(i) for i in range(16)}}),
    (lambda b: b == "carved_pumpkin" or b == "jack_o_lantern", {"facing": HORIZONTAL}),
    (lambda b: b in ("lily_pad", "short_grass", "fern", "dead_bush", "poppy", "dandelion", "cornflower",
                     "azure_bluet", "oxeye_daisy", "allium", "pink_tulip", "red_tulip", "orange_tulip",
                     "white_tulip", "lily_of_the_valley", "blue_orchid", "pumpkin", "melon", "moss_carpet",
                     "bush", "flower_pot", "cauldron"), {}),
    (lambda b: b in ("tall_grass", "large_fern", "rose_bush", "lilac", "peony", "sunflower"),
     {"half": {"upper", "lower"}}),
    (lambda b: b.endswith("_carpet") or b.endswith("_wool") or b.endswith("_planks") or b.endswith("_terracotta")
     or b.endswith("_bricks") or b.endswith("_concrete"), {}),
    (lambda b: b in FULL, {}),
]
FULL = {
    "air", "stone", "cobblestone", "mossy_cobblestone", "andesite", "polished_andesite", "diorite", "granite",
    "calcite", "polished_diorite", "tuff", "polished_tuff", "smooth_stone", "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks",
    "chiseled_stone_bricks", "bricks", "mud_bricks", "terracotta", "white_terracotta", "magma_block", "shroomlight",
    "waxed_cut_copper", "waxed_chiseled_copper", "waxed_copper_block", "white_wool", "glass", "dirt",
    "coarse_dirt", "rooted_dirt", "mud", "packed_mud", "clay", "gravel", "moss_block", "dirt_path", "cobbled_deepslate",
    "deepslate_bricks", "deepslate_tiles", "polished_deepslate", "smooth_stone_slab", "oak_planks", "bookshelf",
    "hay_block", "bone_block", "chiseled_bookshelf", "crafting_table", "smoker", "furnace", "blast_furnace",
    "stripped_oak_log", "lodestone", "iron_block", "glowstone", "sea_lantern", "red_nether_bricks", "nether_bricks",
    "sandstone", "smooth_sandstone", "cut_sandstone", "blackstone", "polished_blackstone", "polished_blackstone_bricks",
    "chiseled_deepslate", "chiseled_tuff_bricks", "tuff_bricks", "lectern", "loom", "grindstone", "bell",
    "scaffolding", "composter", "snow_block", "cut_copper", "waxed_copper_bulb", "oak_sign", "azalea",
    "flowering_azalea", "podzol_block", "chiseled_copper", "dripstone_block", "soul_soil", "sand",
}
GRAVITY = {"sand", "red_sand", "anvil", "dragon_egg", "pointed_dripstone", "scaffolding"}      # gravel: ground only
NON_FULL_SUFFIXES = ("_stairs", "_slab", "_trapdoor", "_door", "_sign", "lantern", "_carpet", "_fence_gate",
                     "campfire", "_fence", "glass_pane", "_bars", "_wall", "_leaves", "barrel_x")
PLANTS = {"poppy", "dandelion", "cornflower", "azure_bluet", "oxeye_daisy", "allium", "pink_tulip", "red_tulip",
          "orange_tulip", "white_tulip", "lily_of_the_valley", "blue_orchid", "short_grass", "fern", "tall_grass",
          "large_fern", "rose_bush", "lilac", "peony", "sunflower", "wheat", "carrots", "potatoes", "beetroots",
          "sweet_berry_bush", "sugar_cane", "dead_bush", "bush", "lily_pad"}
SOIL = {"grass_block", "dirt", "coarse_dirt", "podzol", "rooted_dirt", "farmland", "moss_block", "mud", "mycelium",
        "sand", "red_sand", "clay"}
LOGS = lambda b: b.endswith("_log") or b.endswith("_wood")  # noqa: E731
DIRS = B.DIRS
OPPOSITE = B.OPP
HORIZ = B.HORIZ


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
            if block.endswith("_leaves") and props["persistent"] != "true":
                raise ValueError(f"{data}: design leaves must be persistent=true (they would decay)")
            return
    raise ValueError(f"no schema for block {block}; add one before using it")


def is_solid(block: str) -> bool:
    """A full cube with sturdy faces (panes/bars/fences connect to it, it carries anything)."""
    if block == "air" or block.endswith(NON_FULL_SUFFIXES) or block in PLANTS or block.startswith("potted_"):
        return False
    return B.sturdy(block)


def family(block: str) -> str | None:
    return B._family(block)


# --- structural checks -------------------------------------------------------------------------
def validate(g: dict, layers: list[list[str]], palette: dict, size: tuple, origin: tuple,
             points: dict, signs: list, smoke_keys=("chimney", "smoke"), ground: int = 0) -> int:
    """Every structural check a design must pass. `g` maps design cells (y may be -ground..-1) to chars.
    Returns the block count (no air/keep)."""
    sx, sy, sz = size
    top = sy - ground                                     # design height above y 0
    for c, data in palette.items():
        assert len(c) == 1 and c not in (KEEP, " ", '"', "\\"), c
        check_block_data(data)
    assert len(layers) == sy and all(len(l) == sz and all(len(r) == sx for r in l) for l in layers)
    for layer in layers:
        for row in layer:
            for c in row:
                assert c == KEEP or c in palette, f"unknown char {c!r}"
    inside = lambda p: 0 <= p[0] < sx and 0 <= p[1] < top and 0 <= p[2] < sz  # noqa: E731
    assert inside(origin)

    def plist(v):
        return v if isinstance(v[0], list) else [v]

    for name, v in points.items():
        for p in plist(v):
            assert inside(p), f"point {name} {p} out of bounds"
    dg = {p: palette[c] for p, c in g.items()}
    blk = {c: parse_block_data(d) for c, d in palette.items()}

    def bid(x, y, z):
        v = dg.get((x, y, z))
        return "air" if v is None else parse_block_data(v)[0]

    def props(x, y, z):
        v = dg.get((x, y, z))
        return {} if v is None else parse_block_data(v)[1]

    for key in smoke_keys:
        for p in plist(points[key]) if key in points else []:
            below = bid(p[0], p[1] - 1, p[2])
            assert bid(*p) == "air" and below != "air", f"{key} {p} must sit on its stack"
    for p in plist(points["steam"]) if "steam" in points else []:
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
            or above.endswith("_slab") and props(x, y + 1, z)["type"] != "top" or above.endswith("_chain") \
            or above.endswith("_trapdoor") and props(x, y + 1, z)["half"] == "top" \
            or above.endswith("_stairs") and props(x, y + 1, z)["half"] == "top" or above.endswith("_slab") \
            and props(x, y + 1, z)["type"] == "top" or above.endswith("_log")
        if y < 0 and b in GRAVITY:
            raise AssertionError(f"gravity block {b} in the ground layers {(x, y, z)}")
        if b == "gravel":
            assert y < 0, f"gravel {(x, y, z)} is only allowed in the ground layers (it would fall)"
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
        if b.endswith("_carpet") and b != "moss_carpet":
            assert below != "air", f"carpet needs a floor {(x, y, z)}"
        if b in PLANTS and (y > 0 or (x, y - 1, z) in dg):
            assert below in SOIL or (b == "lily_pad" and below == "water"), \
                f"{b} {(x, y, z)} needs soil below, has {below}"
        if b == "cocoa":
            raise AssertionError("cocoa needs a jungle log behind it; add a rule before using it")
    # connection flags and stair shapes: exactly what the game assigns on placement
    probe = dict(dg)
    for p, d in dg.items():
        ident, pr = parse_block_data(d)
        if B._family(ident):
            probe[p] = B.CONNECT + f"minecraft:{ident}"
    B.resolve(probe)
    stair_probe = dict(dg)
    for p, d in dg.items():
        if parse_block_data(d)[0].endswith("_stairs"):
            stair_probe[p] = d.replace("shape=" + parse_block_data(d)[1]["shape"], "shape=straight")
    B.resolve_stairs(stair_probe)
    for p, d in dg.items():
        ident = parse_block_data(d)[0]
        if B._family(ident):
            assert canon(probe[p]) == canon(d), f"{ident} {p}: written {d} but the neighbours give {probe[p]}"
        if ident.endswith("_stairs"):
            assert canon(stair_probe[p]) == canon(d), f"stair {p}: written {d} but placement gives {stair_probe[p]}"
    assert sx * sy * sz < MAX_VOLUME
    return sum(1 for c in g.values() if blk[c][0] != "air")


def canon(data: str) -> str:
    b, pr = parse_block_data(data.removeprefix(B.CONNECT) if data.startswith(B.CONNECT) else data)
    return b + "[" + ",".join(f"{k}={pr[k]}" for k in sorted(pr)) + "]"


def to_layers(g: dict, size: tuple, ground: int = 0) -> list[list[str]]:
    sx, sy, sz = size
    return [["".join(g.get((x, y - ground, z), KEEP) for x in range(sx)) for z in range(sz)] for y in range(sy)]


def _q(s: str) -> str:
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


def _vec(v) -> str:
    if isinstance(v[0], list):
        return "[" + ", ".join(_vec(p) for p in v) + "]"
    return f"[{v[0]}, {v[1]}, {v[2]}]"


def write_yaml(header: list[str], layers, palette: dict, size: tuple, origin: tuple, points: dict,
               signs: list, ground: int = 0) -> str:
    out = list(header) + [
        f"size: [{size[0]}, {size[1]}, {size[2]}]",
        f"origin: [{origin[0]}, {origin[1]}, {origin[2]}]",
    ]
    if ground:
        out.append(f"ground-depth: {ground}")
    out.append("palette:")
    out += [f"  {_q(c)}: {_q(d)}" for c, d in palette.items()]
    out.append("layers:")
    for y, layer in enumerate(layers):
        out.append(f"  # y={y - ground}")
        out.append("  - [" + ", ".join(_q(r) for r in layer) + "]")
    out.append("points:")
    out += [f"  {k}: {_vec(v)}" for k, v in points.items()]
    out.append("signs:")
    for s in signs:
        out.append(f"  - at: {_vec(s['at'])}")
        out.append("    lines: [" + ", ".join(_q(l) for l in s["lines"]) + "]")
    return "\n".join(out) + "\n"


# --- flat-colour previews (belt/fence use these; the houses use design_views.py) ------------------
def font(size: int):
    try:
        return ImageFont.load_default(size=size)
    except TypeError:
        return ImageFont.load_default()


def layer_sheet(layers, size: tuple, colours: dict, title: str, cell: int = 30, cols: int = 4,
                ground: int = 0) -> Image.Image:
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
        d.text((ox, oy), f"y={y - ground}", fill=(240, 240, 240), font=f)
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
