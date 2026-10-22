---
name: ledger-plot-polish-panels
description: plot-polish panels (PanelLayout ray pick, StationViews.aim, TextPadding) reviewed at d2e33dd; what passed, open LOW/in-game items, test recipe
metadata:
  type: project
---

Branch plot-polish/plugin-coder-panels, verified 2026-10-22 at d2e33dd (coder 79ad7c0; Gandalf tests 13175a9, d2e33dd).
`mise run check-quiet` green: 636 tests. Verdict READY (no HIGH/MEDIUM); in-game check of the text anchor still open.

Passed by reading and tests:
- ClickRouter.route: owner check before dedupe; per-tick dedupe unchanged; PANEL/COOKIE resolved by StationViews.aim
  (nearest of cookie box rayTrace and PanelLayout.pick), cookie fallback keeps the reach check; SHELF unchanged.
- PanelLayout.pick math = PanelUpdater shift: textLeftEdge shifts by leftEdgeShift(renderedWidth) (font px, rendered
  width, not pulse width), so bg left edge = textLeft; pick uses the same bounds. Toggle px -> block mapping checked.
- StationFrame.local/direction = inverse of point (checked by hand and by StationFrameTest.localIsTheInverseOfPointAtAnyYaw).
- Default numbers: rows bottom -0.8125 - 0.42 i, icon-plus-text column 1.8 blocks, click box 1.86 wide, cookie gap 0.22.
- Shelf (clicks panel, offset -2.45) sits below the clicks box bottom (-2.10 at max-rows 4): no steal at defaults.

Open (LOW unless noted):
- IN-GAME CHECK (blocking for "done", not for branch READY): the vertical anchor (text grows UP from its position) is
  assumed in PanelLayout and TextWidth docs; no server jar offline to confirm it. If it is centred, row clicks miss by
  half a text height.
- LOW: text-yaw-offset 180 mirrors the text, but pick ignores the knob (default 0 is fine).
- LOW: Interaction is square in XZ, so each panel box is ~1.86 blocks deep (±0.93 forward). Harmless with aim(),
  but a shelf or other hitbox inside that depth and the box's vertical span would have its events dropped.
- LOW legacy: Row 6-arg ctor (ignored Interaction), Panel hitboxOffset/hitboxWidth/hitboxHeight record parts and their
  reads of station.panel.row.hitbox-* (not in config.yml); rowPitch hardcodes 2*10 and /40 (use TextWidth constants).
- LOW: ClickRouter class Javadoc line ~190 chars.
- LOW: pulse-scale (1.08 for 2 ticks) not in pick bounds.
- PERF-LOW: TextPadding.lines re-parses MiniMessage on every row render (only on changed text, fine).
- Brief deviation (for orchestrator): need-building is "Own <owned> <building>" (no "to unlock"); coder's reason is valid
  (144 px would wrap a locked row to 3 lines).

Test recipe (plugin-tester): defaults via `StationVisualSettings.from(new YamlConfiguration(), p -> { })`; a ray for a row is
`new Offset(right, up, -3)` with dir `(0,0,1)`, max 6; RowBox(true, 2, 116) for a two-line row at widthPx 116.

Re-review: `git diff d2e33dd..HEAD -- src/main/java/dev/casperrs/cookieClicker/view src/main/java/dev/casperrs/cookieClicker/config/StationVisualSettings.java src/main/resources/config.yml src/main/resources/messages.yml`.
