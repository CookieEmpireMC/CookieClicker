---
name: sound-tooling-facts
description: Verified facts about the sound toolchain (soundfile/libsndfile Vorbis quirks, validator/review behaviour, Paper 26.3 SoundCategory) for cookieclicker
metadata:
  type: reference
---

Verified 2026-10-18 (bakery-core):
- soundfile 0.14.0: `sf.write(..., compression_level=0..1)` applies to Vorbis (0 = best quality/largest). Default used.
- libsndfile writes a **random Ogg stream serial** per encode -> files differ each run. Fix lives in `design/sound-tools/synth.py` (`_fix_ogg_serial`: fixed serial + recomputed page CRC). Always write through `synth.write()`.
- `tools/validate_assets.py` finds `resourcepack/**/assets` without needing pack.mcmeta; it checks sounds.json -> .ogg, subtitles in lang/en_us.json, sounds.yml key/fallback/volume/pitch/source.
- `tools/review.py` labels a sound "final" only if its paragraph in `design/sounds.md` contains `Status: final`; needle is the ogg stem with trailing digits stripped (`cookie_click_`).
- Paper 26.3 SoundCategory has `UI` -> `source: ui`.
- Worktree-isolation guard refuses Bash commands that mix `cd && ...` with heredoc file writes or names like `.gitignore`; use the Write tool for new files and keep Bash commands simple.
- Writes to the main checkout's `.claude/agent-memory/` are refused from an isolated worktree; memory goes in the worktree copy (2026-10-18).

Verified 2026-10-19 (starter-house), stable unless the files change:
- `tools/validate_assets.py` (check_sounds_config): a key without ':' is prefixed `minecraft:` and skipped, so `key: "@block"` passes validate-assets only incidentally; sound-check never reads sounds.yml.
- `SoundService` (bakery-core, commit 78318c9) uses ONE volume/pitch for both key and fallback; there is no fallback-volume/pitch field. `@block` support is a plugin-coder task in starter-house.
- `src/test/.../ShippedContentConsistencyTest` hard-codes the 4 bakery-core events; new events don't need test edits from us.
- synth.py now has `gain_to_lufs` (pyloudnorm, same as sound-check), `soft_limit`, `fft_filter` (circular), `place_circular`. Generator LUFS lands ~1 dB lower after Vorbis on spiky material (crackle -19.0 -> -19.1 ok; earlier -20 -> -20.2).
- Guard refuses Bash that runs `python3 - <<EOF` editing JSON plus `git` in one command, and `for s in ...; do mise exec ...$s` loops; write JSON with the Write tool, run scripts one per command.
- `mise run check` in a fresh worktree: ~1 min incl. 238 tests (2026-10-19).

Verified 2026-10-20 (factory-makeover), stable unless tools/sound_check.py changes:
- Fresh worktree: `mise exec -- python` has no numpy until `mise run assets-deps` runs once (mise run sound-check etc. depend on it; plain mise exec doesn't).
- Seam window for loops (tools/sound_check.py): last 2 ms must be < -30 dBFS AND first audible sample (> -40 dBFS) within 5 ms. Tonal loop: Gaussian loop dip 85 % / 80 ms passed; 55 % and 95 % failed.
- config.yml (bakery-core/starter-house): chimney smoke puffs every 10 ticks, crackle sound every 6th puff (= 60 ticks).

Verified 2026-10-21 (conveyor-belt), stable unless SoundService/sound_check change:
- SoundService.java (src/main/java/dev/casperrs/cookieClicker/sound/) still uses one volume+pitch for key and fallback (lines ~105-113).
- `scipy.signal.resample` (FFT) keeps a circular loop seamless when changing its length (used to author a loop for pitch 0.8).
- sound-check loudness warn band is -16 ±4 LUFS: a -20.0 generator target encodes to -20.1 and warns; use -19.8.
- Worktree guard also refuses `sed -i ... $var` (unquoted variable as sed arg); spell the path out.
