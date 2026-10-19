---
name: ledger-house-design
description: review ledger entry for starter-house/model-designer (merge into reviews-ledger.md)
metadata:
  type: reference
---
- Verified at `b44bfa3` (+ Gandalf test commit): `src/main/resources/houses/default.yml`, `design/scripts/house_default.py`, `design/house-default.md`. Block data valid for 26.3 (Material names checked in paper-api sources jar), regen no diff, assets/check/tools-test green. Verdict READY. Tests: `tools/test_house_design.py` (13).
- Re-review: `git diff b44bfa3..HEAD -- src/main/resources/houses design/scripts/house_default.py`.
