# tidy: plugin-coder (config) branch review

**Branch:** `tidy/plugin-tester-config` (= `tidy/plugin-coder-config` merged + test adaptation). **Target:** Paper 26.3, Java 25.
**VERDICT: READY** (no HIGH/MEDIUM findings).

## Checks
- `mise run check`: 415 tests green (405 + 10 new). `validate-assets` 0 problems, `sound-check` 28/28, `balance-check` 19/19.
- Jar: `paper-plugin.yml` has `version: '1.2.2'` filtered, `cookieclicker-libraries.properties` filtered (`h2=...:2.5.252`, `mariadb=...:3.5.10`), `CookieClickerLoader.class` present, no `plugin.yml`, no H2/MariaDB classes shaded.

## Test adaptation (mechanical)
`TextWidth`/`TextPadding` imports (`TextWidthTest` moved to `text/`), `WorldGuardHook.olderThan`, `PlotSettingsTest` (chimney reach assertion and the bad-effect-id test removed: those keys are gone, the effect ids are constants), `ShippedContentConsistencyTest` and `DecorKeepOutTest` read `VisualFallbacks` instead of config.yml keys.

## New tests
- `PluginDescriptorTest`: loader coordinates equal the pom properties; main/loader/api-version/version filtering; no `plugin.yml`; WorldGuard `BEFORE`/required/join-classpath; the declared permission equals `BakeryCommand.PERMISSION` (default op).
- `PluginSettingsOldConfigTest`: the 1.2.1 config.yml (fixture `src/test/resources/config-1.2.1.yml`, from git) gives the same problems and a `PluginSettings` record equal to the new default file (database, pack, hud, plot, house, belt, visuals); an empty config equals the shipped one. This proves "same values" for every moved knob that the old file set.
- `MarkerTemplateTest`: shipped `panel.row.*` templates render equal to a real parse for odd values (tags, `&c`, empty); tags in a value stay literal; gradient template falls back; wrong value count rejected.
- `WarnOnceTest`: 64 racing threads log once; `clear()` re-arms.

## Findings
- HIGH: none. MEDIUM: none.
- LOW-1 Intermediate commit `1579519` deleted `plugin.yml` before `paper-plugin.yml` exists (final tree fine; do not bisect through it).
- LOW-2 `MarkerTemplate` and `WarnOnce` have no callers yet (the world/ui parts are meant to adopt them); until then they are tested but dead code. PERF-W/U items that depend on them are still open.
- LOW-3 Stale text: `storage/DriverConnectionFactory` javadoc and error message say "plugin.yml `libraries:`" (now the loader); `messages.yml` panel comment still says "Background colours: config.yml station.panel.colors" (moved to code). For the ui part.
- LOW-4 `required: true` makes Paper refuse to load us without WorldGuard, so the `plot.missing-worldguard` branch in `onEnable` is only reached when WG is present but disabled or its flags failed. Harmless, same user outcome.
- LOW-5 `WarnOnce` keys are never evicted until `clear()`; callers must not build keys from unbounded input (player names, coordinates).

## Paper-plugin risks (checked)
- **Load order:** `load: BEFORE` + `required: true` keeps WG's `onLoad` ahead of ours; Paper runs every `onLoad` before any `onEnable`, so flag registration is as before. No `depend` list remains.
- **Classpath:** `join-classpath: true` for WG; our code imports `com.sk89q.worldedit` (in `WorldGuardHook`, the only class allowed to). Paper's `PaperPluginClassLoader` group is access-backed by `DependencyContext.isTransitiveDependency`, which uses the reachable nodes of the dependency graph (soft and hard edges), so WorldEdit/FAWE (WG's dependency) is reachable. Source read 2026-10-23 (Paper main `SimpleMetaDependencyTree`, `PaperPluginClassLoader`). Cannot be run offline: in-game first-start check stays on the PR's human list (also with FAWE, whose `provides` name is WorldEdit).
- **Libraries:** drivers come from the loader's library class loader; `DriverConnectionFactory` uses `Class.forName(driver, true, DriverConnectionFactory.class.getClassLoader())`, and `PaperPluginClassLoader.loadClass` asks the library loader, so it resolves. Mirror constant is `MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR`. The loader reads its properties via its own class loader (a loader over the plugin jar, so jar resources should resolve; unverified offline, but a miss fails loudly with "is missing from the plugin jar", so the first-start check catches it).
- **Filtering:** only the two files are filtered; `messages.yml` etc. keep `${...}`-free MiniMessage untouched.
- **Permission:** declared once in `paper-plugin.yml` (`cookieempire.admin`, default op), equals `BakeryCommand.PERMISSION` (test).
- **Wiring:** `PluginWiring.shutdownOrder()` keeps the old disable order step for step (tasks, cinematics, belts, buildings, tracker, outlines, privacy, houses, setup, views, effects, stations, sessions flush last) and now guards `stations.clear()` too; each step independent.

## Standards / behaviour
No wildcard imports or `ChatColor` in the changed files; records and `final` used; `@Nullable` (jspecify) for optional parts. `WarnOnce` is thread-safe (`ConcurrentHashMap.newKeySet().add` is the atomic gate; logging happens after winning it). `MarkerTemplate` is immutable; its fast path is only enabled when swapping markers equals a real parse for three probe value sets, and empty values or values containing the marker character always take the real parse, so `render` equals `parse` (tested on the shipped rows).

## Performance
No new per-tick work. `MarkerTemplate` is the PERF-U1 tool (about 10 us per `buildingRow` parse before); the gain is realised only once `PanelUpdater` uses it. Config load runs on enable/reload only.

## In-game / spark
- First start on 26.3: log shows the drivers downloaded (first run only) and no `plugin.yml`/WorldGuard complaints; `/bakery plot setup` works (WorldEdit classes reachable); try once with FAWE.
- spark (`/spark profiler start --timeout 60`, a few players clicking near a plot): no change from before for this branch.
