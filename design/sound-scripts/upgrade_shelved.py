"""upgrade_shelved: soft "clink" as an upgrade icon lands on the shelf. -> sounds/ui/upgrade_shelved.ogg

A small wooden shelf "tok" (the landing) plus a short glassy-warm bell, E6 with a soft
fifth above (B6) a few ms later. Quiet and cosy, not shrill: the bell is low-passed and decays fast.
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "sound-tools"))
import synth  # noqa: E402

LENGTH_S = 0.300
TOK_START_HZ = 520.0
TOK_END_HZ = 260.0
TOK_TAU_S = 0.008
TOK_DECAY_S = 0.014
TOK_GAIN = 0.8
BELL_NOTES_HZ = [1318.5, 1975.5]         # E6, B6
BELL_TIMES_S = [0.004, 0.030]
BELL_DECAY_S = [0.075, 0.090]
BELL_GAIN = [0.55, 0.30]
BELL_PARTIALS = ((1.0, 1.0), (2.0, 0.25), (3.01, 0.08))
BELL_DETUNE = 1.004
BRIGHTNESS_LP_HZ = 5500.0
FADE_OUT_MS = 40.0


def make() -> np.ndarray:
    t = synth.t_axis(LENGTH_S)
    x = TOK_GAIN * synth.sine_sweep(t, TOK_START_HZ, TOK_END_HZ, TOK_TAU_S) * synth.exp_env(t, TOK_DECAY_S)
    for f, at, decay, g in zip(BELL_NOTES_HZ, BELL_TIMES_S, BELL_DECAY_S, BELL_GAIN):
        note = synth.bell(t, f, decay, BELL_PARTIALS) + 0.6 * synth.bell(t, f * BELL_DETUNE, decay, BELL_PARTIALS)
        synth.place(x, note, at, g)
    return synth.finish(x, fade_out_ms=FADE_OUT_MS, lp_cutoff=BRIGHTNESS_LP_HZ)


if __name__ == "__main__":
    synth.write(make(), "ui/upgrade_shelved")
