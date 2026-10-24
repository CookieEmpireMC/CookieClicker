---
name: storage-mariadb
description: mariadb feature storage facts (schema v4, HikariCP pool, /bakery db migrate via runExclusive) and the open runExclusive Error finding at the 2026-10-24 review
metadata:
  type: project
---

Verified 2026-10-24 at `mariadb/plugin-coder` 0cc05c0 (review branch `mariadb/plugin-tester`, tests at 14e7797).

- **Schema v4** (`JdbcPlayerStore.migrations`): `players.name VARCHAR(16)` and index `<prefix>players_baked_idx`. Both steps check JDBC metadata before running (`columnExists`/`indexExists`), so a crashed v3->v4 rerun passes on H2. Tests: `JdbcPlayerStoreTest` v3->v4 pair. Still unverified on MariaDB: `ENGINE=InnoDB`, `CONVERT TO`, `getColumns`/`getIndexInfo` with the MariaDB catalog. No MariaDB server in this sandbox (`which mariadb mysqld` empty).
- **H2 source is upgraded in place**: `H2Migration.run` calls `from.init()`, so a 1.2.x file ends at v4 and the old plugin refuses it. Documented in `docs/integrations.md` ("keep it as a backup"). LOW.
- **runExclusive** (`SessionManager`): saves and unloads sessions (`saveAsync(..., true)`), moves joins into `rejoin`, runs the task on io, then on main clears `exclusive`, rejoins, calls `done`. RuntimeException in the task is caught (done gets null). An `Error` was not caught: `exclusive` stayed true and no player loaded until restart (M1). **Fixed** in `197b9fb` (catch Throwable); regression test committed at tester `1262775`. Original test text, for reference:
  ```java
  @Test
  void anErrorInsideTheMigrationTaskMustNotStrandPlayers() throws Exception {
      UUID bob = UUID.randomUUID();
      assertTrue(sessions.runExclusive(() -> { throw new LinkageError("driver class missing"); }, r -> { }));
      settle(); settle();
      sessions.join(bob); settle(); settle();
      assertTrue(sessions.isLoaded(bob), "an Error must not hold joins for the rest of the uptime");
  }
  ```
  It fails today (`expected: <true> but was: <false>`).
- **Probe**: `PluginWiring.openPool` probes MariaDB only (main thread, enable only); H2 is not probed. Hikari uses `initializationFailTimeout(-1)`.
- **Passwords**: never in `DatabaseSettings.describe()`, `DriverConnectionFactory` passes it as a driver property. `JdbcPlayerStoreTest.databaseSettingsUrlsAndPrefix` already asserts this.
- **Reload** keeps the running database (`PluginSettings.withDatabase`), so `/bakery db migrate` can't target the wrong DB after a reload.
