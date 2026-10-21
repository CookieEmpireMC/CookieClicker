# Sound style guide (cookieclicker)

The character every sound in the set shares, so new sounds fit the same family.

## Character
- **Soft, round, warm, toy-like, bakery.** Think of tapping and breaking a cookie, a wooden counter, a little shop-door bell; not digital beeps. Satisfying on the hundredth repetition.
- **Pitch range:** UI bodies sit roughly between 200 Hz and 1.2 kHz. Rewards/purchases may go higher (bell fundamentals up to ~2.1 kHz, C7), errors lower but **not below ~170 Hz** for the main pitch, or laptop/phone speakers won't play it.
- **Brightness:** keep energy mostly below ~6 kHz. Crunch transients may reach higher but every sound gets a final **4th-order** low-pass (7-7.5 kHz for crunchy sounds, 2.5 kHz for errors).
- **Length:** clicks 100-150 ms, errors ~200 ms, rewards/purchases 350-400 ms (stay under 400 ms so UI sounds don't trigger the "long UI sound" warning).

## Loudness targets (match `tools/sound_check.py`)
- Clips < 400 ms: about **-18 dBFS RMS** over the audible part. Errors a touch softer (-19).
- Clips >= 400 ms: about **-16 LUFS** integrated.
- Peaks <= **-2 dBFS** in the generator (headroom for Vorbis overshoot; sound-check fails above -1 dBFS).
- Config volume <= 1.0: clicks and errors 0.6, rewards/purchases 0.8.

## Format
- Mono Ogg Vorbis, 44.1 kHz, written with `soundfile` (`format="OGG", subtype="VORBIS"`). Stereo only under `sounds/stereo/` with a written reason.
- No leading silence; 1 ms fade-in, 12 ms squared fade-out (25-40 ms for ringing sounds).
- 1st-order 40 Hz high-pass on every sound to remove DC.
- All of this is done by `finish()` and `write()` in `design/sound-tools/synth.py`; use them.

## Recipes that define "our" sound
- **Tap / pop:** sine with an exponential pitch drop (≈2.2× down to the end pitch, time constant 10-16 ms) and exponential amplitude decay (18-35 ms), plus a quiet sub sine an octave below the end pitch. (`synth.sine_sweep` + `synth.exp_env`)
- **Crunch (the cookie signature):** 6-9 (click) or ~14 (crit) tiny band-passed noise grains (1-4 ms, random centre 1.1-5.2 kHz), front-loaded in the first 45-75 ms, levels decaying with time. Sum them into their own buffer and **soft-clip it with tanh** (drive ~2.2) before mixing: it stops one loud grain from setting the peak. (`synth.crunch_grain`)
- **Bells / sparkle:** additive bell (`synth.bell`), partials (1, 2, 3.01) for high sparkle notes, up to (1, 2, 2.98, 4.2, 5.4) for warmer lower bells; layer a second voice detuned by +0.35-0.4 % at ~60-70 % level for shimmer. Rising major arpeggios = reward.
- **Error:** two muted thuds, second one lower, plus a low-passed odd-harmonic buzz underneath. No beeps, no high pitches.
- **Variants:** 3-4 per frequently repeated sound, differing by seed (grain timing), ±6 % pitch, ±8 % decay and ±10 % crunch level; the plugin adds random pitch on top (sounds.yml `pitch-min/max`).

## Workflow
- Generators in `design/sound-scripts/` (one per sound/family, parameters as constants at the top, fixed seeds), run with `mise run sounds`. Shared helpers only in `design/sound-tools/`.
- Check with `mise run sound-check`, `mise run sound-previews` (Read the PNGs), then `mise run validate-assets`.

## Long sounds and loops (added 2026-10-19, starter-house)
- **Jingles/fanfares (>= 400 ms):** -16 LUFS via `synth.gain_to_lufs`. If peak-limited, apply `synth.soft_limit` (tanh, drive 1.5-2.5) to the mix **before** `finish()`'s low-pass. Warm plucks (`bell` with harmonic partials 1-4) on top of a soft reed/horn pad (harmonics 1-6 at 1/n^1.5, 35 ms attack, low-passed ~2.6 kHz); reuse the crit sparkle bells for "reward".
- **Ambient loops:** about **-19 LUFS** (3 dB under the set, still inside sound-check's ±4) and config volume ~0.5, `source: ambient`. Build circularly (`place_circular`, `fft_filter`, periodic FFT noise), keep transients 60 ms away from the loop point, and dip the bed there; no fades needed.
- **Repeated world sounds (build cinematics):** 4 variants, soft attacks (2 ms on thuds), config volume 0.4-0.5, `source: block`, and a pitch spread of about ±10 %.
- **Fire:** low bed (≤ 450 Hz) + band-passed crackle grains (1.2-5.5 kHz, tanh drive 3) + a few short resonant pops (500-1100 Hz); never a broadband hiss bed.
- **Tonal ambient loops (hum, drone):** partial frequencies are multiples of 1/length Hz; the loop-point dip goes to about -33 dBFS (sound-check: end < -30, start > -40); the bar line of any rhythm sits on the loop point; fixed pitch (1.0) and a replay interval equal to the length in ticks. Low-pass ~3 kHz and keep below the chimney crackle (file ~-19.5 LUFS, config volume ~0.3).
- **Loops with a low-pitched vanilla fallback (added 2026-10-21, conveyor-belt):** the fallback shares the pitch, so author the loop for that fixed pitch: design at the in-game length, FFT-resample (`scipy.signal.resample`) to length x pitch, and pick a pitch where the in-game length is a whole number of ticks. Layered beds sit under the oven hum: file about -19.8 LUFS, lower config volume (0.2), drone tuned consonant with G2 (e.g. D2), rhythm on the half-grid.
