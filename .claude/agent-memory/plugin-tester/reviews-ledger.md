# Review ledger
- starter-house/sound-designer: verified at 319fdff (2026-10-19): sounds.yml/sounds.json/lang/ogg for house_* events, scripts deterministic (rerun -> clean git status), check/validate-assets/sound-check/tools-test green. READY. Open: `@block` in SoundService (coder pass 1) to confirm at integration.
- Verified at `b44bfa3` (+ Gandalf test commit): `src/main/resources/houses/default.yml`, `design/scripts/house_default.py`, `design/house-default.md`. Block data valid for 26.3 (Material names checked in paper-api sources jar), regen no diff, assets/check/tools-test green. Verdict READY. Tests: `tools/test_house_design.py` (13).
- Re-review: `git diff b44bfa3..HEAD -- src/main/resources/houses design/scripts/house_default.py`.
