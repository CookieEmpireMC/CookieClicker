"""Render a waveform + spectrogram PNG for .ogg files to design/sound-previews/ (git-ignored).

Usage (from the repo root):
  mise exec -- python design/sound-tools/render_previews.py [file-or-folder ...]
Without arguments it renders every .ogg under resourcepack/.
One PNG per input file, plus an `_overview.png` stacking all waveforms on a shared time axis.
"""
import sys
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import soundfile as sf

DEFAULT_ROOT = Path("resourcepack")
OUT_DIR = Path("design/sound-previews")


def collect(args: list[str]) -> list[Path]:
    paths = [Path(a) for a in args] or [DEFAULT_ROOT]
    files = []
    for p in paths:
        files += sorted(p.rglob("*.ogg")) if p.is_dir() else [p]
    return files


def render(path: Path) -> Path:
    data, rate = sf.read(str(path), always_2d=True)
    mono = data.mean(axis=1)
    t_ms = np.arange(len(mono)) / rate * 1000
    peak = np.max(np.abs(data))
    fig, (ax_w, ax_s) = plt.subplots(2, 1, figsize=(9, 6), sharex=True)
    ax_w.plot(t_ms, mono, lw=0.6, color="tab:blue")
    for level in (10 ** (-1 / 20), -(10 ** (-1 / 20))):
        ax_w.axhline(level, color="red", lw=0.6, ls="--")
    ax_w.axhline(np.mean(mono), color="orange", lw=0.6)
    ax_w.set_ylim(-1.05, 1.05)
    ax_w.set_ylabel("amplitude")
    ax_w.set_title(f"{path.name}  {len(mono) / rate * 1000:.0f} ms  {data.shape[1]}ch  {rate} Hz  "
                   f"peak {20 * np.log10(max(peak, 1e-12)):.1f} dBFS  (red = -1 dBFS, orange = mean)")
    nfft = 256
    ax_s.specgram(mono, NFFT=nfft, Fs=rate, noverlap=nfft - 32, cmap="magma", vmin=-100, scale="dB",
                  xextent=(0, t_ms[-1]))
    ax_s.set_ylim(0, rate / 2)
    ax_s.set_ylabel("Hz")
    ax_s.set_xlabel("ms")
    fig.tight_layout()
    rel = path.relative_to(DEFAULT_ROOT) if path.is_relative_to(DEFAULT_ROOT) else Path(path.name)
    out = OUT_DIR / rel.with_suffix(".png")
    out.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(out, dpi=90)
    plt.close(fig)
    return out


def overview(files: list[Path]) -> Path:
    fig, axes = plt.subplots(len(files), 1, figsize=(9, 1.6 * len(files)), sharex=True, squeeze=False)
    for ax, path in zip(axes[:, 0], files):
        data, rate = sf.read(str(path), always_2d=True)
        mono = data.mean(axis=1)
        ax.plot(np.arange(len(mono)) / rate * 1000, mono, lw=0.6)
        ax.set_ylim(-1.05, 1.05)
        ax.set_ylabel(path.stem, rotation=0, ha="right", fontsize=8)
    axes[-1, 0].set_xlabel("ms")
    fig.tight_layout()
    out = OUT_DIR / "_overview.png"
    fig.savefig(out, dpi=90)
    plt.close(fig)
    return out


def main() -> int:
    files = collect(sys.argv[1:])
    if not files:
        print("No .ogg files found.")
        return 0
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for path in files:
        print(f"  {render(path)}")
    print(f"  {overview(files)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
