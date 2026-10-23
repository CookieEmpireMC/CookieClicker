# Tidy: architecture, standards and performance review

Reviewer: Gandalf (plugin-tester) · Date: 2026-10-23 · Base: `8940d31` (main + brief) · Target: Paper 26.3 (`26.3.build.205-stable`), Java 25
Read with: `docs/briefs/tidy.md`, the **Standards** section of `docs/architecture.md`, `docs/reviews/2026-10-23-tidy-perf.md` (before-numbers).

Every part below is a list the coder can work through without re-deriving anything: class names, line numbers (at `8940d31`),
what moves where, and PERF findings. Rating: **HIGH** = lag risk at normal player counts (none found), **MEDIUM** = measurable
or scales with players/plots and is cheap to fix (the part fixes it), **LOW** = follow-up list in the PR.

## Summary

| Area | Result |
|---|---|
| Wildcard imports, `ChatColor`/`§`, NMS, `com.sk89q` outside `worldguard/`, `spawnParticle`/`playSound` outside their packages | clean (checked with grep at `8940d31`) |
| Classes over 500 lines | 8: `StationViews` 1071, `PlotSetup` 843, `HouseCinematics` 782, `Effects` 756, `HousePlacer` 565, `ConveyorBelts` 549, `EffectsConfigParser` 533, `VisibleBuildings` 502 |
| Methods over 60 lines | `PlotSetup.build` (170-440, **270 lines**), `StationViews.spawn` (439-598, 160), `CookieClicker.onEnable` (~145), `HouseCinematics.start` (306-397, ~90), `ConveyorBelts.refresh` (273-354, 80), `HousePlacer.paste` (151-273, 120), `VisibleBuildings.show` (259-342, 85) |
| Unread config keys | 3 (`station.snap-to-block`, `station.snap-yaw-degrees`, `plot.chimney-reach`) plus the dead `PluginSettings.StationSettings` record and `PlotSettings.chimneyReach` |
| Duplicate code across classes | 7 groups (list under "Cross-cutting"), roughly 330 lines |
| PERF | 0 HIGH, 6 MEDIUM, 12 LOW. The hottest steady-state cost is MiniMessage re-parsing of panel row text (10.6 us per row, 90 % of a row render) |
| Scheduled tasks | 19 sites, 6 repeating tasks that run for the whole uptime; 3 can start lazily, 2 are duplicates of each other's work (table below) |

## Cross-cutting (decide once, applies to all parts)

### Shared classes to create (each removes copies from two or more classes)
| New class (package) | Replaces | Owner part |
|---|---|---|
| `house/BackupFiles` (static: `newest(Path)`, `stamp`, `sibling`, `toYaml`, `deleteQuietly`) | identical copies in `PlotSetup` (724-770) and `HousePlacer` (314, 434-460) | world |
| `house/DesignFiles` (`read(JavaPlugin, String file)`: data folder `houses/` first, then the jar; `parseYaml(String)`) | `PlotSetup.read` 159, `HousePlacer.readDesign` 137, `VisibleBuildings.read` 230, `PlotSetup.yaml` 688, `VisibleBuildings.yaml` 240 | world |
| `house/WorldOperation` (the `Op` record, `begin`, `guarded`/`step`, `submit`, `finish`, `finishNow`, `handedOff`, `reset`; constructor takes the busy-message path, the failed-message path and the log label) | `HousePlacer` 461-565 and `PlotSetup` 774-843 (same ~80 lines twice, they differ only in message ids and log text); `OperationGate` becomes its private field | world |
| `house/BlockAccessAdapters` (or a static `HouseBackup.worldAccess(World, Map)`) | `PlotSetup.worldAccess` 708, `HousePlacer.worldAccess` 324 | world |
| `log/WarnOnce` (a `Set<String>` + `warn(key, msg)` + `warn(key, msg, Throwable)` over the injected `Logger`) | `warnOnce` in `SoundService` 170, `StationViews` 995, `WorldGuardHook` 157, `Effects` 745/751 | ui creates the class (package `log`), world + vfx use it; until it is merged each part keeps its copy |
| `text/MarkerTemplate` (generalise `hud/HudTemplate`: parse a MiniMessage once with marker texts, swap values at render, fall back to a real parse when the swap is not provably equal) | `HudTemplate`, and the per-render parse in `PanelUpdater.buildingRow`/click row (see PERF-U1) and `Effects` floating text | ui |
| `view` text helpers `TextWidth`, `TextPadding` move to `text/` (pure; `config.StationVisualSettings.rowPitch()` then imports `text`, not `view`: removes the `config` <-> `view` cycle noted in architecture.md) and `PanelLayout.ROW_LINES` moves to `TextWidth` | `config/StationVisualSettings` imports of `view.PanelLayout`, `view.TextWidth` | ui |

`format/NumberFormatter` is a one-class package used only through `Messages.number`: move it to `text/` and delete `format/`.

### Package layout changes (mechanical moves; tests adapt imports only)
| From | To | Part |
|---|---|---|
| `house/` (40 classes) | `house/design/` (`HouseDesign`, `HouseDesignParser`, `HouseLayout`, `FenceStyle`, `FenceLayout`, `BeltStyle`, `Decor`, `DecorKeepOut`, `BuildingSlots`, `DesignFiles`); `house/setup/` (`HousePlacer`, `PlotSetup`, `SetupFiles`, `SetupPlanner`, `WorldOperation`, `OperationGate`, `HouseBackup`, `SetupBackup`, `BackupFiles`); `house/cinematic/` (`HouseCinematics`, `CinematicRun`, `CinematicGuards`, `RevealPlan`, `RevealOrder`, `FakeBlocks`, `KeepOut`, `HouseBox`, `ChimneySmoke`, `CinematicCamera`, `CameraReturn`, `HouseSeen`) | world |
| `view/` (30 classes) | `view/` keeps `StationViews`, `StationView`, `StationSpawner`, `ShelfController`, `CookieAnimator`, `StationAnchors`, `ClickRouter`, `StationInteractions`, `ClickTarget`, `StationFrame`, `CookieMotion`, `CookiePopMotion`, `BasketLook`; `view/panel/` gets `PanelUpdater`, `PanelLayout`, `PanelRows`, `PanelPages`, `PanelKind`, `PanelRowState`, `RowState`, `UpgradeRowState`, `PriceTween`, `BulkMode`, `BulkModes` | ui |
| `format/NumberFormatter` | `text/NumberFormatter` | ui |
| `station/StationFile`, `station/StationCodec` (only the one-time stations.yml migration) | stay, but mark in the class one-liner that they are migration-only and delete them with the migration in a later feature | ui |

Test impact (for me, not the coders): `ArchitectureRulesTest` lines 101/122/131/163 match on paths (`/house/`, `house/FakeBlocks.java`, `house/<cls>.java`); sub-packages keep `/house/` in the path but lines 122, 131 and 163 need the new file paths. I adapt them in the "adapt tests" task.

### Scheduled tasks (all `Bukkit.getScheduler()` sites at `8940d31`)
| # | Site | Purpose | Period | Runs when | Verdict |
|---|---|---|---|---|---|
| 1 | `CookieClicker:225` `BakeryTicker` | 1 Hz production, HUD, panel refresh hook | 20 | always | keep; takes over the panel update from #11 (PERF-U2) |
| 2 | `CookieClicker:369` autosave | `sessions.saveAll` | `autosave-seconds` x 20 | always | keep; write only changed state (PERF-U3) |
| 3 | `CookieClicker:132` `runTask` | hop IO result to main | once | per callback | keep |
| 4 | `StationViews:177` refresh | proximity spawn/despawn, heal, update | 20 (`station.refresh-ticks`) | always | keep, drop its `update()` for existing views (PERF-U2); per-world station index (PERF-U6) |
| 5 | `StationViews:178` animation | cookie spin/bob segments, price rolls, shelf flights | 1 | always, even with 0 views | start with the first view, cancel after the last (PERF-U5) |
| 6 | `ConveyorBelts:204` | belt cookies, refresh every 10 ticks | 1 | always, even with 0 riders | start when a plot has a belt and a player is in a world with a belt plot; stop when no rider and no surface needs a refresh (PERF-W3) |
| 7 | `HouseCinematics:414` | reveal run step | 1 | only while a run exists | good (already lazy) |
| 8 | `CinematicCamera:194` | keep spectate target, notice loss | 1 | only while a shot exists | good (lazy) |
| 9 | `EffectTicker:44` | all effect jobs | 1 | only while jobs exist | good (lazy) |
| 10 | `PedestalPrivacy:69` | hide others near a pedestal | `check-ticks` | only when enabled | good; allocates a player list + map every run (LOW) |
| 11 | `PlotOutlines:58` | `/bakery plot show` redraw | `redraw-ticks` | per command, ends after `duration-seconds` | good |
| 12 | `ChimneySmoke:108` | delayed steam vent start | once per vent per player | per plot entry | replace with the loop's first-play delay in `Effects.loop(..., startDelay)` (vfx request, LOW) |
| 13 | `ChimneySmoke:139` x2 (`loopTimer`) | crackle + ambient sound per player | 60 / 80 | per player in a plot | one `BukkitTask` per player per sound; belt does the same inside its 1-tick task. Replace all three by `sound/LoopPlayer` (one task per loop event, iterating players that are in range and due) (PERF-W4) |
| 14 | `CinematicCamera:252`, `HouseCinematics:283`, `:592`, `VisibleBuildings:477` | `runTask` next-tick hops | once | per event | keep |
| 15 | `VisibleBuildings:350` | stagger effects of a purchase | once per effect | per purchase | keep (bounded by `max-effects-per-purchase`) |
| 16 | `ResourcePackSender:47` | delay the pack after join | once | per join | keep |

Duplicated work: #1 and #4 both recompute the panel rows once per second per viewer (6.5 us each, see perf doc).

---

## Part 1: config (`config/`, `BakeryConfigLoader`, `CookieClicker.java`)

### 1.1 config.yml: every key (144 leaves; 31 keep, 3 remove, 110 constants)
Target ~70 lines. Order: `resource-pack`, `database`, `hud`, `station`, `plot`, `house`, `belt`, `debug`.
All "constant" values keep their **current default** exactly; the old key is simply no longer read (old configs load silently, no problem line, because unknown keys are never inspected).

**Keep (31)**
`resource-pack.enabled`, `.url`, `.sha1`, `.required` · `database.type`, `.table-prefix`, `.autosave-seconds`, `.shutdown-save-timeout-seconds`, `.h2.file`, `.mariadb.host`, `.port`, `.database`, `.username`, `.password`, `.parameters` · `hud.enabled` · `station.view-distance`, `station.click-distance` · `plot.hide-others-near-pedestal.enabled`, `.radius` · `plot.buildings.enabled` · `house.max-blocks`, `house.cinematic.enabled`, `house.cinematic.camera`, `house.chimney.smoke`, `house.steam.enabled`, `house.ambient.enabled`, `house.ambient.radius` · `belt.enabled`, `belt.max-cookies` (entity budget per player) · `debug.log-click-events`.

**Remove: never read (3)**
| Key | Evidence |
|---|---|
| `station.snap-to-block`, `station.snap-yaw-degrees` | parsed into `PluginSettings.StationSettings` (`PluginSettings:71-72`); nothing calls `settings.station()` (grep: no hit outside `config/`). Delete the record, the accessor, `withDatabase`'s argument and both comments in `station/Station.java:10-11` |
| `plot.chimney-reach` | parsed into `PlotSettings.chimneyReach` (`PlotSettings:48`); `/bakery plot setchimney` no longer exists, no reader. Delete the field and the 3-arg constructor (`PlotSettings:11`) |

**Move to a code constant, same value (110)** (the value stays the default in the class that reads it; no `ConfigurationSection` access left for these)
- resource pack: `resource-pack.prompt` (`"resource-pack.prompt"`), `.send-delay-ticks` (20)
- hud: `hud.refresh-seconds` (1)
- station: `station.refresh-ticks` (20); `station.cookie.{offset, scale, bob-amplitude, bob-period-ticks, spin-seconds-per-revolution, segment-ticks, hitbox-margin, view-range, brightness, click-effect-offset}`; `station.pedestal.{enabled, block, offset, width, height}`; `station.basket.{tier-starts, hysteresis, heights.wicker|bushel|golden, cookie-gap, cookie-max-height, move-ticks, tier-up-switch-ticks}`; `station.panels.{buildings, clicks}.{offset, max-rows}`; `station.panel.{text-yaw-offset, icon-yaw-offset, header-scale, text-scale, text-brightness, text-shadow, text-see-through, text-default-background, line-width, text-padding, same-width, gap-after-header, gap-after-toggle, row-gap, click-margin, bulk.scale, tween.ticks, tween.step-ticks, tween.pulse-scale, row.text-offset, row.text-left-edge, row.icon-offset, row.icon-scale, colors.*, icon-brightness.*, view-range, icons.<11 buildings>.fallback-material, default-fallback-material}`; `station.upgrade-fallback-items.<10 upgrades>`; `station.shelf.{offset, columns, spacing, scale, max-icons, hitbox-size, fly-ticks, tooltip-seconds}`
- plot: `plot.show.{particle-step, duration-seconds, redraw-ticks, max-points, plot-effect, house-effect}`; `plot.hide-others-near-pedestal.check-ticks` (10); `plot.buildings.{stage-2, stage-3, view-range, effect-stagger-ticks, max-effects-per-purchase, yaw-offset, fallback-scale, fallback-block, fallback-blocks.<6>, footprints.<6>}`
- house: `house.cinematic.{trigger, teleport-to-viewpoint, viewpoint-radius, blocks-per-tick, max-falling-displays, layer-delay-ticks, hammer-every, keep-out, slow-amount, fake-air-per-tick, refresh-hidden-ticks, reassert-radius, confetti-height}`; `house.chimney.crackle-ticks` (60); `house.steam.every` (7); `house.ambient.every` (80)
- belt: `belt.{cookie-scale, speed.min, speed.max, interval.min, interval.max, cps-scale, sound-every, fallback-item, surface-yaw, cookie-pitch}`

### 1.2 How to implement without touching the 30 consumers
- Keep every settings record shape (`StationVisualSettings.Panel`, `.Cookie`, `BasketSettings`, `BuildingVisualSettings`, `BeltSettings`, `HouseSettings.*`) and their accessors; only the `from(ConfigurationSection, Consumer<String>)` factories shrink: they read the kept keys and take the rest from `private static final` constants / a `DEFAULTS` instance (`BasketSettings` and `BuildingVisualSettings.defaults()` already work this way). Keep the factory signatures: `PanelUpdaterTest.shippedSettings()` and the `config/*SettingsTest` classes call `from(shipped config, problem -> fail)` and keep passing.
- New `config/VisualFallbacks` (final, static maps): building id -> fallback `Material` name, upgrade id -> fallback item, building id -> fallback block, building id -> footprint (values copied from the current config.yml). Rule for ingredient-farm: a new building id needs one line here; the roster rule in architecture.md ("`buildings.yml` ids = ... = `config.yml station.panel.icons.<id>`") then reads "= `VisualFallbacks`". I rewrite `ShippedContentConsistencyTest` lines 155-190 to read `VisualFallbacks` instead of config.yml.
- Drop the clamp/"problem" code for moved values (about 120 lines in `StationVisualSettings.Reader` users, `HouseSettings.from`, `BeltSettings.from`, `PlotSettings.from`). The tests that assert "bad value reported" for moved keys (`HouseSettingsTest`, `BeltSettingsTest`, `PlotSettingsTest`, `StationVisualSettingsTest`) are deleted or reduced by the coder in the same commit; I re-check at branch review.
- `PluginSettings` after: `database`, `autosaveSeconds`, `shutdownSaveTimeoutSeconds`, `hudEnabled`, `resourcePack`, `viewDistance`, `clickDistance`, `debugClicks`, `plot` (hideOthers enabled/radius, buildings enabled), `house` (maxBlocks, cinematic enabled/camera, smoke, steam, ambient enabled/radius), `belt` (enabled, maxCookies). `withDatabase` stays.
- `ResourcePack.promptKey` and `sendDelayTicks` stay in the record, filled from constants.

### 1.3 `CookieClicker.java` (419 lines, 24 fields, `onEnable` ~145 lines)
- Dead surface: the 12 public getters (`settings()` ... `effects()`, lines 396-419) have no caller outside the class (grep over `src/main` and `src/test`); delete them.
- Introduce `interface PluginPart { default void start() {} default void reload() {} void shutdown(); }` (package-private, root package or `lifecycle/`), implemented by `StationViews`, `ConveyorBelts`, `VisibleBuildings`, `HouseCinematics`, `PlotTracker`, `PlotOutlines`, `PedestalPrivacy`, `Effects`, `SessionManager` (adapt, keep existing public methods). `CookieClicker` keeps an ordered `List<PluginPart>`: `onDisable` iterates it in reverse inside `safely`, `reload()` iterates `reload()`; removes the 35 hand-written lines in `onDisable` and the 25 in `reload`. The special-order notes in architecture.md "Enable/Disable/Reload order" stay true: the list order is the documentation.
- Extract wiring into `PluginWiring` (package-private, same package): `build(CookieClicker, PluginSettings, ...)` returns a record `Parts` (the 24 fields). `onEnable` becomes: load messages, WorldGuard check, settings, `Parts p = PluginWiring.build(...)`, start parts, register command. Target: `CookieClicker` <= 150 lines, `PluginWiring` <= 250.
- `executor` lambdas (`io` thread factory, `mainThread` executor, lines 118-134) become `PluginExecutors` (`io()`, `main()`): 20 lines out of the god method.
- `olderThan` (version compare, `CookieClicker:250`) moves to `worldguard/WorldGuardHook` next to `MIN_VERSION`; `CookieClickerVersionCheckTest` is the only test that references it and I fix the import.

### 1.4 `BakeryConfigLoader` (110 lines, fine)
- Keep as is, but `loadSettings` calls `saveDefaultConfig` + `reloadConfig`: with the shorter config the shipped file is still only written when absent (no overwrite of an admin's old config). Good: that is the required behaviour ("old configs load without errors").
- `newProblemList()` is a wrapper for `new ArrayList<>()`; delete it.
- `loadPlots` is a one-line delegate to `plots.load(collector(problems))`; call it directly from `CookieClicker` (standards: no one-line delegation).

### 1.5 paper-plugin.yml (facts from the code the coder and the research note must reconcile)
- `plugin.yml` today: `load: POSTWORLD`, `depend: [WorldGuard]`, `libraries:` (H2 + MariaDB with `${h2.version}`/`${mariadb.version}`), `permissions: cookieempire.admin (default: op)`, no `commands:` (the Brigadier command is registered through `LifecycleEvents.COMMANDS`, `CookieClicker:206`).
- `pom.xml:55-69` filters **only** `plugin.yml` (`${version}`); add `paper-plugin.yml` there, and give the loader the driver versions from the same properties (a filtered `libraries.properties`, not a second hard-coded copy).
- `onLoad` registers WorldGuard flags (`CookieClicker:95`), WorldGuard classes are linked from `worldguard/` and `CookieClicker.onLoad`: the dependency must be declared so that WorldGuard loads before this plugin **and** its classes are visible (check `load: BEFORE` and `join-classpath` in the research note). `getPluginMeta().getVersion()` is used for WG (`CookieClicker:237`): fine with the new file.
- Open check for the coder: how `cookieempire.admin` (default op) is declared when the permission block is not available in `paper-plugin.yml` (research note); the command node uses `.requires(...)`, so an undeclared permission would deny non-op admins with LuckPerms-only grants silently. Add a test that the permission string used in `BakeryCommand` equals the declared one.

### 1.6 PERF (config part)
- **PERF-LOW** `autosave` period is read at `applySettings` and re-created on every `/bakery reload` (fine).
- **PERF-LOW** `reload()` rebuilds everything even when only messages changed: not hot, ignore.

---

## Part 2: world (`house/ plot/ belt/ building/ worldguard/`)

### 2.1 Split plans (classes over ~500 lines and over-long methods)

**`house/PlotSetup` 843 -> ~320** (ranges at `8940d31`)
| New class | Takes | Lines now |
|---|---|---|
| `SetupFiles` (IO side) | `Inputs` record, the file reads of `setup` (132-145), `read`, `deleteOld`, `newest/stamp/sibling/toYaml/deleteQuietly/deleteLater` (-> `BackupFiles`) | 105-112, 132-167, 456-471, 724-772 |
| `SetupPlanner` (main-thread planning, returns an immutable `SetupPlan` record: `Plot updated`, `List<Change> changes`, `SetupBackup backup`, `HouseBox`, messages ids on refusal) | `build` steps: validation (volume/area/max-blocks 185-205), palette -> `BlockData` (208-230), region + layout origin (234-252), **ground** (`ground`, most-common-height 267-296, spread check), house box + cells (298-326), fence plan (328-345), belt frame (`beltStyle` 473-572, ~100 lines: its own class `BeltFramePlanner`), decor (360), points (`spot`, 363-380) | 170-440, 473-609 |
| `PlotSetup` keeps | `setup`, `undo`, `undoNow`, `restore`, applying changes to the world, `Op` plumbing now in `WorldOperation` | 116-157, 610-685 |

`build` ends up as ~6 calls: `validate`, `plan`, `writeBackup` (IO), `apply` (main), `store`, `finish`. No method over 60 lines.

**`house/HouseCinematics` 782 -> ~380**
| New class | Takes |
|---|---|
| `CinematicRun` (the `Run` class 86-147 as a top-level package-private class; `Shape` record 82) | state of one run |
| `CinematicGuards` (an event-only `Listener`, constructed with the `running` map accessor + `selfTeleport`): `onTeleportGuard`, `onMove`, `onInteract`, `onInteractEntity`, `onBreak`, `onPlace`, `onChunkSent`, `reassertNear` (lines 640-735) | the 8 observers that only read run state and call `plan.requestResend()`/cancel |
| `HouseRebuild` (the `/bakery house rebuild` body 737-775) | command logic |
| keeps | triggers (221-298), `start` (split the 90-line method: `checkPreconditions`, `buildRun`, `beginRun`), tick/step/drop/impact/landed/complete (410-545), abort (546-611), join/quit/death/worldchange |

**`house/HousePlacer` 565 -> ~330**: `WorldOperation` (461-565), `BackupFiles` (434-460, 314), `DesignFiles.read` (137-149); split `paste` (151-273, 120 lines) into `planPaste` / `writeBackup` / `applyPaste`.

**`belt/ConveyorBelts` 549 -> ~330**
| New class | Takes |
|---|---|
| `BeltSurface` (nested `Surface` 96-119 + `spawnSurface` 418-452 + `healthy`/`remove`) | shared moving surface per plot |
| `BeltRider` (nested `Rider` 121-158 incl. `cpsChanged`, `Ride` 160-195) | per-player ride state |
| `BeltEntities` (static: `display`, `item`, `cookieTransform`, `halfHeight`, `fallbackItem` 454-523) | entity factory |
| keeps | lifecycle, `tick`, `refresh` (split `refreshSurfaces` / `refreshRiders`), `ride` |

**`building/VisibleBuildings` 502 -> ~420**: move `load`/`read`/`yaml` (197-244) to `BuildingSlotsLoader` (uses `DesignFiles`), `modelStack`/`fallbackBlock`/`prepare` (392-425) to `BuildingDisplays`. Split `show` (259-342, 85 lines) into `diff` (what changes) / `spawnMissing` / `play`.

`worldguard/`, `plot/` have no class over 320 lines; `plot/PlotCodec` 318 is a dense codec: leave.

### 2.2 Duplication to merge
See the cross-cutting table: `BackupFiles`, `DesignFiles`, `WorldOperation`, `BlockAccess` adapter, `WarnOnce`. Also:
- Per-owner display spawning (hidden-by-default in the consumer, then `showEntity` for the owner, non-persistent, tagged) is written four times: `vfx/Spawner`, `StationViews.spawn`, `ConveyorBelts.display/spawnCookie`, `VisibleBuildings.prepare`. A public `vfx.PrivateDisplays.spawn(Location, Class<T>, Player owner, Consumer<T> setup)` that adds the `cookieclicker` tag and PDC key would remove ~60 lines and make the "limits" accounting possible later. This is a cross-owner API (vfx part) -> REQUEST to the vfx-designer in this feature or a follow-up; the world/ui parts keep their copies until it lands.
- `PlotSetup.ground` (580) and `HousePlacer.loaded` (299) / `PlotSetup.cellsLoaded` (694): chunk-loaded checks over a box; one `ChunkChecks.loaded(World, int[] min, int[] size)`.

### 2.3 Naming and placement
- `BeltStyle` (frame style of the conveyor belt, `houses/belt.yml`) lives in `house/` but belongs with the other design files -> `house/design/BeltStyle` (not `belt/`: `belt/` is runtime, `house/design` is "what a plot is built from").
- `HouseSeen` (async seen flag) -> `house/cinematic/`.
- `OperationGate` public but only used by the two placers -> package-private inside `WorldOperation`.
- `ChimneySmoke` plays smoke, steam **and** ambient hum: rename `PlotAmbience` (one responsibility: "what a player in a plot sees and hears while idle"); it is the consumer of the future `sound/LoopPlayer`.
- `KeepOut` is a game-mechanic name for "speed modifiers while hidden": rename `CinematicKeepOut`.
- `CinematicCamera` calls `setGameMode`; unchanged (enforced by `HouseCinematicsCameraSourceTest`).
- `Plot.java` (178) mixes the record with nested value types (`Box`, `Point`, `Viewpoint`, `Pedestal`, `Belt`, `Cell`); fine as long as it stays a pure record file.

### 2.4 PERF (world part)
| ID | Rating | Where | Finding | Fix |
|---|---|---|---|---|
| PERF-W1 | **MEDIUM** | `plot/PlotTracker.java:82` `update` <- `:136` `onMove` (block change), -> `worldguard/WorldGuardHook.java:85` `at` | For **every block change of every player in every world**: `new Location`, `BukkitAdapter.adapt`, `createQuery()`, `getApplicableRegions`, two flag reads + `trim().toLowerCase` per region, `new Presence`, `known()`. Scales with players x movement, independent of plots near them. | `PlotRegistry` keeps `Set<String> worldKeysWithPlots` and one cached bounding box per world (union of resolved plot `RegionShape`s, refreshed on load/reload/setup). `PlotTracker.update` returns at once (after clearing `current` for that player) when the player's world has no plot or the block is outside the box. Cache `value(region, flag)` per region id until reload. |
| PERF-W2 | LOW | `ConveyorBelts.java:273` `refresh` (every 10 ticks) | `Bukkit.getOnlinePlayers()` twice, `world.getPlayers()` per plot, `packs` HashMap rebuilt: O(P + plots x P_world) twice per second | Iterate `getOnlinePlayers()` once, skip plots without a player (`tracker.presence`); reuse the `packs` map field. |
| PERF-W3 | LOW | `ConveyorBelts.java:204`, `:249` | 1-tick task runs for the whole uptime, with 0 riders it only evaluates `tick % 10` | Start on first rider/plot-with-belt, stop when none (refresh from the 20-tick `StationViews.refresh` hook or `PlotTracker.onEnterPlot`). |
| PERF-W4 | LOW | `ChimneySmoke.java:139` `loopTimer`; `ConveyorBelts.java:384-399` | One `BukkitTask` per player per looped sound (2 per player in a plot) plus a per-tick `loopDelay` map-of-maps lookup per rider in the belt | One `sound/LoopPlayer` task per loop event (period = file length) iterating the registered players; belt registers instead of polling every tick. Also fixes the duplicated loop clock code. |
| PERF-W5 | LOW | `ConveyorBelts.Rider.cpsChanged` | CPS cache key is `sum(owned) + upgradesView().size()`: a level-up of a repeatable upgrade that changes CPS would not refresh (none exists today, `upgrades.yml` repeatables are `click-add`) | Add `CookiePouch.revision()` (incremented by every buy/earn-structure change) and compare it: also gives PERF-U1/U2 a cheap "nothing changed" test. Correctness-LOW now, latent bug. |
| PERF-W6 | LOW | `HouseCinematics.tick` `new ArrayList<>(running.values())` per tick, `reassertNear` | Only while a run exists; bounded by players in a cinematic | ignore (follow-up) |
| PERF-W7 | LOW | `PedestalPrivacy.check` | allocates `List<Pos>`, `Map<UUID,Player>`, `Set<Pair>` every `check-ticks` when enabled (off by default) | reuse; skip when fewer than 2 players near a pedestal |
| PERF-W8 | LOW | `VisibleBuildings.onEntitiesUnload` (487), `StationViews.onEntitiesUnload` (1049) | run for every chunk unload on the server and loop its entities | first line `if (byEntity.isEmpty()) return;` |

Checked and fine: `VisibleBuildings` is event-driven (no repeating task, updates only on purchase/join/enter/leave/plot change/pack swap); `PlotOutlines` stops after `duration-seconds`; world writers do their block work in one main-thread step per command (1,500 blocks) behind a backup: not a tick-cost concern.

---

## Part 3: ui (`view/ economy/ station/ hud/ session/ sound/ storage/ command/ format/ pack/ text/`)

### 3.1 Split plan: `view/StationViews` 1071 -> ~330
| New class | Takes (line ranges at `8940d31`) |
|---|---|
| `StationSpawner` (package-private; builds one `StationView` for an owner and station) | `spawn` 439-598 (split the 160-line method: `spawnCookie`, `spawnPedestalOrBasket`, `spawnPanel(kind)` x2, `spawnShelfRoot`), `styleText` 600, `cookieStack`/`basketStack`/`iconStack` 643-680 |
| `StationAnchors` (static, pure) | `cookieOffset`, `panelOffset`, `anchor`, `mirroredClicks`, `panelFrame`, `upOnly`, `chunkLoaded`, `chunksLoaded` (369-427). `mirroredClicks` is package-visible and tested (`ClickRouter`/tests): keep its signature |
| `ShelfController` | `slotAt`, `syncShelf`, `launchToShelf`, `addShelfHitbox`, `moveShelfIcon`, `removeShelfIcon`, `animateShelf` (682-835) |
| `CookieAnimator` | `segment`, `beforePop`, `popMotion` (917-954), `updateBasket`/`showLook`/`moveUp` (844-894) |
| `StationAim` | `aim`/`Aim` record (265-290) next to `ClickRouter` |
| keeps `StationViews` | lifecycle (`start/restart/shutdown/startTasks`), `refresh` loop, the three maps (`byOwner`, `byEntity`, `targets`), `remove*`, event hooks (1003-1070), `sweep` |

`StationView` (243 lines) is mutable state with public-ish fields used across the new classes: keep package-private; that is the reason for splitting into same-package collaborators instead of a sub-package.

### 3.2 Other long units
- `PanelUpdater` 431: `update` (80 lines) splits into `updateToggle` and `updateRows`; `render` (60) into `renderBuildingRow` / `renderUpgradeRow`.
- `JdbcPlayerStore` 430 (schema + migrations + load + save): extract `Schema` (create/migrate v1-v3, lines ~100-190) so the store is load/save only. Under the limit, so only when touched for PERF-U3.
- `Economy` 397: one responsibility (derived values + purchases); fine. Consider `Economy` -> `Economy` (derived values) + `Purchases` (`buy*`, 311-397) only if tests stay mechanical; **not required**.
- `command/BakeryCommand` 234 fine.

### 3.3 Duplication / naming
- `HudTemplate` (hud/) and the row/floating-text parse paths: one `text/MarkerTemplate` (cross-cutting table).
- `SessionManager.saveAll`/`quit` duplicate the "snapshot then saveAsync" pair: fine.
- `StationRegistry`/`StationListener`/`StationFile`/`StationCodec`: `station` is half legacy; after tidy only `Station`, `StationRegistry`, `StationListener` are live. Add the one-line "migration only" note and leave deletion for a later feature (data migration contract).
- `Messages` (125) vs `MessagesTest`: fine.

### 3.4 PERF (ui part)
Measured numbers are in `docs/reviews/2026-10-23-tidy-perf.md` ("Before").
| ID | Rating | Where | Finding | Fix |
|---|---|---|---|---|
| PERF-U1 | **MEDIUM** | `view/PanelUpdater.java:324` `buildingRow` (via `render` 225-270); click rows `:257` | Every text step re-parses the MiniMessage row template: **10.6 us per row, 90 % of a row render** (8 rows = 85 us per roll step). A viewer in Max mode, or anyone right after a purchase, rolls every row for 8 ticks at one text step per 2 ticks, so ~4 renders x 8-12 rows per second per viewer ~ 0.4 ms/s, linear in viewers. | `text/MarkerTemplate` per (message path, padding): compile once, per render swap `name/owned/cost/cps/needed/amount` (the HUD technique, same equality probe). Invalidate on `Messages.load`. Expected 5-10x on `buildingRow`. Keep `renderedKey` as is. |
| PERF-U2 | MEDIUM | `view/StationViews.java:340` (`refresh` -> `update`) and `hud/BakeryTicker.java` `afterProduce` -> `refreshPanels` | The 1 Hz panel recompute runs twice per viewer per second (two 20-tick tasks with the same job). 6.5 us each, plus `heal()` (`canSee` per entity, ~50/s per view) | `refresh(Player, ...)` calls `update` only right after `spawn`; the ticker owns the 1 Hz update. Run `heal` every 5th refresh (or on `PlayerChangedWorldEvent`/`onLoaded` only). |
| PERF-U3 | **MEDIUM** | `storage/JdbcPlayerStore.java:266-350` `save`, `session/SessionManager.saveAll` | Autosave per player: `UPDATE players` + `DELETE`+batched `INSERT` of **all** `buildings` and `upgrades` rows, although buildings/upgrades rarely change; runs for all N online players every `autosave-seconds` on the IO thread (MariaDB: a network round trip per statement). Also saves idle players whose state did not change. | IO-thread `Map<UUID, PouchSnapshot> lastSaved`: skip the whole save when equal to `lastSaved` (still update `last_seen` at most hourly), skip `buildings`/`upgrades` rewrite when `owned`/`upgradeLevels` equal the last saved maps; `quit` removes the entry. Test: a fake `ConnectionFactory` counting statements for equal snapshots (I add it at review). |
| PERF-U4 | LOW | `sound/SoundService.java:142` `parse` | `Key.key(String)` parse + namespace validation on every sound play (clicks: 15/s per player) | store the parsed `Key` in `SoundDef` at load (`def.key()`, `def.fallback()` -> parsed once). |
| PERF-U5 | LOW | `StationViews.java:178` `animationTick` | 1-tick task over all views for the whole uptime; per view per tick: `motionTarget` compare, `panels.animate` (loop 2 panels x rows, early `animating` check), `animateShelf` early return | Start the task with the first view, cancel with the last; skip views that have nothing due (`nextDueTick` per view). |
| PERF-U6 | LOW | `StationViews.java:308` `refresh(Player, ...)` | O(players x stations) per second; per pair `station.distanceSquared(world-string, ...)`, `getWorld().getKey().asString()` per player | `StationRegistry.inWorld(String key)` list; compute the world key once per player. Irrelevant below ~50 stations. |
| PERF-U7 | LOW | `hud/BakeryTicker.run` | `List.copyOf(sessions.loadedPlayers())` + `sessions.get` Optional per player per second | iterate the session map entries directly. |
| PERF-U8 | LOW | `hud/Hud.refresh` after each click, `StationInteractions.clickCookie` | `sendActionBar` per click (15/s per clicking player, one packet each) | keep for now (output is player-visible); a 2-tick coalescing is a follow-up if spark shows packet cost. |
| PERF-U9 | LOW | `StationInteractions.java:87` clicks | `views.refreshPanels` recomputes both panels after **every** click though only the cookie count changed (6.5 us) | after PERF-W5's `CookiePouch.revision()`: skip unless the revision changed or `pouch` crossed the lowest unaffordable cost (`nextThreshold` per view). |
| PERF-U10 | LOW | `StationViews.onEntitiesUnload` | see PERF-W8 | |
| PERF-U11 | LOW | `session/SessionBukkitListener`, `pack/ResourcePackTracker` | event-driven, no findings | |

Measured and fine: `Economy.click` 0.2 us, `totalCps` 0.6 us, `Hud.render` 1.3-1.8 us.

---

## Part 4: vfx (`vfx/`, its tests, `effects.yml` comments)

### 4.1 Split plan
**`vfx/Effects` 756 -> ~320**
| New class | Takes |
|---|---|
| `PresetLibrary` (package-private) | `Compiled`/`Resolved` records (94-100), `readConfig` (645-671), `resolve` (673-690), `particleData` (690-725), `item`/`modelled` (726-744), `popSequence` (632-643), `presets` map + `get(id)` + `ids()` |
| `BlockDrops` | `dropBlock` x2 (337-419), `impact` (548-570), `landInstantly` (542), `lightAbove` (571), `blockDropSpec` (578-596) with the defaults constant; owns `drops`, `dropEntities` |
| `Loops` | `loop` x2 (425-483) and `loops` `ViewerJobs` |
| `Effects` keeps | lifecycle (`load/reload/shutdown/sweepLeftovers`), `play`/`playLayers`/`pop`, delegating `dropBlock`/`loop` (signatures unchanged), counters, `ready`/`onMainThread` |

**`vfx/config/EffectsConfigParser` 533 -> ~200**: `ParticleLayerParser` (194-370: `particle`, `particleByKey` (public, keep a delegating static on `EffectsConfigParser` if tests call it), `particleData`, `material`, `itemModel`, `thin`), `DisplayLayerParsers` (372-515: `floatingText`, `itemCrumbs`, `scalePop`, `blockDrop`, `brightness`); the root keeps `parse`, `limits`, `presets`, `loopEvery`, `minInterval`, the `layer` dispatch (164, package-private, used by tests), `enumValue`.

### 4.2 Duplication / naming
- `Effects.warnOnce` x2 -> `log/WarnOnce`.
- `new Transformation(...)` is built by hand in `DisplayJob:108`, `PopJob:73/78` and `BlockDropBody:42`: one `vfx/Transformations` helper (translation, rotation, uniform scale).
- `Spawner` is package-private; `StationViews`, `ConveyorBelts`, `VisibleBuildings` re-implement it (see 2.2): make a public `PrivateDisplays` facade (vfx owns it).
- `ParticleJob`, `FloatingTextJob`, `CrumbJob`, `PopJob`, `DropJob`, `LoopJob` are clear; `Job`/`ViewerJobs`/`DisplayRegistry`/`Throttle` fine.

### 4.3 PERF (vfx part)
| ID | Rating | Where | Finding | Fix |
|---|---|---|---|---|
| PERF-V1 | MEDIUM | `Effects.java:266` floating text per play | `miniMessage.deserialize(t.text(), resolver)` for every click number: 3.4 us, plus a `TagResolver` list, 15 clicks/s per clicking player (measured: 3.6 us) | `text/MarkerTemplate` per layer (compile once at load; `<amount>` swapped); fall back to the parse when `compile` returns null. Beats the cost of the `Display` spawn it feeds only slightly, but removes per-click garbage; do it together with PERF-U1 so the helper exists once. |
| PERF-V2 | LOW | `Effects.java:231`, `:307`, `:370`, `:442` | `ready("play('" + effectId + "')")` builds a String on every call (`play` is the click hot path) just for a warning that almost never fires | `ready(Supplier<String>)` or pass the verb and id separately (`ready("play", effectId)`). |
| PERF-V3 | LOW | `vfx/LoopJob.java:28-31` | `stillValid` (`viewer.isOnline() && getWorld().equals`) evaluated **every tick** per loop (3-5 loops per player in a plot); `age % every` after it | check validity only when `age % every == 0`; quit/world change are already handled by `viewerGone`/`cancelFor`. |
| PERF-V4 | LOW | `vfx/DisplayJob.java:78-88` | per tick loops entities x all keyframes comparing `k.tick() == t` | per-track `int[] keyTicks` or a pointer to the next keyframe: O(1) per tick. Only matters with the 8-per-player cap saturated. |
| PERF-V5 | LOW | `vfx/ParticleJob.java:57-77` | one `spawnParticle` packet per point per tick; cap `max-particles-per-effect` 200 bounds a play, not the rate: a looped preset (`factory_steam` 8 particles/37 ticks per vent, `house_chimney_smoke` 3 particles/10 ticks per point) costs ~15-25 packets/s per player standing in a plot | within limits; if spark shows packet pressure, raise `loop-every` in `effects.yml` (asset change, out of scope). |
| PERF-V6 | LOW | `Throttle.forget` | `keySet().removeIf` O(entries) on quit | per-viewer sub-map; negligible. |

Limits check (`effects.yml` `limits`): `Spawner`/`DisplayRegistry.reserve` enforce `max-display-entities-per-player` 8 / global 150, `ViewerJobs.hasRoom` enforces falling blocks 24/240 and loops 8; every display is non-persistent, tagged, removed on completion, chunk unload, quit, reload and shutdown (`DisplayJob.cleanup`, `Effects.shutdown`). No finding.

---

## What I will do next (plugin-tester, "adapt tests" and review tasks)
- After each part merges: adapt `ArchitectureRulesTest` paths, `ShippedContentConsistencyTest` (fallbacks from `VisualFallbacks`), the `config/*SettingsTest` classes, the moved-class imports; keep `TidyPerfTest` (src/test/.../view) running to fill the "After" column.
- Tests I will add at review (not now): save-skipping in `JdbcPlayerStore` (PERF-U3), `PlotTracker` early-out (PERF-W1), `MarkerTemplate` equals-real-parse probe (PERF-U1/V1), "old config with removed keys loads without problems", "permission declared in the plugin file equals the one the command requires".
