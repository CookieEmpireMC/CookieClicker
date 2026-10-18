"""cookie_click: a crunchy, satisfying cookie tap/bite. 4 variants -> sounds/ui/cookie_click_<n>.ogg

Recipe: a soft pitch-dropping "tap" (the finger on the cookie) + a cluster of tiny band-passed
noise grains (the crumbs cracking), front-loaded so the crunch reads as one bite, + a quiet sub.
Variants differ by seed (grain timing/colour), pitch and decay; the plugin adds random pitch on top.
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "sound-tools"))
import synth  # noqa: E402

LENGTH_S = 0.120
TAP_START_HZ = 640.0      # tap pitch at the attack
TAP_END_HZ = 290.0        # tap pitch it drops to
TAP_TAU_S = 0.014         # pitch-drop time constant
TAP_DECAY_S = 0.020
TAP_GAIN = 0.55
SUB_RATIO = 0.5           # sub sine relative to TAP_END_HZ
SUB_GAIN = 0.16
GRAINS = (6, 9)           # min/max crumb grains per bite
GRAIN_SPREAD_S = 0.045    # grains land within this window (front-loaded)
GRAIN_MS = (1.2, 3.5)
GRAIN_BAND_HZ = (1100.0, 4800.0)
CRUNCH_GAIN = 0.85
CRUNCH_DRIVE = 2.2       # tanh soft-clip on the crumb layer: tames single loud grains (lower crest factor)
BRIGHTNESS_LP_HZ = 7000.0

# (seed, pitch factor, decay factor, crunch factor)
VARIANTS = [
    (101, 1.00, 1.00, 1.00),
    (202, 0.94, 1.08, 1.10),
    (303, 1.06, 0.92, 0.92),
    (404, 0.98, 1.04, 1.05),
]


def make(seed: int, pitch: float, decay: float, crunch: float) -> np.ndarray:
    rng = np.random.default_rng(seed)
    t = synth.t_axis(LENGTH_S)
    x = TAP_GAIN * synth.sine_sweep(t, TAP_START_HZ * pitch, TAP_END_HZ * pitch, TAP_TAU_S) \
        * synth.exp_env(t, TAP_DECAY_S * decay)
    x += SUB_GAIN * np.sin(2 * np.pi * TAP_END_HZ * pitch * SUB_RATIO * t) * synth.exp_env(t, TAP_DECAY_S * 1.2, 0.002)

    crumbs = np.zeros_like(t)
    n = rng.integers(GRAINS[0], GRAINS[1] + 1)
    times = np.sort(GRAIN_SPREAD_S * rng.random(n) ** 1.8)   # front-loaded
    times[0] = 0.0                                             # first crack on the attack
    for at in times:
        centre = rng.uniform(*GRAIN_BAND_HZ)
        grain = synth.crunch_grain(rng, rng.uniform(*GRAIN_MS), centre * 0.6, min(centre * 1.6, 9000))
        grain /= np.max(np.abs(grain)) + 1e-12
        level = CRUNCH_GAIN * crunch * (0.35 + 0.65 * rng.random()) * np.exp(-at / 0.03)
        synth.place(crumbs, grain, at, level)
    x += np.tanh(CRUNCH_DRIVE * crumbs) / CRUNCH_DRIVE * 1.6
    return synth.finish(x, lp_cutoff=BRIGHTNESS_LP_HZ)


if __name__ == "__main__":
    for i, (seed, pitch, decay, crunch) in enumerate(VARIANTS, start=1):
        synth.write(make(seed, pitch, decay, crunch), f"ui/cookie_click_{i}")
