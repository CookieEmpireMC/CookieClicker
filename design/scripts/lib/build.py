"""Block-structure kit library: Build (grid + lint notes), Ramp (clustered material gradients), Wall (a facade frame),
and the detail kits the style guide asks for (windows with sills/boxes/shutters/lit rooms, doors with frame and hood,
jetty, gable roof, chimney). Style rules: design/style-guide.md "Block structures"; lint: design/build-lint.md.

Axes as everywhere: x west -> east, y up, z north -> south. A Wall maps (u, y, w) = (along the wall, up, outward depth)
to world cells so one kit builds a facade on any side.
"""
from __future__ import annotations

import random

import numpy as np
from scipy.ndimage import gaussian_filter

import blocks as B
from blocks import AIR, DIRS, HORIZ, OPP, lg, plain, sl, st, stripped, td
from build_lint import Notes


# --- material ramps ---------------------------------------------------------------------------------
class Ramp:
    """A material as a ramp of blocks: a dominant base plus variants in clusters (R6/R7).
    variants: [(block data, share, bias)] with bias 'low' (weathers near the ground), 'high' or None.
    pick(x, y, z) is deterministic; clusters come from smoothed noise, so the variants form patches of 2-6 blocks."""

    def __init__(self, base: str, variants: list[tuple], seed: int = 1, shape=(48, 24, 48), height: float = 8.0,
                 y0: int = 0):
        self.base, self.variants, self.shape, self.height, self.y0 = base, variants, shape, height, y0
        rng = np.random.default_rng(seed)
        self.fields = []
        for i, (data, share, bias) in enumerate(variants):
            f = gaussian_filter(rng.standard_normal(shape), 0.9)
            f = (f - f.mean()) / f.std()
            f = (f + 0.55 * rng.standard_normal(shape)) / 1.13            # clusters with a few speckles
            self.fields.append(f)

    def pick(self, x: int, y: int, z: int) -> str:
        ix, iy, iz = (x + 8) % self.shape[0], (y + 4) % self.shape[1], (z + 8) % self.shape[2]
        for (data, share, bias), f in zip(self.variants, self.fields):
            v = f[ix, iy, iz]
            t = min(max(y - self.y0, 0), self.height) / self.height
            if bias == "low":
                v += 0.9 * (1 - t) * 2 - 0.9
            elif bias == "high":
                v += 0.9 * t * 2 - 0.9
            # threshold of the standard normal for the wanted share
            if v > _z_for_share(share):
                return data
        return self.base

    def assign(self, cells: list) -> dict:
        """Exact quotas for a small set of cells (x, y, z): each variant gets round(share * n) cells, chosen where its
        noise field is highest, so the patches stay clustered but every block of the ramp is really used."""
        left = list(cells)
        out = {}
        n = len(cells)
        for (data, share, bias), f in zip(self.variants, self.fields):
            k = max(1, round(share * n))
            def val(c):
                return f[(c[0] + 8) % self.shape[0], (c[1] + 4) % self.shape[1], (c[2] + 8) % self.shape[2]]
            for c in sorted(left, key=val, reverse=True)[:k]:
                out[c] = data
            left = [c for c in left if c not in out]
        for c in left:
            out[c] = self.base
        return out

    def ids(self) -> list[str]:
        return [B.block_id(self.base)] + [B.block_id(d) for d, _, _ in self.variants]


def _z_for_share(share: float) -> float:
    from statistics import NormalDist
    return NormalDist().inv_cdf(1 - share)


# --- build container -------------------------------------------------------------------------------------------
class Build:
    def __init__(self, size: tuple, ground: int = 0):
        self.size = size                            # (sx, sy_above_ground, sz); layers = ground + sy
        self.ground = ground
        self.g: dict[tuple, str] = {}
        self.notes = Notes()
        self.rng = random.Random(7)
        self.scenes: list[dict] = []
        self._scene: dict | None = None

    def scene(self, name: str, anchor: str) -> None:
        """Start a decor scene: every put() until the next scene() is recorded (lint D1/D2)."""
        self._scene = {"name": name, "anchor": anchor, "cells": []}
        self.scenes.append(self._scene)

    def put(self, x: int, y: int, z: int, data: str | None) -> None:
        if data is None:
            return
        sx, sy, sz = self.size
        assert 0 <= x < sx and -self.ground <= y < sy and 0 <= z < sz, f"out of the design box: {(x, y, z)} {data}"
        self.g[(x, y, z)] = data
        if self._scene is not None and data != AIR:
            self._scene["cells"].append((x, y, z))

    def get(self, x: int, y: int, z: int) -> str | None:
        return self.g.get((x, y, z))

    def block(self, x: int, y: int, z: int) -> str:
        v = self.g.get((x, y, z))
        return "air" if v is None else B.block_id(v)

    def fill(self, x1, y1, z1, x2, y2, z2, data) -> None:
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.put(x, y, z, data(x, y, z) if callable(data) else data)

    def clear(self, x1, y1, z1, x2, y2, z2) -> None:
        self.fill(x1, y1, z1, x2, y2, z2, AIR)

    def volume(self, name: str, footprint: tuple, top: int) -> None:
        self.notes.volumes.append({"name": name, "footprint": footprint, "top": top})


# --- a facade frame ---------------------------------------------------------------------------------------------------
class Wall:
    """(u, y, w): u along the wall, y up, w outward (w = 0 is the wall plane, +1 one cell in front of it).
    `out` = compass direction the wall faces, `along` = compass direction of increasing u; origin = (x, z) of u=0, w=0."""

    def __init__(self, b: Build, out: str, along: str, origin: tuple):
        self.b, self.out, self.along, self.origin = b, out, along, origin
        self.inn = OPP[out]
        self.back = OPP[along]

    def xyz(self, u: int, y: int, w: int = 0) -> tuple:
        ox, oz = self.origin
        a, o = DIRS[self.along], DIRS[self.out]
        return ox + a[0] * u + o[0] * w, y, oz + a[2] * u + o[2] * w

    def put(self, u: int, y: int, w: int, data: str | None) -> None:
        if data is not None:
            self.b.put(*self.xyz(u, y, w), data)

    def get(self, u: int, y: int, w: int = 0) -> str | None:
        return self.b.get(*self.xyz(u, y, w))

    def log(self, mat: str, axis: str = "y", stripped_: bool = False) -> str:
        """Log whose axis is 'y', 'along' (horizontal along the wall) or 'depth' (through the wall)."""
        ax = {"y": "y", "along": "x" if self.along in ("east", "west") else "z",
              "depth": "x" if self.out in ("east", "west") else "z"}[axis]
        return (stripped if stripped_ else lg)(mat, ax)

    def stair(self, mat: str, half: str = "bottom", toward: str = "out") -> str:
        face = {"out": self.out, "in": self.inn, "along": self.along, "back": self.back}[toward]
        return st(mat, face, half)

    def trapdoor(self, mat: str, half: str = "bottom", open: bool = False, toward: str = "out") -> str:
        face = {"out": self.out, "in": self.inn, "along": self.along, "back": self.back}[toward]
        return td(mat, face, half, open)


# --- kits ---------------------------------------------------------------------------------------------------------
def window(wall: Wall, u: int, y: int, *, width: int = 1, height: int = 1, w: int = 0, sill: str = "spruce",
           shutters: str | None = "spruce", box: str | None = "potted_poppy", light: bool = True,
           room: str = "table", mullion: str | None = None, shutter_w: int = 1, glass: str = "@glass_pane") -> None:
    """A window `width` x `height` cells at (u..u+width-1, y..y+height-1) in the wall plane (w), with a projecting sill
    (top-half trapdoor), an optional flower box (a potted plant on the sill), open-trapdoor shutters at both ends and a
    lit room behind: a lantern on a small table, 2 blocks inside (R5)."""
    for i in range(width):
        for j in range(height):
            wall.put(u + i, y + j, w, mullion if (mullion and width == 3 and i == 1) else glass)
    for i in range(width):
        if sill:
            wall.put(u + i, y - 1, w + 1, wall.trapdoor(sill, "top", False))
        if box:
            wall.put(u + i, y, w + 1, plain(box))
    if shutters:
        for j in range(height):
            wall.put(u - 1, y + j, w + shutter_w, wall.trapdoor(shutters, "bottom", True))
            wall.put(u + width, y + j, w + shutter_w, wall.trapdoor(shutters, "bottom", True))
    if light:                                   # a wall shelf (top slab) with a standing lantern right behind the pane
        cx = u + width // 2
        wall.put(cx, y - 1, w - 1, sl("spruce", "top"))
        wall.put(cx, y, w - 1, B.lantern(False))


def door(wall: Wall, u: int, y: int, *, mat: str = "spruce", frame: str = "dark_oak", hood: bool = True,
         hinge: str = "left", w: int = 0, lintel: bool = True) -> tuple:
    """A door at (u, y..y+1) in the wall plane with log jambs, a stripped-log lintel one block wider each side and an
    optional stair hood on two brackets (R3). Returns the lower door cell."""
    wall.put(u, y, w, B.dr(mat, wall.out, "lower", hinge))
    wall.put(u, y + 1, w, B.dr(mat, wall.out, "upper", hinge))
    for s in (-1, 1):
        wall.put(u + s, y, w, lg(frame, "y"))
        wall.put(u + s, y + 1, w, lg(frame, "y"))
    if lintel:
        for s in (-1, 0, 1):
            wall.put(u + s, y + 2, w, wall.log(frame, "along", True))
    if hood:
        for s in (-1, 0, 1):
            wall.put(u + s, y + 3, w + 1, wall.stair(frame, "bottom", "out"))
    return wall.xyz(u, y, w)


def gable_roof(b: Build, *, axis: str, w0: int, w1: int, a0: int, a1: int, y_eave: int, field: str, eave: str,
               rake: str, ridge: str, cap: str | None, overhang: int = 1, name: str = "roof",
               ramp_pick=None, rake_ends=(True, True)) -> dict:
    """45-degree gable roof of stairs. axis 'z': the ridge runs along z; walls occupy x w0..w1 (outer faces), the roof
    spans z a0..a1 (overhang included). Eave stairs sit level with the top plate row `y_eave` (one outside the wall);
    returns {'y_ridge': ..., 'profile': {x: y}} for the gable infill. `field`/`eave`/`rake` are material names
    ('dark_oak', 'spruce'); `ridge` is a block data string, `cap` an optional block data on top of it.
    `ramp_pick(x, y, z) -> material` (optional) overrides the field material per cell (weathering)."""
    lo, hi = w0 - overhang, w1 + overhang
    c = (w0 + w1) // 2
    assert (w1 - w0) % 2 == 0, "odd wall count so the ridge is on a cell"
    profile = {}
    eave_cells, field_cells = [], []
    for x in range(lo, c):
        profile[x] = y_eave + (x - lo)
        profile[hi - (x - lo)] = profile[x]
    profile[c] = profile[c - 1]
    y_ridge = profile[c]
    for a in range(a0, a1 + 1):
        for x, y in profile.items():
            pos = (x, y, a) if axis == "z" else (a, y, x)
            if x == c:
                b.put(*pos, ridge)
                if cap:
                    b.put(pos[0], pos[1] + 1, pos[2], cap)
                continue
            west = x < c
            toward = ("east" if west else "west") if axis == "z" else ("south" if west else "north")
            edge_row = x in (lo, hi)
            mat = field
            if ramp_pick:
                mat = ramp_pick(*pos)
            if edge_row:
                mat = eave
            if (a == a0 and rake_ends[0]) or (a == a1 and rake_ends[1]):
                mat = rake
            b.put(*pos, st(mat, toward))
            b.notes.roof_cells.add(pos)
            (eave_cells if edge_row else field_cells).append(pos)
    b.notes.roofs.append({"name": name, "eave": eave_cells, "field": field_cells, "overhang": overhang})
    return {"y_ridge": y_ridge, "profile": profile, "centre": c}


def stack(b: Build, x: int, z: int, y0: int, y_top: int, *, base: Ramp, shaft: Ramp, base_courses: int = 3,
          band_y: int | None = None, name: str = "chimney") -> tuple:
    """THE chimney (R11): a 2 x 2 stack at cells x..x+1, z..z+1 from y0 to y_top (the top block): `base_courses` of
    stone, a brick shaft, an optional setback band at band_y, a corbel ring of inverted brick stairs (corner slabs)
    two blocks below the top and a crown of two stone-brick slabs. Returns the smoke cell (air above the flue, one above the crown)."""
    cells = [(x + i, z + j) for i in (0, 1) for j in (0, 1)]
    shaft_cells = [(cx, y, cz) for y in range(y0 + base_courses, y_top + 1) for (cx, cz) in cells]
    picked = shaft.assign(shaft_cells)
    for y in range(y0, y_top + 1):
        for (cx, cz) in cells:
            b.put(cx, y, cz, base.pick(cx, y, cz) if y < y0 + base_courses else picked[(cx, y, cz)])
    for (cx, cz) in [(x - 1 + i, z - 1 + j) for i in range(4) for j in range(4)
                     if (i in (0, 3)) != (j in (0, 3))]:           # slim collar: top-half brick slabs on the 8 edge cells
        b.put(cx, y_top, cz, sl("brick", "top"))
    b.notes.chimneys.append(name)
    return (x, y_top + 1, z)
