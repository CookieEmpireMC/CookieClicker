"""Textured preview renderer for block structures (real 26.3 block models and textures from the client jar).

    world = {(x, y, z): "minecraft:oak_stairs[facing=east,...]"}      # same coordinates as the design grid
    scene = Scene(); scene.add_blocks(world)                          # culls faces hidden by opaque cubes
    img = render(scene, persp(eye, yaw, pitch, fov=70, size=(1280, 720)))      # a player's eye view
    img = render(scene, ortho(center, yaw, pitch, px_per_block=40, size=(1200, 800)))   # iso-like view

Angles are Minecraft's: yaw 0 looks south (+Z), 90 west, 180 north; pitch > 0 looks down. Light is the game's
face shading (top 1.0, N/S 0.8, E/W 0.6, bottom 0.5); emissive blocks (lanterns, campfire, shroomlight, magma) are drawn
fullbright. Translucent textures (glass, water) are blended back to front after the opaque pass. `ss` supersamples.
Needs numpy + Pillow and the client jar (block_models.available()).
"""
from __future__ import annotations

import math

import numpy as np
from PIL import Image

import block_models as bm
from voxel_render import camera_basis

SKY_TOP, SKY_HORIZON = np.array([112, 160, 232], float), np.array([196, 218, 244], float)


class Scene:
    def __init__(self):
        self.quads: list[tuple] = []

    def add_blocks(self, blocks: dict, cull_with: dict | None = None, offset=(0, 0, 0)) -> None:
        """Add every block of `blocks`; faces with a cullface are skipped when the neighbour in `cull_with`
        (default: `blocks`) is an opaque full cube."""
        world = blocks if cull_with is None else cull_with
        ox, oy, oz = offset
        for (x, y, z), data in blocks.items():
            if data.endswith("air") and data.startswith("minecraft:air"):
                continue
            block = bm.parse(data)[0]
            try:
                quads = bm.block_quads(data)
            except KeyError:
                continue
            for q in quads:
                c = q["cull"]
                if c:
                    d = bm.DIRS[c]
                    nb = world.get((x + d[0], y + d[1], z + d[2]))
                    if nb is not None and bm.is_opaque_cube(nb):
                        continue
                arr, kind = bm.texture(q["tex"])
                self.quads.append((q["p0"] + (x + ox, y + oy, z + oz), q["va"], q["vb"], q["n"], arr, kind, q["uv"],
                                   q["rot"], bm.tint_color(block, q["tint"]), q["shade"], q["emissive"]))

    def bounds(self):
        pts = []
        for p0, va, vb, *_ in self.quads:
            pts += [p0, p0 + va, p0 + vb, p0 + va + vb]
        a = np.array(pts)
        return a.min(axis=0), a.max(axis=0)


class Camera:
    def __init__(self, kind, pos, yaw, pitch, size, fov=70.0, scale=40.0):
        self.kind, self.pos, self.yaw, self.pitch, self.size = kind, np.asarray(pos, float), yaw, pitch, size
        self.fov, self.scale = fov, scale
        self.f, self.r, self.u = camera_basis(yaw, pitch)


def persp(eye, yaw, pitch, fov=70.0, size=(1280, 720)) -> Camera:
    return Camera("persp", eye, yaw, pitch, size, fov=fov)


def ortho(center, yaw, pitch, px_per_block=40.0, size=(1200, 800)) -> Camera:
    return Camera("ortho", center, yaw, pitch, size, scale=px_per_block)


def _shade(n) -> float:
    if n[1] > 0.5:
        return 1.0
    if n[1] < -0.5:
        return 0.5
    return min(1.0, 0.8 * abs(n[2]) + 0.6 * abs(n[0]) + 0.9 * abs(n[1]))


def render(scene: Scene, cam: Camera, ss: int = 2, ground: float | None = None, fog: float = 0.0) -> Image.Image:
    W, H = cam.size[0] * ss, cam.size[1] * ss
    f, r, u = cam.f, cam.r, cam.u
    if cam.kind == "persp":
        focal = (H / 2) / math.tan(math.radians(cam.fov) / 2)
        O0, Ox, Oy = cam.pos, np.zeros(3), np.zeros(3)
        D0, Dx, Dy = f, r, -u
        k = 1 / focal
        unit = lambda a: ((a[0] - W / 2) * k, (a[1] - H / 2) * k)  # noqa: E731
    else:
        s = cam.scale * ss
        O0, Ox, Oy = cam.pos - f * 2000, r, -u
        D0, Dx, Dy = f, np.zeros(3), np.zeros(3)
        unit = lambda a: ((a[0] - W / 2) / s, (a[1] - H / 2) / s)  # noqa: E731

    ys0 = (np.arange(H) + 0.5)
    xs0 = (np.arange(W) + 0.5)
    color = np.zeros((H, W, 3), np.float32)
    # sky gradient
    t = np.clip(np.linspace(0, 1, H), 0, 1)[:, None, None]
    color[:] = (SKY_TOP * (1 - t) + SKY_HORIZON * t) if cam.kind == "persp" else np.array([120, 124, 134], float)
    depth = np.full((H, W), np.inf, np.float32)

    def project(pts):
        d = pts - (cam.pos if cam.kind == "persp" else cam.pos)
        if cam.kind == "persp":
            z = d @ f
            with np.errstate(divide="ignore", invalid="ignore"):
                return W / 2 + (d @ r) / z * focal, H / 2 - (d @ u) / z * focal, z
        return W / 2 + (d @ r) * s, H / 2 - (d @ u) * s, d @ f

    def visible(p0, n):
        if cam.kind == "persp":
            return float((cam.pos - p0) @ n) > 1e-7
        return float(D0 @ n) < -1e-7

    def draw(q, translucent: bool) -> None:
        p0, va, vb, n, arr, kind, uv, rot, tint, shade, emissive = q
        if not visible(p0, n):
            return
        corners = np.array([p0, p0 + va, p0 + vb, p0 + va + vb])
        sx, sy, z = project(corners)
        if cam.kind == "persp":
            if (z <= 0.05).all():
                return
            if (z <= 0.05).any():
                x0, x1, y0, y1 = 0, W, 0, H
            else:
                x0, x1 = max(int(sx.min()), 0), min(int(sx.max()) + 2, W)
                y0, y1 = max(int(sy.min()), 0), min(int(sy.max()) + 2, H)
        else:
            x0, x1 = max(int(sx.min()), 0), min(int(sx.max()) + 2, W)
            y0, y1 = max(int(sy.min()), 0), min(int(sy.max()) + 2, H)
        if x0 >= x1 or y0 >= y1:
            return
        X = ((xs0[x0:x1] - W / 2) * (k if cam.kind == "persp" else 1 / s))[None, :]
        Y = ((ys0[y0:y1] - H / 2) * (k if cam.kind == "persp" else 1 / s))[:, None]
        dn = D0 @ n + X * (Dx @ n) + Y * (Dy @ n)
        with np.errstate(divide="ignore", invalid="ignore"):
            tt = ((p0 - O0) @ n - X * (Ox @ n) - Y * (Oy @ n)) / dn
        la, lb = float(va @ va), float(vb @ vb)
        if la < 1e-12 or lb < 1e-12:
            return
        pa = (O0 @ va - p0 @ va) + X * (Ox @ va) + Y * (Oy @ va) + tt * (D0 @ va + X * (Dx @ va) + Y * (Dy @ va))
        pb = (O0 @ vb - p0 @ vb) + X * (Ox @ vb) + Y * (Oy @ vb) + tt * (D0 @ vb + X * (Dx @ vb) + Y * (Dy @ vb))
        a, b = pa / la, pb / lb
        inside = (tt > 0) & (a >= 0) & (a < 1) & (b >= 0) & (b < 1)
        if not inside.any():
            return
        if rot == 90:
            a, b = b, 1 - a
        elif rot == 180:
            a, b = 1 - a, 1 - b
        elif rot == 270:
            a, b = 1 - b, a
        S = arr.shape[0]
        tx = np.clip(((uv[0] + a * (uv[2] - uv[0])) / 16 * S).astype(np.int32), 0, S - 1)
        ty = np.clip(((uv[1] + b * (uv[3] - uv[1])) / 16 * S).astype(np.int32), 0, S - 1)
        texel = arr[ty, tx]
        alpha = texel[..., 3]
        sub_d = depth[y0:y1, x0:x1]
        if translucent:
            ok = inside & (alpha > 8) & (tt < sub_d)
        else:
            ok = inside & (alpha >= 128) & (tt < sub_d)
        if not ok.any():
            return
        rgb = texel[..., :3].astype(np.float32)
        if tint is not None:
            rgb = rgb * (np.array(tint, np.float32) / 255)
        sh = 1.0 if (emissive or not shade) else _shade(n)
        rgb = rgb * sh
        if emissive:
            rgb = np.minimum(rgb * 1.2 + 14, 255)
        sub_c = color[y0:y1, x0:x1]
        if translucent:
            al = (alpha / 255.0)[..., None]
            sub_c[ok] = (sub_c * (1 - al) + rgb * al)[ok]
        else:
            sub_c[ok] = rgb[ok]
            sub_d[ok] = tt[ok]

    opaque = [q for q in scene.quads if q[5] != "translucent"]
    trans = [q for q in scene.quads if q[5] == "translucent"]
    for q in opaque:
        draw(q, False)
    if ground is not None:                                              # infinite grass plane under everything
        grass, _ = bm.texture("block/grass_block_top")
        X = ((xs0 - W / 2) * (k if cam.kind == "persp" else 1 / s))[None, :]
        Y = ((ys0 - H / 2) * (k if cam.kind == "persp" else 1 / s))[:, None]
        dy = D0[1] + X * Dx[1] + Y * Dy[1]
        with np.errstate(divide="ignore", invalid="ignore"):
            tg = (ground - O0[1] - X * Ox[1] - Y * Oy[1]) / dy
        okg = (tg > 0) & (tg < depth) & np.isfinite(tg)
        if okg.any():
            px = O0[0] + X * Ox[0] + Y * Oy[0] + tg * (D0[0] + X * Dx[0] + Y * Dy[0])
            pz = O0[2] + X * Ox[2] + Y * Oy[2] + tg * (D0[2] + X * Dx[2] + Y * Dy[2])
            px, pz = np.where(okg, px, 0), np.where(okg, pz, 0)
            tx = ((px - np.floor(px)) * 16).astype(np.int32) % 16
            ty = ((pz - np.floor(pz)) * 16).astype(np.int32) % 16
            g = grass[ty, tx, :3].astype(np.float32) * (np.array(bm.GRASS, np.float32) / 255)
            if fog > 0:
                fr = np.clip(tg * fog, 0, 1)[..., None]
                g = g * (1 - fr) + color * fr
            color[okg] = g[okg]
            depth[okg] = tg[okg]
    c0 = scene.bounds() if trans else None
    if trans:
        pos = cam.pos
        trans.sort(key=lambda q: -float(np.linalg.norm(q[0] + (q[1] + q[2]) / 2 - pos)))
        for q in trans:
            draw(q, True)
    img = Image.fromarray(np.clip(color, 0, 255).astype(np.uint8), "RGB")
    if ss > 1:
        img = img.resize(cam.size, Image.LANCZOS)
    return img


def fit_ortho(scene: Scene, yaw: float, pitch: float, width: int = 1200, margin: float = 1.0):
    """An orthographic camera that frames the whole scene at the largest integer px/block that fits `width`."""
    lo, hi = scene.bounds()
    f, r, u = camera_basis(yaw, pitch)
    pts = np.array([[x, y, z] for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])])
    xs, ys = pts @ r, pts @ u
    wb, hb = xs.max() - xs.min() + 2 * margin, ys.max() - ys.min() + 2 * margin
    s = width / wb
    centre_screen = np.array([(xs.max() + xs.min()) / 2, (ys.max() + ys.min()) / 2])
    centre = centre_screen[0] * r + centre_screen[1] * u + f * float((pts.mean(axis=0)) @ f)
    return ortho(centre, yaw, pitch, s, (width, int(hb * s)))


def contact_sheet(images: list[Image.Image], cols: int = 2, pad: int = 8, bg=(40, 44, 52)) -> Image.Image:
    w = max(i.width for i in images)
    h = max(i.height for i in images)
    rows = (len(images) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * (w + pad) + pad, rows * (h + pad) + pad), bg)
    for n, im in enumerate(images):
        sheet.paste(im, (pad + (n % cols) * (w + pad), pad + (n // cols) * (h + pad)))
    return sheet
