---
name: storage-facts
description: Storage/JDBC facts verified in the mariadb feature (2026-10-24) - HikariCP 7.0.2 setup, MariaDB 11.4 docker smoke recipe, schema v4 SQL that works on H2 + MariaDB
metadata:
  type: reference
---

Verified 2026-10-24 (mariadb feature, branch mariadb/plugin-coder) by smoke tests against H2 2.5.252 and a real
MariaDB 11.4 container. Stable for these versions.

**HikariCP 7.0.2** (Maven Central, `com.zaxxer:HikariCP`; depends on slf4j-api, which Paper provides parent-first)
- `HikariConfig#setDataSource(DataSource)` with our own DataSource over `DriverConnectionFactory` = Hikari never
  touches DriverManager (`storage/PooledConnectionFactory`). Also `setMaximumPoolSize/MinimumIdle/ConnectionTimeout/
  MaxLifetime/InitializationFailTimeout(-1)/PoolName`.
- Hikari's own fail-fast logs a stack trace; for one clean log line probe with the raw factory first
  (`PooledConnectionFactory.probe`), then build the pool with initializationFailTimeout -1.
- MariaDB driver messages contain no password: "Access denied for user 'x'@'host' (using password: YES)",
  "Socket fail to connect to host:port. Connection refused".

**SQL that works on both H2 and MariaDB/MySQL**
- Index idempotency: `DatabaseMetaData#getIndexInfo(catalog, null, tablePattern, false, true)` + compare INDEX_NAME
  (H2 stores upper case). `ALTER TABLE t ENGINE=InnoDB, CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`
  is MariaDB-only, re-runnable. Dialect from `getMetaData().getDatabaseProductName()` ("MariaDB"/"MySQL"/"H2").
- `UPDATE ... SET name = COALESCE(?, name)` with setString(null) works on both.
- Documented grants (plugin: SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX; website: SELECT) are enough
  for init + v4 upgrade + migrate; website user gets "DELETE command denied".

**MariaDB smoke recipe** (docker is installed)
`docker run -d --rm --name cc-smoke-mariadb -p 33306:3306 -e MARIADB_ROOT_PASSWORD=smoke -e MARIADB_DATABASE=cookieclicker mariadb:11.4`,
then a single-file Java smoke with the repo classpath (see [[repo-tooling-facts]]); `docker stop cc-smoke-mariadb` after.

**Tester pin**: `PluginDescriptorTest.loaderDownloadsExactlyTheDriverVersionsOfThePom` lists the loader's
coordinates exactly; adding a library to `cookieclicker-libraries.properties` fails it until Gandalf updates it.
