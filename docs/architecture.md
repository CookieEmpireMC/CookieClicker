# Architecture

Maintained by the plugin-tester. Last updated: 2026-10-19 (starter-house pass 1).

**Target:** Paper 26.3 (`26.3.build.205-stable`), Java 25, Maven, `api-version: '26.3'`. JDBC drivers (H2, MariaDB) are `provided` and loaded by Paper from `plugin.yml` `libraries:`. **WorldGuard 7.0.20 + WorldEdit 7.4.6 (or FAWE 2.16)** are `provided` and a hard `depend` (since starter-house).

## Packages (`dev.casperrs.cookieClicker`)
| Package | Role | Threading |
|---|---|---|
| (root) `CookieClicker`, `BakeryConfigLoader` | wiring and lifecycle (enable, reload, disable), config loading | main |
| `economy` | pure-Java model from `docs/balance/model.md`: `Economy`, `CookiePouch`, `EconomyConfigParser`, crit with injected RNG | main (pouch not thread-safe; snapshots cross threads) |
| `format` | `NumberFormatter` (pure, immutable) | any |
| `storage` | `PlayerStore`, `HouseSeenStore`, `JdbcPlayerStore` (H2/MariaDB, schema **v2**: v1 + `house_seen(uuid, plot_id, seen_at)`), `DatabaseSettings` | single IO thread only |
| `session` | `SessionManager`: online pouches, async load/save, load tokens, autosave, flush on disable | main API, IO behind it |
| `station` | `Station`, `StationRegistry` (the live per-player anchors; fed from plot pedestals, listeners for views), `StationCodec`, `StationFile` (now only the one-time migration source + legacy codec) | main; file write on IO |
| `worldguard` | `WorldGuardHook`: the **only** class importing `com.sk89q`; registers `bakery-plot` / `bakery-house` StringFlags in `onLoad`; implements `plot.PlotRegions` through `RegionContainer`/`RegionQuery`; never keeps region objects | main |
| `plot` | Pure: `Plot`, `PlotCodec` (plots.yml), `PlotResolver` (flags first, names as fallback, duplicates = error), `RegionShape` (box + exact `contains`, pack/unpack), `Outline`, `StationMigration` (plan), `Presence`. Services: `PlotRegions` (interface to WG), `PlotRegistry` (plots + resolved region **ids**), `PlotFile` (plots.yml, atomic async write), `PlotService` (load/migrate/update, keeps `StationRegistry` equal to pedestals), `PlotTracker` (+ `PlotPresenceListener`: enter/leave plot/house, checked only on block change / teleport / respawn / join), `PlotOutlines` (`/bakery plot show`, through `Effects`), `PedestalPrivacy` (optional hide-others) | main; plots.yml write on IO |
| `house` | Pure: `HouseDesign`, `HouseDesignParser` (`houses/<name>.yml`), `HouseBackup` (undo data, `BlockAccess` seam), `HouseSeen` (async seen flag on the IO thread, callbacks on main). `HousePlacer`: `/bakery house place|undo`, **the only code that changes world blocks**: structure `.nbt` snapshot + yml cell list under `house-backups/<plot>/` | world on main; files on IO |
| `sound` | `SoundService` (the only place that plays sounds), `SoundConfigParser` | main |
| `view` | Per-player station visuals: `StationViews` (proximity lifecycle task every `station.refresh-ticks`, spin/bob motion task every `segment-ticks`, sweeps, all cleanup paths), `ClickRouter` (the only click entry: owner, distance, per-tick dedupe), `StationInteractions` (click/buy -> economy, Effects, sounds, HUD, panel), pure helpers `StationFrame`, `CookieMotion`, `RowState`, `ClickTarget`; `StationView` = one owner's entities at one station (10 per station for 2 buildings: cookie, hitbox, pedestal, header, 3 per row) | main |
| `vfx` | `Effects` engine (the only place that spawns particles/effect displays), shape maths in `vfx.shape` | main |
| `hud` | `BakeryTicker` (1 Hz production, then HUD and the panel refresh hook), `Hud` (action bar), `HudTemplate` (template parsed once; marker swap, used only when provably equal to a real parse) | main |
| `pack` | `ResourcePackSender` (stacked pack, stable UUID, `replace(false)`), `ResourcePackTracker` (status of **our** pack UUID only) | main |
| `text` | `Messages` (MiniMessage from `messages.yml`, placeholders unparsed) | main |
| `config`, `command` | `PluginSettings`, `PlotSettings`, `HouseSettings`, `StationVisualSettings` (layout/looks only, no balance), `YamlFiles`; Brigadier `/bakery plot ...|house place|undo|reload` (`/bakery station` is gone) | main |

## Rules
- Load: `onLoad` registers the WorldGuard flags (impossible later: WG locks its registry on enable). Needs `depend: [WorldGuard]`; with `paper-plugin.yml` (feature `optimize`) WG must keep loading first.
- Enable order: messages -> WorldGuard check (missing/disabled/flags not registered: log `plot.missing-worldguard`, disable) -> settings, balance, sounds, sessions (+`HouseSeen`), plots (`PlotService.load`: read plots.yml or migrate stations.yml, resolve region ids, fill `StationRegistry`) -> `views.sweep(true)` -> `effects.setPackCheck` + `effects.load()` -> listeners -> `views.start()` -> `PlotTracker.start()`, `PlotOutlines`, `PedestalPrivacy.restart()`, `HousePlacer`, command. The sweep removes every `cookieclicker`-tagged entity, so it must run before Effects load.
- Disable order: stop tasks -> `PlotTracker.shutdown()`, `PlotOutlines.shutdown()`, `PedestalPrivacy.shutdown()` (shows everyone it hid), `HousePlacer.reset()` -> `views.shutdown()` (removes every view, unregisters, sweeps) -> `effects.shutdown()` -> `stations.clear()` -> `sessions.shutdown()` (save, close store, stop IO). Each step is wrapped so a throw never skips the session flush.
- Reload: outlines stopped, `HousePlacer.reset()`, plots re-read and **region ids re-resolved** (`/rg redefine` + `/bakery reload` moves or resizes a plot), stations replaced from pedestals (listeners fire), `effects.reload()`, `views.restart()`, `PlotTracker.resyncAll()`, `PedestalPrivacy.restart()`.
- World writes: only `house/HousePlacer` (`/bakery house place|undo`) changes blocks; `ArchitectureRulesTest` enforces it (and no `com.sk89q` outside `worldguard/`, no region objects cached, region checks guarded by `hasChangedBlock()`). Fake blocks (`sendBlockChange`) arrive with pass 2 and are banned until then.
- Region data: only region **ids** are cached; shapes are re-read from WorldGuard on use. Flags are the source of truth, `plot-region` / `house-region` in plots.yml only a fallback. Plot ids `[a-z0-9_-]{1,64}`.
- Threads: file/DB work (plots.yml, backups, `house_seen`, design files) on the single IO thread; every Bukkit/WorldGuard/world call and every user message on main (`HousePlacer` hops with `submit` / `finish`).
- Entities (cookie, hitboxes, panel): spawned hidden-by-default inside the spawn consumer, then `showEntity` to the owner only; non-persistent, tagged `cookieclicker` + PDC `cookieclicker:station`; removed on quit, world change, station removal, entity-unload, reload, disable; unhealthy views respawn.
- Bukkit/entity calls on the main thread only; DB and file writes on the single IO thread.
- No balance numbers or player text in Java; no NMS; no `playSound` outside `sound/`, no `spawnParticle` outside `vfx/` (enforced by `ArchitectureRulesTest`).
