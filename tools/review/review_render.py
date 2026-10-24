#!/usr/bin/env python3
"""Place a whole plot on the local review server and render review shots with Chunky.

    mise run review-render <design> [--shots eye,34,door] [--at X Y Z] [--spp 128] [--no-place] [--no-render]

1. Plans the plot like `/bakery plot setup` (tools/review/plot_plan.py): house at layout.house-at incl. its
   ground-depth layers, decor, fence. A design without a layout is placed alone.
2. Places it over RCON (127.0.0.1 only): clears the area, resets the flat ground, then `setblock ... strict`
   (no neighbour updates, so water doesn't flow and nothing pops), supports first; then `save-all flush`.
3. Writes one Chunky scene per shot (y range -64.., camera from Minecraft yaw/pitch) and renders it headlessly.
   Output: <shots>/<design>_<shot>.png.

Starts the review server when RCON doesn't answer (and leaves it running); never stops a running one. Before a
start it makes server.properties loopback-only (server-ip=127.0.0.1, refused if it names another address) with RCON
on and a random password kept in <home>/rcon.password (owner-only), which the client then uses.
Paths (env): BAKERY_REVIEW_HOME (default ~/bakery-review), BAKERY_REVIEW_SERVER (<home>/server),
BAKERY_REVIEW_CHUNKY (<home>/chunky), BAKERY_REVIEW_TEXTURES (<chunky>/home/resources/minecraft.jar),
BAKERY_REVIEW_SCENES (<home>/scenes), BAKERY_REVIEW_SHOTS (<home>/shots), BAKERY_REVIEW_JAVA (java on PATH),
BAKERY_RCON_HOST (loopback only, 127.0.0.1), BAKERY_RCON_PORT (25575), BAKERY_RCON_PASSWORD (overrides the
stored password).
"""
import argparse
import glob
import json
import math
import os
import secrets
import shutil
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import plot_plan  # noqa: E402
from rcon import Rcon, reachable  # noqa: E402

REPO = os.path.realpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
ALL_SHOTS = ("eye", "34", "door")
# block ids (substrings) that need a block below/behind them: placed after everything else
ATTACHED = ("stairs", "slab", "fence", "pane", "bars", "wall", "door", "trapdoor", "lantern", "torch", "button",
            "sign", "carpet", "flower", "poppy", "dandelion", "tulip", "rose", "grass", "fern", "chain", "ladder",
            "candle", "pot", "rail", "banner", "leaves", "vine", "pod", "head", "skull", "bush", "sapling",
            "lever", "plate", "bell", "wheat", "crop", "water", "lava", "snow", "moss_carpet", "lily", "petals")
CLEAR_MARGIN = 2
WORLD_MIN_Y = -64


def env_path(name, default):
    return os.path.expanduser(os.environ.get(name, default))


def paths():
    home = env_path("BAKERY_REVIEW_HOME", "~/bakery-review")
    chunky = env_path("BAKERY_REVIEW_CHUNKY", os.path.join(home, "chunky"))
    return {
        "home": home,
        "server": env_path("BAKERY_REVIEW_SERVER", os.path.join(home, "server")),
        "chunky": chunky,
        "textures": env_path("BAKERY_REVIEW_TEXTURES", os.path.join(chunky, "home", "resources", "minecraft.jar")),
        "scenes": env_path("BAKERY_REVIEW_SCENES", os.path.join(home, "scenes")),
        "shots": env_path("BAKERY_REVIEW_SHOTS", os.path.join(home, "shots")),
        "java": os.environ.get("BAKERY_REVIEW_JAVA", "java"),
    }


LOOPBACK = ("127.0.0.1", "localhost", "::1")
PASSWORD_FILE = "rcon.password"


def rcon_endpoint():
    host = os.environ.get("BAKERY_RCON_HOST", "127.0.0.1")
    if host not in LOOPBACK:
        sys.exit("BAKERY_RCON_HOST must be a loopback address: RCON stays bound to 127.0.0.1")
    return host, int(os.environ.get("BAKERY_RCON_PORT", "25575"))


# ---------------------------------------------------------------- server

def read_properties(path):
    """server.properties as (lines, {key: value})."""
    if not os.path.exists(path):
        return [], {}
    with open(path, encoding="utf-8") as f:
        lines = f.read().splitlines()
    values = {}
    for line in lines:
        if line and not line.lstrip().startswith("#") and "=" in line:
            k, v = line.split("=", 1)
            values[k.strip()] = v.strip()
    return lines, values


def write_properties(path, lines, updates):
    """Sets `updates` in server.properties, keeping every other line as it is."""
    todo = dict(updates)
    out = []
    for line in lines:
        key = line.split("=", 1)[0].strip() if "=" in line and not line.lstrip().startswith("#") else None
        if key in todo:
            out.append(f"{key}={todo.pop(key)}")
        else:
            out.append(line)
    out.extend(f"{k}={v}" for k, v in todo.items())
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(out) + "\n")


def check_loopback(values, props):
    ip = values.get("server-ip", "")
    if ip and ip not in LOOPBACK:
        sys.exit(f"{props} has server-ip={ip}; the review server must bind to 127.0.0.1 only. Refusing.")
    return bool(ip)


def stored_password(home):
    """The review RCON password kept in BAKERY_REVIEW_HOME (created random, owner-only, on first use)."""
    path = os.path.join(home, PASSWORD_FILE)
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            pw = f.read().strip()
        if pw:
            return pw
    pw = secrets.token_urlsafe(24)
    os.makedirs(home, exist_ok=True)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        f.write(pw + "\n")
    return pw


def ensure_server(p):
    """(host, port, password) of a running loopback-only review server; starts one when RCON doesn't answer.

    A running server is never touched: it is used with whichever password it was started with (the stored one, or
    the one still in server.properties from before the random password existed). Before a start, server.properties
    gets server-ip=127.0.0.1 (refused when it names another address), enable-rcon=true, the port and the stored
    random password, so an old fixed password is replaced on the next start.
    """
    host, port = rcon_endpoint()
    props = os.path.join(p["server"], "server.properties")
    lines, values = read_properties(props)
    bound = check_loopback(values, props)
    stored = os.environ.get("BAKERY_RCON_PASSWORD") or stored_password(p["home"])
    for pw in dict.fromkeys([stored, values.get("rcon.password", "")]):
        if pw and reachable(host, port, pw):
            if not bound:
                sys.exit(f"A server answers RCON but {props} has no server-ip=127.0.0.1; fix it and restart it.")
            if pw != stored:
                print("  the running server still uses its old RCON password; it switches to the stored random "
                      "one on its next start by this tool")
            return host, port, pw
    jar = os.path.join(p["server"], "paper.jar")
    if not os.path.exists(jar):
        sys.exit(f"Review server not running and no {jar}; set BAKERY_REVIEW_SERVER")
    write_properties(props, lines, {"server-ip": "127.0.0.1", "enable-rcon": "true", "rcon.port": str(port),
                                    "rcon.password": stored, "broadcast-rcon-to-ops": "false"})
    log = open(os.path.join(p["server"], "review-server.log"), "ab")
    print(f"Starting the review server in {p['server']} (it keeps running; stop it with RCON 'stop')")
    subprocess.Popen([p["java"], "-Xms1G", "-Xmx2G", "-jar", jar, "--nogui"], cwd=p["server"], stdout=log,
                     stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL, start_new_session=True)
    for _ in range(180):
        time.sleep(1)
        if reachable(host, port, stored):
            return host, port, stored
    sys.exit("The review server did not open RCON within 180 s; see review-server.log")


# ---------------------------------------------------------------- placing

def attached(data):
    ident = plot_plan.block_id(data)
    return any(k in ident for k in ATTACHED)


def place(plan, endpoint):
    host, port, pw = endpoint
    min_x, min_z, max_x, max_z = plan.box
    x1, z1, x2, z2 = min_x - CLEAR_MARGIN, min_z - CLEAR_MARGIN, max_x + CLEAR_MARGIN, max_z + CLEAR_MARGIN
    low = min([y for _, y, _ in plan.blocks] + [plan.y0 - 3])
    top = max(plan.top(), plan.y0 + plot_plan.DECOR_MAX_Y) + 2
    with Rcon(host, port, pw) as r:
        r.cmd(f"forceload add {x1} {z1} {x2} {z2}")
        wait_loaded(r, x1, z1, x2, z2, plan.y0)
        # clear the area top-down, then reset the flat ground (grass on dirt) so earlier runs leave nothing
        for y in range(top, plan.y0 - 1, -1):
            fill(r, x1, y, z1, x2, z2, "minecraft:air")
        for y in range(plan.y0 - 1, max(low, WORLD_MIN_Y + 1) - 1, -1):
            fill(r, x1, y, z1, x2, z2, "minecraft:grass_block" if y == plan.y0 - 1 else "minecraft:dirt")
        r.cmd(f"kill @e[type=item,x={x1},y={low},z={z1},dx={x2 - x1},dy={top - low},dz={z2 - z1}]")
        order = sorted(plan.blocks.items(), key=lambda kv: (attached(kv[1]), kv[0][1]))
        errors = 0
        for (x, y, z), data in order:
            out = r.cmd(f"setblock {x} {y} {z} {data} strict")
            if out and "Changed the block" not in out and "Could not set" not in out:
                errors += 1
                if errors <= 10:
                    print(f"  setblock {x} {y} {z} {data} -> {out}", file=sys.stderr)
        print(f"Placed {len(order)} blocks ({errors} errors)")
        print(r.cmd("save-all flush") or "saved")
        r.cmd(f"forceload remove {x1} {z1} {x2} {z2}")
        time.sleep(1)


def fill(r, x1, y, z1, x2, z2, block):
    out = r.cmd(f"fill {x1} {y} {z1} {x2} {y} {z2} {block}")
    if out and "Successfully filled" not in out and "No blocks were filled" not in out:
        print(f"  fill y {y}: {out}", file=sys.stderr)


def wait_loaded(r, x1, z1, x2, z2, y):
    corners = [(x1, z1), (x2, z1), (x1, z2), (x2, z2)]
    for _ in range(60):
        if all("passed" in r.cmd(f"execute if loaded {x} {y} {z}").lower() for x, z in corners):
            return
        time.sleep(0.5)
    print("  warning: chunks not reported loaded after 30 s; placing anyway", file=sys.stderr)


# ---------------------------------------------------------------- scenes and rendering

def scene(name, p, cam, box, spp, width, height):
    x, y, z, yaw, pitch = cam
    min_x, min_z, max_x, max_z = box
    margin = 48
    chunks = [[cx, cz] for cx in range((min_x - margin) >> 4, ((max_x + margin) >> 4) + 1)
              for cz in range((min_z - margin) >> 4, ((max_z + margin) >> 4) + 1)]
    return {
        "sdfVersion": 9, "name": name, "width": width, "height": height, "sppTarget": spp, "outputMode": "PNG",
        "rayDepth": 5, "yClipMin": WORLD_MIN_Y, "yClipMax": 64, "yMin": WORLD_MIN_Y, "yMax": 64,
        "pathTrace": True, "renderActors": False,
        "world": {"path": os.path.join(p["server"], "world"), "dimension": "minecraft:overworld"},
        "chunkList": chunks,
        "camera": {"name": "camera 1", "position": {"x": x, "y": y, "z": z},
                   # verified 2026-10-24: chunky yaw = radians(90 - mcYaw), chunky pitch = radians(-90 + mcPitch)
                   "orientation": {"roll": 0.0, "pitch": math.radians(-90 + pitch), "yaw": math.radians(90 - yaw)},
                   "projectionMode": "PINHOLE", "fov": 70.0},
        "sun": {"altitude": 0.9, "azimuth": 4.0, "drawTexture": True},
        "sky": {"mode": "SIMULATED", "simulatedSky": "Preetham"},
    }


def render(name, data, p):
    d = os.path.join(p["scenes"], name)
    os.makedirs(d, exist_ok=True)
    # stale octree/dump/snapshots make Chunky render the old world or resume an old image
    for old in glob.glob(os.path.join(d, "*.octree2")) + glob.glob(os.path.join(d, "*.dump")) \
            + glob.glob(os.path.join(d, "*.json.backup")):
        os.remove(old)
    shutil.rmtree(os.path.join(d, "snapshots"), ignore_errors=True)
    path = os.path.join(d, name + ".json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=1)
    launcher = os.path.join(p["chunky"], "ChunkyLauncher.jar")
    for need in (launcher, p["textures"]):
        if not os.path.exists(need):
            sys.exit(f"Missing {need}; set BAKERY_REVIEW_CHUNKY / BAKERY_REVIEW_TEXTURES")
    cmd = [p["java"], f"-Dchunky.home={os.path.join(p['chunky'], 'home')}", "-jar", launcher,
           "-texture", p["textures"], "-reload-chunks", "-render", path, "-f"]
    res = subprocess.run(cmd, cwd=p["chunky"], capture_output=True, text=True)
    pngs = sorted(glob.glob(os.path.join(d, "snapshots", "*.png")), key=os.path.getmtime)
    if res.returncode != 0 or not pngs:
        sys.stderr.write((res.stdout or "")[-2000:] + (res.stderr or "")[-2000:])
        sys.exit(f"Chunky did not render {name} (exit {res.returncode})")
    return pngs[-1]


# ---------------------------------------------------------------- main

def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("design", help="house design name in src/main/resources/houses (e.g. factory)")
    ap.add_argument("--shots", default=",".join(ALL_SHOTS), help="comma list of eye,34,door (default: all)")
    ap.add_argument("--at", nargs=3, type=int, default=[0, -60, 0], metavar=("X", "Y0", "Z"),
                    help="plot min corner; Y0 = first air layer (flat world: -60)")
    ap.add_argument("--houses", default=os.path.join(REPO, "src", "main", "resources", "houses"))
    ap.add_argument("--spp", type=int, default=128)
    ap.add_argument("--size", nargs=2, type=int, default=[1280, 720], metavar=("W", "H"))
    ap.add_argument("--no-place", action="store_true", help="render what is on the server already")
    ap.add_argument("--no-render", action="store_true", help="only place the plot")
    a = ap.parse_args(argv)
    wanted = [s.strip() for s in a.shots.split(",") if s.strip()]
    bad = [s for s in wanted if s not in ALL_SHOTS]
    if bad:
        ap.error(f"unknown shot(s) {bad}; use {','.join(ALL_SHOTS)}")
    p = paths()
    plan = plot_plan.plan(a.houses, a.design, tuple(a.at))
    for problem in plan.problems:
        print(f"  {problem}")
    if not a.no_place:
        place(plan, ensure_server(p))
    if a.no_render:
        return
    cams = plot_plan.shots(plan)
    os.makedirs(p["shots"], exist_ok=True)
    for shot in wanted:
        if shot not in cams:
            print(f"  {a.design} has no '{shot}' shot (no points.door?); skipped")
            continue
        name = f"{a.design}_{shot}"
        started = time.time()
        png = render(name, scene(name, p, cams[shot], plan.box, a.spp, *a.size), p)
        out = os.path.join(p["shots"], name + ".png")
        shutil.copyfile(png, out)
        print(f"{out} ({time.time() - started:.0f} s)")


if __name__ == "__main__":
    main()
