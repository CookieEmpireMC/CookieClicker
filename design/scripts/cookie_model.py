"""Generate the thick 3D cookie model and its baked-edge texture.

Outputs:
  resourcepack/assets/cookieclicker/models/item/cookie.json        (3D model, replaces the flat one)
  resourcepack/assets/cookieclicker/textures/item/cookie_edge.png   (64x64, side crust)

The cookie stands upright: both big faces (north/south) show the 64x64 cookie art
(textures/item/cookie.png, made by cookie.py, which runs first), thickness runs along Z.
The round shape is a stack of horizontal slabs whose widths are fitted to the art's alpha
mask, so the slab outline follows the painted rim. Centred on (8, 8, 8).
Run with `mise run assets`. Deterministic. Spec: design/cookie.md
"""
import json
import random
from itertools import combinations
from pathlib import Path

import numpy as np
from PIL import Image

ART = Path("resourcepack/assets/cookieclicker/textures/item/cookie.png")
EDGE = Path("resourcepack/assets/cookieclicker/textures/item/cookie_edge.png")
FACE = Path("resourcepack/assets/cookieclicker/textures/item/cookie_face.png")
MODEL = Path("resourcepack/assets/cookieclicker/models/item/cookie.json")
SEED = 4242

THICK = 4.0          # model units along Z (front face z=6, back face z=10)
Z1, Z2 = 8 - THICK / 2, 8 + THICK / 2
STEP = 0.25          # 1 texel of the 64 px art = 0.25 model units
N_SIDE = 4           # slabs above the centre slab (total slabs = 2 * N_SIDE + 1)
MIN_TOP_HALF = 1.75  # the top slab must not become a thin nub
MAX_HOLES = 3        # transparent art pixels allowed per slab corner region

# Same dough ramp as cookie.py (light -> dark) plus chocolate
DOUGH = ["#F7DC9A", "#EBBE6C", "#D69A4C", "#B8743A", "#8E5032", "#5C2F2A"]
CHOC = ["#86523C", "#54301F", "#311A1E"]


def rgba(h: str) -> tuple[int, int, int, int]:
    return int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16), 255


# ---------------------------------------------------------------- slab layout

def fit_layout(alpha: np.ndarray):
    """Pick slab boundaries (y offsets from the centre) and half-widths whose union matches
    the art's silhouette best: cost = painted pixels left uncovered + 3 x transparent pixels
    covered (holes are worse than a trimmed rim)."""
    px = alpha.shape[0] / 16  # pixels per unit
    # The back face shows the art too, so fit the art and its mirror image together.
    mask = alpha & alpha[:, ::-1]
    mask = mask & mask[::-1, :]  # symmetric top/bottom as well (slabs are mirrored)
    rows = np.where(mask.any(axis=1))[0]
    top = 8 - rows[0] / px

    def band_cost(lo: float, hi: float):
        r0, r1 = int(round((8 - hi) * px)), int(round((8 - lo) * px))
        band = mask[r0:r1]
        best = None
        w = STEP
        while w <= 8:
            c0, c1 = int(round((8 - w) * px)), int(round((8 + w) * px))
            inside = band[:, c0:c1]
            cost = band.sum() - inside.sum() + 3 * (~inside).sum()
            if best is None or cost < best[0]:
                best = (cost, w)
            w += STEP
        return best

    cands = np.arange(1.0, top, STEP * 2)  # boundaries on even texels; centre slab >= 2 units
    best = None
    for cut in combinations(cands, N_SIDE):
        bounds = [0.0, *cut, top]
        slabs, total = [], 0.0
        for i in range(N_SIDE + 1):
            cost, w = band_cost(bounds[i], bounds[i + 1])
            slabs.append((bounds[i], bounds[i + 1], w))
            total += cost * (1 if i == 0 else 2)
        if slabs[-1][2] < MIN_TOP_HALF:
            continue
        if best is None or total < best[0]:
            best = (total, slabs)
    return best[1]


# ---------------------------------------------------------------- edge texture

def edge_strip(rng: random.Random, length: int, thick: int = 16) -> np.ndarray:
    """Ramp indices for one strip: index [along, across]. Across = thickness (front -> back).
    Codes: 0..5 dough ramp, 10..12 chocolate."""
    idx = np.full((length, thick), 2, int)
    # crust lips at both faces; the inner lip row wobbles so the border is lumpy, not a stripe
    idx[:, 0] = idx[:, thick - 1] = 4
    idx[:, 1] = idx[:, thick - 2] = 3
    a = 0
    while a < length:
        run = rng.randint(2, 5)
        if rng.random() < 0.5:
            idx[a:a + run, 2] = 3
        if rng.random() < 0.5:
            idx[a:a + run, thick - 3] = 3
        a += run
    # golden baked blisters: irregular light clusters in the middle, one highlight pixel each
    a = rng.randint(0, 3)
    while a < length - 2:
        w, h = rng.randint(3, 6), rng.randint(2, 4)
        c = rng.randint(4, thick - 4 - h)
        idx[a:a + w, c:c + h] = 1
        idx[a, c] = idx[min(a + w - 1, length - 1), c + h - 1] = 2   # knock the corners off
        idx[a + 1, c + 1] = 0
        a += w + rng.randint(1, 4)
    # a few chocolate chunks poking out of the side (3x3, lit corner and shadow corner)
    a = rng.randint(3, 10)
    while a < length - 4:
        c = rng.randint(5, thick - 8)
        idx[a:a + 3, c:c + 3] = 11
        idx[a, c] = 10
        idx[a + 2, c + 2] = 12
        idx[a + 2, c] = idx[a, c + 2] = 2 if rng.random() < 0.5 else 11
        a += rng.randint(16, 26)
    return idx


def build_edge() -> Image.Image:
    rng = random.Random(SEED)
    pal = {i: rgba(c) for i, c in enumerate(DOUGH)}
    pal.update({10 + i: rgba(c) for i, c in enumerate(CHOC)})
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    p = img.load()
    # rows 0..15: horizontal strip (u = along the edge, v = thickness), for up/down faces
    h = edge_strip(rng, 64)
    for a in range(64):
        for c in range(16):
            p[a, c] = pal[h[a, c]]
    # cols 0..15, rows 16..63: vertical strip (u = thickness, v = along), for east/west faces
    v = edge_strip(rng, 48)
    for a in range(48):
        for c in range(16):
            p[c, 16 + a] = pal[v[a, c]]
    return img


# ---------------------------------------------------------------- face texture

def slab_mask(slabs, size: int) -> np.ndarray:
    """Pixels of a size x size face texture covered by the slab stack (row 0 = top, y = 16)."""
    px = size / 16
    m = np.zeros((size, size), bool)
    for i, (lo, hi, w) in enumerate(slabs):
        c0, c1 = int(round((8 - w) * px)), int(round((8 + w) * px))
        for y_lo, y_hi in ((lo, hi), (-hi, -lo)) if i else ((-hi, hi),):
            r0, r1 = int(round((8 - y_hi) * px)), int(round((8 - y_lo) * px))
            m[r0:r1, c0:c1] = True
    return m


def build_face(art: Image.Image, slabs) -> Image.Image:
    """The cookie art trimmed to the slab silhouette, with the outline redrawn on the new edge
    (darkest crust on the shadow side, one step lighter on the lit top-left side), so the
    trimmed rim never looks cut off. Pixels the art leaves transparent stay transparent."""
    size = art.width
    a = np.asarray(art).copy()
    keep = slab_mask(slabs, size) & (a[..., 3] > 0)
    pad = np.pad(keep, 1)
    edge = keep & ~(pad[:-2, 1:-1] & pad[2:, 1:-1] & pad[1:-1, :-2] & pad[1:-1, 2:])
    ys, xs = np.mgrid[0:size, 0:size] + 0.5
    dx, dy = xs - size / 2, ys - size / 2
    lit = (-dx - dy) / (np.hypot(dx, dy) * np.sqrt(2) + 1e-9)
    out = np.zeros_like(a)
    out[keep] = a[keep]
    out[edge & (lit > 0.35)] = rgba(DOUGH[4])
    out[edge & (lit <= 0.35)] = rgba(DOUGH[5])
    return Image.fromarray(out, "RGBA")


# ---------------------------------------------------------------- model

def r(v: float) -> float:
    return round(v, 4)


def build_model(slabs) -> dict:
    elements = []
    bands = []
    for i, (lo, hi, w) in enumerate(slabs):
        if i == 0:
            bands.append((-hi, hi, w))
        else:
            bands.append((lo, hi, w))
            bands.append((-hi, -lo, w))
    bands.sort()
    for y_lo, y_hi, w in bands:
        x1, x2 = 8 - w, 8 + w
        y1, y2 = 8 + y_lo, 8 + y_hi
        L = x2 - x1          # horizontal length (units)
        Hh = y2 - y1         # vertical length (units)
        # horizontal strip: u 0..16 is 16 units of edge, v 0..4 is the thickness (16 px)
        hu0 = r(8 - L / 2)
        # vertical strip: u 0..4 = thickness, v 4..16 = 12 units of edge
        vv0 = r(4 + (12 - Hh) / 2)
        faces = {
            "north": {"uv": [r(16 - x2), r(16 - y2), r(16 - x1), r(16 - y1)], "texture": "#face"},
            "south": {"uv": [r(x1), r(16 - y2), r(x2), r(16 - y1)], "texture": "#face"},
            # up/down: v runs along z (north -> south for up, south -> north for down)
            "up": {"uv": [hu0, 0, r(hu0 + L), 4], "texture": "#edge"},
            "down": {"uv": [hu0, 4, r(hu0 + L), 0], "texture": "#edge"},
            # east: u runs along -z, west: along +z; v runs down the slab
            "east": {"uv": [4, vv0, 0, r(vv0 + Hh)], "texture": "#edge"},
            "west": {"uv": [0, vv0, 4, r(vv0 + Hh)], "texture": "#edge"},
        }
        elements.append({"from": [r(x1), r(y1), Z1], "to": [r(x2), r(y2), Z2], "faces": faces})

    return {
        "credit": "Cookie Empire, original art by Pixel Pat (generated by design/scripts/cookie_model.py)",
        "textures": {
            "face": "cookieclicker:item/cookie_face",
            "edge": "cookieclicker:item/cookie_edge",
            "particle": "cookieclicker:item/cookie",
        },
        "elements": elements,
        # Hand/GUI views only; an ItemDisplay with transform NONE ignores this block.
        "display": {
            "gui": {"rotation": [15, -25, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.45, 0.45, 0.45]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
            "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
            "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.5, 0.5, 0.5]},
            "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.5, 0.5, 0.5]},
            "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.6, 0.6, 0.6]},
            "firstperson_lefthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.6, 0.6, 0.6]},
        },
    }


def main() -> None:
    alpha = np.asarray(Image.open(ART).convert("RGBA"))[..., 3] > 0
    slabs = fit_layout(alpha)
    EDGE.parent.mkdir(parents=True, exist_ok=True)
    build_edge().save(EDGE)
    build_face(Image.open(ART).convert("RGBA"), slabs).save(FACE)
    print(f"wrote {FACE}")
    model = build_model(slabs)
    MODEL.write_text(json.dumps(model, indent=2) + "\n")
    print(f"wrote {MODEL} ({len(model['elements'])} elements)")
    for lo, hi, w in slabs:
        print(f"  band y {lo:+.2f}..{hi:+.2f}  half-width {w:.2f}")
    print(f"wrote {EDGE}")


if __name__ == "__main__":
    main()
