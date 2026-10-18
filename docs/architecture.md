# Architecture

Maintained by the plugin-tester. Last updated: 2026-10-18 (bakery-core pass 2).

**Target:** Paper 26.3 (`26.3.build.205-stable`), Java 25, Maven, `api-version: '26.3'`. JDBC drivers (H2, MariaDB) are `provided` and loaded by Paper from `plugin.yml` `libraries:`.

## Packages (`dev.casperrs.cookieClicker`)
| Package | Role | Threading |
|---|---|---|
| (root) `CookieClicker`, `BakeryConfigLoader` | wiring and lifecycle (enable, reload, disable), config loading | main |
| `economy` | pure-Java model from `docs/balance/model.md`: `Economy`, `CookiePouch`, `EconomyConfigParser`, crit with injected RNG | main (pouch not thread-safe; snapshots cross threads) |
| `format` | `NumberFormatter` (pure, immutable) | any |
| `storage` | `PlayerStore`, `JdbcPlayerStore` (H2/MariaDB, schema v1), `DatabaseSettings` | single IO thread only |
| `session` | `SessionManager`: online pouches, async load/save, load tokens, autosave, flush on disable | main API, IO behind it |
| `station` | `Station`, `StationRegistry` (listeners for pass 2), `StationCodec`, `StationFile` | main; file write on IO |
| `sound` | `SoundService` (the only place that plays sounds), `SoundConfigParser` | main |
| `view` | Per-player station visuals: `StationViews` (proximity lifecycle task every `station.refresh-ticks`, spin/bob motion task every `segment-ticks`, sweeps, all cleanup paths), `ClickRouter` (the only click entry: owner, distance, per-tick dedupe), `StationInteractions` (click/buy -> economy, Effects, sounds, HUD, panel), pure helpers `StationFrame`, `CookieMotion`, `RowState`, `ClickTarget`; `StationView` = one owner's entities at one station (10 per station for 2 buildings: cookie, hitbox, pedestal, header, 3 per row) | main |
| `vfx` | `Effects` engine (the only place that spawns particles/effect displays), shape maths in `vfx.shape` | main |
| `hud` | `BakeryTicker` (1 Hz production, then HUD and the panel refresh hook), `Hud` (action bar), `HudTemplate` (template parsed once; marker swap, used only when provably equal to a real parse) | main |
| `pack` | `ResourcePackSender` (stacked pack, stable UUID, `replace(false)`), `ResourcePackTracker` (status of **our** pack UUID only) | main |
| `text` | `Messages` (MiniMessage from `messages.yml`, placeholders unparsed) | main |
| `config`, `command` | `PluginSettings`, `StationVisualSettings` (layout/looks only, no balance), `YamlFiles`; Brigadier `/bakery` | main |

## Rules
- Enable order: settings, messages, balance, sounds, sessions, stations -> `views.sweep(true)` -> `effects.setPackCheck` + `effects.load()` -> listeners -> `views.start()`. The sweep removes every `cookieclicker`-tagged entity, so it must run before Effects load.
- Disable order: stop tasks -> `views.shutdown()` (removes every view, unregisters, sweeps) -> `effects.shutdown()` -> `stations.clear()` -> `sessions.shutdown()` (save, close store, stop IO).
- Reload: stations replaced (listeners fire), `effects.reload()`, `views.restart()` (every view removed, next refresh respawns).
- Entities (cookie, hitboxes, panel): spawned hidden-by-default inside the spawn consumer, then `showEntity` to the owner only; non-persistent, tagged `cookieclicker` + PDC `cookieclicker:station`; removed on quit, world change, station removal, entity-unload, reload, disable; unhealthy views respawn.
- Bukkit/entity calls on the main thread only; DB and file writes on the single IO thread.
- No balance numbers or player text in Java; no NMS; no `playSound` outside `sound/`, no `spawnParticle` outside `vfx/` (enforced by `ArchitectureRulesTest`).
