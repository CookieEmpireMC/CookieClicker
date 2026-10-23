# tidy

**Status:** planned  <!-- planned | in progress | review | done -->
**Created:** 2026-10-23 · **Depends on:** panel-align (merged, PR #14, 1.2.1)

## Goal
Make the codebase smaller and easier to work in before `ingredient-farm`: far fewer comments, a short `config.yml`
with only the options an admin really sets (resource pack on top), code that follows Java standards and is well
structured, and **less server load**: a performance pass plus the move to `paper-plugin.yml` (the queued `optimize`
feature, decided 2026-10-18). **No gameplay change:** everything a player sees, hears and earns stays exactly the same.

## Player story
Nothing changes for players. As an admin, I open `config.yml` and see the resource pack settings first, then a
short list of options I would actually change. As a coder agent, I read classes that are small, named clearly and
commented only where the code can't explain itself.

## Acceptance criteria
### Comments (every owner, in their own files)
- [ ] Remove comments that repeat the code, narrate history ("since 1.1.0", "was …", "unused since …") or explain
      the obvious. Keep only comments a coder agent really needs: non-obvious *why* (version quirks, client
      behaviour, thread rules, maths), public API contracts where the name isn't enough, and format notes in
      config files that admins edit. Javadoc only on non-obvious public types/methods, one or two lines.
- [ ] Applies to `src/main/java` (incl. `vfx`), `src/test/java`, and every YAML in `src/main/resources`
      (incl. `houses/`). `buildings.yml` / `upgrades.yml` keep their short tuning notes (the balance reports refer
      to them) but lose anything outdated.
### Memory (stale knowledge out, current knowledge in)
- [ ] Every agent prunes its own `.claude/agent-memory/bakery-team-<agent>/`: delete facts that are outdated
      (old ways replaced by a better one, classes/files that moved or no longer exist, per-feature notes whose
      lesson is already in the code, docs or a later note), merge duplicates, and keep `MEMORY.md` an accurate
      index. Coders, vfx-designer and plugin-tester do this at the end of their tidy run (after the refactor, so
      new class names are right).
- [ ] The CookieMemory PR (orchestrator, below) also removes outdated rules from the shared skills, not just adds
      the new ones.
### config.yml
- [ ] `resource-pack:` is the first section, then `database:`, then the rest.
- [ ] Every key that no code reads is removed (e.g. `plot.chimney-reach`). Every remaining key is read by code
      that has a built-in default, so a missing key never breaks.
- [ ] Internal tuning knobs an admin has no reason to change (render offsets, scales, tween/tick timings,
      panel/basket/shelf geometry, colours, fallback materials/blocks, footprints, hitbox margins) move out of
      `config.yml` into constants or a defaults class in code, with the **same values**. Kept in `config.yml`:
      resource pack, database, autosave, feature toggles (`hud.enabled`, `plot.buildings.enabled`,
      `house.cinematic.enabled`, `belt.enabled`, `plot.hide-others-near-pedestal`, …), distances that matter for
      server load (view distances / ranges), `debug`. Target: under ~100 lines incl. comments.
- [ ] Old server configs that still have removed keys load without errors or warnings spam (unknown keys are
      ignored).
### Standards and structure
- [ ] A short standards section in `docs/architecture.md` (plugin-tester) that the coders follow: naming, one
      responsibility per class, method length, `final`/records/immutability, null handling (`Optional` vs
      `@Nullable`), no wildcard imports, consistent package-by-feature layout, logging, Bukkit main-thread rules.
- [ ] Classes over ~500 lines are split along clear responsibilities (`StationViews`, `PlotSetup`,
      `HouseCinematics`, `Effects`, `HousePlacer`, `ConveyorBelts`, `EffectsConfigParser`), and the review's
      findings on duplication, naming and package placement are fixed.
- [ ] Behaviour-preserving only: `mise run check`, `validate-assets`, `sound-check`, `balance-check` pass, and the
      tests pass with only mechanical changes (renamed/moved classes). Public command, permission, config,
      messages.yml and plots.yml/save formats stay compatible (except the removed config keys).
### Performance
- [ ] The review lists PERF findings per package (per-tick work, per-player loops, repeated lookups, allocations
      in hot paths, packet/entity updates that could be skipped, DB writes that could be batched or skipped when
      nothing changed, effects/particles over their `limits`) with a HIGH/MEDIUM/LOW rating. Every HIGH and MEDIUM
      is fixed by the owning part; LOW ones go to follow-ups.
- [ ] Repeating tasks: each of the ~19 scheduled tasks has a reason to exist; tasks that do nothing when no player
      is near a plot skip early or stop; no two tasks redo the same work.
- [ ] A cheap measurement before/after (`docs/reviews/2026-10-23-tidy-perf.md`, plugin-tester): a JMH-free
      micro-benchmark or timing test for the hottest paths it finds (e.g. economy tick, panel/HUD redraw,
      belt tick), run with `mise run test`, showing the change. No regressions.
- [ ] **paper-plugin.yml** is the plugin definition, with a `PluginLoader` + `MavenLibraryResolver` for the H2 /
      MariaDB drivers, resolved through **Paper's default Maven mirror** (not Maven Central directly). A
      `plugin.yml` stays only if the research note confirms a reason on 26.3 (and which file Paper uses when both
      exist). WorldGuard dependency, load order (flags registered before WorldGuard enables), commands and
      permissions behave as before.
- [ ] **Async:** only calls the research note marks thread-safe on 26.3, and only where a spark profile shows they
      matter. Without a profile from the user, no new async moves in this feature (DB and file I/O are already
      async); the note's list goes to follow-ups. Entity/world work stays on the main thread.
### Release
- [ ] Version bumped to **1.2.2** in this PR.

## Out of scope
- New features, balance changes, asset changes, new dependencies (the drivers move to the loader, they aren't new).
- Async moves without a spark profile (see Performance).

## Assets needed
none (comment/format clean-up in designer-owned YAML only).

## Balance impact
none (`buildings.yml` / `upgrades.yml` values unchanged; golden values unchanged).

## Research needed
`docs/research/paper-plugin.md` (resource-pack-researcher, Citation Cindy), for Paper 26.3:
- `paper-plugin.yml` fields, `PluginLoader` + `MavenLibraryResolver` with Paper's default mirror, dependency /
  load-order syntax for WorldGuard (we register flags in `onLoad`), commands (Brigadier `LifecycleEvents` vs
  `plugin.yml` commands) and permissions; is there any reason to keep `plugin.yml`, and which file wins when both
  exist. Paper plugins are marked experimental: note any risk.
- Thread-safety list for the calls the review flags as hot (scheduler, Adventure sends, display-entity metadata,
  WorldGuard queries), so async moves can follow a spark profile.

## Open questions
none

## Contract (fixed names; change only via the orchestrator)
| Kind | ID | Owner | Notes |
|---|---|---|---|
| doc | `docs/architecture.md` → "Standards" section | plugin-tester | written first; the coders follow it |
| review | `docs/reviews/2026-10-23-tidy-architecture.md` | plugin-tester | findings per package, the split plan for big classes |
| config | `config.yml` order: `resource-pack`, `database`, … | plugin-coder | kept keys listed in the coder's report |

## Tasks
| Agent | Task | Needs (gate) | Branch |
|---|---|---|---|
| resource-pack-researcher | `docs/research/paper-plugin.md` | — | feature branch (docs) |
| plugin-tester | architecture + standards + performance review: standards section, findings with PERF ratings, split plan for the big classes, before-measurements | — | `tidy/plugin-tester-review` |
| plugin-coder (config) | `config.yml` shrink/reorder, `config/` package + `BakeryConfigLoader` + `CookieClicker.java` wiring, constants for moved knobs, comments in these files, **paper-plugin.yml + loader**, version 1.2.2 | review, research | `tidy/plugin-coder-config` |
| plugin-coder (world) | standards, structure, PERF fixes and comments in `house/`, `plot/`, `belt/`, `building/`, `worldguard/` | config part merged | `tidy/plugin-coder-world` |
| plugin-coder (ui) | standards, structure, PERF fixes and comments in `view/`, `economy/`, `station/`, `hud/`, `session/`, `sound/`, `storage/`, `command/`, `format/`, `pack/`, `text/`, plus `messages.yml` / `plugin.yml` comments | config part merged | `tidy/plugin-coder-ui` |
| vfx-designer | the same (incl. PERF fixes) for the `vfx` package and its tests, `effects.yml` comments | review | `tidy/vfx-designer` |
| sound-designer | `sounds.yml` comments | — | `tidy/sound-designer` |
| model-designer | `houses/*.yml` comments | — | `tidy/model-designer` |
| balance-designer | outdated notes in `buildings.yml` / `upgrades.yml` | — | `tidy/balance-designer` |
| orchestrator | after the integration review: CookieMemory PR (user merges) moving the lasting rules into the shared team knowledge (`bakery-context`): code standards + comment rule, performance rules (per-tick/per-player work, scheduled tasks, DB writes, async only with a spark profile + the thread-safety table), minimal config.yml (admin options only, resource pack first, defaults in code), paper-plugin.yml + loader pattern; and removes rules in the shared skills that tidy made outdated | integration READY | CookieMemory branch |
| plugin-tester | test comments; adapt tests to moved/renamed classes; review each code branch, then integration incl. after-measurements | owner DONE | (reviewed branch) |

The world and ui parts only move classes inside their own packages; a move across the two goes into their report
instead. Neither changes `config/`.

## Status
| Agent | Status | Branch | Verdict | Notes |
|---|---|---|---|---|
| | planned | | | |

## Merge order
1. research note + plugin-tester review (docs), 2. model-designer, sound-designer, balance-designer, 3. vfx-designer,
4. plugin-coder (config), 5. plugin-coder (world), 6. plugin-coder (ui)

## Needs human review
- [ ] Your server's `config.yml`: compare with the new default (removed keys are ignored; copy your url/sha1/database over)
- [ ] Paper loads the plugin from `paper-plugin.yml` on 26.3 and downloads the drivers through the mirror (first start)
- [ ] Optional: a spark profile (`/spark profiler start` … `stop`) with a few players on the plot, for the async follow-up
- [ ] In game: quick smoke test (plot entry cinematic, panels, buying, belt, effects, sounds) that nothing changed
