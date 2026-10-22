"""basket_tier_up: the cookie basket reaches a higher tier (~1.2 s). -> sounds/ui/basket_tier_up.ogg

A soft rising whoosh (band-passed noise, centre sweeping up), a quick major arpeggio of warm
bells (C5 E5 G5 C6) that lands on a ringing E6/G6/C7 sparkle chord, and a few coin-shimmer
grains on top. Same family as the crit sparkle / purchase ding, bigger and longer.
Mono (positional-safe). Original synthesis.
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "sound-tools"))
import synth  # noqa: E402

SEED = 4242
LENGTH_S = 1.2
TARGET_LUFS = -16.0

C5, E5, G5, C6 = 523.25, 659.26, 783.99, 1046.5
E6, G6, C7 = 1318.5, 1568.0, 2093.0

ARPEGGIO = [(0.00, C5), (0.07, E5), (0.14, G5), (0.21, C6)]
LAND_AT = 0.30
LAND_NOTES = [C6, E6, G6, C7]
ARP_PARTIALS = ((1.0, 1.0), (2.0, 0.4), (3.0, 0.15), (4.0, 0.06))
LAND_PARTIALS = ((1.0, 1.0), (2.0, 0.3), (3.01, 0.1))
ARP_DECAY_S = 0.22
LAND_DECAY_S = 0.45
DETUNE = 1.0038
ARP_GAIN = 0.40
LAND_GAIN = 0.30
WHOOSH_GAIN = 0.35
WHOOSH_LEN_S = 0.45
WHOOSH_LO, WHOOSH_HI = (500.0, 1800.0), (2500.0, 5500.0)   # band edges at start -> end
SHIMMER_GRAINS = 10
SHIMMER_GAIN = 0.12
LIMIT_DRIVE = 1.8
BRIGHTNESS_LP_HZ = 7000.0
FADE_OUT_MS = 220.0


def whoosh(rng: np.random.Generator) -> np.ndarray:
    """Noise whose band-pass window slides up; made by crossfading a few fixed bands."""
    n = int(WHOOSH_LEN_S * synth.SR)
    noise = rng.standard_normal(n)
    out = np.zeros(n)
    steps = 6
    t = np.linspace(0, 1, n)
    for i in range(steps):
        k = i / (steps - 1)
        lo = WHOOSH_LO[0] + (WHOOSH_HI[0] - WHOOSH_LO[0]) * k
        hi = WHOOSH_LO[1] + (WHOOSH_HI[1] - WHOOSH_LO[1]) * k
        w = np.exp(-((t - k) ** 2) / (2 * 0.12 ** 2))
        out += synth.bandpass(noise, lo, hi) * w
    env = np.sin(np.pi * np.clip(t, 0, 1)) ** 1.5
    return out * env / (np.max(np.abs(out)) + 1e-12)


def bell2(f: float, decay: float, partials) -> np.ndarray:
    tb = synth.t_axis(1.1)
    return synth.bell(tb, f, decay, partials) + 0.65 * synth.bell(tb, f * DETUNE, decay, partials)


def make() -> np.ndarray:
    rng = np.random.default_rng(SEED)
    x = np.zeros(int(round(LENGTH_S * synth.SR)))
    synth.place(x, whoosh(rng), 0.0, WHOOSH_GAIN)
    for at, f in ARPEGGIO:
        synth.place(x, bell2(f, ARP_DECAY_S, ARP_PARTIALS), at, ARP_GAIN)
    for f in LAND_NOTES:
        synth.place(x, bell2(f, LAND_DECAY_S, LAND_PARTIALS), LAND_AT, LAND_GAIN)
    shimmer = np.zeros_like(x)
    for at in LAND_AT + np.sort(rng.uniform(0.0, 0.35, SHIMMER_GRAINS)):
        grain = synth.crunch_grain(rng, rng.uniform(1.0, 2.5), 3500.0, 6500.0)
        synth.place(shimmer, grain / (np.max(np.abs(grain)) + 1e-12), at, rng.uniform(0.5, 1.0))
    x += SHIMMER_GAIN * np.tanh(2.2 * shimmer)
    x = synth.soft_limit(x, LIMIT_DRIVE)
    x = synth.finish(x, fade_out_ms=FADE_OUT_MS, lp_cutoff=BRIGHTNESS_LP_HZ)
    return synth.gain_to_lufs(x, TARGET_LUFS)


if __name__ == "__main__":
    synth.write(make(), "ui/basket_tier_up")
