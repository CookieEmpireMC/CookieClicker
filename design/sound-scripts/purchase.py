"""purchase: bakery-flavoured "ka-ching". -> sounds/ui/purchase.ogg

Not a cash register: "ka" is a warm wooden counter knock with a pinch of crumb crunch,
"ching" is a little bakery shop-door bell struck twice, a fifth apart (G5 then D6),
with a slightly detuned second voice so it shimmers like a real small bell.
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "sound-tools"))
import synth  # noqa: E402

SEED = 4242
LENGTH_S = 0.380
# "ka": counter knock
KNOCK_START_HZ = 460.0
KNOCK_END_HZ = 210.0
KNOCK_TAU_S = 0.010
KNOCK_DECAY_S = 0.018
KNOCK_GAIN = 1.0
CRUMB_GRAINS = 3
CRUMB_GAIN = 0.35
# "ching": shop-door bell
BELL_NOTES_HZ = [784.0, 1174.7]          # G5, D6
BELL_TIMES_S = [0.040, 0.105]
BELL_DECAY_S = [0.070, 0.100]
BELL_GAIN = 0.42
BELL_PARTIALS = ((1.0, 1.0), (2.0, 0.45), (2.98, 0.18), (4.2, 0.08), (5.4, 0.04))
BELL_DETUNE = 1.0035
BRIGHTNESS_LP_HZ = 7500.0
FADE_OUT_MS = 40.0


def make() -> np.ndarray:
    rng = np.random.default_rng(SEED)
    t = synth.t_axis(LENGTH_S)
    x = KNOCK_GAIN * synth.sine_sweep(t, KNOCK_START_HZ, KNOCK_END_HZ, KNOCK_TAU_S) * synth.exp_env(t, KNOCK_DECAY_S)
    for at in [0.0] + list(0.012 * rng.random(CRUMB_GRAINS - 1)):
        grain = synth.crunch_grain(rng, rng.uniform(1.0, 2.5), 1500.0, 4500.0)
        synth.place(x, grain / (np.max(np.abs(grain)) + 1e-12), at, CRUMB_GAIN)
    for f, at, decay in zip(BELL_NOTES_HZ, BELL_TIMES_S, BELL_DECAY_S):
        tb = synth.t_axis(LENGTH_S)
        note = synth.bell(tb, f, decay, BELL_PARTIALS) + 0.7 * synth.bell(tb, f * BELL_DETUNE, decay, BELL_PARTIALS)
        synth.place(x, note, at, BELL_GAIN)
    return synth.finish(x, fade_out_ms=FADE_OUT_MS, lp_cutoff=BRIGHTNESS_LP_HZ)


if __name__ == "__main__":
    synth.write(make(), "ui/purchase")
