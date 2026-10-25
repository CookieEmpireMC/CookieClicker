"""recipe_book: Recipe Book sounds. Original synthesis, mono.

-> sounds/recipe/unlock.ogg  warm short fanfare (~1.1 s): plucked C-E-G-C pickup into a ringing C major chord
-> sounds/recipe/open.ogg    soft page turn (~260 ms): swishy band-passed noise sweep plus a small paper flick
-> sounds/recipe/buy.ogg     page flip then a bright two-note chime (~380 ms)
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "sound-tools"))
import synth  # noqa: E402

SEED = 5150
C5, E5, G5, C6, E6, G6, C7 = 523.25, 659.26, 783.99, 1046.5, 1318.5, 1568.0, 2093.0
C4 = 261.63

PLUCK_PARTIALS = ((1.0, 1.0), (2.0, 0.45), (3.0, 0.18), (4.0, 0.07))
SPARKLE_PARTIALS = ((1.0, 1.0), (2.0, 0.3), (3.01, 0.1))
PICKUP = [(0.00, C5), (0.08, E5), (0.16, G5)]
CHORD_AT, CHORD = 0.26, [C5, E5, G5, C6]
UNLOCK_LEN_S = 1.1
UNLOCK_LUFS = -16.0
OPEN_LEN_S = 0.26
BUY_LEN_S = 0.38
LP_HZ = 7000.0


def pluck(f: float, decay: float, length: float = 0.9) -> np.ndarray:
    t = synth.t_axis(length)
    return synth.bell(t, f, decay, PLUCK_PARTIALS, attack=0.003) + 0.6 * synth.bell(t, f * 1.003, decay, PLUCK_PARTIALS, attack=0.003)


def sparkle(f: float, decay: float = 0.16, length: float = 0.6) -> np.ndarray:
    t = synth.t_axis(length)
    return synth.bell(t, f, decay, SPARKLE_PARTIALS) + 0.65 * synth.bell(t, f * 1.0038, decay, SPARKLE_PARTIALS)


def swish(rng: np.random.Generator, length: float, f_from: float, f_to: float, peak_at: float = 0.4) -> np.ndarray:
    """Paper swish: noise through a band-pass whose centre glides, with a soft bump envelope."""
    n = int(length * synth.SR)
    t = np.linspace(0, 1, n)
    noise = rng.standard_normal(n)
    out = np.zeros(n)
    steps = 6
    for i in range(steps):
        a, b = int(i * n / steps), int((i + 1) * n / steps)
        fc = f_from * (f_to / f_from) ** ((i + 0.5) / steps)
        band = synth.bandpass(noise, fc * 0.7, fc * 1.4)
        w = np.zeros(n)
        w[a:b] = 1.0
        w = np.convolve(w, np.hanning(int(n / steps) | 1), mode="same")
        out += band * w
    env = np.sin(np.pi * t ** (np.log(0.5) / np.log(peak_at))) ** 1.5
    return out * env


def flick(rng: np.random.Generator) -> np.ndarray:
    g = synth.crunch_grain(rng, 5.0, 2000.0, 5500.0)
    return g / (np.max(np.abs(g)) + 1e-12)


def make_unlock() -> np.ndarray:
    rng = np.random.default_rng(SEED)
    x = np.zeros(int(UNLOCK_LEN_S * synth.SR))
    for at, f in PICKUP:
        synth.place(x, pluck(f, 0.14), at, 0.45)
    for f in CHORD:
        synth.place(x, pluck(f, 0.4), CHORD_AT, 0.32)
    synth.place(x, pluck(C4, 0.45), CHORD_AT, 0.4)
    for at, f in [(0.38, E6), (0.48, G6), (0.58, C7)]:
        synth.place(x, sparkle(f), at, 0.16)
    x = synth.soft_limit(x, 2.0)
    x = synth.finish(x, fade_out_ms=250.0, lp_cutoff=LP_HZ)
    return synth.gain_to_lufs(x, UNLOCK_LUFS)


def make_open() -> np.ndarray:
    rng = np.random.default_rng(SEED + 1)
    x = np.zeros(int(OPEN_LEN_S * synth.SR))
    synth.place(x, swish(rng, 0.20, 1400.0, 3800.0), 0.0, 1.0)
    synth.place(x, flick(rng), 0.015, 0.25)
    synth.place(x, flick(rng), 0.19, 0.18)
    x = synth.finish(x, fade_out_ms=40.0, lp_cutoff=6500.0, target_rms_dbfs=-19.5)
    return x


def make_buy() -> np.ndarray:
    rng = np.random.default_rng(SEED + 2)
    x = np.zeros(int(BUY_LEN_S * synth.SR))
    synth.place(x, swish(rng, 0.11, 1800.0, 3600.0), 0.0, 0.55)
    synth.place(x, flick(rng), 0.0, 0.2)
    synth.place(x, sparkle(G6, 0.12, 0.35), 0.07, 0.5)
    synth.place(x, sparkle(C7, 0.14, 0.3), 0.14, 0.55)
    x = synth.finish(x, fade_out_ms=60.0, lp_cutoff=LP_HZ)
    return x


if __name__ == "__main__":
    synth.write(make_unlock(), "recipe/unlock")
    synth.write(make_open(), "recipe/open")
    synth.write(make_buy(), "recipe/buy")
