---
name: plot-polish-panels-facts
description: Ray-picked panel clicks (PanelLayout), padded centred column, pinned tests kept alive, worktree guard + smoke-run recipe (2026-10-22)
metadata:
  type: reference
---

Verified 2026-10-22 (branch plot-polish/plugin-coder-panels, commits 8556fd3, 37f63bb). Stable unless view/ changes.
- Panel clicks: one Interaction per panel (only a trigger, `ClickTarget.Kind.PANEL`); `StationViews.aim(view, eye, dir, max)`
  picks nearest of cookie box (`Entity#getBoundingBox().rayTrace(Vector, Vector, double)`, verified javap 26.3) and
  `PanelLayout.pick` (row/toggle by rendered bounds). `StationFrame.local/direction` = inverse of `point`.
- Layout numbers live in `PanelLayout` (pure): rows assumed 2 lines, line height 10 px, row bg padded to
  line-width - 4 px, header/toggle padded to column width with line width 1000.
- 1.2.1 (2026-10-22, commit ff9f3e4): column = TEXT column only (rowWidthPx at text-scale = 1.825 blocks), centred on
  the anchor (textLeft -0.9125); icons hang left (icon centre -1.1125); click box centre offset to (-0.175).
  Toggle click band reaches half-way into the gaps above/below. Defaults: header 0.72, bulk 0.5, gaps 0.22/0.28.
  Width budget: paged toggle "▲ [×1]   ×5   ×10   Max ▼" = 136-137 px, fits only at bulk scale <= 0.5 (0.6 needs
  1-space gaps). Measure widths with a Smoke class calling `TextWidth.width(MiniMessage..., 1000)`.
- Guard refuses Bash commands containing `!` in a -Dtest arg when combined with cd/redirects; run
  `mise exec -C <worktree> -- mvn ... "-Dtest=!Class#m1+m2"` as the only command. `Panel.rowPitch()` is DERIVED
  (2 lines + row-gap) because TextWidthTest pins `p.rowPitch()`.
- Legacy row hitbox components + 6-arg Row ctor removed 2026-10-22 (cleanup; no test used them). PanelLayout is now
  public (ROW_LINES public) so config's rowPitch() can use it. PanelUpdaterTest uses the 5-arg Row and 8-arg Panel ctors.
- Toggle label spans: labels get `Component.insertion("cookieclicker:bulk:<MODE>")` (Adventure 5.2 has
  `Component#insertion(String)`), `TextWidth.spans` walks the tree and merges split leaves.
- Width budget: "Own 10 Wheat Farm to unlock" = 144 px > 120, so need-building is "Own <owned> <building>".
- Worktree guard refuses python heredocs and multi-`sed -i` loops ("too complex"): use Edit/Write.
- Smoke-run pure view classes: `mise exec -- mvn -q dependency:build-classpath -Dmdep.outputFile=<scratch>/cp.txt`, then
  javac/java with `-cp "target/classes:$(cat <scratch>/cp.txt)"` (allowed) and a class in the same package (compiled,
  not single-file launch, so package-private access works).
