"""Generate the big clickable cookie: 64x64 pixel art, light from the top-left.

Output: resourcepack/assets/cookieclicker/textures/item/cookie.png
Run with `mise run assets`. Deterministic (fixed seed), so re-running gives the same PNG.
Spec: design/cookie.md
"""
import math
import random
from pathlib import Path

import numpy as np
from scipy import ndimage
from PIL import Image

OUT = Path("resourcepack/assets/cookieclicker/textures/item/cookie.png")
SIZE = 64
SEED = 1337

# Dough ramp, light -> dark, hue-shifted (yellow highlights, red-brown shadows)
DOUGH = [
    "#F7DC9A",  # 0 highlight
    "#EBBE6C",  # 1 light
    "#D69A4C",  # 2 base
    "#B8743A",  # 3 mid shadow
    "#8E5032",  # 4 shadow
    "#5C2F2A",  # 5 outline / deepest
]
# Chocolate ramp, light -> dark (dark end leans purple)
CHOC = [
    "#86523C",  # 0 highlight edge / glint
    "#54301F",  # 1 base
    "#311A1E",  # 2 dark
]

LIGHT = np.array([-1.0, -1.0, 1.2])
LIGHT /= np.linalg.norm(LIGHT)


def hex_rgba(h: str) -> tuple[int, int, int, int]:
    return int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16), 255


def value_noise(rng: random.Random, size: int, cell: float) -> np.ndarray:
    """Smooth value noise in [-1, 1] (bicubic-ish via smoothstep interpolation)."""
    n = int(size / cell) + 3
    grid = np.array([[rng.uniform(-1, 1) for _ in range(n)] for _ in range(n)])
    ys, xs = np.mgrid[0:size, 0:size] / cell
    x0, y0 = xs.astype(int), ys.astype(int)
    fx, fy = xs - x0, ys - y0
    sx, sy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a = grid[y0, x0] * (1 - sx) + grid[y0, x0 + 1] * sx
    b = grid[y0 + 1, x0] * (1 - sx) + grid[y0 + 1, x0 + 1] * sx
    return a * (1 - sy) + b * sy


def lumps(rng: random.Random, xs: np.ndarray, ys: np.ndarray, count: int = 40) -> np.ndarray:
    """Round baked mounds: gaussian bumps scattered over the face."""
    h = np.zeros_like(xs)
    for _ in range(count):
        a = rng.uniform(0, 2 * math.pi)
        rr = 22 * math.sqrt(rng.uniform(0, 1))
        cx, cy = SIZE / 2 + rr * math.cos(a), SIZE / 2 + rr * math.sin(a)
        rad = rng.uniform(2.0, 3.6)
        amp = rng.uniform(0.5, 0.9)
        h += amp * np.exp(-((xs - cx) ** 2 + (ys - cy) ** 2) / (2 * rad * rad))
    return h


def edge_radius(rng: random.Random):
    """Radius of the hand-made edge as a function of angle: a circle with a few soft lumps."""
    harmonics = [(k, rng.uniform(0.4, 0.75) / math.sqrt(k), rng.uniform(0, 2 * math.pi)) for k in (2, 3, 5, 7)]
    base = 29.0

    def r(theta: np.ndarray) -> np.ndarray:
        out = np.full_like(theta, base)
        for k, amp, ph in harmonics:
            out += amp * np.sin(k * theta + ph)
        return out

    return r


def polygon_mask(cx: float, cy: float, verts: list[tuple[float, float]]) -> np.ndarray:
    """Even-odd fill of a polygon, sampled at pixel centres."""
    ys, xs = np.mgrid[0:SIZE, 0:SIZE] + 0.5
    inside = np.zeros((SIZE, SIZE), bool)
    n = len(verts)
    for i in range(n):
        x1, y1 = verts[i]
        x2, y2 = verts[(i + 1) % n]
        cond = (y1 > ys) != (y2 > ys)
        xint = (x2 - x1) * (ys - y1) / (y2 - y1 + 1e-9) + x1
        inside ^= cond & (xs < xint)
    return inside


def make_chunks(rng: random.Random, edge_r) -> np.ndarray:
    """Label map of chocolate chunks (0 = none, 1..n = chunk id)."""
    labels = np.zeros((SIZE, SIZE), int)
    # Hand-placed layout (centre-relative), jittered by the seed; avoids the rim and each other
    spots = [(-12, -13, 4.2), (6, -16, 3.4), (16, -4, 4.0), (-3, -3, 4.6), (-17, 3, 3.6),
             (9, 9, 4.4), (-8, 14, 3.8), (19, 12, 2.8), (-1, 21, 2.8), (-21, -6, 2.6)]
    for i, (dx, dy, rad) in enumerate(spots, start=1):
        cx = SIZE / 2 + dx + rng.uniform(-0.8, 0.8)
        cy = SIZE / 2 + dy + rng.uniform(-0.8, 0.8)
        nverts = rng.randint(5, 7)
        rot = rng.uniform(0, 2 * math.pi)
        verts = []
        for v in range(nverts):
            a = rot + 2 * math.pi * v / nverts + rng.uniform(-0.25, 0.25)
            rr = rad * 1.12 * rng.uniform(0.75, 1.15)
            verts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
        mask = polygon_mask(cx, cy, verts) & (labels == 0)
        labels[mask] = i
    return labels


def remove_orphans(idx: np.ndarray, region: np.ndarray, passes: int = 2) -> np.ndarray:
    """Replace pixels whose 4 neighbours all differ with the most common neighbour value."""
    idx = idx.copy()
    for _ in range(passes):
        changed = False
        for y in range(1, SIZE - 1):
            for x in range(1, SIZE - 1):
                if not region[y, x]:
                    continue
                nb = [idx[y - 1, x], idx[y + 1, x], idx[y, x - 1], idx[y, x + 1]]
                if all(v != idx[y, x] for v in nb):
                    vals = [v for v in nb if v >= 0]
                    if vals:
                        idx[y, x] = max(set(vals), key=vals.count)
                        changed = True
        if not changed:
            break
    return idx


def build() -> Image.Image:
    rng = random.Random(SEED)
    edge_r = edge_radius(rng)

    ys, xs = np.mgrid[0:SIZE, 0:SIZE] + 0.5
    dx, dy = xs - SIZE / 2, ys - SIZE / 2
    r = np.hypot(dx, dy)
    theta = np.arctan2(dy, dx)
    re = edge_r(theta)
    inside = r <= re
    d = re - r  # distance inside the edge

    # Height field: rounded rim, gentle dome, lumpy baked surface
    rim_w, rim_h = 6.0, 6.0
    t = np.clip(d / rim_w, 0, 1)
    h = rim_h * np.sqrt(1 - (1 - t) ** 2)
    h += 1.2 * np.clip(1 - (r / re) ** 2, 0, 1)
    h += lumps(rng, xs, ys) * np.clip(d / 4.0, 0, 1)
    h += value_noise(rng, SIZE, 9.0) * 0.4

    gy, gx = np.gradient(h)
    n = np.dstack([-gx, -gy, np.ones_like(h)])
    n /= np.linalg.norm(n, axis=2, keepdims=True)
    shade = n @ LIGHT

    # Quantise into dough ramp indices (flat face = base)
    idx = np.full((SIZE, SIZE), -1, int)
    bands = [(0.86, 0), (0.73, 1), (0.53, 2), (0.36, 3)]
    dough = np.full((SIZE, SIZE), 4, int)
    for thr, i in reversed(bands):
        dough[shade > thr] = i
    idx[inside] = dough[inside]
    idx = remove_orphans(idx, inside)

    # Chocolate chunks and their contact shadows (falling bottom-right, away from the light)
    labels = make_chunks(rng, edge_r)
    labels[~inside] = 0
    chunk = labels > 0
    shadow = np.zeros_like(chunk)
    shadow[1:, 1:] |= chunk[:-1, :-1]
    shadow[1:, :] |= chunk[:-1, :]
    shadow &= inside & ~chunk
    idx[shadow] = np.maximum(idx[shadow] + 2, 3).clip(max=4)

    # Chunk shading: each chunk is a rounded lump; shade it with the same light
    ch = np.zeros((SIZE, SIZE))
    for lab in range(1, labels.max() + 1):
        m = labels == lab
        if m.any():
            dist = ndimage.distance_transform_edt(m)
            ch += np.sqrt(np.clip(dist / 2.5, 0, 1)) * 2.2 * m
    cgy, cgx = np.gradient(ndimage.gaussian_filter(ch, 0.6))
    cn = np.dstack([-cgx, -cgy, np.ones_like(ch)])
    cn /= np.linalg.norm(cn, axis=2, keepdims=True)
    cshade = cn @ LIGHT
    choc_idx = np.where(cshade > 0.80, 0, np.where(cshade > 0.50, 1, 2))
    choc_idx = remove_orphans(np.where(chunk, choc_idx, -1), chunk)

    # Outline: selective, lighter on the lit (top-left) side
    pad = np.pad(inside, 1)
    edge = inside & ~(pad[:-2, 1:-1] & pad[2:, 1:-1] & pad[1:-1, :-2] & pad[1:-1, 2:])
    lit = (-dx - dy) / (np.hypot(dx, dy) * math.sqrt(2) + 1e-9)
    outline_idx = np.where(lit > 0.35, 4, 5)

    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()
    dough_rgba = [hex_rgba(c) for c in DOUGH]
    choc_rgba = [hex_rgba(c) for c in CHOC]
    for y in range(SIZE):
        for x in range(SIZE):
            if not inside[y, x]:
                continue
            if edge[y, x]:
                px[x, y] = dough_rgba[outline_idx[y, x]]
            elif chunk[y, x]:
                px[x, y] = choc_rgba[choc_idx[y, x]]
            else:
                px[x, y] = dough_rgba[idx[y, x]]
    return img


def clean_isolated(img: Image.Image) -> Image.Image:
    """Final pass: a dough pixel with no same-coloured pixel among its 8 neighbours takes the
    most common colour of its 4 neighbours. Diagonal 1 px lines (contact shadows) are kept."""
    src = img.copy()
    s, d = src.load(), img.load()
    keep = {hex_rgba(c) for c in CHOC}
    for y in range(1, SIZE - 1):
        for x in range(1, SIZE - 1):
            c = s[x, y]
            if c[3] == 0 or c in keep:
                continue
            four = [s[x + a, y + b] for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))]
            eight = four + [s[x + a, y + b] for a, b in ((1, 1), (-1, -1), (1, -1), (-1, 1))]
            if all(n[3] for n in four) and c not in eight:
                d[x, y] = max(set(four), key=four.count)
    return img


def main() -> None:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img = clean_isolated(build())
    img.save(OUT)
    colours = {c for c in img.get_flattened_data() if c[3] > 0}
    print(f"wrote {OUT} ({img.width}x{img.height}, {len(colours)} colours)")


if __name__ == "__main__":
    main()
