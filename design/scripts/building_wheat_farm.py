"""Panel icon for the Wheat Farm building: a tied wheat sheaf standing on a tilled soil patch.

Output: resourcepack/assets/cookieclicker/textures/item/building/wheat_farm.png (32x32)
Run with `mise run assets`. Deterministic. Spec: design/building-icons.md
"""
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
from pixel import Canvas, clean_orphans, ellipse, grid, hex_rgba, lit_side, shade  # noqa: E402

OUT = Path("resourcepack/assets/cookieclicker/textures/item/building/wheat_farm.png")
S = 32

GRAIN = ["#FFE68C", "#F0BF4C", "#CC922C", "#8E5A22", "#5E3519"]   # light -> outline
STRAW = ["#EED27A", "#C9A243", "#93702C"]                         # stalks
TWINE = ["#D2654A", "#9E3A2E", "#66241F"]
SOIL = ["#8C6040", "#6A452E", "#4A2E22", "#2F1C17"]               # light -> deepest furrow
SPROUT = ["#9CC452", "#5E8A34"]


def line_mask(x0, y0, x1, y1, width=1.0):
    ys, xs = grid(S)
    dx, dy = x1 - x0, y1 - y0
    L2 = dx * dx + dy * dy
    t = np.clip(((xs - x0) * dx + (ys - y0) * dy) / L2, 0, 1)
    d = np.hypot(xs - (x0 + t * dx), ys - (y0 + t * dy))
    return d <= width / 2 + 0.05


def rotated_ellipse(cx, cy, rx, ry, ang):
    ys, xs = grid(S)
    c, s = math.cos(ang), math.sin(ang)
    u = (xs - cx) * c + (ys - cy) * s
    v = -(xs - cx) * s + (ys - cy) * c
    return (u / rx) ** 2 + (v / ry) ** 2 <= 1


def dilate(m: np.ndarray) -> np.ndarray:
    out = m.copy()
    out[1:] |= m[:-1]
    out[:-1] |= m[1:]
    out[:, 1:] |= m[:, :-1]
    out[:, :-1] |= m[:, 1:]
    return out


def build():
    c = Canvas(S)
    ys, xs = grid(S)
    fy, fx = np.floor(ys), np.floor(xs)

    # --- soil patch: a low mound with furrows, outlined in the deepest soil colour
    soil = ellipse(S, 16, 27.6, 12.5, 3.2)
    c.paint(dilate(soil), SOIL[3])
    c.paint_ramp(shade(soil, [0.78, 0.50], round_px=2.0), SOIL[:3])
    for row, x0, x1 in ((27, 6, 11), (28, 20, 26), (29, 9, 13)):
        c.paint(soil & (fy == row) & (fx >= x0) & (fx <= x1), SOIL[2])
    for sx, sy in ((7, 25), (25, 26)):   # sprouts
        c.paint((fx == sx) & (fy >= sy) & (fy <= sy + 1), SPROUT[1])
        c.paint((fx == sx - 1) & (fy == sy - 1), SPROUT[0])
        c.paint((fx == sx + 1) & (fy == sy - 1), SPROUT[0])

    # --- the bundle: stalks splay a little under the tie, cut ends on the soil
    tie_y = 18
    bundle = np.zeros((S, S), bool)
    for x_top, x_bot in ((14.0, 11.5), (15.3, 14.0), (16.7, 18.0), (18.0, 20.5)):
        bundle |= line_mask(x_top, 12, x_bot, 27.2, width=1.6)
    bundle |= line_mask(16, 12, 16, 27, width=3.4)
    c.paint(dilate(bundle), GRAIN[4])
    c.paint(bundle, STRAW[1])
    c.paint(bundle & (xs < 15.5), STRAW[0])
    c.paint(bundle & (xs > 17.5), STRAW[2])
    c.paint(bundle & (fy == 27), STRAW[2])        # cut ends in the soil
    for x0, x1 in ((14.6, 13.0), (17.4, 19.0)):   # straw lines in the bundle
        c.paint(line_mask(x0, 20.5, x1, 26.5, width=0.8) & bundle, STRAW[2])

    # --- ears, back to front; each gets its own outline so they stay separate
    ears = [(8.2, 10.2, -0.55), (23.8, 10.2, 0.55), (11.8, 7.0, -0.27), (20.2, 7.0, 0.27), (16.0, 5.4, 0.0)]
    grain_any = np.zeros((S, S), bool)
    for ex, ey, tilt in ears:
        m = rotated_ellipse(ex, ey, 2.1, 5.0, tilt)
        grain_any |= m
        ring = dilate(m) & ~m
        c.paint(ring & lit_side(S, ex, ey), GRAIN[3])
        c.paint(ring & ~lit_side(S, ex, ey), GRAIN[4])
        c.paint_ramp(shade(m, [0.84, 0.60], round_px=1.8), GRAIN[:3])
        # kernel seam: zigzag of shadow pixels down the ear's middle, every other row
        rows = np.where(m.any(axis=1))[0]
        for i, r in enumerate(rows[1:-1]):
            cols = np.where(m[r])[0]
            mid = (cols[0] + cols[-1]) / 2
            x = int(math.floor(mid + (0.5 if i % 2 else -0.5)))
            c.paint((fy == r) & (fx == x), GRAIN[2])
        # awn on the tip
        tx, ty = ex + 5.2 * math.sin(tilt), ey - 5.2 * math.cos(tilt)
        c.paint((fx == int(tx)) & (fy == int(ty)), GRAIN[3])

    # --- twine tie
    tie = (fy >= tie_y) & (fy <= tie_y + 1) & (np.abs(xs - 16) <= 3.0)
    c.paint(dilate(tie) & ~grain_any, TWINE[2])
    c.paint(tie, TWINE[1])
    c.paint(tie & (fy == tie_y) & (xs < 17.5), TWINE[0])
    c.paint((fy == tie_y + 2) & (fx == 15), TWINE[1])   # knot end
    c.paint((fy == tie_y + 3) & (fx == 15), TWINE[2])

    img = c.image()
    keep = {hex_rgba(x) for x in (*SPROUT, *TWINE, GRAIN[2], GRAIN[3])}
    return clean_orphans(img, keep)


def main() -> None:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img = build()
    img.save(OUT)
    colours = {p for p in img.get_flattened_data() if p[3] > 0}
    print(f"wrote {OUT} ({img.width}x{img.height}, {len(colours)} colours)")


if __name__ == "__main__":
    main()
