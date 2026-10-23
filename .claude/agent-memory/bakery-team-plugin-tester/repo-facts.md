---
name: repo-facts
description: durable facts about CookieClicker code and layout numbers a reviewer re-derives otherwise (post-tidy class names), plus known open issues
metadata:
  type: project
---

Verified at tidy integration 2026-10-23 (feature/tidy + cleanup). Package map and rules: `docs/architecture.md`; do not copy it here.

**Layout (post-tidy names)**: house = `house/design`, `house/setup` (`HousePlacer`, `PlotSetup`, `SetupPlanner`), `house/cinematic` (`HouseCinematics`, `PlotAmbience`, camera). Panel pure types in `view/panel` (`PanelLayout`, `PanelRows`, `PriceTween`, `RowState`...); `PanelUpdater`/`PanelText` stay in `view` (package-private). Text helpers in `text/` (`TextWidth`, `TextPadding`, `MarkerTemplate`, `NumberFormatter`, `Messages`). `log/WarnOnce`. Config records in `config/` (`PluginSettings`, `StationVisualSettings`, `VisualFallbacks`, ...); root `CookieClicker` (lifecycle), `PluginWiring`, `PluginPart`, `PluginExecutors` (single IO thread), `CookieClickerLoader` (+ `cookieclicker-libraries.properties` filtered from the pom). No `plugin.yml`.
- Sound: `SoundService` (only `playSound`), `LoopPacer` (per player+event last play tick; `tryPlay` refuses inside one period), `LoopPlayer` (shared 1-tick task for ambient loops; first-beat drift bug MEDIUM-1 in `2026-10-23-tidy-integration.md`: after a due tick `due` must become `now + period`).
- Vfx: `Effects` facade over `Loops`/`BlockDrops`/`PrivateDisplays`/`PresetLibrary`; `LoopJob(every, startDelay, ...)`; floating text uses `MarkerTemplate.render` for `cookie_click`.
- Economy: `CookiePouch.revision()` bumps on `addBuilding`/`addUpgradeLevels` (key for `BeltRider.cpsChanged`); any new CPS input (stars, level, crowns) must bump it or extend the key.
- `PlotRegistry.mayContainPlot`: one union box per world UID of regions flagged plot/house of a known plot, cached 30 s (`BOUNDS_TTL_MILLIS`, injectable clock), cleared by `replaceAll`/`put`.
- `JdbcPlayerStore` save skip: `lastSaved` set after commit only; load/`saveFinal` clear it; `PouchSnapshot` maps are `Map.copyOf` (value equality).
- Config key for visible buildings is `plot.buildings.*`; basket sits on the pedestal spot (inside the front keep-out zone).
- Paper class loading (read from Paper source 2026-10-23, not run on a server): a Paper plugin's loader group = transitive dependencies (`SimpleMetaDependencyTree`), so WorldEdit is reachable through WorldGuard `join-classpath: true`.

**Geometry (shipped factory layout; stable until `StationVisualSettings`/`factory.yml` change)**
- `TextWidth`: 40 px per block, line 10 px, space 4 px; blocks = px*scale/40. Row column 146 px (1.825 blocks), `textLeft` -0.9125, row icon centre -1.1125, header 0.18, toggle 0.125, rowHeight 0.25, `rowBottom(i)` = -0.875 - 0.42 i. Compare widths in integer px (float compare at exactly half a space failed).
- Cookie click box 1.5 wide x 1.7 high on the pedestal axis; fallback panel offset 2.15 clears it by 0.1075. Shipped layout: pedestal 18,6; shop 20,6; clicks 16,6; viewpoint 15,2; belt 18,11 -> 18,7. Viewpoint -> shop panel 6.4 > click-distance 6 (open LOW).
- Overlap test recipe: `PanelCookieOverlapTest` (frame yaw 0, panels at x=+-2, eye 1.62, `BoundingBox.rayTrace` vs `PanelLayout.pick`).
- Pack: `mise run pack` is reproducible (same SHA-1 twice).

**Open items (details in the tidy integration review)**: MEDIUM-1 LoopPlayer; config<->belt/storage package cycle; PrivateDisplays adoption; in-game check of panel text vertical anchor; v2->v3 ALTER not idempotent (use `ADD COLUMN IF NOT EXISTS`).
