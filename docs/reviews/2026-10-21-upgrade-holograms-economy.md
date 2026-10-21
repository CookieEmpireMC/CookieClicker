# Review: upgrade-holograms / plugin-coder-economy

- **Reviewer:** Gandalf (plugin-tester), branch review
- **Branch:** `upgrade-holograms/plugin-coder-economy` (coder commits a3d9af5, 33f7261, e32654b, 58e128e; tester commits 42bb819, e50facf, 54f9eb6, e70945a)
- **Target:** Paper 26.3 (`pom.xml`), Java 25
- **Contract:** `docs/balance/model.md` ("Bulk cost and Max", "Upgrades", "State per player"), `docs/balance/golden-values.json`
- **Verdict:** VERDICT: READY

## Test results
- `mise run check-quiet`: 598 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS (after the test commits).
- `mise run balance-check`: 6/6 targets pass.
- `mise run tools-test`: 134 tests OK, 1 skipped (`ownership.test_ownership.LibSyncTests`, "no .claude/hooks/ownership_lib.py in this checkout"; environment, not this branch).
- `ownership-check` (`--base feature/upgrade-holograms`): 8 commits checked, all inside their author's files.
- Before the tester commits the branch had 13 failures + 3 errors (the pinned old behaviour listed in the brief). All are updated, none weakened.

### What the tests now cover (economy and storage)
- `GoldenValuesTest` (6, none skipped): `max-bulk`; building `single[]`, `buy-10-from`, and `bulk[]` (exact); every upgrade's definition (type, value, cost, cost-growth, max-level), its `single[L]` for each level below max-level, and `bulk[]` (exact); all 190 `max-affordable[]` cases through `Economy.maxAffordableBuildings/maxAffordableUpgrade` with every unlock met; click base state; `states[]` with the level map (total CPS, click power, normal/crit/expected click within 1e-9 relative).
- `BulkMathTest` (4, new): exact boundary for k = 1..60 (affordable k, one cookie short → k−1), zero / cap 0 / hard MAX_BULK cap / count 0, growth 1 linear, seeded brute-force match on 3,000 random cases (base, growth, owned, cap, pouch).
- `EconomyTest` (+9): bulk batch rounded once (305 for ×10 from 0), all-or-nothing (CANT_AFFORD leaves pouch and owned untouched), BAD_COUNT / LOCKED / UNKNOWN change nothing, Max buys 4 for 100 cookies and reports CANT_AFFORD for 5, one-time upgrade with count 5 bought once, repeatable levels to max-level (450, then MAX_LEVEL, then 313), Max stops at max-level, `clickPowerGain` with the level override for add, multiplier (2³ → 2⁴) and max-level.
- `JdbcPlayerStoreTest` (+1, updated 4): schema v3; v1 → v3 keeps upgrades as level 1; new v2 → v3 test (upgrades become level 1, then levels round-trip); corrupt rows with column names, level 0 repaired to 1 with a report.
- `NumberFormatterTest` (rewritten, 11): 999, 1K, 999.9K for 999,999 (never 1M), every one of the 33 suffix boundaries (1, 1.5, and 999.99 just below), scientific past DTg (1e102, 1.5e150, 1.7e308), round tenths across 10^6 to 10^99 against the real list, and a BigDecimal never-exceeds check.
- `MessagesTest`, `HudAndTickerTest`: 33 suffixes, `1.2M` / `1.5M`; the cookie glyph U+E000 is checked for presence with the pack and absence without it.
- `tools/balance/test_balance.py` (+3): empty `upgrades: {}` config loads; `series_cost` at k = 1 equals the single cost and batches round once (305); `series_max` matches brute force (1,500 seeded cases); cap 3, cap 0, MAX_BULK.

## Production review (against model.md)
Checked line by line: `BulkMath`, `Economy`, `CookiePouch`, `PouchSnapshot`, `UpgradeDef`, `PurchaseResult`, `EconomyConfigParser`, `JdbcPlayerStore` (migrations, load, replace), `NumberFormatter`, `messages.yml` numbers and the `Messages` defaults.

- **Evaluation order and rounding:** `bulkCost` = `ceil((base × g^n) × ratio)`, ratio `(g^k − 1)/(g − 1)` or `k` at g = 1, one ceil per batch; k = 1 gives ratio exactly 1.0. Matches model.md.
- **Max:** log estimate, clamp to `[1, cap]`, non-finite estimate → cap, two bounded ±1 correction loops; cap = min(cap, MAX_BULK); returns 0 when cap ≤ 0 or the pouch is short of one. Matches. NaN pouch → 0.
- **Atomic buys:** every failure path (UNKNOWN, BAD_COUNT, LOCKED, ALREADY_OWNED, MAX_LEVEL, CANT_AFFORD) returns before `spend` or `add`. After `spend` there is no failing call. `NaN` pouch fails the affordability check.
- **One-time upgrades:** count forced to 1 before the cost; `upgradeBulkCost` returns +∞ once the level is at the cap.
- **MAX_LEVEL / BAD_COUNT:** `level + count > max-level` in `long` arithmetic (no overflow); count outside `1..MAX_BULK`.
- **Effect at level:** additive `value × L`, multipliers `value ^ L` (`UpgradeDef.effectAt`, `EffectType.multiplier()`); CPS fraction applies to total CPS including the override.
- **clickPowerGain with override:** `levelled()` adds an unowned override ID; the override level replaces the stored level for that ID only; the cap is applied before the call.
- **Config validation:** cost > 0, base-cost > 0, cost-growth ≥ 1, max-level only with cost-growth and integer ≥ 1, cost-growth → repeatable, non-negative numbers, unknown effect or building references rejected.
- **Storage:** v2 → v3 is `ALTER TABLE cc_upgrades ADD COLUMN upgrade_level INT DEFAULT 1 NOT NULL`. The `DEFAULT` gives existing rows level 1 on H2 (tested) and MariaDB (accepts the same column definition; read, not run). Load repairs `< 1` (and NULL, defensively) to 1 with a problem entry. Save skips levels ≤ 0 and invalid IDs.
- **Old ×1 path:** `StationInteractions` still calls `buyBuilding` → `buyBuildings(…, 1)`; the existing `buyBuilding` / `buyUpgrade` tests pass.
- **NumberFormatter:** BigDecimal shortest repr gives an exact exponent; truncation with `RoundingMode.DOWN`; 999.9999… shows 999.9; 999,999 shows 999.9K; past the 33rd suffix (10^99) it is scientific.

### Findings
| Severity | Where | Finding | Owner action |
|---|---|---|---|
| LOW | `storage/JdbcPlayerStore.java` `migrations()`, v2 → v3 entry | The ALTER is not idempotent. H2 and MariaDB commit DDL implicitly (per their docs; not reproduced here), so the `setAutoCommit(false)` / rollback wrapper does not cover it. If the process dies after the ALTER but before `writeSchemaVersion`, the next start fails with "duplicate column" and `init()` throws. Fix: `ADD COLUMN IF NOT EXISTS upgrade_level INT DEFAULT 1 NOT NULL` (H2 2.x and MariaDB 10.0.2+). | follow-up |
| LOW | `storage/JdbcPlayerStore.java` `load()` | The `rs.wasNull()` branch is unreachable on the v3 schema (the column is NOT NULL). It is harmless defensive code. | follow-up |
| LOW | `format/NumberFormatter.java` `Settings.groupingSeparator` | Unused since short suffixes start at 1,000. Kept so old settings still load. Remove when the config is next cleaned up. | follow-up |
| LOW | Design question, `Economy.clickPower` | If `max-level` is lowered below a saved level, the effect keeps the saved level (no clamp); only further buys are refused. Probably right (no data loss), but say so in the balance notes. | balance-designer, follow-up |

No HIGH or MEDIUM findings. No PERF-HIGH.

## Performance
- Per click: `clickPower` → `levelled()` (no allocation when the override is null) → one pass over the upgrades the player owns. About 10 operations for the shipped 10 upgrades.
- Per second per player (`BakeryTicker.produce`): `totalCps` = one pass over owned buildings, each with one pass over upgrades (`buildingMultiplier`), then the global product. About 2 × 10 operations. Negligible.
- Per panel row (later branches): `maxAffordable*` is one `log`, one `pow` and at most 2–3 `bulkCost` calls. `clickPowerGain` with an unowned override allocates one list (PERF-LOW: fine at 2 rows per panel; cache per refresh if the panel grows).
- Storage: one `DELETE` plus one batched `INSERT` per upgrade row set on save, as before. Upgrade rows add one `INT` column. No extra queries on load.

## Architecture
- `BulkMath` is pure and shared by buildings and upgrades; `Economy` stays the only place that combines configs and state. Consistent with `docs/architecture.md` (economy: pure Java, main thread). The economy row there now mentions `BulkMath` and the level map (commit e70945a).
- Balance numbers are read from `buildings.yml` / `upgrades.yml`; nothing is hardcoded except `MAX_BULK = 10000` (the model's contract constant, also in `tools/balance/model.py`).

## Notes for the panels branch
- `Economy.buyMaxBuildings` / `buyMaxUpgrades` buy at least 1 and return the reason when nothing is affordable. The panel should show that reason, not a silent no-op.
- `buyUpgrades` returns MAX_LEVEL for a batch that would go over the cap (no partial buy). The Max button must use `buyMaxUpgrades`, so it never hits that case with a count it computed.
- Number formatting is per call with a `BigDecimal`. For tween steps at 1 Hz × rows, that is fine; if tweens format every tick, cache the string per value.

## In-game checks (for the PR's test guide)
- Economy has no visible surface yet; the in-game check starts with the panels branch.
- Profile with spark only if the panel tick cost is in question: `/spark profiler start --timeout 60` while buying, then check the share of the tick in the economy and HUD methods.

VERDICT: READY
