"""belt_loop: soft mechanical conveyor-belt hum with a light rhythmic rattle of slats, seamless loop (4.0 s file, 5.0 s in game at pitch 0.8).
-> sounds/factory/belt_loop.ogg

Sits under factory_ambient (the oven hum, G2 98 Hz, 60 bpm breath), so it's built to stay out of its way:
- a thin motor hum on D2 (73.6 Hz, a fourth under the oven's G2, so the two drones are consonant),
  only 5 soft harmonics, with a second voice 0.2 Hz higher (one slow swell per loop). Every partial
  has a whole number of cycles in the loop, so the tone is seamless.
- a quiet rubber-belt "whir": periodic FFT noise band-passed 250-1100 Hz, gently pulsed at the slat rate.
- the slat rattle: 5 slats per second (25 per loop) roll over the end roller, each a soft, dull
  wooden/rubber clack (low thump + a short band-passed tick), with an accent every 4th slat and a
  little timing/level jitter so it reads as mechanical but never as a drill or a metronome.
- a few faint roller squeaks-that-aren't: very quiet low "tok"s from the rollers, two per loop.
Slats sit on the off-grid (0.1 s + k*0.2 s), so no transient is within 60 ms of the loop point;
the bed dips at the loop point (Gaussian, 85 %) for sound-check's seam rules.

Authored for PLAY_PITCH 0.8: sounds.yml shares one pitch between our key and the vanilla fallback
(block.beehive.work, which needs a low pitch to read as a belt). So everything is designed at in-game
time/frequency (DESIGN_LENGTH_S = 5.0 s, partials multiples of 0.2 Hz) and then FFT-resampled
(circular, stays seamless) to a 4.0 s file. At pitch 0.8 the client plays it back as exactly the design:
5.0 s = 100 ticks, hum on D2.
"""
import sys
from pathlib import Path

import numpy as np
from scipy import signal

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "sound-tools"))
import synth  # noqa: E402

SEED = 2621
PLAY_PITCH = 0.8              # the fixed pitch in sounds.yml; file = design sped up by 1/PLAY_PITCH
LENGTH_S = 5.0                # design length = in-game length at PLAY_PITCH: 100 ticks
FILE_LENGTH_S = LENGTH_S * PLAY_PITCH   # 4.0 s file
TARGET_LUFS = -19.8           # below factory_ambient (-19.5); sound-check range is -16 +-4
# motor hum (design frequencies must be multiples of 1/LENGTH_S = 0.2 Hz)
HUM_F0 = 73.6                 # ~D2
HUM_DETUNE_HZ = 0.2
HUM_DETUNE_GAIN = 0.5
HUM_HARMONICS = 5
HUM_ROLLOFF = 1.5
HUM_GAIN = 0.45               # relative to the whir (RMS); the hum is a hint, the oven owns the drone
# belt whir
WHIR_BAND = (250.0, 1100.0)
WHIR_GAIN = 0.55
WHIR_PULSE_DEPTH = 0.25       # whir swells slightly with every slat
# slat rattle
SLATS_PER_S = 5               # 25 per loop: integer, so the rhythm loops
SLAT_OFFSET_S = 0.1           # half a slat period: keeps every slat 100 ms off the loop point
SLAT_GAIN = 0.9
SLAT_ACCENT = 1.35            # every 4th slat (a belt joint)
SLAT_JITTER_S = 0.004
SLAT_TICK_BAND = (900.0, 2600.0)
SLAT_THUMP_HZ = 210.0
# roller toks
TOKS = 2
TOK_GAIN = 0.35
TOK_HZ = 160.0
# loop point
LOOP_DIP_DEPTH = 0.85
LOOP_DIP_WIDTH_S = 0.08
HP_HZ, LP_HZ = 55.0, 3000.0   # warm: almost nothing above 3 kHz, like the oven hum
LIMIT_DRIVE = 1.4


def circular_dist(t: np.ndarray, length: float) -> np.ndarray:
    d = np.mod(t, length)
    return np.minimum(d, length - d)


def hum(t: np.ndarray) -> np.ndarray:
    out = np.zeros_like(t)
    rng = np.random.default_rng(SEED + 1)
    for n in range(1, HUM_HARMONICS + 1):
        gain = 1.0 / n ** HUM_ROLLOFF
        phase = rng.uniform(0, 2 * np.pi)
        out += gain * np.sin(2 * np.pi * HUM_F0 * n * t + phase)
        out += HUM_DETUNE_GAIN * gain * np.sin(2 * np.pi * (HUM_F0 + HUM_DETUNE_HZ) * n * t + phase + 0.9)
    return out / np.sqrt(np.mean(out ** 2))


def slat(rng: np.random.Generator) -> np.ndarray:
    """Soft dull clack: a short low thump plus a quick band-passed tick (rubber slat over a roller)."""
    tt = synth.t_axis(0.06)
    f = SLAT_THUMP_HZ * rng.uniform(0.95, 1.05)
    thump = np.sin(2 * np.pi * f * tt) * synth.exp_env(tt, 0.012, attack=0.003)
    tick = synth.bandpass(rng.standard_normal(len(tt)), *SLAT_TICK_BAND, order=2)
    tick = tick / (np.max(np.abs(tick)) + 1e-12) * synth.exp_env(tt, 0.004, attack=0.0015)
    x = thump + 0.55 * tick
    return x / np.max(np.abs(x))


def tok(rng: np.random.Generator) -> np.ndarray:
    tt = synth.t_axis(0.09)
    f = TOK_HZ * rng.uniform(0.95, 1.05)
    x = np.sin(2 * np.pi * f * tt) * synth.exp_env(tt, 0.025, attack=0.004)
    return x / np.max(np.abs(x))


def make() -> np.ndarray:
    rng = np.random.default_rng(SEED)
    n = int(round(LENGTH_S * synth.SR))
    t = np.arange(n) / synth.SR
    period = 1.0 / SLATS_PER_S

    pulse = 1 - WHIR_PULSE_DEPTH * (0.5 + 0.5 * np.cos(2 * np.pi * (t - SLAT_OFFSET_S) / period))
    whir = synth.fft_filter(rng.standard_normal(n), highpass_hz=WHIR_BAND[0], lowpass_hz=WHIR_BAND[1], order=3)
    whir /= np.sqrt(np.mean(whir ** 2))
    bed = WHIR_GAIN * whir * pulse + HUM_GAIN * hum(t)

    events = np.zeros(n)
    for k in range(int(round(LENGTH_S * SLATS_PER_S))):
        gain = SLAT_GAIN * (SLAT_ACCENT if k % 4 == 0 else 1.0) * rng.uniform(0.8, 1.05)
        at = SLAT_OFFSET_S + k * period + rng.uniform(-SLAT_JITTER_S, SLAT_JITTER_S)
        synth.place_circular(events, slat(rng), at, gain)
    for k in range(TOKS):
        at = (k + 0.5) * LENGTH_S / TOKS + 0.05
        synth.place_circular(events, tok(rng), at, TOK_GAIN)

    x = synth.soft_limit(bed / np.max(np.abs(bed)) * 0.6 + events, LIMIT_DRIVE)   # limit before the low-pass
    x = synth.fft_filter(x, highpass_hz=HP_HZ, lowpass_hz=LP_HZ, order=4)
    x -= np.mean(x)
    x *= 1 - LOOP_DIP_DEPTH * np.exp(-(circular_dist(t, LENGTH_S) / LOOP_DIP_WIDTH_S) ** 2)
    x = signal.resample(x, int(round(FILE_LENGTH_S * synth.SR)))   # FFT resample: circular, seamless
    return synth.gain_to_lufs(x, TARGET_LUFS)


if __name__ == "__main__":
    synth.write(make(), "factory/belt_loop")
