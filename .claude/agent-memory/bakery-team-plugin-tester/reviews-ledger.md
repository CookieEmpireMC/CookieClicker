---
name: reviews-ledger
description: per area, the commit last verified and what passed; re-review with git diff <commit>..HEAD -- <paths>
metadata:
  type: project
---

Older per-feature ledgers (starter-house, upgrade-holograms, plot-polish, building-roster, panel-align) were folded in on 2026-10-23: their areas were reshaped by tidy, so only the tidy state below counts. Check any of these with `git diff <commit>..HEAD -- <path>`; tests always run in full. Reports live in `docs/reviews/`.

| Area | Verified at | Passed / notes |
|---|---|---|
| tidy architecture/standards/perf review | `8940d31` | `tidy-architecture.md`, `tidy-perf.md` (before numbers) |
| config part (`config/`, `paper-plugin.yml`, loader, `MarkerTemplate`, `WarnOnce`) | tidy/plugin-tester-config (415 tests then) | READY; `PluginDescriptorTest`, `PluginSettingsOldConfigTest` + fixture `config-1.2.1.yml` |
| ui part (`view`, `economy`, `station`, `hud`, `session`, `sound`, `storage`, `text`) | `d2ae11c` | READY; U1-U5 verified, `JdbcPlayerStoreSkipTest`, `PanelTextTest` |
| vfx part (`vfx/**`, `effects.yml`) | `4a6d3b1` | READY; parser output identical to pre-split; V1-V4 |
| world part (`house`, `plot`, `belt`, `building`, `worldguard`) | tidy/plugin-tester-world | READY; `PlotRegistryBoundsTest` |
| cleanup (`LoopPlayer`, `PlotRegistry` TTL, `BeltRider` key) + integration | tip of tidy/plugin-tester-integration (includes `318f1d5`) | READY after the MEDIUM-1 fix; `LoopPlayerTest`; 395 tests / 93 asset files / 28 sounds / 19 balance targets / 73 tools tests |
| older house/sound/balance designer assets | 2026-10-19..22 | asset-only branches, checked through `validate-assets`, `sound-check`, `balance-check`; Rick merges them without a branch review |
| mariadb storage (`storage/**`, `session/SessionManager` runExclusive, `command/DbCommand`, `PluginWiring.openPool`) | `mariadb/plugin-coder` 0cc05c0 | NOT READY (2026-10-24): MEDIUM runExclusive stranded by an Error; see `storage-mariadb.md`. Tests at `mariadb/plugin-tester` 14e7797: 404 tests green |

| build-quality code (`house/design` ground/decor/fence, `house/setup` DesignBlocks/planner/backup, `plot` LiquidGuard/Box/PlotCodec, `house/cinematic/HouseShapes`, `tools/review`) | `build-quality/plugin-coder` b13b6c3 (merged cb8c832) | READY (2026-10-24, re-review of 10e2279..b13b6c3): HIGH-1 fixed (`PlotRegistry.inPasteArea`, LiquidGuard scoped to paste areas; regression `PlotRegistryBoundsTest.liquidFlowIsStoppedOnly...`), MEDIUM-1 fixed (review-render loopback check, random stored password, `tools/test_review_render.py`), LOW-1 (plotChanged hook in HousePlacer), LOW-2, LOW-3 fixed. Open LOW: server.properties (holds the RCON password) is not chmod 600; LOW-4 Python planner drift (follow-up); PERF-LOW boundsOf O(plots+regions) per 30 s. Tests at `build-quality/plugin-tester` cf2daf0 + dd05d80: 417 Java green, tools-test 81 green. |

| countryside-builds setup (`farm/**`, `house/setup/FarmSetup|FarmBackup*|ChunkHold|PasteProgress`, `command/FarmCommand`, `config/FarmSettings`, `PlotRegistry.inPasteArea`, `PlotFile` farms, `BakeryCommand` root) | `countryside-builds/plugin-coder-setup` 7fc2604 (tests `countryside-builds/plugin-tester` 87dd3dd, 436 green) | READY (2026-10-25, re-review 7fc2604..dd016b7, tests e747f3c, 439 green): MEDIUM-1, MEDIUM-2, PERF-MEDIUM fixed with regression tests; follow-ups: off-main Structure serialization unverified, undo holds all slabs uncompressed. Details: `countryside-farm.md` |

Re-review: `git diff 318f1d5..HEAD -- src/main/java/dev/casperrs/cookieClicker/sound`.
