"""Real vanilla block models and textures from the Minecraft client jar, for the structure previews.

The jar is NOT part of the repo (and never committed: *.jar is git-ignored). It is read in place:
  $BAKERY_CLIENT_JAR, default ~/bakery-review/chunky/home/resources/minecraft.jar (the 26.3 client jar)
`available()` is False without it; generators then skip their textured previews (the YAML is unaffected).

block_quads(data) turns a block data string ("minecraft:oak_stairs[facing=east,...]") into textured quads in
block-local coordinates (0..1), by resolving blockstates/<id>.json -> models/block/*.json (parent chain, elements,
element and variant rotations, face uv), exactly like the game's model baker does (minus uvlock and AO).
A quad is a dict: p0 (top-left corner as seen from outside), va (u direction), vb (v direction, down in the texture),
n (outward normal), tex (texture name), uv (u1, v1, u2, v2 in 0..16), rot (face uv rotation), tint (tintindex or -1),
cull (face direction name or None), shade (bool), emissive (bool).
Spec of the formats: Minecraft Wiki "Model" / "Blockstates definition" (parent chain, variants, multipart).
"""
from __future__ import annotations

import io
import json
import math
import os
import zipfile
from functools import lru_cache
from pathlib import Path

import numpy as np
from PIL import Image

DEFAULT_JAR = Path.home() / "bakery-review" / "chunky" / "home" / "resources" / "minecraft.jar"


def jar_path() -> Path:
    return Path(os.environ.get("BAKERY_CLIENT_JAR", str(DEFAULT_JAR)))


def available() -> bool:
    return jar_path().is_file()


@lru_cache(None)
def _zip() -> zipfile.ZipFile:
    return zipfile.ZipFile(jar_path())


def _read(path: str) -> bytes:
    return _zip().read("assets/minecraft/" + path)


def _has(path: str) -> bool:
    try:
        _zip().getinfo("assets/minecraft/" + path)
        return True
    except KeyError:
        return False


def _strip(name: str) -> str:
    return name.split(":", 1)[1] if ":" in name else name


# --- textures ----------------------------------------------------------------------------------------
@lru_cache(None)
def texture(name: str) -> tuple[np.ndarray, str]:
    """(RGBA uint8 array HxWx4 of the first frame, kind) with kind 'opaque' | 'cutout' | 'translucent'."""
    name = _strip(name)
    img = Image.open(io.BytesIO(_read(f"textures/{name}.png"))).convert("RGBA")
    w, h = img.size
    if h > w:                                   # animated strip: first frame
        img = img.crop((0, 0, w, w))
    a = np.array(img)
    alpha = a[..., 3]
    mid = int(((alpha > 8) & (alpha < 247)).sum())
    if mid > 0.04 * alpha.size:
        kind = "translucent"
    elif (alpha < 128).any():
        kind = "cutout"
    else:
        kind = "opaque"
    return a, kind


# --- models ------------------------------------------------------------------------------------------
@lru_cache(None)
def model(name: str) -> dict:
    name = _strip(name)
    if name.startswith("builtin/") or not _has(f"models/{name}.json"):
        return {"elements": [], "textures": {}}
    data = json.loads(_read(f"models/{name}.json"))
    parent = model(data["parent"]) if "parent" in data else {"elements": [], "textures": {}}
    textures = dict(parent["textures"])
    textures.update({k: (v["sprite"] if isinstance(v, dict) else v) for k, v in data.get("textures", {}).items()})
    return {"elements": data.get("elements", parent["elements"]), "textures": textures}


def _resolve_tex(ref: str, textures: dict) -> str | None:
    seen = 0
    while ref.startswith("#"):
        ref = textures.get(ref[1:], "")
        seen += 1
        if not ref or seen > 12:
            return None
    return _strip(ref)


@lru_cache(None)
def blockstate(block: str) -> dict | None:
    return json.loads(_read(f"blockstates/{block}.json")) if _has(f"blockstates/{block}.json") else None


def _match(cond: dict, props: dict) -> bool:
    if "OR" in cond:
        return any(_match(c, props) for c in cond["OR"])
    if "AND" in cond:
        return all(_match(c, props) for c in cond["AND"])
    return all(props.get(k) in str(v).split("|") for k, v in cond.items())


def select_models(block: str, props: dict) -> list[dict]:
    bs = blockstate(block)
    if bs is None:
        return []
    pick = lambda v: v[0] if isinstance(v, list) else v  # noqa: E731
    if "variants" in bs:
        for key, v in bs["variants"].items():
            want = dict(p.split("=") for p in key.split(",")) if key else {}
            if all(props.get(k) == val for k, val in want.items()):
                return [pick(v)]
        return []
    out = []
    for part in bs.get("multipart", []):
        if "when" not in part or _match(part["when"], props):
            out.append(pick(part["apply"]))
    return out


# --- geometry ----------------------------------------------------------------------------------------
DIRS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0),
        "down": (0, -1, 0)}


def _rot(axis: str, deg: float) -> np.ndarray:
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == "y":
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def _face_geometry(face: str, f, t):
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
    }[face]


def _default_uv(face: str, f, t):
    x1, y1, z1 = f
    x2, y2, z2 = t
    return {"down": [x1, 16 - z2, x2, 16 - z1], "up": [x1, z1, x2, z2],
            "north": [16 - x2, 16 - y2, 16 - x1, 16 - y1], "south": [x1, 16 - y2, x2, 16 - y1],
            "west": [z1, 16 - y2, z2, 16 - y1], "east": [16 - z2, 16 - y2, 16 - z1, 16 - y1]}[face]


def _nearest_dir(v) -> str:
    v = np.asarray(v, float)
    return max(DIRS, key=lambda d: float(np.dot(v, DIRS[d])))


def _model_quads(part: dict) -> list[dict]:
    m = model(part["model"])
    rx, ry = part.get("x", 0), part.get("y", 0)
    quads = []
    for el in m["elements"]:
        f, t = el["from"], el["to"]
        rotation = el.get("rotation")
        for face, fd in el.get("faces", {}).items():
            p0, va, vb = (np.array(v, float) for v in _face_geometry(face, f, t))
            n = np.array(DIRS[face], float)
            if rotation:
                R = _rot(rotation["axis"], rotation["angle"])
                o = np.array(rotation.get("origin", [8, 8, 8]), float)
                if rotation.get("rescale"):
                    k = 1 / math.cos(math.radians(abs(rotation["angle"])))
                    S = np.ones(3) * k
                    S[{"x": 0, "y": 1, "z": 2}[rotation["axis"]]] = 1
                    p0, va, vb = (p0 - o) * S + o, va * S, vb * S
                p0, va, vb, n = (R @ (p0 - o)) + o, R @ va, R @ vb, R @ n
            if rx or ry:
                Rm = _rot("y", -ry) @ _rot("x", -rx)
                c = np.array([8.0, 8, 8])
                p0, va, vb, n = (Rm @ (p0 - c)) + c, Rm @ va, Rm @ vb, Rm @ n
            tex = _resolve_tex(fd.get("texture", ""), m["textures"])
            if tex is None:
                continue
            cull = fd.get("cullface")
            if cull and (rx or ry):
                cull = _nearest_dir(np.array(DIRS[cull], float) @ (_rot("y", -ry) @ _rot("x", -rx)).T)
            quads.append({
                "p0": p0 / 16, "va": va / 16, "vb": vb / 16, "n": n / max(np.linalg.norm(n), 1e-9), "tex": tex,
                "uv": fd.get("uv") or _default_uv(face, f, t), "rot": fd.get("rotation", 0),
                "tint": fd.get("tintindex", -1), "cull": cull, "shade": el.get("shade", True),
            })
    return quads


# --- block -> quads ----------------------------------------------------------------------------------
def parse(data: str) -> tuple[str, dict]:
    block, _, rest = data.removeprefix("minecraft:").partition("[")
    return block, dict(p.split("=") for p in rest.rstrip("]").split(",")) if rest else {}


EMISSIVE_WORDS = ("lantern", "shroomlight", "magma_block", "glowstone", "sea_lantern", "froglight", "torch",
                  "jack_o_lantern", "copper_bulb")


def _emissive(block: str, props: dict) -> bool:
    if block == "campfire":
        return props.get("lit") == "true"
    return any(w in block for w in EMISSIVE_WORDS)


def _box_quads(f, t, tex: str, tint=-1) -> list[dict]:
    out = []
    for face in DIRS:
        p0, va, vb = (np.array(v, float) for v in _face_geometry(face, f, t))
        out.append({"p0": p0 / 16, "va": va / 16, "vb": vb / 16, "n": np.array(DIRS[face], float), "tex": tex,
                    "uv": _default_uv(face, f, t), "rot": 0, "tint": tint, "cull": None, "shade": True})
    return out


@lru_cache(None)
def block_quads(data: str) -> tuple:
    block, props = parse(data)
    if block == "air":
        return ()
    emissive = _emissive(block, props)
    if block == "water":                                               # fluids have no block model
        h = 14 if props.get("level", "0") == "0" else 12
        qs = [q for q in _box_quads((0, 0, 0), (16, h, 16), "block/water_still", 0) if q["n"][1] > 0]
        for q in qs:
            q["cull"] = None
        return tuple(dict(q, emissive=False) for q in qs)
    if block.endswith("_wall_sign") or block.endswith("_sign") or block.endswith("hanging_sign"):
        wood = block.replace("_wall_hanging_sign", "").replace("_hanging_sign", "").replace("_wall_sign", "") \
            .replace("_sign", "")
        tex = f"block/{wood}_planks"
        if block.endswith("_wall_sign"):
            f, t = (0, 4.5, 14), (16, 12.5, 16)
            rot = {"north": 0, "south": 180, "west": 270, "east": 90}[props.get("facing", "north")]
            qs = _box_quads(f, t, tex)
        elif block.endswith("hanging_sign"):
            qs = _box_quads((1, 0, 7), (15, 10, 9), tex) + _box_quads((3, 10, 7.5), (4, 16, 8.5), "block/iron_block") \
                + _box_quads((12, 10, 7.5), (13, 16, 8.5), "block/iron_block")
            rot = 0 if int(props.get("rotation", "8")) in (0, 8) else 90
        else:
            qs = _box_quads((7, 0, 7), (9, 10, 9), tex)
            rot = 0
        if rot:
            Rm = _rot("y", -rot)
            c = np.array([.5, .5, .5])
            for q in qs:
                q["p0"], q["va"], q["vb"], q["n"] = (Rm @ (q["p0"] - c)) + c, Rm @ q["va"], Rm @ q["vb"], Rm @ q["n"]
        return tuple(dict(q, emissive=False) for q in qs)
    parts = select_models(block, props)
    quads = []
    for part in parts:
        quads += _model_quads(part)
    for q in quads:
        q["emissive"] = emissive
    if not quads:
        raise KeyError(f"no model for {data}")
    return tuple(quads)


@lru_cache(None)
def is_opaque_cube(data: str) -> bool:
    block, props = parse(data)
    if block in ("air", "water"):
        return False
    try:
        qs = block_quads(data)
    except KeyError:
        return False
    full = {}
    for q in qs:
        if q["cull"] and abs(np.linalg.norm(q["va"]) - 1) < 1e-6 and abs(np.linalg.norm(q["vb"]) - 1) < 1e-6 \
                and texture(q["tex"])[1] == "opaque":
            full[q["cull"]] = True
    return len(full) == 6


# --- tints (plains biome) ---------------------------------------------------------------------------
GRASS, FOLIAGE, WATER = (145, 189, 89), (119, 171, 47), (63, 118, 228)
FIXED = {"birch_leaves": (128, 167, 85), "spruce_leaves": (97, 153, 97), "lily_pad": (32, 128, 48)}
NO_TINT_LEAVES = ("cherry_leaves", "azalea_leaves", "flowering_azalea_leaves", "pale_oak_leaves")


def tint_color(block: str, tintindex: int):
    if tintindex < 0:
        return None
    if block in FIXED:
        return FIXED[block]
    if block == "water":
        return WATER
    if block.endswith("_leaves") or block in ("vine", "bush"):
        return None if block in NO_TINT_LEAVES else FOLIAGE
    return GRASS
