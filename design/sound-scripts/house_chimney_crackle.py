"""house_chimney_crackle: soft fire crackle, seamless 3 s loop. -> sounds/house/chimney_crackle.ogg

Built circularly so the end flows into the start: a quiet low "fire breath" bed (periodic
FFT-shaped noise with a slow periodic swell) plus scattered crackles (tiny band-passed
noise grains, some in little clusters) and a few soft wood "pops" with a short resonance.
Every event wraps around the loop point, filtering is done in the frequency domain, and the
bed dips gently around the loop point (no crackles there) so a replay that starts a bit
early or late never clicks. Quieter than the UI set on purpose: it's a background loop.
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "sound-tools"))
import synth  # noqa: E402

SEED = 3141
LENGTH_S = 3.0
TARGET_LUFS = -19.0           # within sound-check's ±4 of -16; background loop, keep it soft
# bed
BED_LP_HZ = 450.0
BED_GAIN = 0.14               # relative to the crackle layer before normalising
BED_SWELL_DEPTH = 0.35        # slow periodic swell (whole cycles over the loop)
LOOP_DIP_DEPTH = 0.90         # how far the bed dips at the loop point (0..1)
LOOP_DIP_WIDTH_S = 0.12
LOOP_GUARD_S = 0.060          # no crackles this close to the loop point
# crackles
CRACKLES_PER_S = 9.0
CLUSTER_CHANCE = 0.3
CRACKLE_LOW_HZ, CRACKLE_HIGH_HZ = 1200.0, 5500.0
CRACKLE_DB_RANGE = (-22.0, -2.0)
POPS = 4
POP_HZ_RANGE = (500.0, 1100.0)
POP_DECAY_S = 0.006
POP_GAIN = 0.8
CRACKLE_DRIVE = 3.0           # tanh soft-clip of the crackle layer (style guide: crunch recipe)
HP_HZ, LP_HZ = 60.0, 6500.0
LIMIT_DRIVE = 1.6             # synth.soft_limit on the mix: lets the loop reach its loudness without spiky peaks


def circular_dist(t: np.ndarray, length: float) -> np.ndarray:
    d = np.mod(t, length)
    return np.minimum(d, length - d)


def event_times(rng: np.random.Generator, count: int) -> np.ndarray:
    times = rng.uniform(0, LENGTH_S, count * 3)
    times = times[circular_dist(times, LENGTH_S) > LOOP_GUARD_S]
    return np.sort(times[:count])


def make() -> np.ndarray:
    rng = np.random.default_rng(SEED)
    n = int(round(LENGTH_S * synth.SR))
    t = np.arange(n) / synth.SR

    # bed: periodic noise (FFT of exactly n samples), shaped towards the lows
    bed = synth.fft_filter(rng.standard_normal(n), highpass_hz=80.0, lowpass_hz=BED_LP_HZ, order=3)
    bed /= np.sqrt(np.mean(bed ** 2))
    swell = 1 + BED_SWELL_DEPTH * (0.6 * np.sin(2 * np.pi * 1 * t / LENGTH_S + 0.7)
                                   + 0.4 * np.sin(2 * np.pi * 3 * t / LENGTH_S + 2.1))
    dip = 1 - LOOP_DIP_DEPTH * np.exp(-(circular_dist(t, LENGTH_S) / LOOP_DIP_WIDTH_S) ** 2)
    bed *= swell * dip

    # crackles
    crackle = np.zeros(n)
    for at in event_times(rng, int(CRACKLES_PER_S * LENGTH_S)):
        hits = [at] + list(at + np.cumsum(rng.uniform(0.004, 0.015, rng.integers(1, 4)))) \
            if rng.random() < CLUSTER_CHANCE else [at]
        level = synth.db_to_lin(rng.uniform(*CRACKLE_DB_RANGE))
        for i, hit in enumerate(hits):
            centre = rng.uniform(CRACKLE_LOW_HZ * 1.3, CRACKLE_HIGH_HZ / 1.3)
            grain = synth.crunch_grain(rng, rng.uniform(0.5, 2.5), centre / 1.3, centre * 1.3)
            synth.place_circular(crackle, grain / (np.max(np.abs(grain)) + 1e-12), hit, level * 0.7 ** i)
    for at in event_times(rng, POPS):
        tp = synth.t_axis(0.04)
        f = rng.uniform(*POP_HZ_RANGE)
        pop = np.sin(2 * np.pi * f * tp) * synth.exp_env(tp, POP_DECAY_S, attack=0.0005)
        click = synth.crunch_grain(rng, 1.0, 1500.0, 5000.0)
        synth.place(pop, 0.6 * click / (np.max(np.abs(click)) + 1e-12), 0.0)
        synth.place_circular(crackle, pop, at, POP_GAIN)
    crackle = np.tanh(CRACKLE_DRIVE * crackle) / np.tanh(CRACKLE_DRIVE)

    x = synth.soft_limit(crackle + BED_GAIN * bed, LIMIT_DRIVE)   # before the low-pass: no saturation fizz
    x = synth.fft_filter(x, highpass_hz=HP_HZ, lowpass_hz=LP_HZ, order=4)
    x -= np.mean(x)
    return synth.gain_to_lufs(x, TARGET_LUFS)


if __name__ == "__main__":
    synth.write(make(), "house/chimney_crackle")
