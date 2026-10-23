---
name: testing-recipes
description: how to test this repo offline (no MockBukkit/Mockito): golden values, Proxy fakes, shipped files, tools, perf test, sandbox quirks
metadata:
  type: reference
---

Verified 2026-10-23 at tidy integration (392 Java tests, 73 tools tests). Re-check a recipe only if the named class moved.

**Setup**
- Paper API (provided) puts Gson, SnakeYAML, Adventure + plain serializer on the test classpath. No Mockito/MockBukkit in `pom.xml`: test pure classes; Bukkit types via `java.lang.reflect.Proxy` (World with `getUID/equals/hashCode`, Plugin/Server/BukkitScheduler/BukkitTask for `EffectTicker`, a `Display` that records `setTransformation` for `PopJob`; see `PlotRegistryBoundsTest`, `EffectTickerTest`, `PopMotionTest`).
- Not testable without a server: `StationViews`, `ClickRouter`, `HouseCinematics`, `ConveyorBelts` ride task, `LoopPlayer`/`PlotAmbience` (use `Bukkit.getCurrentTick()`/scheduler). Test their pure helpers.
- One class: `MAVEN_ARGS="-Dtest=X" mise run test`. `mise run test` output is ~250 KB: grep `Tests run:`. Per-class counts: `target/surefire-reports/*.txt`.
- `YamlConfiguration.loadFromString` works without a server (config parser tests). Shipped files: `new File("src/main/resources/config.yml")`; messages via `RealMessages.yaml()` / `RealMessages.formatter()`. `getResource("plugin.yml")` finds WorldGuard's jar: check `Files.exists` on the source path.
- Python one-offs needing PyYAML: `.venv/bin/python -I`. Ownership check from the branch itself: `mise run ownership-check <branch> --base feature/<name>`.

**Economy / golden values** (`docs/balance/golden-values.json`): top keys `cost-growth`, `max-bulk`, `click`, `costs.<b>.{single[101], buy-10-from, bulk[]}`, `upgrades.<id>.{...}`, `max-affordable[]`, `building-cps`, `building-milestones`, `states[].upgrades` is a MAP id->level. Max cases: pouch baked = `Double.MAX_VALUE` and buildings owned = 1000 so unlocks never hide the boundary. `GoldenValuesTest` reads it all. Fixtures `EconomyTestSupport` are package-private to `economy`: other packages copy the small `map()`/`economy()` helpers. `PouchSnapshot(pouch, baked, owned, Map levels)`. Schema migration tests: v1 DB uses prefix `zz_` and `schema_version 1`; v2 also needs `zz_house_seen`; corrupt-row inserts must name columns.
- `JdbcPlayerStoreSkipTest`: sentinel-row trick (UPDATE `cc_players` SET pouch=-777 directly, then save; skipped iff the sentinel survives).
- NumberFormatter real list: 33 suffixes K..DTg, `{value}{suffix}` no space.

**Text / Adventure**
- Strict MiniMessage test: building `.name` values leave `<#hex>` open on purpose; parse those leniently.
- `<gradient>` yields `VirtualComponentImpl` (identity equals): compare `toString()` minus `@hash` or plain text. `MarkerTemplate` probe rejects such templates, so they always real-parse.
- Timings (warm JVM, this machine): MiniMessage parse of a 6-placeholder row ~5.5 us, plain serializer 0.15 us.

**Perf test**: `view/TidyPerfTest` prints `[tidy-perf]` lines, writes `target/tidy-perf.txt`, asserts nothing about time. Numbers +-30 % between runs: compare orders of magnitude, same sitting. Results in `docs/reviews/2026-10-23-tidy-perf.md`.

**Probes**: throwaway test classes (e.g. parser equivalence: copy the old file from an extracted `git archive`, rename the class, assert `toString()` equal; particle cost via `EffectsConfigParser.parse(...).preset(id).particleCost()`). Delete before committing.

**Moving/pruning tests**: do it with a Python script from the scratchpad (`git mv` + package line + FQN rewrite + imports from a class->package map). Never remove `@Override` methods inside anonymous classes in `setUp`; Python removal must track `"""` parity. Path-reading tests (`ArchitectureRulesTest`, `HouseCinematicsCameraSourceTest`) break on moves and show up as the only compile/test failures.

**Tools**: `tools/github_app/test_github_app.py`: throwaway key via `openssl genrsa`, fake GitHub = `http.server` on 127.0.0.1, point `$GITHUB_API_URL` at it, pass `environ` dict, always set `GITHUB_APP_CONFIG` to an EXISTING empty file. `tools/balance/test_balance.py` has seeded brute-force series tests. `tools-test` 73 tests, 1 env skip (ownership LibSync without a hooks dir).

**Sandbox quirks (this worktree)**: Bash refuses compound commands that contain `git` (also inside a filename such as `github`) together with loops, `$VAR`, heredocs or redirects, and commands with `for`/`$VAR` paths even without git. Use plain single commands with literal paths, or Write a script to the scratchpad and run `python3 -I <script>`. Pipes (`| grep | tail`) are fine. Use the Read tool for files with odd names.
