"""Tiny orthographic renderer for coloured boxes in block space (for structure previews).

Each box is (from_xyz, to_xyz, rgb) in block units (1.0 = one block). World axes as in
Minecraft: +X east, +Y up, +Z south. Faces get Minecraft-style directional shading and a thin
dark edge so single blocks stay readable. Z-buffered, opaque only.

Library: render(boxes, yaw, pitch, px_per_unit) -> PIL.Image (cropped to content)
Camera: yaw 0 looks from the south (+Z) towards north; yaw 90 looks from the east;
yaw 180 from the north. Positive pitch looks down from above.
"""
from __future__ import annotations

import math

import numpy as np
from PIL import Image

SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}
NORMALS = {"up": (0, 1, 0), "down": (0, -1, 0), "north": (0, 0, -1), "south": (0, 0, 1),
           "east": (1, 0, 0), "west": (-1, 0, 0)}


def _rot(axis: str, deg: float) -> np.ndarray:
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def _faces(f, t):
    x1, y1, z1 = f
    x2, y2, z2 = t
    dx, dy, dz = x2 - x1, y2 - y1, z2 - z1
    return {
        "north": ((x2, y2, z1), (-dx, 0, 0), (0, -dy, 0)),
        "south": ((x1, y2, z2), (dx, 0, 0), (0, -dy, 0)),
        "east": ((x2, y2, z2), (0, 0, -dz), (0, -dy, 0)),
        "west": ((x1, y2, z1), (0, 0, dz), (0, -dy, 0)),
        "up": ((x1, y2, z1), (dx, 0, 0), (0, 0, dz)),
        "down": ((x1, y1, z2), (dx, 0, 0), (0, 0, -dz)),
    }


def render(boxes, yaw: float = 35, pitch: float = 30, px_per_unit: int = 40,
           edge: float = 0.045, background=(0, 0, 0, 0)) -> Image.Image:
    view = _rot("x", pitch) @ _rot("y", -yaw)
    pts = []
    for f, t, _ in boxes:
        for x in (f[0], t[0]):
            for y in (f[1], t[1]):
                for z in (f[2], t[2]):
                    pts.append(view @ np.array([x, y, z], float))
    pts = np.array(pts)
    lo, hi = pts.min(axis=0), pts.max(axis=0)
    pad = 0.5
    W = int((hi[0] - lo[0] + 2 * pad) * px_per_unit)
    H = int((hi[1] - lo[1] + 2 * pad) * px_per_unit)
    ox, oy = lo[0] - pad, hi[1] + pad
    color = np.zeros((H, W, 4), np.float32)
    color[:] = background
    depth = np.full((H, W), -1e9, np.float32)

    for f, t, rgb in boxes:
        size = np.array(t, float) - np.array(f, float)
        for direction, (p0, va, vb) in _faces(f, t).items():
            if (view @ np.array(NORMALS[direction], float))[2] <= 1e-6:
                continue
            s0 = view @ np.array(p0, float)
            sa = view @ np.array(va, float)
            sb = view @ np.array(vb, float)
            m = np.array([[sa[0], sb[0]], [sa[1], sb[1]]])
            if abs(np.linalg.det(m)) < 1e-9:
                continue
            inv = np.linalg.inv(m)
            corners = np.array([s0, s0 + sa, s0 + sb, s0 + sa + sb])
            cx = (corners[:, 0] - ox) * px_per_unit
            cy = (oy - corners[:, 1]) * px_per_unit
            x0, x1 = max(int(cx.min()), 0), min(int(cx.max()) + 1, W)
            y0, y1 = max(int(cy.min()), 0), min(int(cy.max()) + 1, H)
            if x0 >= x1 or y0 >= y1:
                continue
            ys, xs = np.mgrid[y0:y1, x0:x1] + 0.5
            px = xs / px_per_unit + ox - s0[0]
            py = oy - ys / px_per_unit - s0[1]
            a = inv[0, 0] * px + inv[0, 1] * py
            b = inv[1, 0] * px + inv[1, 1] * py
            inside = (a >= 0) & (a < 1) & (b >= 0) & (b < 1)
            if not inside.any():
                continue
            z = s0[2] + a * sa[2] + b * sb[2]
            la = max(np.linalg.norm(va), 1e-6)
            lb = max(np.linalg.norm(vb), 1e-6)
            border = (np.minimum(a, 1 - a) * la < edge) | (np.minimum(b, 1 - b) * lb < edge)
            shade = np.where(border, SHADE[direction] * 0.62, SHADE[direction])
            ok = inside & (z > depth[y0:y1, x0:x1] + 1e-5)
            sub_c = color[y0:y1, x0:x1]
            sub_d = depth[y0:y1, x0:x1]
            for ch in range(3):
                sub_c[..., ch][ok] = (rgb[ch] * shade)[ok]
            sub_c[..., 3][ok] = 255
            sub_d[ok] = z[ok]
    return Image.fromarray(np.clip(color, 0, 255).astype(np.uint8), "RGBA")
