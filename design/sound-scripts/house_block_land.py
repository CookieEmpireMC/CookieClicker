"""house_block_land: soft "thunk" of a block landing, with a little bounce. -> sounds/house/block_land_1..4.ogg

A round, low, pitch-dropping thud with a 2nd harmonic (so it carries on small speakers),
a short low-passed noise "puff", a pinch of dust grit, then a smaller, slightly higher
second thud for the bounce (variants 3/4 add a tiny third settle). Soft attack (2 ms) so
dozens in a row never feel like knocks on a door.
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "sound-tools"))
import synth  # noqa: E402

SEED = 5120
LENGTH_S = 0.230
BODY_START_HZ = 300.0
BODY_END_HZ = 175.0          # style guide: main pitch not below ~170 Hz
BODY_TAU_S = 0.012
BODY_DECAY_S = 0.032
HARM2_GAIN = 0.25
HARM2_DECAY_S = 0.025
PUFF_LP_HZ = 1200.0
PUFF_DECAY_S = 0.012
PUFF_GAIN = 0.45
GRIT_GRAINS = 2
GRIT_GAIN = 0.10
BOUNCE_PITCH = 1.12
BOUNCE_DECAY_S = 0.025
BOUNCE_GAIN_BOOST = 1.25    # bounce must read clearly after the shorter body
ATTACK_S = 0.002
BRIGHTNESS_LP_HZ = 5000.0
TARGET_RMS = -18.0
FADE_OUT_MS = 30.0

# variant: (pitch factor, bounce time s, bounce gain, settle (time, gain) or None)
VARIANTS = [
    (1.00, 0.085, 0.30, None),
    (0.94, 0.095, 0.26, None),
    (1.05, 0.080, 0.32, (0.130, 0.10)),
    (0.97, 0.090, 0.28, (0.142, 0.09)),
]


def thud(rng: np.random.Generator, pitch: float, decay: float, puff: float) -> np.ndarray:
    t = synth.t_axis(LENGTH_S)
    env = synth.exp_env(t, decay * rng.uniform(0.92, 1.08), attack=ATTACK_S)
    x = synth.sine_sweep(t, BODY_START_HZ * pitch, BODY_END_HZ * pitch, BODY_TAU_S) * env
    x += HARM2_GAIN * synth.sine_sweep(t, 2 * BODY_START_HZ * pitch, 2 * BODY_END_HZ * pitch, BODY_TAU_S) \
        * synth.exp_env(t, HARM2_DECAY_S, attack=ATTACK_S)
    noise = synth.lowpass(rng.standard_normal(len(t)), PUFF_LP_HZ, order=2)
    noise *= synth.exp_env(t, PUFF_DECAY_S, attack=ATTACK_S)
    x += puff * noise / (np.max(np.abs(noise)) + 1e-12)
    return x


def make(variant: int) -> np.ndarray:
    rng = np.random.default_rng(SEED + variant)
    pitch, bounce_at, bounce_gain, settle = VARIANTS[variant]
    x = thud(rng, pitch, BODY_DECAY_S, PUFF_GAIN)
    for _ in range(GRIT_GRAINS):
        grain = synth.crunch_grain(rng, rng.uniform(1.0, 3.0), 800.0, 2500.0)
        synth.place(x, grain / (np.max(np.abs(grain)) + 1e-12), rng.uniform(0.002, 0.020), GRIT_GAIN)
    synth.place(x, thud(rng, pitch * BOUNCE_PITCH, BOUNCE_DECAY_S, PUFF_GAIN * 0.5), bounce_at, bounce_gain * BOUNCE_GAIN_BOOST)
    if settle:
        synth.place(x, thud(rng, pitch * BOUNCE_PITCH * 1.05, BOUNCE_DECAY_S * 0.7, PUFF_GAIN * 0.3), *settle)
    return synth.finish(x, fade_out_ms=FADE_OUT_MS, lp_cutoff=BRIGHTNESS_LP_HZ, target_rms_dbfs=TARGET_RMS)


if __name__ == "__main__":
    for i in range(len(VARIANTS)):
        synth.write(make(i), f"house/block_land_{i + 1}")
