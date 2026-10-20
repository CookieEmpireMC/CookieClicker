"""factory_ambient: low, warm oven/machine hum of the little cookie factory, seamless 4 s loop.
-> sounds/factory/ambient.ogg

Built circularly so the end flows into the start (see the style guide, "Ambient loops"):
- a warm hum: a soft harmonic stack on G2 (98 Hz) with a second voice 0.25 Hz higher, so the
  slow beating completes exactly one cycle per loop; harmonics up to ~800 Hz carry it on small
  speakers. Every partial has a whole number of cycles in the loop, so the tone is seamless.
- a soft low rumble (periodic FFT noise, low-passed) for the oven's body.
- a gentle machine rhythm: the hum "breathes" four times per loop (1 Hz, 60 bpm) with a soft
  muffled "chuff" at each beat and a very quiet wooden tick on the off-beat. Slow and soft so it
  never becomes a metronome.
- a few faint, dull oven crackles (rarer, lower and darker than house_chimney_crackle).
The bar line sits on the loop point: the breath's trough is there and the first chuff comes
just after it, so a replay that starts a little early or late is masked by the rhythm.
Quieter than the chimney crackle on purpose (file and config volume): it's a bed, not a feature.
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "sound-tools"))
import synth  # noqa: E402

SEED = 2610
LENGTH_S = 4.0                # 80 ticks: replay every 80 ticks at pitch 1.0
TARGET_LUFS = -19.5           # under the chimney crackle (-19.1); sound-check range is -16 ±4
# hum (all frequencies must be multiples of 1/LENGTH_S = 0.25 Hz to loop seamlessly)
HUM_F0 = 98.0                 # G2
HUM_DETUNE_HZ = 0.25          # second voice: one slow beat per loop
HUM_DETUNE_GAIN = 0.55
HUM_HARMONICS = 8
HUM_ROLLOFF = 1.25            # harmonic n gain = 1/n^rolloff
HUM_ODD_BOOST = 1.25          # slightly more odd harmonics: a soft "machine" hum, still round
# rumble
RUMBLE_LP_HZ = 260.0
RUMBLE_GAIN = 0.30            # relative to the hum (RMS)
# rhythm
BEATS = 4                     # breaths per loop (1 Hz)
BREATH_DEPTH = 0.35           # hum amplitude dip at each beat (0..1)
LOOP_DIP_DEPTH = 0.85         # dip at the loop point: sound-check needs end < -30 dBFS and start > -40 dBFS
LOOP_DIP_WIDTH_S = 0.08
CHUFF_DELAY_S = 0.07          # chuff starts this long after each beat (keeps 60+ ms off the loop point)
CHUFF_LEN_S = 0.30
CHUFF_BAND = (180.0, 900.0)
CHUFF_GAIN = 0.55
CHUFF_ACCENT = 1.3            # first chuff of the bar a bit stronger (masks the loop point)
TICK_GAIN = 0.10              # off-beat wooden tick, very quiet
TICK_HZ = 620.0
# crackle
CRACKLES = 5
CRACKLE_BAND = (900.0, 3200.0)
CRACKLE_DB_RANGE = (-30.0, -18.0)
LOOP_GUARD_S = 0.060
HP_HZ, LP_HZ = 50.0, 3000.0   # warm: almost nothing above 3 kHz
LIMIT_DRIVE = 1.3


def circular_dist(t: np.ndarray, length: float) -> np.ndarray:
    d = np.mod(t, length)
    return np.minimum(d, length - d)


def hum(t: np.ndarray) -> np.ndarray:
    out = np.zeros_like(t)
    rng = np.random.default_rng(SEED + 1)
    for n in range(1, HUM_HARMONICS + 1):
        gain = (HUM_ODD_BOOST if n % 2 else 1.0) / n ** HUM_ROLLOFF
        phase = rng.uniform(0, 2 * np.pi)
        out += gain * np.sin(2 * np.pi * HUM_F0 * n * t + phase)
        out += HUM_DETUNE_GAIN * gain * np.sin(2 * np.pi * (HUM_F0 + HUM_DETUNE_HZ) * n * t + phase + 1.3)
    return out / np.sqrt(np.mean(out ** 2))


def chuff(rng: np.random.Generator) -> np.ndarray:
    """Soft muffled puff: band-passed noise with a slow rise and fall (a bellows/piston breath)."""
    tc = synth.t_axis(CHUFF_LEN_S)
    noise = synth.bandpass(rng.standard_normal(len(tc)), *CHUFF_BAND, order=2)
    env = np.sin(np.pi * tc / CHUFF_LEN_S) ** 2 * np.exp(-tc / 0.12)
    body = np.sin(2 * np.pi * HUM_F0 * 2 * tc) * np.exp(-tc / 0.08) * np.clip(tc / 0.02, 0, 1)
    x = noise / (np.max(np.abs(noise)) + 1e-12) * env + 0.5 * body
    return x / np.max(np.abs(x))


def tick(rng: np.random.Generator) -> np.ndarray:
    tt = synth.t_axis(0.05)
    f = TICK_HZ * rng.uniform(0.97, 1.03)
    x = np.sin(2 * np.pi * f * tt) * synth.exp_env(tt, 0.010, attack=0.002)
    x += 0.4 * np.sin(2 * np.pi * f * 0.5 * tt) * synth.exp_env(tt, 0.014, attack=0.002)
    return x / np.max(np.abs(x))


def make() -> np.ndarray:
    rng = np.random.default_rng(SEED)
    n = int(round(LENGTH_S * synth.SR))
    t = np.arange(n) / synth.SR
    beat = LENGTH_S / BEATS

    # hum with a slow breath on every beat and a deeper one at the bar line (= loop point)
    phase = np.mod(t, beat) / beat
    breath = 1 - BREATH_DEPTH * (0.5 + 0.5 * np.cos(2 * np.pi * phase)) ** 3
    dip = 1 - LOOP_DIP_DEPTH * np.exp(-(circular_dist(t, LENGTH_S) / LOOP_DIP_WIDTH_S) ** 2)
    bed = hum(t) * breath * dip

    rumble = synth.fft_filter(rng.standard_normal(n), highpass_hz=45.0, lowpass_hz=RUMBLE_LP_HZ, order=3)
    rumble /= np.sqrt(np.mean(rumble ** 2))
    bed += RUMBLE_GAIN * rumble * (0.7 + 0.3 * breath) * dip

    events = np.zeros(n)
    for b in range(BEATS):
        gain = CHUFF_GAIN * (CHUFF_ACCENT if b == 0 else 1.0) * rng.uniform(0.9, 1.05)
        synth.place_circular(events, chuff(rng), b * beat + CHUFF_DELAY_S, gain)
        synth.place_circular(events, tick(rng), b * beat + beat * 0.5 + rng.uniform(-0.01, 0.01), TICK_GAIN)

    times = rng.uniform(0, LENGTH_S, CRACKLES * 4)
    times = np.sort(times[circular_dist(times, LENGTH_S) > LOOP_GUARD_S + 0.03][:CRACKLES])
    for at in times:
        centre = rng.uniform(CRACKLE_BAND[0] * 1.3, CRACKLE_BAND[1] / 1.3)
        grain = synth.crunch_grain(rng, rng.uniform(1.0, 3.0), centre / 1.3, centre * 1.3)
        level = synth.db_to_lin(rng.uniform(*CRACKLE_DB_RANGE))
        synth.place_circular(events, grain / (np.max(np.abs(grain)) + 1e-12), at, level * 3.0)

    x = synth.soft_limit(bed / np.max(np.abs(bed)) + events, LIMIT_DRIVE)   # limit before the low-pass
    x = synth.fft_filter(x, highpass_hz=HP_HZ, lowpass_hz=LP_HZ, order=4)
    x -= np.mean(x)
    return synth.gain_to_lufs(x, TARGET_LUFS)


if __name__ == "__main__":
    synth.write(make(), "factory/ambient")
