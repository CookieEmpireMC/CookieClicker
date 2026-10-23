---
name: sound-tooling-facts
description: Verified facts about the sound toolchain (soundfile/libsndfile Vorbis quirks, validator/review behaviour, synth helpers, loop seams, worktree guard) for cookieclicker
metadata:
  type: reference
---

Re-verified against the repo 2026-10-23 (tidy); first verified 2026-10-18..21.

Encoding and synth helpers (`design/sound-tools/synth.py`):
- soundfile 0.14.0: `sf.write(..., compression_level=0..1)` applies to Vorbis; default used.
- libsndfile writes a random Ogg stream serial per encode; `_fix_ogg_serial` (fixed serial + recomputed CRC) fixes it. Always write through `synth.write()`.
- Helpers: `gain_to_lufs` (pyloudnorm, same as sound-check), `soft_limit`, `fft_filter` (circular), `place_circular`.
- Vorbis lands ~0.1-0.2 dB below the generator LUFS. sound-check warns outside -16 +-4 LUFS (`LOUDNESS_TOLERANCE`), so target -19.8, not -20.0.
- `scipy.signal.resample` (FFT) keeps a circular loop seamless when changing its length (e.g. a loop for pitch 0.8).
- Loop seam rule (tools/sound_check.py): last 2 ms < -30 dBFS AND first audible sample (> -40 dBFS) within 5 ms. Tonal loop: Gaussian dip 85 % / 80 ms passed; 55 % and 95 % failed.

Validators:
- `tools/validate_assets.py` finds `resourcepack/**/assets` without pack.mcmeta; checks sounds.json -> .ogg, subtitles in lang/en_us.json, sounds.yml key/fallback/volume/pitch/source. A key without ':' is prefixed `minecraft:` and skipped (so `@block` passes only incidentally). sound-check never reads sounds.yml.
- `tools/review.py` marks a sound "final" only if its paragraph in `design/sounds.md` contains `Status: final`; needle = ogg stem with trailing digits stripped (`cookie_click_`).
- `SoundService` (src/main/java/dev/casperrs/cookieClicker/sound/) uses ONE volume/pitch for key and fallback; no separate fallback volume/pitch. `key: "@block"` is supported (vanilla place sound of the block; fallback used if unresolved).
- Paper 26.3 SoundCategory has `UI` -> `source: ui`.

Environment quirks:
- Fresh worktree: `mise exec -- python` has no numpy until `mise run assets-deps` ran once (mise run sound tasks depend on it).
- Worktree guard refuses: Bash mixing `cd && ...` with heredoc file writes, names like `.gitignore`, `python3 - <<EOF` editing JSON plus `git`, `for s in ...; do mise exec ...$s` loops, and `sed -i ... $var`. Use the Write tool for files, one script per command, spell paths out.
- Writes to the main checkout's `.claude/agent-memory/` are refused from an isolated worktree; memory lives in the worktree copy and is committed on the branch.
