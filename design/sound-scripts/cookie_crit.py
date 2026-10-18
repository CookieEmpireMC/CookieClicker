"""cookie_crit: the "crispy crit". A bigger, crispier crunch plus a rising golden sparkle. -> sounds/ui/cookie_crit.ogg

Recipe: the cookie_click family (pitch-dropping tap + crumb grains) but deeper and with more,
wider-spread grains, then a fast rising major arpeggio of soft bells (C6-E6-G6-C7) that rings
out briefly. Clearly more rewarding than a click without becoming a fanfare (crits happen ~5 %).
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "sound-tools"))
import synth  # noqa: E402

SEED = 777
LENGTH_S = 0.390
# crunch
TAP_START_HZ = 560.0
TAP_END_HZ = 230.0
TAP_TAU_S = 0.016
TAP_DECAY_S = 0.032
TAP_GAIN = 0.55
SUB_GAIN = 0.2
GRAINS = 14
GRAIN_SPREAD_S = 0.075
GRAIN_MS = (1.2, 4.0)
GRAIN_BAND_HZ = (1200.0, 5200.0)
CRUNCH_GAIN = 0.9
CRUNCH_DRIVE = 2.2
# sparkle
SPARKLE_NOTES_HZ = [1046.5, 1318.5, 1568.0, 2093.0]   # C6 E6 G6 C7
SPARKLE_START_S = 0.030
SPARKLE_STEP_S = 0.034
SPARKLE_DECAY_S = 0.085
SPARKLE_LAST_DECAY_S = 0.110                         # top note rings a little longer
SPARKLE_GAIN = 0.32
SPARKLE_DETUNE = 1.004                               # second, slightly detuned voice = shimmer
SPARKLE_PARTIALS = ((1.0, 1.0), (2.0, 0.3), (3.01, 0.08))  # fewer high partials: no harsh 8-9 kHz
BRIGHTNESS_LP_HZ = 7500.0
FADE_OUT_MS = 40.0


def make() -> np.ndarray:
    rng = np.random.default_rng(SEED)
    t = synth.t_axis(LENGTH_S)
    x = TAP_GAIN * synth.sine_sweep(t, TAP_START_HZ, TAP_END_HZ, TAP_TAU_S) * synth.exp_env(t, TAP_DECAY_S)
    x += SUB_GAIN * np.sin(2 * np.pi * TAP_END_HZ * 0.5 * t) * synth.exp_env(t, TAP_DECAY_S, 0.002)

    crumbs = np.zeros_like(t)
    times = np.sort(GRAIN_SPREAD_S * rng.random(GRAINS) ** 1.5)
    times[0] = 0.0
    for at in times:
        centre = rng.uniform(*GRAIN_BAND_HZ)
        grain = synth.crunch_grain(rng, rng.uniform(*GRAIN_MS), centre * 0.6, min(centre * 1.6, 9500))
        grain /= np.max(np.abs(grain)) + 1e-12
        synth.place(crumbs, grain, at, CRUNCH_GAIN * (0.4 + 0.6 * rng.random()) * np.exp(-at / 0.05))
    x += np.tanh(CRUNCH_DRIVE * crumbs) / CRUNCH_DRIVE * 1.6

    for i, f in enumerate(SPARKLE_NOTES_HZ):
        last = i == len(SPARKLE_NOTES_HZ) - 1
        decay = SPARKLE_LAST_DECAY_S if last else SPARKLE_DECAY_S
        tb = synth.t_axis(LENGTH_S)
        note = synth.bell(tb, f, decay, SPARKLE_PARTIALS) + 0.6 * synth.bell(tb, f * SPARKLE_DETUNE, decay, SPARKLE_PARTIALS)
        synth.place(x, note, SPARKLE_START_S + i * SPARKLE_STEP_S, SPARKLE_GAIN * (0.8 + 0.1 * i))
    return synth.finish(x, fade_out_ms=FADE_OUT_MS, lp_cutoff=BRIGHTNESS_LP_HZ)


if __name__ == "__main__":
    synth.write(make(), "ui/cookie_crit")
