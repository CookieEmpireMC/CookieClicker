---
name: upgrade-panels-facts
description: Panels/shelf/spin-fix structure after upgrade-holograms panels part, pinned tests to respect, PopJob timing facts (2026-10-21)
metadata:
  type: reference
---

Verified 2026-10-21 (branch upgrade-holograms/plugin-coder-panels, commits 42a9db5, ffb9388). Stable unless code changes.
- `EffectTicker.start(job)` runs the job's first step synchronously inside `Effects.pop(...)`, so a PopMotion is called
  in the click handler itself (step 0), then from the ticker. No public pop duration; StationViews learns the pop's end
  from CookiePopMotion callbacks (`motionTarget`, `motionFromPop`) and segments only at `now > target`.
- View structure: `StationView.Panel` (header, toggle + hitbox, Row slots with changing `id`), `ShelfIcon`;
  `PanelUpdater` does rows/tweens/text, `StationViews` does lifecycle, shelf, one 1-tick animation task.
- Pinned by tests: `RowState.of(e,p,id)` + `Style.messagePath()` = `panel.row.*` (kept as buildings rows);
  StationInteractions constants CLICK/CRIT/PURCHASE/CANT_AFFORD (ShippedContentConsistencyTest regex);
  StationVisualSettingsTest counts problems for bad input, so new keys must not report on absent values.
- Shelf moves = one teleport with `setTeleportDuration` set at spawn (avoids metadata/position packet-order doubts).
- Smoke test pure view classes: `java -cp target/classes:<snakeyaml 2.2>:<jspecify 1.0.0>:<joml 1.10.8> Smoke.java`
  (jars under ~/.m2; full literal paths).
- Guard refused a multi-heredoc `cat > A <<EOF ... cat > B` Bash call; write files with the Write tool.
