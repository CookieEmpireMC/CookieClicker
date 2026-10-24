# mariadb

**Status:** review  <!-- planned | in progress | review | done -->
**Created:** 2026-10-24 · **Depends on:** tidy (merged, PR #15, 1.2.2)

## Goal
Make MariaDB a first-class storage backend so the live server can run on it and a website (leaderboard etc., a
later feature) can read the data while the server runs. Adds a connection pool, a one-time H2 → MariaDB migration
command and a website-friendly schema. Version **1.3.0**. No gameplay change.

## Player story
Nothing changes for players: cookies, buildings and upgrades load and save exactly as before. As an admin, I follow
a short guide to install MariaDB on the same machine, set `database.type: mariadb`, run `/bakery db migrate` once,
and every player's progress is there. If MariaDB is down, the plugin refuses to start instead of silently saving
somewhere else.

## Acceptance criteria
### Pool
- [x] Connections come from a **HikariCP** pool, downloaded by `CookieClickerLoader` like the drivers (version in a
      `pom.xml` property, filtered into `cookieclicker-libraries.properties`, `provided`/not shaded). The pool loads
      the driver class through the plugin's class loader (no `DriverManager`).
- [x] `JdbcPlayerStore` borrows a connection per operation (try-with-resources) instead of keeping one cached
      connection; `ConnectionFactory` stays the seam tests use.
- [x] H2 keeps working (small pool, e.g. max 2; file locking unchanged). MariaDB pool size, connection timeout and
      max lifetime have built-in defaults; at most `database.mariadb.pool-size` (default 4) is exposed in
      `config.yml`.
- [x] Shutdown closes the pool after the final saves (respecting `shutdown-save-timeout-seconds`).
### Fail fast
- [x] With `type: mariadb`, if the database is unreachable or the login fails at enable, the plugin logs one clear
      error (host/port/database/user, never the password) and disables itself. It never falls back to H2.
### Migration
- [x] `/bakery db migrate` (permission `cookieempire.admin`, console or player) copies every table (players,
      buildings, upgrades, house_seen) from the H2 file at `database.h2.file` into the configured MariaDB, in one
      transaction per table batch, off the main thread, then reports row counts per table.
- [x] Refuses when the current type is not mariadb, when the H2 file doesn't exist, or when the MariaDB tables
      already hold player rows (unless `/bakery db migrate --force`, which replaces them). Online players are saved
      first; the migrate runs while nobody's data is mid-save (it goes through the same IO thread).
- [x] Migrated data loads identically (same pouch, baked, prestige, buildings, upgrade levels, house_seen).
### Website-ready schema (schema version 4)
- [x] `players` gets a `name VARCHAR(16)` column (last known name, updated on load/save) and an index on `baked`
      (leaderboard: `ORDER BY baked DESC`). Both via the existing idempotent migration steps, on H2 and MariaDB.
- [x] MariaDB tables use `ENGINE=InnoDB` and `utf8mb4`; column names stay stable from here on (documented as the
      website contract).
### Docs
- [x] A "MariaDB" section in `docs/integrations.md`: install on the same host, create the database, a read/write
      user for the plugin and a **read-only user for the website** (`GRANT SELECT`), the config keys, running
      `/bakery db migrate`, backups (`mariadb-dump`), and the table/column contract for the website.
### Tests (important only)
- [x] Pool-backed store save/load round trip on H2; schema v3 → v4 upgrade keeps data; migrate copies every table
      (H2 → H2 in-memory stands in for MariaDB) and refuses a non-empty target without `--force`.

## Out of scope
- The website itself (later feature).
- Ingredient farm (next feature, 1.4.0).
- Moving `plots.yml` / `stations.yml` into the database.

## Assets needed
| Kind | What | Owner |
|---|---|---|
| model / texture / glyph | none | — |
| sound | none | — |
| effect | none | — |

## Balance impact
none

## Research needed
none (HikariCP + MariaDB Connector/J are standard; the loader already resolves Maven artifacts).

## Open questions
none (decided with the user on 2026-10-23).

## Contract (fixed names; change only via the orchestrator)
| Kind | ID | Owner | Notes |
|---|---|---|---|
| command | `/bakery db migrate [--force]` | plugin-coder | `cookieempire.admin` |
| message keys | `db.migrate.*` | plugin-coder | in messages.yml (started, done with counts, refused reasons, failed) |
| config key | `config.yml: database.mariadb.pool-size` | plugin-coder | default 4 |
| schema | v4: `players.name VARCHAR(16)`, index `<prefix>players_baked_idx` on `players(baked)` | plugin-coder | website contract |
| library | `com.zaxxer:HikariCP:${hikaricp.version}` | plugin-coder | via `cookieclicker-libraries.properties` |
| doc | `docs/integrations.md` → "MariaDB" | plugin-coder | setup guide + website contract |

## Tasks
| Agent | Task | Needs (gate) | Branch |
|---|---|---|---|
| plugin-coder | pool, fail-fast, schema v4, `/bakery db migrate`, messages, config key, docs section, version 1.3.0 | brief | `mariadb/plugin-coder` |
| plugin-tester | tests per "Tests (important only)", branch review, then integration review | coder DONE | `mariadb/plugin-tester` |

## Status
| Agent | Status | Branch | Verdict | Notes |
|---|---|---|---|---|
| plugin-coder | done | `mariadb/plugin-coder` (merged) | READY | fix round: M1 + L1–L3 |
| plugin-tester | done | `mariadb/plugin-tester` (merged) | READY | 405 tests; MariaDB-only SQL needs the human check |

## Merge order
1. plugin-coder
2. plugin-tester (tests)

## Needs human review
- [ ] On the server: install MariaDB per the guide, set `type: mariadb`, restart, run `/bakery db migrate`, check a
      few players' progress.
- [ ] Stop MariaDB and restart the server: the plugin must refuse to start with a clear error.
