---
name: ledger-coder-pass1
description: review ledger entry for starter-house plugin-coder pass 1 (WorldGuard, plot, house packages)
metadata:
  type: reference
---
- starter-house/plugin-coder pass 1 reviewed at sync-merge commit 57df6ec (2026-10-19). Areas: worldguard/, plot/, house/, storage schema v2, SoundService.playBlock (`@block`), BakeryCommand plot/house, CookieClicker wiring (onLoad flags, enable/disable/reload order), PlotSettings/HouseSettings, messages plot.*/house.*, docs/integrations.md WorldGuard, tools/validate_assets.py `@block`.
- Passed: thread rules, id-only caching, hasChangedBlock guard, cleanup paths, no com.sk89q outside worldguard/, WG provided/not shaded, MiniMessage placeholders unparsed, stations.yml migration reads data folder, all checks green (376 tests).
- OPEN at that commit: Outline.points hangs for plot.show.max-points < 12 (BLOCKING, test OutlineTest...StillTerminates is @Disabled until fixed); HousePlacer backup written after the paste (MEDIUM); busy flag not reset on exceptions (LOW).
- Re-review with `git diff 57df6ec..HEAD -- src/main/java/dev/casperrs/cookieClicker/plot src/main/java/dev/casperrs/cookieClicker/house src/main/java/dev/casperrs/cookieClicker/config`.
- Test tricks: Proxy-based `org.bukkit.World` (return `NamespacedKey` from `getKey`, not an Adventure `Key`) lets PlotService/PlotRegistry run without a server; `assertTimeoutPreemptively` does not stop a spinning thread, so @Disable known-hang tests.
- Not unit-testable: WorldGuardHook, HousePlacer world/structure code, playBlock sound group, PlotTracker/PedestalPrivacy events (need MockBukkit or in-game).
- Fix round 1 re-reviewed at 696f907+ (2026-10-19): Outline hang fixed (OutlineTest enabled, PlotSettingsTest added, 382 tests green), backup-before-paste order OK, busy via guarded() OK. OPEN: HousePlacer.reset() does not invalidate in-flight IO op, so reload during the backup write still pastes afterwards and can clear busy of a newer op (needs a generation token). Re-review: `git diff 696f907..HEAD -- src/main/java/dev/casperrs/cookieClicker/house`.
