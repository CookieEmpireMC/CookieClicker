"""Measure every .ogg in the asset folders and fail on files Minecraft or players would notice.

Run via `mise run sound-check`. Fails on: wrong format, stereo outside a `stereo/` folder
(positional sounds must be mono), peaks above -1 dBFS, leading silence, abrupt endings,
odd sample rates. "Audible" means above SILENCE_DBFS (absolute); RMS for short clips is
measured from the first to the last audible sample.
Warns on DC offset, very long UI sounds and loudness far from the target.
"""
import sys
from pathlib import Path

import numpy as np
import pyloudnorm
import soundfile as sf

ROOTS = ["resourcepack", "itemsadder"]
SAMPLE_RATES = {44100, 48000}
MAX_PEAK_DBFS = -1.0
LEADING_SILENCE_MS = 5.0       # first audible sample must start within this
END_MAX_DBFS = -30.0           # last 2 ms must have faded below this (no abrupt cut-off)
SILENCE_DBFS = -40.0           # "audible" threshold for the leading-silence check
TARGET_LUFS = -16.0            # sounds >= 400 ms
TARGET_RMS_DBFS = -18.0        # shorter sounds: integrated LUFS needs >= 400 ms of audio
LOUDNESS_TOLERANCE = 4.0
LUFS_MIN_SECONDS = 0.4


def db(x: float) -> float:
    return 20 * np.log10(max(x, 1e-12))


def check(path: Path) -> tuple[str, list[str], list[str]]:
    errors, warnings = [], []
    info = sf.info(str(path))
    if info.format != "OGG" or info.subtype != "VORBIS":
        errors.append(f"format {info.format}/{info.subtype}, expected OGG/VORBIS")
    data, rate = sf.read(str(path), always_2d=True)
    mono = data.mean(axis=1)
    duration = len(mono) / rate

    if rate not in SAMPLE_RATES:
        errors.append(f"sample rate {rate}, expected 44100 or 48000")
    if info.channels != 1 and "stereo" not in path.parts:
        errors.append(f"{info.channels} channels; positional sounds must be mono (stereo only under a stereo/ folder)")

    peak = db(float(np.max(np.abs(data)))) if data.size else -np.inf
    if peak > MAX_PEAK_DBFS:
        errors.append(f"peak {peak:.1f} dBFS above {MAX_PEAK_DBFS} dBFS")

    audible = np.flatnonzero(np.abs(mono) > 10 ** (SILENCE_DBFS / 20))
    lead_ms = (audible[0] / rate * 1000) if audible.size else duration * 1000
    if lead_ms > LEADING_SILENCE_MS:
        errors.append(f"{lead_ms:.1f} ms leading silence (max {LEADING_SILENCE_MS:.0f} ms)")

    tail = mono[-max(1, int(rate * 0.002)):]
    end_level = db(float(np.max(np.abs(tail)))) if tail.size else -np.inf
    if end_level > END_MAX_DBFS:
        errors.append(f"ends at {end_level:.1f} dBFS; fade out below {END_MAX_DBFS:.0f} dBFS to avoid a click")

    dc = float(np.mean(mono)) if mono.size else 0.0
    if abs(dc) > 0.01:
        warnings.append(f"DC offset {dc:+.3f}")

    if duration >= LUFS_MIN_SECONDS:
        loudness = pyloudnorm.Meter(rate).integrated_loudness(data)
        level, target, unit = loudness, TARGET_LUFS, "LUFS"
    else:
        active = mono[audible[0]:audible[-1] + 1] if audible.size else mono
        level = db(float(np.sqrt(np.mean(active ** 2)))) if active.size else -np.inf
        target, unit = TARGET_RMS_DBFS, "dBFS RMS"
    if abs(level - target) > LOUDNESS_TOLERANCE:
        warnings.append(f"loudness {level:.1f} {unit}, target {target:.0f} ±{LOUDNESS_TOLERANCE:.0f}")
    if "ui" in path.parts and duration > 0.4:
        warnings.append(f"UI sound is {duration * 1000:.0f} ms (aim for 50-400 ms)")

    line = (f"{path}  {duration * 1000:6.0f} ms  {info.channels}ch  {rate} Hz  "
            f"peak {peak:6.1f} dBFS  {level:6.1f} {unit}")
    return line, errors, warnings


def main() -> int:
    files = sorted(p for root in ROOTS if Path(root).is_dir() for p in Path(root).rglob("*.ogg"))
    failed = 0
    for path in files:
        try:
            line, errors, warnings = check(path)
        except (RuntimeError, sf.LibsndfileError) as e:
            line, errors, warnings = str(path), [f"unreadable: {e}"], []
        print(line)
        for message in errors:
            print(f"  FAIL {message}")
        for message in warnings:
            print(f"  warn {message}")
        failed += bool(errors)
    print(f"Checked {len(files)} sound(s), {failed} failed.")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
