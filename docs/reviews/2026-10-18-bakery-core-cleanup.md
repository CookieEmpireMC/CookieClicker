# Review: bakery-core cleanup pass (branch bakery-core/plugin-coder-cleanup)

Reviewer: Gandalf. Target: Paper 26.x (Java 25). Date 2026-10-18.

## Verdict: READY

## Behaviour-preservation, item by item
- `git diff feature/bakery-core...HEAD -- src/test` is empty (Wesly touched no tests).
- L1: `refreshPanels` now public, wired via `BakeryTicker.setAfterProduce(views::refreshPanels)`; `onLoaded` calls the extracted per-player `refresh`. The ticker now looks up the online player every second instead of only on HUD seconds (one map lookup per loaded player per second). OK.
- L6 (scheme): exact `http`/`https`; `HTTP://` is parsed by URI as scheme "HTTP" and is now rejected (case-sensitive), a deliberate tightening. Info-log half skipped, see follow-up.
- L5: try/catch around `views.shutdown()` and `effects.shutdown()`; session flush no longer skippable. OK.
- L2/L3: new keys `station.cookie.click-effect-offset` 0.1, `station.cookie.brightness` 15, `station.panel.text-brightness` 15, `text-shadow` true, `text-see-through` false, `text-default-background` false: all defaults equal the removed hardcoded values; config.yml documents them.
- L4: `locked` default now 0x90181818 (was falling into the 0xB0202020 default). The shipped config already sets `#90181818`, so only a config missing the key changes. Approved fix.
- PERF-LOW-1/2: entity list built once (immutable, same order); `chunksLoaded` without `List.of`; tag check before PDC read (the sweep tags every view entity, so equal result).
- PERF-LOW-3 `HudTemplate`: see below. `Hud.render` falls back to a real parse when a value is empty or the template did not compile; recompiles when the raw text changes after a reload.
- T1: `StationFile(Path, Logger, Executor, Defaults)` seam; plugin constructor delegates; same default-copy and load behaviour.

## HudTemplate verification (new `hud/HudTemplateTest`)
Fast-path output `equals` a real MiniMessage deserialize for: both shipped `hud.action-bar` templates (3000 random value pairs each from ~440 values: huge 5000-digit, formatter output up to Double.MAX_VALUE/infinity, `<red>`, `</gold>`, `<click:...>`, `&c`, section sign, backslashes, quotes, newlines, NUL, the marker character, `<cookies>`/`<cps>` as values), and for hover (nested too), click, insert, lang, repeated and bold/italic-wrapped placeholders (1500 pairs each; compiled null counts as the parse path). Gradient and rainbow templates compile to null (fall back). A template containing the marker character is never compiled.
Finding: placeholders inside click/insert/lang tag arguments are not resolved by MiniMessage at all (literal text), so those templates legitimately compile and still equal a parse. No mismatch found. The probe guard is what makes this safe; the test pins output equality rather than "null".
Also switched `StationFileTest` to the new seam (Unsafe/JavaPlugin hack removed).

## Performance
L1 adds `refreshPanels` once per second per online player with a session: per station view, per row, one `RowState.of` (a few pow/compare operations) and an equality check. `RowState` is all static between real changes (`needed` is the static unlock threshold), so an unchanged row sends no packets; only affordability flips or purchases re-render. About 10 rows x views per player per second: negligible. HudTemplate removes a MiniMessage parse per player per HUD refresh (now a tree swap). No PERF-HIGH. Spark: profile `/spark profiler start --timeout 60` while idling next to a station with several players; the plugin should stay well under 1% of tick, with `refreshPanels` and `Hud.render` barely visible.

## Results
- `mise run test`: 233 tests, 0 failures (includes 5 new HudTemplate tests; StationFileTest unchanged in count). `check` BUILD SUCCESS, `validate-assets` 0 problems, `sound-check` 7/7, `balance-check` 6/6, `tools-test` 133 OK, ownership-check OK.

## Follow-ups (not blocking)
- L6 info-log for an empty pack URL: `PluginSettingsResourcePackTest.emptyUrlOrHashIsNotSentAndSaysSo` pins exactly 1 problem; leave as is, change test and code together in a later feature.
- Scheme check is now case-sensitive (`HTTP://`): acceptable, mention in release notes if desired.
