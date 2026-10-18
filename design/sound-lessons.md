# Sound lessons

One dated bullet per lesson. Promote to `sound-style-guide.md` when it becomes a rule.

- 2026-10-18 (smoke test, test_click): The pitch-drop sine + short low-passed noise tick recipe produced a clean pop on the first try. Second iteration only shortened the tail so the fade-out no longer cut audible ringing.
- 2026-10-18: Normalising RMS over "audible = above -40 dB relative to peak" in the generator lands within about ±1 dB of sound-check's measurement (absolute -40 dBFS threshold, after Vorbis). Good enough; no need to replicate sound-check exactly.
- 2026-10-18: Vorbis decoding leaves a small non-zero first sample even after a 1 ms fade-in, and moves peaks by a few tenths of a dB (seen up to ±1 dB on grainy sounds). Keep the generator's peak ceiling at -2 dBFS.
- 2026-10-18: A spectrogram with a -120 dB floor makes faint codec noise look like content; the preview renderer uses -100 dB.
- 2026-10-18: Paper 26.3 `SoundCategory` has a `UI` constant, so UI sounds use `source: ui` in sounds.yml.
- 2026-10-18 (bakery-core): Crunch grains normalised by peak made single grains set the file's peak, so the peak ceiling, not the RMS target, decided the level (RMS -19 to -20). Soft-clipping the summed crumb layer with tanh (drive 2.2) fixed it in one step. Promoted to the style guide.
- 2026-10-18 (bakery-core): A 2nd-order 7-8 kHz final low-pass still left faint 10-15 kHz fizz from the noise grains on the spectrogram; 4th order removed it without dulling the crunch. Promoted.
- 2026-10-18 (bakery-core): First cant_afford draft sat at 150-260 Hz: fine on headphones, near-silent on laptop speakers, and peak -8.6 dBFS at -19 RMS (sounds quieter than its RMS). Pitched it up to 175-340 Hz with a 140 Hz buzz low-passed at 1.1 kHz so its harmonics carry. Keep error fundamentals above ~170 Hz. Promoted.
- 2026-10-18 (bakery-core): A 2 ms attack on the second thud of a double-thud made a visible broadband click on the spectrogram; 4 ms removed it.
- 2026-10-18 (bakery-core): Bell partials ≥ 4× on a C7 note (8.7 kHz) looked harsh on the spectrogram; high sparkle notes now use only partials 1, 2, 3.
- 2026-10-18: `sf.write(..., compression_level=...)` does apply to Vorbis in soundfile 0.14.0 (0.0 = highest quality, largest file; 1.0 = smallest). The default is fine for short UI sounds; not used.
- 2026-10-18 (bakery-core): 3 generate/measure/preview iterations for 4 events (7 files). All passed sound-check from the first run; iterations were for crest factor, high-frequency fizz and small-speaker audibility.
- 2026-10-18 (bakery-core): libsndfile writes a random Ogg stream serial on every encode, so "fixed seeds" alone still gave different .ogg bytes each run (git churn on every `mise run sounds`). Only the serial and page CRCs differed; `synth.write()` now sets a fixed serial and recomputes the CRCs, and regeneration is byte-identical.
