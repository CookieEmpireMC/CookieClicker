"""cant_afford: soft, non-annoying "nope". -> sounds/ui/cant_afford.ogg

Two muted low thuds, the second lower ("uh-uh"), with a quiet low-passed buzz underneath.
Dark (energy below ~2 kHz, but pitched above 170 Hz so laptop speakers still play it),
short and a little quieter than the rest of the set so
repeated misclicks never grate.
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "sound-tools"))
import synth  # noqa: E402

LENGTH_S = 0.200
THUDS = [  # (start s, start Hz, end Hz, decay s, gain)
    (0.000, 340.0, 210.0, 0.030, 1.0),
    (0.080, 285.0, 175.0, 0.040, 0.85),
]
THUD_TAU_S = 0.012
THUD_ATTACK_S = 0.004   # slow enough that the second thud has no click
BUZZ_HZ = 140.0
BUZZ_GAIN = 0.22
BUZZ_DECAY_S = 0.060
BUZZ_LP_HZ = 1100.0
BRIGHTNESS_LP_HZ = 2500.0
TARGET_RMS_DBFS = -19.0   # a touch softer than the -18 set target
FADE_OUT_MS = 25.0


def make() -> np.ndarray:
    t_total = synth.t_axis(LENGTH_S)
    x = np.zeros_like(t_total)
    for at, f0, f1, decay, gain in THUDS:
        t = synth.t_axis(LENGTH_S - at)
        synth.place(x, synth.sine_sweep(t, f0, f1, THUD_TAU_S) * synth.exp_env(t, decay, THUD_ATTACK_S), at, gain)
    # square-ish buzz (odd harmonics), low-passed so it's a muted "bzz", not a buzzer
    phase = 2 * np.pi * BUZZ_HZ * t_total
    buzz = np.sin(phase) + 0.33 * np.sin(3 * phase) + 0.2 * np.sin(5 * phase)
    x += BUZZ_GAIN * synth.lowpass(buzz, BUZZ_LP_HZ) * synth.exp_env(t_total, BUZZ_DECAY_S, 0.004)
    return synth.finish(x, fade_out_ms=FADE_OUT_MS, lp_cutoff=BRIGHTNESS_LP_HZ, target_rms_dbfs=TARGET_RMS_DBFS)


if __name__ == "__main__":
    synth.write(make(), "ui/cant_afford")
