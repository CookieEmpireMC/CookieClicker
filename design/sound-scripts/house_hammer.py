"""house_hammer: light hammer taps on wood/nails. -> sounds/house/hammer_1..4.ogg

Each tap: a short wooden "tok" (pitch-dropping sine) + a soft, quickly damped nail ping
(three inharmonic partials) + a tiny strike transient. Variants: two single taps,
a "tap-tap" and a light "tap-tap-tap", so dozens in a row during the build don't sound
like a machine gun. Authored a little low on purpose: sounds.yml plays it at pitch ~1.15-1.35
(shared with the vanilla anvil fallback, which must sound high and small).
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "sound-tools"))
import synth  # noqa: E402

SEED = 7310
# wooden body
BODY_START_HZ = 760.0
BODY_END_HZ = 380.0
BODY_TAU_S = 0.008
BODY_DECAY_S = 0.022
BODY_GAIN = 1.0
# nail ping: (ratio to NAIL_HZ, gain, decay s)
NAIL_HZ = 1900.0
NAIL_PARTIALS = ((1.0, 0.30, 0.020), (1.39, 0.17, 0.014), (2.05, 0.08, 0.009))   # short "tink", not a beep
# strike transient
STRIKE_GAIN = 0.25
BRIGHTNESS_LP_HZ = 6000.0
TARGET_RMS = -19.0          # a touch under the set: these repeat dozens of times per build
FADE_OUT_MS = 25.0

# variant: (length s, [(time s, pitch factor, gain)], nail detune)
VARIANTS = [
    (0.150, [(0.000, 1.00, 1.0)], 1.00),
    (0.150, [(0.000, 0.95, 1.0)], 1.04),
    (0.260, [(0.000, 1.02, 1.0), (0.105, 1.05, 0.70)], 0.98),
    (0.330, [(0.000, 0.97, 0.9), (0.085, 1.00, 0.65), (0.170, 1.03, 0.50)], 1.02),
]


def tap(rng: np.random.Generator, length: float, pitch: float, nail_detune: float) -> np.ndarray:
    t = synth.t_axis(length)
    x = BODY_GAIN * synth.sine_sweep(t, BODY_START_HZ * pitch, BODY_END_HZ * pitch, BODY_TAU_S) \
        * synth.exp_env(t, BODY_DECAY_S * rng.uniform(0.92, 1.08))
    for ratio, gain, decay in NAIL_PARTIALS:
        f = NAIL_HZ * pitch * nail_detune * ratio
        x += gain * np.sin(2 * np.pi * f * t + rng.uniform(0, 2 * np.pi)) * synth.exp_env(t, decay)
    grain = synth.crunch_grain(rng, rng.uniform(1.0, 2.0), 2000.0, 5500.0)
    synth.place(x, grain / (np.max(np.abs(grain)) + 1e-12), 0.0, STRIKE_GAIN)
    return x


def make(variant: int) -> np.ndarray:
    rng = np.random.default_rng(SEED + variant)
    length, taps, detune = VARIANTS[variant]
    x = np.zeros(int(round(length * synth.SR)))
    for at, pitch, gain in taps:
        synth.place(x, tap(rng, length, pitch, detune), at, gain)
    return synth.finish(x, fade_out_ms=FADE_OUT_MS, lp_cutoff=BRIGHTNESS_LP_HZ, target_rms_dbfs=TARGET_RMS)


if __name__ == "__main__":
    for i in range(len(VARIANTS)):
        synth.write(make(i), f"house/hammer_{i + 1}")
