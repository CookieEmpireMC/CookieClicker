---
name: building-roster-world-facts
description: Visible buildings (building-roster world part, 2026-10-22) - plot layout origin from house box, purchase hook, MessagesTest literal trap
metadata:
  type: reference
---

Verified 2026-10-22 (branch building-roster/plugin-coder-world, commits 0b77c57, 44a8047). Stable for this code.
- Plot stores no layout origin. Layout cell (0,0,0) in the world = `plot.house()` min - `layout.houseAt` +
  `design.origin` (PlotSetup: houseAt world = ax+hx, y0+hy, az+hz; box min = houseAt - origin).
  `building/BuildingLayout.origin` does it with the factory design read at enable/reload.
- Purchase hook: `StationInteractions.setBuildingsBought(Consumer<Player>)`, fired after a successful BUILDINGS row
  buy; nothing else in Economy/session tells about purchases.
- MessagesTest scans every `"plot.x"`/`"house.x"`/`"panel.x"`… string literal in src/main/java (except
  Plugin/Plot/HouseSettings.java) and fails if it is not in messages.yml: config paths with those prefixes go in
  PlotSettings or use a relative section (`c.getConfigurationSection("plot.buildings")` from PlotSettings).
- PlotSetup.Inputs gained `slots` (building-slots.yml text); DecorKeepOut.of(design) kept for DecorKeepOutTest.
- Cleanup 2026-10-22 (commit 803425e): decor keep-out uses `plot.buildings.footprints` (id -> side, config.yml
  only; getConfig falls back to the jar's config.yml for a missing section) via
  `PlotSetup(..., Supplier<Map<String,Double>> footprints)` -> `DecorKeepOut.of(design, slots, footprints)`.
  Scroll vs unlock pulse: `StationView.Panel.scrolled`, set in PanelUpdater.scroll, cleared in update.
- `Economy.nextBuildingCps` has no main caller but RowStateTest uses it: keep it.
- Worktree guard: a Bash command mixing heredoc/sed file writes with a git commit is refused; write memory with
  Write/Edit, then commit in a plain separate command.
