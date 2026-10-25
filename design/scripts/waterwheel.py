"""The mill's waterwheel: item model `cookieclicker:waterwheel` (breastshot wheel, 16 paddles, 58 cuboids).

Outputs (final locations):
  resourcepack/assets/cookieclicker/items/waterwheel.json            (item model definition)
  resourcepack/assets/cookieclicker/models/item/waterwheel.json      (58 cuboids, centred on the axle at 8,8,8)
  resourcepack/assets/cookieclicker/textures/item/waterwheel.png     (32x32 placeholder atlas)
  design/scripts/previews/waterwheel_*.png                           (model renders, x8 texture)
Run with `mise run assets`. Deterministic. Spec: design/waterwheel.md (and design/countryside.md, Mill).

Geometry: the axle runs along model x through (8, 8, 8); the disc lies in the y-z plane, outer radius 24 units (tips at -16 and
32, the limits of a model), width 14 units along x. Shown with the item display transformation scale 7/3 (about 2.333) the disc
is 7 blocks across and 2.04 blocks wide. The display rotates about the model's local x axis (axis of the axle).
Element rotations are single-axis (x) in 22.5 degree steps, so the wheel has 16 paddles (every 22.5 degrees), not 12.
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
import model_render  # noqa: E402
from preview import upscale  # noqa: E402

NS = ROOT / "resourcepack/assets/cookieclicker"
ITEM_DEF = NS / "items/waterwheel.json"
MODEL = NS / "models/item/waterwheel.json"
TEX = NS / "textures/item/waterwheel.png"
PREVIEWS = ROOT / "design/scripts/previews"
C = 8.0                                  # the axle: y = z = 8
R = 24.0
# texture regions (uv units 0..16 on a 32 px atlas = 2 px per unit): plank, timber, iron
PLANK, TIMBER, IRON = (0, 0), (8, 0), (0, 8)
PALETTE = {
    "plank": ["#4a3220", "#5f4129", "#7a5535", "#93693f"],
    "timber": ["#2f2016", "#3f2b1c", "#523724", "#664630"],
    "iron": ["#2b2e33", "#42464d", "#5b6068", "#7a8088"],
    "moss": "#5c7b3a",
}


def tile_uv(tile: tuple, du: float, dv: float) -> list:
    u0, v0 = tile
    return [u0, v0, u0 + min(du, 8), v0 + min(dv, 8)]


def bar(cy: float, cz: float, psi: float, length: float, thick: float, x0: float, x1: float, tile: tuple) -> dict:
    """A cuboid x0..x1 wide whose long axis points at psi degrees (from +z towards +y, any multiple of 22.5), centred at
    (cy, cz). Built as an axis-aligned bar rotated about its own centre around the x axis (rotation about +x by t moves a
    bar along z to the angle -t, a bar along y to 90 - t)."""
    psi = psi % 180.0
    if psi <= 45 or psi >= 135:
        psi = psi - 180 if psi >= 135 else psi
        along_z, theta = True, -psi
    else:
        along_z, theta = False, 90 - psi
    assert theta in (-45, -22.5, 0, 22.5, 45), (psi, theta)
    hl, ht = length / 2, thick / 2
    f = [x0, cy - (ht if along_z else hl), cz - (hl if along_z else ht)]
    t = [x1, cy + (ht if along_z else hl), cz + (hl if along_z else ht)]
    for i in range(3):
        assert -16 <= f[i] and t[i] <= 32, ("outside the model limits", f, t)
    dx = x1 - x0
    faces = {}
    for d in ("north", "south", "east", "west", "up", "down"):
        if d in ("east", "west"):
            size = (thick if not along_z else thick, length)
            faces[d] = {"uv": tile_uv(tile, thick, length), "texture": "#wood"}
        elif d in ("north", "south"):
            faces[d] = {"uv": tile_uv(tile, dx, thick if along_z else length), "texture": "#wood"}
        else:
            faces[d] = {"uv": tile_uv(tile, dx, length if along_z else thick), "texture": "#wood"}
    el = {"from": [round(v, 3) for v in f], "to": [round(v, 3) for v in t], "faces": faces}
    if theta != 0:
        el["rotation"] = {"angle": theta, "axis": "x", "origin": [(x0 + x1) / 2, cy, cz]}
    return el


def polar(phi: float, r: float) -> tuple:
    a = math.radians(phi)
    return C + r * math.sin(a), C + r * math.cos(a)             # (y, z)


def build_elements() -> list:
    els = []
    # axle and hub
    els.append(bar(C, C, 0, 3, 3, 0, 16, IRON))
    els.append(bar(C, C, 0, 8, 8, 3, 13, IRON))
    # two shrouds (side rims): an octagon of 8 segments, plus 4 diametral spokes on each side
    for x0, x1 in ((1, 3), (13, 15)):
        for k in range(8):
            phi = 22.5 + 45 * k
            y, z = polar(phi, 22.0)
            els.append(bar(y, z, phi + 90, 20, 3, x0, x1, TIMBER))
        for k in range(4):
            els.append(bar(C, C, 45 * k, 40, 1.5, x0 + .25, x1 - .25, TIMBER))
    # 16 paddles (radial boards) and their tip caps (the bucket's lip)
    for k in range(16):
        phi = 22.5 * k
        y, z = polar(phi, 19.5)
        els.append(bar(y, z, phi, 8, 1.5, 3, 13, PLANK))
    for k in range(16):
        phi = 22.5 * k
        y, z = polar(phi, 22.9)
        els.append(bar(y, z, phi + 90, 5.5, 1.2, 3, 13, PLANK))
    return els


def write_model() -> dict:
    els = build_elements()
    assert len(els) <= 64, len(els)
    model = {"textures": {"wood": "cookieclicker:item/waterwheel", "particle": "cookieclicker:item/waterwheel"},
             "elements": els}
    MODEL.parent.mkdir(parents=True, exist_ok=True)
    MODEL.write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")
    ITEM_DEF.parent.mkdir(parents=True, exist_ok=True)
    ITEM_DEF.write_text(json.dumps({"model": {"type": "minecraft:model", "model": "cookieclicker:item/waterwheel"}},
                                   indent=2) + "\n", encoding="utf-8")
    return model


def hexc(h: str) -> tuple:
    return int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16), 255


def paint_tile(img: np.ndarray, x0: int, y0: int, ramp: list, grain: str, seed: int, moss: bool = False) -> None:
    """16 x 16 px tile: boards with a dark seam, lit top-left, a few knots; grain 'h' (horizontal boards) or 'metal'."""
    rng = np.random.default_rng(seed)
    cols = [hexc(c) for c in ramp]
    for y in range(16):
        for x in range(16):
            band = y // 4 if grain == "h" else (x + y) // 5
            base = 1 + (band % 2)
            if grain == "h" and y % 4 == 3:
                base = 0                                              # seam between boards
            elif grain == "h" and y % 4 == 0:
                base = min(base + 1, 3)                               # lit upper edge of each board
            img[y0 + y, x0 + x] = cols[base]
    for _ in range(3 if grain == "h" else 1):                          # knots / rivets as 2 px clusters
        kx, ky = rng.integers(2, 13), rng.integers(1, 13)
        img[y0 + ky, x0 + kx] = cols[0]
        img[y0 + ky, x0 + kx + 1] = cols[1]
    if moss:
        for _ in range(7):
            mx, my = rng.integers(0, 16), rng.integers(8, 16)
            img[y0 + my, x0 + mx] = hexc(PALETTE["moss"])
            img[y0 + my, min(x0 + mx + 1, x0 + 15)] = hexc(PALETTE["moss"])


def write_texture() -> None:
    img = np.zeros((32, 32, 4), np.uint8)
    paint_tile(img, 0, 0, PALETTE["plank"], "h", 1, moss=True)
    paint_tile(img, 16, 0, PALETTE["timber"], "h", 2, moss=True)
    paint_tile(img, 0, 16, PALETTE["iron"], "metal", 3)
    paint_tile(img, 16, 16, PALETTE["plank"], "h", 4)
    TEX.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(img, "RGBA").save(TEX)


def previews() -> None:
    PREVIEWS.mkdir(parents=True, exist_ok=True)
    imgs = [model_render.render(MODEL, yaw, pitch, px_per_unit=8, size_units=52) for yaw, pitch in
            ((90, 0), (60, 18), (0, 0), (120, -20))]
    model_render.sheet(imgs).save(PREVIEWS / "waterwheel_model.png")
    upscale(Image.open(TEX), 8).save(PREVIEWS / "waterwheel_texture_x8.png")


def main() -> int:
    model = write_model()
    write_texture()
    previews()
    print(f"{MODEL.relative_to(ROOT)}: {len(model['elements'])} cuboids")
    return 0


if __name__ == "__main__":
    sys.exit(main())
