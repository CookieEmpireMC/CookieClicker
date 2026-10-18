"""Plot every target/vfx-points/<effect>.csv (written by the EffectsPreviewExport JUnit test from the real Java
shape code) to design/vfx-previews/<effect>.png: a 3D view, a top view (x/z) and a side view (x/y), coloured by tick.

Run via `mise run vfx-previews` (which runs the export first). Minecraft axes: y is up.
"""
import csv
import sys
from collections import defaultdict
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
from matplotlib import cm  # noqa: E402

POINTS = Path("target/vfx-points")
OUT = Path("design/vfx-previews")
MARKERS = ["o", "*", "^", "s", "D", "P"]


def load(path: Path):
    layers = defaultdict(lambda: {"t": [], "x": [], "y": [], "z": []})
    with path.open(newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            layer = layers[row["layer"]]
            layer["t"].append(int(row["tick"]))
            for k in "xyz":
                layer[k].append(float(row[k]))
    return layers


def plot(path: Path) -> Path:
    layers = load(path)
    max_tick = max((max(l["t"]) for l in layers.values() if l["t"]), default=0)
    total = sum(len(l["t"]) for l in layers.values())
    norm = matplotlib.colors.Normalize(vmin=0, vmax=max(max_tick, 1))
    cmap = cm.viridis

    fig = plt.figure(figsize=(16, 5.6), layout="constrained")
    ax3d = fig.add_subplot(1, 3, 1, projection="3d")
    ax_top = fig.add_subplot(1, 3, 2)
    ax_side = fig.add_subplot(1, 3, 3)

    for i, (name, l) in enumerate(sorted(layers.items())):
        marker = MARKERS[i % len(MARKERS)]
        colors = cmap(norm(l["t"]))
        label = f"{name} ({len(l['t'])} pts)"
        # Minecraft y is up: plot it on matplotlib's z axis in 3D
        ax3d.scatter(l["x"], l["z"], l["y"], c=colors, marker=marker, s=22, depthshade=False, label=label)
        ax_top.scatter(l["x"], l["z"], c=colors, marker=marker, s=26, label=label)
        ax_side.scatter(l["x"], l["y"], c=colors, marker=marker, s=26, label=label)

    # one block for scale
    for ax in (ax_top, ax_side):
        ax.axhline(0, color="#bbb", lw=0.6, zorder=0)
        ax.axvline(0, color="#bbb", lw=0.6, zorder=0)
        ax.set_aspect("equal", adjustable="datalim")
        ax.grid(True, alpha=0.25)
    ax3d.set_xlabel("x")
    ax3d.set_ylabel("z")
    ax3d.set_zlabel("y (up)")
    ax3d.set_box_aspect((1, 1, 1))
    lim = 1.6
    ax3d.set_xlim(-lim, lim)
    ax3d.set_ylim(-lim, lim)
    ax3d.set_zlim(0, 2 * lim)
    ax3d.set_title("3D")
    ax_top.set_title("top (x / z)")
    ax_top.set_xlabel("x")
    ax_top.set_ylabel("z")
    ax_side.set_title("side (x / y)")
    ax_side.set_xlabel("x")
    ax_side.set_ylabel("y (up)")
    ax_top.legend(loc="upper left", fontsize=7, framealpha=0.8)

    sm = cm.ScalarMappable(norm=norm, cmap=cmap)
    fig.colorbar(sm, ax=[ax3d, ax_top, ax_side], shrink=0.8, label="tick")
    fig.suptitle(f"{path.stem}: {total} particles over {max_tick + 1} tick(s)")
    OUT.mkdir(parents=True, exist_ok=True)
    out = OUT / f"{path.stem}.png"
    fig.savefig(out, dpi=110)
    plt.close(fig)
    return out


def main() -> int:
    csvs = sorted(POINTS.glob("*.csv"))
    if not csvs:
        print(f"No CSVs in {POINTS}; run the *PreviewExport test first (mise run vfx-previews)")
        return 1
    for path in csvs:
        print(f"Wrote {plot(path)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
