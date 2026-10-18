# Architecture

Maintained by the plugin-tester. Last updated: 2026-10-18 (bakery-core pass 1).

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
| `hud` | `BakeryTicker` (1 Hz production), `Hud` (action bar) | main |
| `pack` | `ResourcePackTracker` | main |
| `text` | `Messages` (MiniMessage from `messages.yml`, placeholders unparsed) | main |
| `config`, `command` | `PluginSettings`, `YamlFiles`; Brigadier `/bakery` | main |

## Rules
- Disable order: stop tasks -> `stations.clear()` -> `sessions.shutdown()` (save, close store, stop IO).
- Bukkit/entity calls on the main thread only; DB and file writes on the single IO thread.
- No balance numbers or player text in Java; no NMS; no `playSound` outside `sound/`, no `spawnParticle` outside `vfx/` (enforced by `ArchitectureRulesTest`).
