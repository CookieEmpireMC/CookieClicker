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

| build-quality code (`house/design` ground/decor/fence, `house/setup` DesignBlocks/planner/backup, `plot` LiquidGuard/Box/PlotCodec, `house/cinematic/HouseShapes`, `tools/review`) | `build-quality/plugin-coder` 10e2279 | NOT READY (2026-10-24): HIGH LiquidGuard cancels flows inside the union box of ALL plot regions (outside plots too); MEDIUM RCON loopback not enforced on the server side (tool never writes server.properties, default password `review`); LOW Box.ground stale after `house place`; LOW cinematic pit (accepted). Tests at `build-quality/plugin-tester` 24ce235 + a315224: 416 Java green, tools-test 77 green. Re-review: `git diff 10e2279..HEAD -- src/main tools/review`. |

Re-review: `git diff 318f1d5..HEAD -- src/main/java/dev/casperrs/cookieClicker/sound`.
