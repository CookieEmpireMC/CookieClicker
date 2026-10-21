"""bulk_toggle: light UI tick when cycling x1/x5/x10/Max. -> sounds/ui/bulk_toggle_1..3.ogg

A tiny dry wooden tick: short sine pop with a fast pitch drop and a single faint crumb grain.
Three variants (pitch +-8 %, grain seed) so repeated clicks don't machine-gun.
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "sound-tools"))
import synth  # noqa: E402

SEED = 777
LENGTH_S = 0.090
START_HZ = 900.0
END_HZ = 520.0
TAU_S = 0.007
DECAY_S = 0.016
VARIANT_PITCH = [1.0, 0.92, 1.08]
SUB_GAIN = 0.25
GRAIN_GAIN = 0.18
BRIGHTNESS_LP_HZ = 6000.0
FADE_OUT_MS = 20.0


def make(i: int) -> np.ndarray:
    rng = np.random.default_rng(SEED + i)
    p = VARIANT_PITCH[i]
    t = synth.t_axis(LENGTH_S)
    env = synth.exp_env(t, DECAY_S)
    x = synth.sine_sweep(t, START_HZ * p, END_HZ * p, TAU_S) * env
    x += SUB_GAIN * synth.sine_sweep(t, END_HZ * p / 2, END_HZ * p / 2, TAU_S) * env
    grain = synth.crunch_grain(rng, 1.5, 2000.0, 4500.0)
    synth.place(x, grain / (np.max(np.abs(grain)) + 1e-12), 0.0, GRAIN_GAIN)
    return synth.finish(x, fade_out_ms=FADE_OUT_MS, lp_cutoff=BRIGHTNESS_LP_HZ)


if __name__ == "__main__":
    for i in range(3):
        synth.write(make(i), f"ui/bulk_toggle_{i + 1}")
