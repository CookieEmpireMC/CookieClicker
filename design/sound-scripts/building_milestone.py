"""building_milestone: a building type upgrades its look at 25/100 owned (~0.95 s). -> sounds/building/milestone.ogg

Celebratory but smaller than basket_tier_up: a wooden thud, then a quick warm G-major arpeggio
(G5 B5 D6) landing on a ringing G5/D6/G6/B6 chord with a few coin-shimmer grains and a soft
horn-like pad under the landing. No whoosh. Mono (world position).
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "sound-tools"))
import synth  # noqa: E402

SEED = 9917
LENGTH_S = 0.95
TARGET_LUFS = -16.0
THUD_START_HZ, THUD_END_HZ, THUD_TAU_S, THUD_DECAY_S, THUD_GAIN = 360.0, 170.0, 0.014, 0.05, 0.9
G5, B5, D6, G6, B6 = 784.0, 987.8, 1174.7, 1568.0, 1975.5
ARPEGGIO = [(0.04, G5), (0.11, B5), (0.18, D6)]
LAND_AT = 0.26
LAND_NOTES = [G5, D6, G6, B6]
ARP_PARTIALS = ((1.0, 1.0), (2.0, 0.4), (3.0, 0.15), (4.0, 0.06))
LAND_PARTIALS = ((1.0, 1.0), (2.0, 0.3), (3.01, 0.1))
ARP_DECAY_S, LAND_DECAY_S = 0.18, 0.35
DETUNE = 1.0038
ARP_GAIN, LAND_GAIN = 0.40, 0.28
PAD_GAIN = 0.12
SHIMMER_GRAINS, SHIMMER_GAIN = 6, 0.10
LIMIT_DRIVE = 1.8
LP_HZ = 6500.0
FADE_OUT_MS = 200.0


def bell2(f, decay, partials):
    tb = synth.t_axis(1.0)
    return synth.bell(tb, f, decay, partials) + 0.65 * synth.bell(tb, f * DETUNE, decay, partials)


def make() -> np.ndarray:
    rng = np.random.default_rng(SEED)
    n = int(round(LENGTH_S * synth.SR))
    t = synth.t_axis(LENGTH_S)
    x = THUD_GAIN * synth.sine_sweep(t, THUD_START_HZ, THUD_END_HZ, THUD_TAU_S) * synth.exp_env(t, THUD_DECAY_S)
    for at, f in ARPEGGIO:
        synth.place(x, bell2(f, ARP_DECAY_S, ARP_PARTIALS), at, ARP_GAIN)
    for f in LAND_NOTES:
        synth.place(x, bell2(f, LAND_DECAY_S, LAND_PARTIALS), LAND_AT, LAND_GAIN)
    # soft pad under the landing: harmonics of G4 at 1/n^1.5, slow attack, low-passed
    pad = np.zeros(n)
    for h in range(1, 6):
        pad += np.sin(2 * np.pi * 392.0 * h * t) / h ** 1.5
    env = np.clip((t - LAND_AT) / 0.035, 0, 1) * np.exp(-np.clip(t - LAND_AT, 0, None) / 0.30)
    x += PAD_GAIN * synth.lowpass(pad, 2600.0) * env
    shimmer = np.zeros(n)
    for at in LAND_AT + np.sort(rng.uniform(0.0, 0.25, SHIMMER_GRAINS)):
        g = synth.crunch_grain(rng, rng.uniform(1.0, 2.5), 3500.0, 6000.0)
        synth.place(shimmer, g / (np.max(np.abs(g)) + 1e-12), at, rng.uniform(0.5, 1.0))
    x += SHIMMER_GAIN * np.tanh(2.2 * shimmer)
    x = synth.soft_limit(x, LIMIT_DRIVE)
    x = synth.finish(x, fade_out_ms=FADE_OUT_MS, lp_cutoff=LP_HZ)
    return synth.gain_to_lufs(x, TARGET_LUFS)


if __name__ == "__main__":
    synth.write(make(), "building/milestone")
