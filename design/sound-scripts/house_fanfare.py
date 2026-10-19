"""house_fanfare: short cheerful "your bakery is open!" fanfare (~2.8 s). -> sounds/house/fanfare.ogg

Rhythm "da-da-da-DAA ... ta-DAAA": a quick plucked pickup G4-C5-E5-G5, a short F major
(IV) chord, then a ringing C major (I) chord on a warm reed/horn-ish pad with plucks on top,
a pinch of shaker grains and three little sparkle bells (E6-G6-C7) like the crit sparkle.
Mono (also fine as a positional sound). Original synthesis.
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "sound-tools"))
import synth  # noqa: E402

SEED = 9001
LENGTH_S = 2.80
TARGET_LUFS = -16.0

# notes in Hz
G4, A4, C5, E5, F5, G5, A5, C6 = 392.0, 440.0, 523.25, 659.26, 698.46, 783.99, 880.0, 1046.5
F3, C4 = 174.61, 261.63
E6, G6, C7 = 1318.5, 1568.0, 2093.0

PICKUP = [(0.00, G4), (0.09, C5), (0.18, E5), (0.27, G5)]
IV_AT, IV_CHORD, IV_BASS, IV_LEN = 0.55, [F5, A5, C6], F3, 0.18
I_AT, I_CHORD, I_BASS, I_LEN = 0.80, [C5, E5, G5, C6], C4, 1.55
SPARKLES = [(0.95, E6), (1.07, G6), (1.19, C7)]

PLUCK_PARTIALS = ((1.0, 1.0), (2.0, 0.45), (3.0, 0.18), (4.0, 0.07))
PLUCK_DECAY_S = 0.22
PLUCK_GAIN = 0.45
PAD_HARMONICS = 6
PAD_ATTACK_S = 0.035
PAD_RELEASE_S = 0.45
PAD_GAIN = 0.30
PAD_LP_HZ = 2600.0
VIBRATO_HZ, VIBRATO_DEPTH = 5.2, 0.003
SPARKLE_PARTIALS = ((1.0, 1.0), (2.0, 0.3), (3.01, 0.1))
SPARKLE_GAIN = 0.18
SPARKLE_DETUNE = 1.0038
SHAKER_GRAINS = 8
SHAKER_GAIN = 0.08
BRIGHTNESS_LP_HZ = 7500.0
FADE_OUT_MS = 300.0
LIMIT_DRIVE = 2.0           # synth.soft_limit: tames stacked pluck attacks on the chords


def pluck(f: float, length: float = 1.2, decay: float = PLUCK_DECAY_S) -> np.ndarray:
    t = synth.t_axis(length)
    return synth.bell(t, f, decay, PLUCK_PARTIALS, attack=0.003) + 0.6 * synth.bell(t, f * 1.003, decay, PLUCK_PARTIALS, attack=0.003)


def pad(f: float, hold: float) -> np.ndarray:
    """Warm reed/horn-ish sustained note: harmonics 1..n at 1/n^1.5 with a soft attack and release."""
    t = synth.t_axis(hold + PAD_RELEASE_S * 4)
    freq = f * (1 + VIBRATO_DEPTH * np.sin(2 * np.pi * VIBRATO_HZ * t) * np.clip(t / 0.3, 0, 1))
    phase = 2 * np.pi * np.cumsum(freq) / synth.SR
    x = sum(np.sin(n * phase) / n ** 1.5 for n in range(1, PAD_HARMONICS + 1))
    env = np.clip(t / PAD_ATTACK_S, 0, 1) * np.where(t < hold, 1.0, np.exp(-(t - hold) / PAD_RELEASE_S))
    return synth.lowpass(x * env, PAD_LP_HZ, order=2)


def make() -> np.ndarray:
    rng = np.random.default_rng(SEED)
    x = np.zeros(int(round(LENGTH_S * synth.SR)))
    for at, f in PICKUP:
        synth.place(x, pluck(f, decay=0.16), at, PLUCK_GAIN)
    synth.place(x, pad(G5, 0.18), PICKUP[-1][0], PAD_GAIN * 0.7)
    for f in IV_CHORD:
        synth.place(x, pluck(f, decay=0.12), IV_AT, PLUCK_GAIN * 0.6)
        synth.place(x, pad(f, IV_LEN), IV_AT, PAD_GAIN * 0.8)
    synth.place(x, pluck(IV_BASS, decay=0.18), IV_AT, PLUCK_GAIN * 0.8)
    for f in I_CHORD:
        synth.place(x, pluck(f, decay=0.35), I_AT, PLUCK_GAIN * 0.7)
        synth.place(x, pad(f, I_LEN), I_AT, PAD_GAIN)
    synth.place(x, pluck(I_BASS, decay=0.45), I_AT, PLUCK_GAIN)
    synth.place(x, pad(I_BASS, I_LEN * 0.8), I_AT, PAD_GAIN * 0.7)
    for at, f in SPARKLES:
        tb = synth.t_axis(0.8)
        note = synth.bell(tb, f, 0.18, SPARKLE_PARTIALS) + 0.65 * synth.bell(tb, f * SPARKLE_DETUNE, 0.18, SPARKLE_PARTIALS)
        synth.place(x, note, at, SPARKLE_GAIN)
    shaker = np.zeros_like(x)
    for at in I_AT + np.sort(rng.uniform(0.0, 0.12, SHAKER_GRAINS)):
        grain = synth.crunch_grain(rng, rng.uniform(1.0, 3.0), 3500.0, 6500.0)
        synth.place(shaker, grain / (np.max(np.abs(grain)) + 1e-12), at, 1.0)
    x += SHAKER_GAIN * np.tanh(2.2 * shaker)
    x = synth.soft_limit(x, LIMIT_DRIVE)
    x = synth.finish(x, fade_out_ms=FADE_OUT_MS, lp_cutoff=BRIGHTNESS_LP_HZ)
    return synth.gain_to_lufs(x, TARGET_LUFS)


if __name__ == "__main__":
    synth.write(make(), "house/fanfare")
