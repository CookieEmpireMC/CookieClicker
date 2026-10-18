"""Shared synthesis helpers for the cookieclicker sound scripts.

Lives in design/sound-tools/ (not design/sound-scripts/) so `mise run sounds` doesn't run it.
Scripts import it with:
    sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "sound-tools"))
    import synth
"""
from pathlib import Path

import numpy as np
import soundfile as sf
from scipy import signal

SR = 44100
REPO = Path(__file__).resolve().parents[2]
SOUNDS_DIR = REPO / "resourcepack" / "assets" / "cookieclicker" / "sounds"

PEAK_CEILING_DBFS = -2.0   # 1 dB headroom for Vorbis overshoot (sound-check fails above -1)
TARGET_RMS_DBFS = -18.0    # clips < 400 ms
AUDIBLE_REL_DB = -40.0     # "audible part" for the RMS measurement, relative to peak


def t_axis(seconds: float) -> np.ndarray:
    return np.arange(int(round(seconds * SR))) / SR


def db_to_lin(db: float) -> float:
    return 10 ** (db / 20)


def sine_sweep(t: np.ndarray, f_start: float, f_end: float, tau: float, phase: float = 0.0) -> np.ndarray:
    """Sine whose frequency glides exponentially from f_start towards f_end (time constant tau)."""
    freq = f_end + (f_start - f_end) * np.exp(-t / tau)
    return np.sin(2 * np.pi * np.cumsum(freq) / SR + phase)


def exp_env(t: np.ndarray, decay: float, attack: float = 0.001) -> np.ndarray:
    """Linear attack, exponential decay."""
    env = np.exp(-t / decay)
    if attack > 0:
        env *= np.clip(t / attack, 0, 1)
    return env


def bandpass(x: np.ndarray, low: float, high: float, order: int = 2) -> np.ndarray:
    sos = signal.butter(order, [low, high], btype="bandpass", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def lowpass(x: np.ndarray, cutoff: float, order: int = 2) -> np.ndarray:
    sos = signal.butter(order, cutoff, btype="lowpass", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def highpass(x: np.ndarray, cutoff: float = 40.0, order: int = 1) -> np.ndarray:
    sos = signal.butter(order, cutoff, btype="highpass", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def place(buf: np.ndarray, sound: np.ndarray, at_seconds: float, gain: float = 1.0) -> None:
    """Mix `sound` into `buf` starting at `at_seconds` (truncated at the end of buf)."""
    start = int(round(at_seconds * SR))
    if start >= len(buf):
        return
    end = min(len(buf), start + len(sound))
    buf[start:end] += gain * sound[:end - start]


def crunch_grain(rng: np.random.Generator, length_ms: float, low: float, high: float) -> np.ndarray:
    """One crumb crack: a few ms of band-passed noise with a sharp attack and fast decay."""
    t = t_axis(length_ms / 1000)
    noise = rng.standard_normal(len(t))
    env = exp_env(t, decay=length_ms / 1000 / 3.0, attack=0.0002)
    return bandpass(noise, low, high) * env


def bell(t: np.ndarray, f0: float, decay: float, partials=((1.0, 1.0), (2.0, 0.35), (3.01, 0.12), (4.17, 0.06)),
         attack: float = 0.0015) -> np.ndarray:
    """Soft additive bell: partials as (ratio, gain); higher partials decay faster."""
    out = np.zeros_like(t)
    for ratio, gain in partials:
        out += gain * np.sin(2 * np.pi * f0 * ratio * t) * np.exp(-t * ratio ** 0.7 / decay)
    if attack > 0:
        out *= np.clip(t / attack, 0, 1)
    return out


def finish(x: np.ndarray, fade_in_ms: float = 1.0, fade_out_ms: float = 12.0, lp_cutoff: float = 6000.0,
           target_rms_dbfs: float = TARGET_RMS_DBFS) -> np.ndarray:
    """DC high-pass, final low-pass, fades, then gain to the RMS target capped by the peak ceiling."""
    x = highpass(x, 40.0)
    if lp_cutoff:
        x = lowpass(x, lp_cutoff, order=4)   # 4th order: no faint 10-15 kHz fizz from the grains
    n_in = max(1, int(SR * fade_in_ms / 1000))
    x[:n_in] *= np.linspace(0, 1, n_in)
    n_out = max(1, int(SR * fade_out_ms / 1000))
    x[-n_out:] *= np.linspace(1, 0, n_out) ** 2
    peak = np.max(np.abs(x))
    audible = np.flatnonzero(np.abs(x) > peak * db_to_lin(AUDIBLE_REL_DB))
    active = x[audible[0]:audible[-1] + 1]
    rms = np.sqrt(np.mean(active ** 2))
    gain = db_to_lin(target_rms_dbfs) / rms
    gain = min(gain, db_to_lin(PEAK_CEILING_DBFS) / peak)
    return x * gain


OGG_SERIAL = 0x434F4F4B   # fixed stream serial ("COOK") so regenerating gives byte-identical files


def _ogg_crc(data: bytes) -> int:
    crc = 0
    for byte in data:
        crc ^= byte << 24
        for _ in range(8):
            crc = ((crc << 1) ^ 0x04C11DB7) & 0xFFFFFFFF if crc & 0x80000000 else (crc << 1) & 0xFFFFFFFF
    return crc


def _fix_ogg_serial(path: Path) -> None:
    """libsndfile picks a random Ogg stream serial per encode; replace it and recompute page CRCs."""
    data = bytearray(path.read_bytes())
    pos = 0
    while pos < len(data):
        if data[pos:pos + 4] != b"OggS":
            raise ValueError(f"{path}: not an Ogg page at byte {pos}")
        n_segments = data[pos + 26]
        body = sum(data[pos + 27:pos + 27 + n_segments])
        end = pos + 27 + n_segments + body
        data[pos + 14:pos + 18] = OGG_SERIAL.to_bytes(4, "little")
        data[pos + 22:pos + 26] = b"\0\0\0\0"
        data[pos + 22:pos + 26] = _ogg_crc(bytes(data[pos:end])).to_bytes(4, "little")
        pos = end
    path.write_bytes(bytes(data))


def write(x: np.ndarray, relative: str) -> Path:
    """Write mono Ogg Vorbis to resourcepack/assets/cookieclicker/sounds/<relative>.ogg (deterministic bytes)."""
    out = SOUNDS_DIR / f"{relative}.ogg"
    out.parent.mkdir(parents=True, exist_ok=True)
    sf.write(str(out), x.astype(np.float32), SR, format="OGG", subtype="VORBIS")
    _fix_ogg_serial(out)
    peak = 20 * np.log10(np.max(np.abs(x)))
    print(f"  wrote {out.relative_to(REPO)}  {len(x) / SR * 1000:.0f} ms  pre-encode peak {peak:.1f} dBFS")
    return out
