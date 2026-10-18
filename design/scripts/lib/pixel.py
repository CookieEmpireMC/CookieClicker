"""Shared pixel-art helpers for the icon/glyph generators.

Approach (see design/style-guide.md): build each part as a boolean mask, give it a height
field (rounded by a distance transform), light it from the top-left, quantise into the
part's ramp, then add a selective outline and clean up orphan pixels.
"""
from __future__ import annotations

import math

import numpy as np
from scipy import ndimage
from PIL import Image

LIGHT = np.array([-1.0, -1.0, 1.2])
LIGHT /= np.linalg.norm(LIGHT)


def hex_rgba(h: str) -> tuple[int, int, int, int]:
    return int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16), 255


def grid(size: int):
    """Pixel-centre coordinates (ys, xs)."""
    return np.mgrid[0:size, 0:size] + 0.5


def ellipse(size: int, cx: float, cy: float, rx: float, ry: float) -> np.ndarray:
    ys, xs = grid(size)
    return ((xs - cx) / rx) ** 2 + ((ys - cy) / ry) ** 2 <= 1.0


def rect(size: int, x0: int, y0: int, x1: int, y1: int) -> np.ndarray:
    """Inclusive pixel rectangle."""
    m = np.zeros((size, size), bool)
    m[y0:y1 + 1, x0:x1 + 1] = True
    return m


def polygon(size: int, verts: list[tuple[float, float]]) -> np.ndarray:
    ys, xs = grid(size)
    inside = np.zeros((size, size), bool)
    n = len(verts)
    for i in range(n):
        x1, y1 = verts[i]
        x2, y2 = verts[(i + 1) % n]
        cond = (y1 > ys) != (y2 > ys)
        xint = (x2 - x1) * (ys - y1) / (y2 - y1 + 1e-9) + x1
        inside ^= cond & (xs < xint)
    return inside


def shade(mask: np.ndarray, thresholds: list[float], round_px: float = 3.0,
          extra_height: np.ndarray | None = None) -> np.ndarray:
    """Ramp index per pixel (0 = lightest) for a rounded lump the shape of `mask`.
    thresholds: descending normal.light cut-offs; len(thresholds) + 1 ramp steps."""
    dist = ndimage.distance_transform_edt(np.pad(mask, 1))[1:-1, 1:-1]
    h = np.sqrt(np.clip(dist / round_px, 0, 1)) * round_px
    if extra_height is not None:
        h = h + extra_height
    h = ndimage.gaussian_filter(h, 0.7)
    gy, gx = np.gradient(h)
    n = np.dstack([-gx, -gy, np.ones_like(h)])
    n /= np.linalg.norm(n, axis=2, keepdims=True)
    lit = n @ LIGHT
    idx = np.full(mask.shape, len(thresholds), int)
    for i, t in reversed(list(enumerate(thresholds))):
        idx[lit > t] = i
    return np.where(mask, idx, -1)


def edge_of(mask: np.ndarray) -> np.ndarray:
    pad = np.pad(mask, 1)
    return mask & ~(pad[:-2, 1:-1] & pad[2:, 1:-1] & pad[1:-1, :-2] & pad[1:-1, 2:])


def lit_side(size: int, cx: float, cy: float) -> np.ndarray:
    """True where a pixel lies on the top-left (lit) side of the point (cx, cy)."""
    ys, xs = grid(size)
    dx, dy = xs - cx, ys - cy
    return (-dx - dy) / (np.hypot(dx, dy) * math.sqrt(2) + 1e-9) > 0.35


class Canvas:
    """RGBA image built from layers of palette colours (later layers paint over earlier)."""

    def __init__(self, size: int):
        self.size = size
        self.px = np.zeros((size, size, 4), np.uint8)

    def paint(self, mask: np.ndarray, colour) -> None:
        self.px[mask] = hex_rgba(colour) if isinstance(colour, str) else colour

    def paint_ramp(self, idx: np.ndarray, ramp: list[str]) -> None:
        for i, c in enumerate(ramp):
            self.px[idx == i] = hex_rgba(c)

    def alpha(self) -> np.ndarray:
        return self.px[..., 3] > 0

    def image(self) -> Image.Image:
        return Image.fromarray(self.px.copy(), "RGBA").copy()


def clean_orphans(img: Image.Image, keep: set[tuple[int, int, int, int]] = frozenset()) -> Image.Image:
    """A pixel with no same-coloured pixel among its 8 neighbours (and all 4 neighbours opaque)
    takes the most common colour of its 4 neighbours. Colours in `keep` are left alone."""
    src = img.copy()
    s, d = src.load(), img.load()
    w, h = img.size
    for y in range(1, h - 1):
        for x in range(1, w - 1):
            c = s[x, y]
            if c[3] == 0 or c in keep:
                continue
            four = [s[x + a, y + b] for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))]
            eight = four + [s[x + a, y + b] for a, b in ((1, 1), (-1, -1), (1, -1), (-1, 1))]
            if all(n[3] for n in four) and c not in eight:
                d[x, y] = max(set(four), key=four.count)
    return img
