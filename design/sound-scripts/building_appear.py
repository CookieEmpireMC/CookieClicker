"""building_appear: a new building pops up on the plot (~0.4 s). -> sounds/building/appear_{1..3}.ogg

A soft wooden "plop" (pitch-dropping sine + sub + a pinch of crumb grains) followed by a small
warm bell "ding" (one note, detuned twin). Three variants differ in ding note (a pentatonic
step), plop pitch and seed so it stays pleasant when it plays often. Mono (world position).
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "sound-tools"))
import synth  # noqa: E402

SEED = 7301
LENGTH_S = 0.390
PLOP_START_HZ = (420.0, 390.0, 450.0)
PLOP_END_HZ = (190.0, 175.0, 205.0)
PLOP_TAU_S = 0.014
PLOP_DECAY_S = 0.040
SUB_GAIN = 0.35
CRUMB_GRAINS = 2
CRUMB_GAIN = 0.20
DING_HZ = (880.0, 988.0, 1175.0)         # A5, B5, D6 (A major pentatonic-ish)
DING_AT_S = 0.055
DING_DECAY_S = 0.11
DING_GAIN = 0.38
DING_PARTIALS = ((1.0, 1.0), (2.0, 0.40), (2.98, 0.15), (4.2, 0.06))
DETUNE = 1.0036
LP_HZ = 6500.0
FADE_OUT_MS = 60.0


def make(i: int) -> np.ndarray:
    rng = np.random.default_rng(SEED + i)
    t = synth.t_axis(LENGTH_S)
    x = synth.sine_sweep(t, PLOP_START_HZ[i], PLOP_END_HZ[i], PLOP_TAU_S) * synth.exp_env(t, PLOP_DECAY_S)
    x += SUB_GAIN * np.sin(2 * np.pi * PLOP_END_HZ[i] * 0.5 * t) * synth.exp_env(t, PLOP_DECAY_S * 0.8)
    for at in [0.0] + list(0.01 * rng.random(CRUMB_GRAINS - 1)):
        g = synth.crunch_grain(rng, rng.uniform(1.0, 2.5), 1200.0, 3500.0)
        synth.place(x, g / (np.max(np.abs(g)) + 1e-12), at, CRUMB_GAIN)
    f = DING_HZ[i]
    note = synth.bell(t, f, DING_DECAY_S, DING_PARTIALS) + 0.7 * synth.bell(t, f * DETUNE, DING_DECAY_S, DING_PARTIALS)
    synth.place(x, note, DING_AT_S, DING_GAIN)
    return synth.finish(x, fade_out_ms=FADE_OUT_MS, lp_cutoff=LP_HZ)


if __name__ == "__main__":
    for i in range(3):
        synth.write(make(i), f"building/appear_{i + 1}")
