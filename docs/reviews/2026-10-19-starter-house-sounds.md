# Review: starter-house / sound-designer (2026-10-19)

Target: Paper 26.x (Java 25). Reviewer: Gandalf. Branch head reviewed: 319fdff (+ test commit).

## Results
- `mise run check`: green (238 tests before my addition, 239 after). `validate-assets`: 12 files, 0 problems. `sound-check`: 17 sounds, 0 failed (all mono 44.1 kHz; new peaks -2.3 to -5.1 dBFS; fanfare -16.0 LUFS, crackle -19.1 LUFS). `tools-test`: 133 OK.
- Determinism: re-ran all four `design/sound-scripts/house_*.py`; `git status` stayed clean, the .ogg files are byte-identical.
- Contract (brief lines 87-91): keys, fallbacks and the `@block` special key match exactly. Variants: hammer 4, block_land 4 (needs 3+), fanfare 1, crackle 1 (3.0 s, within 2-4 s), source `ambient`.
- Subtitles: all four events have a subtitle entry in `sounds.json` and a key in `lang/en_us.json`.
- Loop seam: the claim is plausible. End-to-start jump is -55 dBFS, no crackles within 60 ms of the loop point, and the design says to replay every 60 ticks. At pitch 0.97-1.03 the file lasts 2.91-3.09 s, so a 3.0 s replay leaves a gap of up to 0.09 s or overlaps by up to 0.09 s; the bed dips at the seam, so this is benign. Needs a human listen (already listed in `design/sounds.md`).
- Added test `ShippedContentConsistencyTest.starterHouseSoundEventsMatchTheBriefContract` (plain file reads). It passes against current production code and also checks `@block` as a special key (no sounds.json entry, vanilla fallback).

## Bugs
None blocking.

## Notes
- LOW: `house_block_place` (`@block`) is not played yet. SoundService support is plugin-coder pass 1, so the event is warned once and skipped until then. Integration review must confirm that `@block` is implemented and honours `volume`, `pitch-*`, `source` and `fallback`.
- LOW: chimney replay interval (60 ticks) is a coder constant. At pitch 0.97 the file lasts 3.09 s, so about 0.09 s overlaps. This is inaudible given the dip; keep the interval at 60 ticks or lower.
- Ownership: this branch also carries the orchestrator's earlier main-line commits (docs, agents, CLAUDE.md). That is expected for a branch cut from the feature branch; Rick should run `ownership-check` as usual.

Performance: sounds only. Hammer and land repeat per block with small files (4-5 KB); no code on this branch, so no hot-path cost. Profile with spark during the cinematic only.

Needs human listen: all four are placeholders (see `design/sounds.md`).

VERDICT: READY
