"""Tiny orthographic renderer for coloured boxes in block space (for structure previews).

Each box is (from_xyz, to_xyz, rgb) in block units (1.0 = one block). World axes as in
Minecraft: +X east, +Y up, +Z south. Faces get Minecraft-style directional shading and a thin
dark edge so single blocks stay readable. Z-buffered, opaque only.

Library: render(boxes, yaw, pitch, px_per_unit) -> PIL.Image (cropped to content)
         render_perspective(boxes, eye, yaw, pitch, fov, size) -> PIL.Image (a player's view,
         Minecraft yaw/pitch, vertical FOV like the game setting); project(...) for framing checks
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


# --- perspective camera (what a player sees from a point) -------------------------------------
def camera_basis(yaw: float, pitch: float):
    """Minecraft angles: yaw 0 looks south (+Z), 90 west (-X), 180 north; pitch > 0 looks down.
    Returns (forward, right, up) unit vectors."""
    y, p = math.radians(yaw), math.radians(pitch)
    f = np.array([-math.sin(y) * math.cos(p), -math.sin(p), math.cos(y) * math.cos(p)])
    r = np.array([-math.cos(y), 0.0, -math.sin(y)])
    return f, r, np.cross(r, f)


def project(points, eye, yaw: float, pitch: float, fov: float = 70.0, aspect: float = 16 / 9):
    """Normalised screen coords (-1..1 = frame edge; x right, y up) and depth for world points."""
    f, r, u = camera_basis(yaw, pitch)
    d = np.asarray(points, float) - np.asarray(eye, float)
    z = d @ f
    t = math.tan(math.radians(fov) / 2)
    return (d @ r) / z / (t * aspect), (d @ u) / z / t, z


def render_perspective(boxes, eye, yaw: float, pitch: float, fov: float = 70.0, size=(960, 540),
                       sky=(150, 190, 235, 255), edge_px: float = 1.0) -> Image.Image:
    """Ray-cast coloured boxes from `eye` (world block units) with a vertical `fov` like the game's FOV setting.
    Faces get the same directional shade as render(); a thin dark edge marks block borders."""
    W, H = size
    f, r, u = camera_basis(yaw, pitch)
    focal = (H / 2) / math.tan(math.radians(fov) / 2)
    eye = np.asarray(eye, float)
    color = np.zeros((H, W, 4), np.float32)
    color[:] = sky
    depth = np.full((H, W), np.inf, np.float32)
    for fr, to, rgb in boxes:
        lo, hi = np.array(fr, float), np.array(to, float)
        for direction, n in NORMALS.items():
            axis = int(np.flatnonzero(n)[0])
            plane = hi[axis] if n[axis] > 0 else lo[axis]
            if (eye[axis] - plane) * n[axis] <= 1e-9:          # back face
                continue
            others = [a for a in range(3) if a != axis]
            corners = []
            for a0 in (lo[others[0]], hi[others[0]]):
                for a1 in (lo[others[1]], hi[others[1]]):
                    c = np.zeros(3)
                    c[axis], c[others[0]], c[others[1]] = plane, a0, a1
                    corners.append(c)
            d = np.array(corners) - eye
            zc = d @ f
            if (zc <= 0.05).any():
                x0, x1, y0, y1 = 0, W, 0, H
            else:
                sx = W / 2 + (d @ r) / zc * focal
                sy = H / 2 - (d @ u) / zc * focal
                x0, x1 = max(int(sx.min()), 0), min(int(sx.max()) + 2, W)
                y0, y1 = max(int(sy.min()), 0), min(int(sy.max()) + 2, H)
            if x0 >= x1 or y0 >= y1:
                continue
            ys, xs = np.mgrid[y0:y1, x0:x1] + 0.5
            ray = (f[None, None, :] + ((xs - W / 2) / focal)[..., None] * r[None, None, :]
                   - ((ys - H / 2) / focal)[..., None] * u[None, None, :])
            with np.errstate(divide="ignore", invalid="ignore"):
                t = (plane - eye[axis]) / ray[..., axis]
            hit = eye[None, None, :] + t[..., None] * ray
            a = hit[..., others[0]]
            b = hit[..., others[1]]
            inside = (t > 0) & (a >= lo[others[0]]) & (a <= hi[others[0]]) & (b >= lo[others[1]]) & (b <= hi[others[1]])
            if not inside.any():
                continue
            ew = edge_px * t / focal                            # world size of one pixel at that depth
            border = ((np.minimum(a - lo[others[0]], hi[others[0]] - a) < ew)
                      | (np.minimum(b - lo[others[1]], hi[others[1]] - b) < ew))
            shade = np.where(border, SHADE[direction] * 0.62, SHADE[direction])
            ok = inside & (t < depth[y0:y1, x0:x1])
            sub_c = color[y0:y1, x0:x1]
            for ch in range(3):
                sub_c[..., ch][ok] = (rgb[ch] * shade)[ok]
            sub_c[..., 3][ok] = 255
            depth[y0:y1, x0:x1][ok] = t[ok]
    return Image.fromarray(np.clip(color, 0, 255).astype(np.uint8), "RGBA")
