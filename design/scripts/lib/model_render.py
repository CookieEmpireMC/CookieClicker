"""Tiny software renderer for vanilla JSON block/item models (cuboid elements only).

Orthographic, nearest-neighbour texture sampling, alpha test (cutout), z-buffer, back-face
culling and Minecraft-style directional face shading. Good enough to judge shape, proportions,
UV mapping and texture seams before testing in game.

Usage (CLI): python design/scripts/lib/model_render.py <model.json> <out.png> [yaw pitch ...]
             (pairs of yaw/pitch degrees; several pairs are rendered side by side)
Library:     render(model_path, yaw, pitch, display=None) -> PIL.Image

Conventions (same as Minecraft):
- Model space 0..16, centre (8, 8, 8). Camera sits on +Z looking towards -Z, +Y up,
  so yaw 0 / pitch 0 shows the south face. Positive yaw turns the model to the right (the east
  face comes into view), positive pitch tilts the top towards the camera.
- Face UVs are [u1, v1, u2, v2] in 0..16; `rotation` turns the texture clockwise on the face.
- Element `rotation` (angle/axis/origin) is supported, `rescale` is ignored.
- `display` transforms ([rotation, translation, scale] of one slot) can be applied with
  display=<dict>; rotation is applied X, then Y, then Z, about the model centre.
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

PACK_ASSETS = Path("resourcepack/assets")
# Minecraft's per-direction brightness for shaded faces
SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}
NORMALS = {"up": (0, 1, 0), "down": (0, -1, 0), "north": (0, 0, -1), "south": (0, 0, 1),
           "east": (1, 0, 0), "west": (-1, 0, 0)}


def resolve_texture(ref: str, textures: dict, assets: Path) -> Image.Image:
    while ref.startswith("#"):
        ref = textures[ref[1:]]
    ns, _, path = ref.partition(":") if ":" in ref else ("minecraft", "", ref)
    png = assets / ns / "textures" / f"{path}.png"
    img = Image.open(png).convert("RGBA")
    if png.with_suffix(".png.mcmeta").exists() and img.height > img.width:   # animated strip: first frame
        img = img.crop((0, 0, img.width, img.width))
    return img


def face_frame(direction: str, f, t):
    """Top-left corner (as seen from outside) and the u/v edge vectors of a face."""
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
    }[direction]


def rot_matrix(axis: str, deg: float) -> np.ndarray:
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == "y":
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def render(model_path: Path, yaw: float = 30, pitch: float = 20, display: dict | None = None,
           px_per_unit: int = 20, size_units: float = 28, assets: Path = PACK_ASSETS,
           background=(0, 0, 0, 0)) -> Image.Image:
    model = json.loads(Path(model_path).read_text())
    textures = model.get("textures", {})
    tex_cache: dict[str, np.ndarray] = {}

    W = H = int(size_units * px_per_unit)
    color = np.zeros((H, W, 4), np.float32)
    color[:] = background
    depth = np.full((H, W), -1e9, np.float32)

    centre = np.array([8.0, 8.0, 8.0])
    # display transform (applied in model space, about the centre)
    disp = np.eye(3)
    disp_t = np.zeros(3)
    if display:
        r = display.get("rotation", [0, 0, 0])
        disp = rot_matrix("z", r[2]) @ rot_matrix("y", r[1]) @ rot_matrix("x", r[0])
        disp = disp @ np.diag(display.get("scale", [1, 1, 1]))
        disp_t = np.array(display.get("translation", [0, 0, 0]), float)
    view = rot_matrix("x", pitch) @ rot_matrix("y", -yaw)

    def to_view(p):
        return view @ (disp @ (np.asarray(p, float) - centre) + disp_t)

    for el in model.get("elements", []):
        f, t = el["from"], el["to"]
        el_rot = np.eye(3)
        el_origin = centre
        if "rotation" in el:
            er = el["rotation"]
            el_rot = rot_matrix(er["axis"], er["angle"])
            el_origin = np.array(er.get("origin", [8, 8, 8]), float)
        for direction, face in el.get("faces", {}).items():
            p0, va, vb = face_frame(direction, f, t)
            p0 = el_rot @ (np.array(p0, float) - el_origin) + el_origin
            va = el_rot @ np.array(va, float)
            vb = el_rot @ np.array(vb, float)
            n_world = disp @ (el_rot @ np.array(NORMALS[direction], float))
            n_view = view @ n_world
            if n_view[2] <= 1e-6:
                continue  # back face
            s0 = to_view(p0)
            sa = view @ (disp @ va)
            sb = view @ (disp @ vb)
            m = np.array([[sa[0], sb[0]], [sa[1], sb[1]]])
            if abs(np.linalg.det(m)) < 1e-9:
                continue
            inv = np.linalg.inv(m)
            corners = np.array([s0, s0 + sa, s0 + sb, s0 + sa + sb])
            # screen coords: x right, y up -> pixel row grows downwards
            cx = corners[:, 0] * px_per_unit + W / 2
            cy = -corners[:, 1] * px_per_unit + H / 2
            x0, x1 = max(int(cx.min()), 0), min(int(cx.max()) + 1, W)
            y0, y1 = max(int(cy.min()), 0), min(int(cy.max()) + 1, H)
            if x0 >= x1 or y0 >= y1:
                continue
            ys, xs = np.mgrid[y0:y1, x0:x1] + 0.5
            px = (xs - W / 2) / px_per_unit - s0[0]
            py = -(ys - H / 2) / px_per_unit - s0[1]
            a = inv[0, 0] * px + inv[0, 1] * py
            b = inv[1, 0] * px + inv[1, 1] * py
            inside = (a >= 0) & (a < 1) & (b >= 0) & (b < 1)
            if not inside.any():
                continue
            z = s0[2] + a * sa[2] + b * sb[2]

            tex_ref = face["texture"]
            if tex_ref not in tex_cache:
                tex_cache[tex_ref] = np.asarray(resolve_texture(tex_ref, textures, assets), np.float32)
            tex = tex_cache[tex_ref]
            th, tw = tex.shape[:2]
            u1, v1, u2, v2 = face.get("uv", [0, 0, 16, 16])
            rot = face.get("rotation", 0) % 360
            p, q = {0: (a, b), 90: (b, 1 - a), 180: (1 - a, 1 - b), 270: (1 - b, a)}[rot]
            u = u1 + p * (u2 - u1)
            v = v1 + q * (v2 - v1)
            tu = np.clip((u / 16 * tw).astype(int), 0, tw - 1)
            tv = np.clip((v / 16 * th).astype(int), 0, th - 1)
            texel = tex[tv, tu]
            shade = SHADE[direction] if model.get("shade", True) and face.get("shade", True) else 1.0
            ok = inside & (texel[..., 3] > 0) & (z > depth[y0:y1, x0:x1])
            sub_c = color[y0:y1, x0:x1]
            sub_d = depth[y0:y1, x0:x1]
            lit = texel.copy()
            lit[..., :3] *= shade
            sub_c[ok] = lit[ok]
            sub_c[ok, 3] = 255
            sub_d[ok] = z[ok]
    return Image.fromarray(np.clip(color, 0, 255).astype(np.uint8), "RGBA")


def sheet(images: list[Image.Image], labels: list[str] | None = None,
          bg=(58, 64, 76, 255)) -> Image.Image:
    """Images side by side on a solid background."""
    pad = 12
    w = sum(i.width for i in images) + pad * (len(images) + 1)
    h = max(i.height for i in images) + 2 * pad
    out = Image.new("RGBA", (w, h), bg)
    x = pad
    for img in images:
        out.alpha_composite(img, (x, pad))
        x += img.width + pad
    return out


def main() -> int:
    model, out = Path(sys.argv[1]), Path(sys.argv[2])
    angles = [float(v) for v in sys.argv[3:]] or [0, 0, 35, 20, 90, 0]
    imgs = [render(model, angles[i], angles[i + 1]) for i in range(0, len(angles), 2)]
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet(imgs).save(out)
    print(out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
