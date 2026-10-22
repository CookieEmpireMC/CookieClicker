"""Generate the cookie basket: 3 tiers x 5 fill steps (15 models), 3 atlases, 1 item model definition.

Outputs (all under resourcepack/assets/cookieclicker/):
  items/cookie_basket.json                   select on custom_model_data strings[0] = "<tier>_<fill>"
  models/item/basket/<tier>_<fill>.json      15 cuboid models (fill k = base + pile layers 1..k)
  textures/item/basket/<tier>.png            one atlas per tier (128x128, golden 256x256, 2 texels per model unit)
  design/scripts/basket_heights.json         widths and top heights (read by the spec check)

Origin = bottom centre (8, 0, 8). The four sides are identical, so the 180 degree Y turn of item displays
does not matter. No element rotations. Deterministic. Spec: design/basket.md
"""
import json
import random
from pathlib import Path

import numpy as np
from PIL import Image

NS = "cookieclicker"
ASSETS = Path("resourcepack/assets") / NS
ATLAS_SIZE = {"wicker": 128, "bushel": 128, "golden": 256}
PX = 2  # texels per model unit
FILLS = range(5)

# ---- palettes (light -> dark), hue shifted: lights lean yellow, shadows lean red/purple
DOUGH = ["#F7DC9A", "#EBBE6C", "#D69A4C", "#B8743A", "#8E5032", "#5C2F2A"]   # same as the cookie
CHIP = ["#86523C", "#54301F", "#311A1E"]
STRAW = ["#F0D58A", "#DDB464", "#BE8A45", "#955F35", "#63402B"]
WILLOW = ["#D8A06A", "#B97A4C", "#94583A", "#6E3F2E", "#4A2B26"]
COPPER = ["#F2B684", "#D98A56", "#B5623E", "#8A4430", "#5A2E2C"]
GOLD = ["#FFF2A8", "#F4CF55", "#D79B2B", "#A96A24", "#6E3F24"]
GOLDWEAVE = ["#E9C46A", "#C99A3E", "#9C6B2E", "#74482B", "#4C2E24"]
FLOOR = ["#5A3A2A", "#3F2822"]


def rgba(h):
    return np.array([int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16), 255], np.uint8)


# ---------------------------------------------------------------- painters
def weave(w, h, pal, seed, dark=0):
    """Brick-like woven strands: 3 px tall (lit top, body, shadow), 1 px gaps, rows offset."""
    rng = random.Random(seed)
    img = np.zeros((h, w, 4), np.uint8)
    img[:] = rgba(pal[min(4, 3 + dark)])
    rows = 4
    span = 7
    for r0 in range(0, h, rows):
        off = (r0 // rows % 2) * 3
        for y in range(r0, min(r0 + 3, h)):
            ramp = [0 + dark, 1 + dark, 2 + dark][y - r0]
            for x in range(w):
                if (x + off) % span == 0:
                    img[y, x] = rgba(pal[min(4, 3 + dark)])
                else:
                    c = ramp
                    if (x + off) % span == 1 and y - r0 < 2:
                        c = max(0, c - 1)  # lit left end of each strand
                    if rng.random() < 0.06 and y - r0 == 1:
                        c = min(4, c + 1)
                    img[y, x] = rgba(pal[min(4, c)])
    return img


def band(w, h, pal, seed, rivets=True):
    """Metal band / rim: lit top row, body, shadow rows, rivets."""
    img = np.zeros((h, w, 4), np.uint8)
    for y in range(h):
        if y == 0:
            c = 0
        elif y == 1 and h > 3:
            c = 1
        elif y == h - 1:
            c = 4
        elif y == h - 2 and h > 3:
            c = 3
        else:
            c = 2 if h > 3 else 1
        img[y, :] = rgba(pal[c])
    if rivets and h >= 4:
        for x in range(2, w - 1, 6):
            img[h // 2, x] = rgba(pal[0])
            if h // 2 + 1 < h:
                img[h // 2, x + 1] = rgba(pal[4])
    return img


def rope(w, h, pal, seed):
    """Twisted rim (wicker): diagonal two-tone."""
    img = np.zeros((h, w, 4), np.uint8)
    for y in range(h):
        for x in range(w):
            c = 1 if (x + y * 2) % 6 < 3 else 2
            if y == 0:
                c = 0
            if y == h - 1:
                c = 3
            img[y, x] = rgba(pal[c])
    return img


def flat(w, h, cols, seed):
    rng = random.Random(seed)
    img = np.zeros((h, w, 4), np.uint8)
    for y in range(h):
        for x in range(w):
            img[y, x] = rgba(cols[1] if rng.random() < 0.18 else cols[0])
    return img


def discs(w, h, rx, ry, stagger, seed, row_step):
    """Cookies seen from above (rx == ry) or from the side (flat ellipses): a staggered lattice, painted
    top to bottom so lower ones overlap the upper ones; dome shading lit from the top-left, chips."""
    rng = random.Random(seed)
    img = np.zeros((h, w, 4), np.uint8)
    for y in range(h):
        for x in range(w):
            img[y, x] = rgba(DOUGH[5] if (x + y) % 5 else DOUGH[4])
    sx = rx * 1.7
    cells = []
    row = 0
    cy = -ry * 0.3
    while cy < h + ry:
        cx = -rx * 0.6 + (sx / 2 if (row % 2 and stagger) else 0)
        while cx < w + rx:
            cells.append((cy + rng.uniform(-0.8, 0.8), cx + rng.uniform(-0.8, 0.8),
                          rx * rng.uniform(0.9, 1.1), ry * rng.uniform(0.9, 1.1), rng.random()))
            cx += sx
        cy += row_step
        row += 1
    cells.sort()
    for cy, cx, ax, ay, tone in cells:
        for y in range(max(0, int(cy - ay) - 1), min(h, int(cy + ay) + 2)):
            for x in range(max(0, int(cx - ax) - 1), min(w, int(cx + ax) + 2)):
                dx, dy = (x + 0.5 - cx) / ax, (y + 0.5 - cy) / ay
                d2 = dx * dx + dy * dy
                if d2 > 1:
                    continue
                lit = -0.7 * dx - 0.7 * dy  # +1 = toward the light (top-left)
                if d2 > 0.62:
                    c = 4 if lit < 0.15 else 3
                    if d2 > 0.82 and lit < -0.1:
                        c = 5
                else:
                    c = 1 if lit > 0.35 else (2 if lit > -0.35 else 3)
                    if lit > 0.75 and d2 < 0.3:
                        c = 0
                    if tone > 0.7 and c == 2:
                        c = 1
                img[y, x] = rgba(DOUGH[c])
        if ax > 2.2 and rng.random() < 0.55:  # chips
            for _ in range(1):
                px_ = int(cx + rng.uniform(-0.45, 0.45) * ax)
                py_ = int(cy + rng.uniform(-0.45, 0.45) * ay)
                if 0 <= px_ < w - 1 and 0 <= py_ < h:
                    img[py_, px_] = rgba(CHIP[1])
                    img[py_, px_ + 1] = rgba(CHIP[1])
                    if py_ > 0 and ay > 2.5:
                        img[py_ - 1, px_] = rgba(CHIP[0])
    return img


def paint(mat, w, h, seed):
    if mat == "pile_top":
        return discs(w, h, 4.2, 4.2, True, seed, 5.8)
    if mat == "pile_side":
        return discs(w, h, 4.6, 3.0, True, seed, 3.0)
    kind, _, pal = mat.partition(":")
    P = {"straw": STRAW, "willow": WILLOW, "copper": COPPER, "gold": GOLD, "goldweave": GOLDWEAVE}.get(pal)
    if kind == "weave":
        return weave(w, h, P, seed)
    if kind == "weave_in":
        return weave(w, h, P, seed, dark=1)
    if kind == "band":
        return band(w, h, P, seed)
    if kind == "rope":
        return rope(w, h, P, seed)
    if kind == "floor":
        return flat(w, h, FLOOR, seed)
    raise ValueError(mat)


# ---------------------------------------------------------------- geometry
class Box:
    def __init__(self, a, b, mat, faces, inner=None, tag=""):
        self.a, self.b, self.mat, self.faces, self.inner, self.tag = a, b, mat, faces, inner or {}, tag


def ring(x0, x1, y0, y1, t, mat, outer_faces=("north", "south", "east", "west", "up"),
         inner_mat=None, inner_faces=True, tag=""):
    """Four wall boxes around a square cavity. N and S span the full width, E and W sit between them."""
    boxes = []
    inner = {"north": inner_mat, "south": inner_mat, "east": inner_mat, "west": inner_mat}
    base = {"mat": mat}
    f_n = [f for f in outer_faces if f != "south"] + (["south"] if inner_faces else [])
    boxes.append(Box((x0, y0, x0), (x1, y1, x0 + t), mat, [f for f in outer_faces if f != "south"] + (["south"] if inner_faces else []), {"south": inner_mat} if inner_mat else {}, tag + "N"))
    boxes.append(Box((x0, y0, x1 - t), (x1, y1, x1), mat, [f for f in outer_faces if f != "north"] + (["north"] if inner_faces else []), {"north": inner_mat} if inner_mat else {}, tag + "S"))
    boxes.append(Box((x1 - t, y0, x0 + t), (x1, y1, x1 - t), mat, [f for f in outer_faces if f not in ("north", "south", "west")] + (["west"] if inner_faces else []), {"west": inner_mat} if inner_mat else {}, tag + "E"))
    boxes.append(Box((x0, y0, x0 + t), (x0 + t, y1, x1 - t), mat, [f for f in outer_faces if f not in ("north", "south", "east")] + (["east"] if inner_faces else []), {"east": inner_mat} if inner_mat else {}, tag + "W"))
    return boxes


def tier_base(tier):
    """(base boxes, pile layer boxes, extra fill-gated boxes, info)."""
    out = ("north", "south", "east", "west")
    if tier == "wicker":
        a, b = 0.75, 15.25
        wall = ring(a + 0.25, b - 0.25, 0, 4.0, 1.5, "weave:straw", out, "weave_in:straw", tag="wall")
        rim = ring(a, b, 4.0, 4.5, 1.75, "rope:straw", out + ("up",), "rope:straw", tag="rim")
        floor = [Box((2.5, 0, 2.5), (13.5, 1.0, 13.5), "floor:", ["up"], tag="floor")]
        base = floor + wall + rim
        cav = (2.5, 13.5)
        tops = [5.0, 6.5, 8.0, 9.5]
        ins = [0, 0.5, 1.5, 3.0]
        rim_top = 4.5
        width = b - a
    elif tier == "bushel":
        a, b = -0.75, 16.75
        wall = ring(a + 0.25, b - 0.25, 0, 7.0, 1.75, "weave:willow", out, "weave_in:willow", tag="wall")
        rim = ring(a, b, 7.0, 7.75, 2.0, "band:copper", out + ("up",), "band:copper", tag="rim")
        bands = (ring(a, b, 1.5, 2.5, 1.0, "band:copper", out, None, False, tag="bandlo")
                 + ring(a, b, 4.25, 5.25, 1.0, "band:copper", out, None, False, tag="bandhi"))
        floor = [Box((1.25, 0, 1.25), (14.75, 1.0, 14.75), "floor:", ["up"], tag="floor")]
        base = floor + wall + bands + rim
        cav = (1.25, 14.75)
        tops = [8.0, 9.5, 11.0, 12.5]
        ins = [0, 0.75, 2.25, 4.0]
        rim_top = 7.75
        width = b - a
    else:  # golden
        a, b = -3.0, 19.0
        wall = ring(-2.5, 18.5, 0, 8.0, 2.0, "weave:goldweave", out, "weave_in:goldweave", tag="wall")
        rim = ring(-2.75, 18.75, 8.0, 9.0, 2.25, "band:gold", out + ("up",), "band:gold", tag="rim")
        band = ring(-2.75, 18.75, 0.5, 1.5, 1.0, "band:gold", out, None, False, tag="band")
        s = 2.5
        posts = [Box((x, 0, z), (x + s, 11.0, z + s), "band:gold", ["north", "south", "east", "west", "up"], tag=f"post{i}")
                 for i, (x, z) in enumerate([(-3.0, -3.0), (16.5, -3.0), (-3.0, 16.5), (16.5, 16.5)])]
        floor = [Box((-0.5, 0, -0.5), (16.5, 1.0, 16.5), "floor:", ["up"], tag="floor")]
        base = floor + wall + band + rim + posts
        cav = (-0.5, 16.5)
        tops = [10.0, 12.5, 15.5, 19.0]
        ins = [0, 1.0, 3.0, 5.0]
        rim_top = 9.0
        width = 22.0
    # pile layers
    layers = []
    lo = 1.0
    for i, (top, inset) in enumerate(zip(tops, ins)):
        c0, c1 = cav[0] + inset, cav[1] - inset
        if i == 0:
            layers.append(Box((cav[0], lo, cav[0]), (cav[1], top, cav[1]), "pile_top", ["up"], tag="pile1"))
        else:
            if tier == "golden":
                c0, c1 = cav[0] + inset - 0.5, cav[1] - inset + 0.5
            layers.append(Box((c0, lo, c0), (c1, top, c1), "pile_top",
                              ["north", "south", "east", "west", "up"], {f: "pile_side" for f in ("north", "south", "east", "west")},
                              tag=f"pile{i + 1}"))
        lo = top
    extra = []
    if tier == "golden":  # spilled cookies over the rim, from fill 3
        sp = [(5.0, -2.5, 11.0, -0.5), (5.0, 16.5, 11.0, 18.5), (-2.5, 5.0, -0.5, 11.0), (16.5, 5.0, 18.5, 11.0)]
        for i, (x0, z0, x1, z1) in enumerate(sp):
            extra.append(Box((x0, 9.0, z0), (x1, 10.5, z1), "pile_top", ["north", "south", "east", "west", "up"],
                             {f: "pile_side" for f in ("north", "south", "east", "west")}, tag=f"spill{i}"))
    info = {"width": width, "rim_top": rim_top, "tops": tops}
    return base, layers, extra, info


# ---------------------------------------------------------------- atlas + models
def face_size(box, face):
    dx, dy, dz = (box.b[i] - box.a[i] for i in range(3))
    w, h = {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy),
            "up": (dx, dz), "down": (dx, dz)}[face]
    return int(round(w * PX)), int(round(h * PX))


class Atlas:
    def __init__(self, seed, size):
        self.size = size
        self.img = np.zeros((size, size, 4), np.uint8)
        self.x = self.y = self.rowh = 0
        self.cache = {}
        self.seed = seed

    def get(self, mat, w, h):
        key = (mat, w, h)
        if key not in self.cache:
            if self.x + w > self.size:
                self.x, self.y, self.rowh = 0, self.y + self.rowh, 0
            if self.y + h > self.size:
                raise SystemExit(f"atlas full at {key}")
            self.img[self.y:self.y + h, self.x:self.x + w] = paint(mat, w, h, self.seed + len(self.cache))
            self.cache[key] = (self.x, self.y)
            self.x += w
            self.rowh = max(self.rowh, h)
        x, y = self.cache[key]
        k = 16 / self.size
        return [x * k, y * k, (x + w) * k, (y + h) * k]


def model_json(tier, boxes, atlas, fill):
    elements = []
    for bx in boxes:
        faces = {}
        for f in bx.faces:
            mat = bx.inner.get(f) or bx.mat
            w, h = face_size(bx, f)
            faces[f] = {"uv": [round(v, 4) for v in atlas.get(mat, w, h)], "texture": "#0"}
        elements.append({"name": bx.tag, "from": list(bx.a), "to": list(bx.b), "faces": faces})
    return {
        "textures": {"0": f"{NS}:item/basket/{tier}", "particle": f"{NS}:item/basket/{tier}"},
        "elements": elements,
        "display": {"gui": {"rotation": [30, 225, 0], "scale": [0.55, 0.55, 0.55], "translation": [0, -1, 0]},
                    "fixed": {"rotation": [0, 0, 0], "scale": [0.6, 0.6, 0.6], "translation": [0, -3, 0]}},
    }


def main():
    mdir = ASSETS / "models/item/basket"
    tdir = ASSETS / "textures/item/basket"
    mdir.mkdir(parents=True, exist_ok=True)
    tdir.mkdir(parents=True, exist_ok=True)
    heights = {}
    cases = []
    for ti, tier in enumerate(["wicker", "bushel", "golden"]):
        base, layers, extra, info = tier_base(tier)
        atlas = Atlas(100 + ti * 1000, ATLAS_SIZE[tier])
        # paint every face once in a fixed order (fill 4 + extras) so UVs are identical across fills
        for fill in FILLS:
            boxes = base + layers[:fill] + (extra if fill >= 3 else [])
            (mdir / f"{tier}_{fill}.json").write_text(json.dumps(model_json(tier, boxes, atlas, fill), indent=2) + "\n")
            cases.append({"when": f"{tier}_{fill}", "model": {"type": "minecraft:model", "model": f"{NS}:item/basket/{tier}_{fill}"}})
            top = info["rim_top"] if fill == 0 else info["tops"][fill - 1]
            heights[f"{tier}_{fill}"] = {"width": info["width"] / 16, "top": top / 16, "elements": len(boxes)}
        Image.fromarray(atlas.img).save(tdir / f"{tier}.png")
    item = {"model": {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
                      "cases": cases,
                      "fallback": {"type": "minecraft:model", "model": f"{NS}:item/basket/wicker_0"}}}
    (ASSETS / "items/cookie_basket.json").write_text(json.dumps(item, indent=2) + "\n")
    for k, v in heights.items():
        print(k, v)


if __name__ == "__main__":
    main()
