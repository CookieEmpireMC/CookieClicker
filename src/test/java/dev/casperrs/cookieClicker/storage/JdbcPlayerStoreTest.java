package dev.casperrs.cookieClicker.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.economy.PouchSnapshot;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcPlayerStoreTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private String url;
    private JdbcPlayerStore store;
    private final UUID player = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        url = "jdbc:h2:mem:store" + COUNTER.incrementAndGet() + ";DB_CLOSE_DELAY=-1";
        store = newStore();
        store.init();
    }

    @AfterEach
    void tearDown() throws SQLException {
        store.close();
        try (Connection c = DriverManager.getConnection(url); Statement st = c.createStatement()) {
            st.execute("SHUTDOWN");
        }
    }

    private JdbcPlayerStore newStore() {
        return new JdbcPlayerStore(() -> DriverManager.getConnection(url), "cc_", () -> 1234L);
    }

    private void sql(String statement) throws SQLException {
        try (Connection c = DriverManager.getConnection(url); Statement st = c.createStatement()) {
            st.execute(statement);
        }
    }

    @Test
    void roundTripKeepsEverythingIncludingUnknownIds() throws Exception {
        PouchSnapshot s = new PouchSnapshot(123.5, 999.25, Map.of("baker", 12, "removed_building", 3), Set.of("mitts", "gone"));
        store.save(player, s);
        LoadedPouch loaded = store.load(player).orElseThrow();
        assertEquals(s, loaded.snapshot());
        assertTrue(loaded.problems().isEmpty());
    }

    @Test
    void playersAreIsolated() throws Exception {
        UUID other = UUID.randomUUID();
        store.save(player, new PouchSnapshot(1, 1, Map.of("a", 1), Set.of()));
        store.save(other, new PouchSnapshot(2, 2, Map.of("a", 7), Set.of("x")));
        assertEquals(1, store.load(player).orElseThrow().snapshot().owned().get("a"));
        assertEquals(7, store.load(other).orElseThrow().snapshot().owned().get("a"));
    }

    @Test
    void saveSanitisesBadValuesAndKeepsBakedAtLeastPouch() throws Exception {
        store.save(player, new PouchSnapshot(50, 10, Map.of(), Set.of()));
        PouchSnapshot s = store.load(player).orElseThrow().snapshot();
        assertEquals(50, s.pouch());
        assertEquals(50, s.baked());
        store.save(player, new PouchSnapshot(Double.NaN, Double.NEGATIVE_INFINITY, Map.of(), Set.of()));
        s = store.load(player).orElseThrow().snapshot();
        assertEquals(0, s.pouch());
        assertEquals(0, s.baked());
    }

    @Test
    void corruptRowsAreRepairedAndReported() throws Exception {
        sql("INSERT INTO cc_players (uuid, pouch, baked, last_seen) VALUES ('" + player + "', CAST('NaN' AS DOUBLE), -7, 0)");
        sql("INSERT INTO cc_buildings VALUES ('" + player + "', 'good', 4)");
        sql("INSERT INTO cc_buildings VALUES ('" + player + "', 'negative', -2)");
        sql("INSERT INTO cc_buildings VALUES ('" + player + "', 'zero', 0)");
        sql("INSERT INTO cc_buildings VALUES ('" + player + "', ' ', 5)");
        sql("INSERT INTO cc_upgrades (uuid, upgrade_id, upgrade_level) VALUES ('" + player + "', ' ', 1)");
        sql("INSERT INTO cc_upgrades (uuid, upgrade_id, upgrade_level) VALUES ('" + player + "', 'fine', 3)");
        sql("INSERT INTO cc_upgrades (uuid, upgrade_id, upgrade_level) VALUES ('" + player + "', 'zero_level', 0)");
        LoadedPouch loaded = store.load(player).orElseThrow();
        PouchSnapshot s = loaded.snapshot();
        assertEquals(0, s.pouch());
        assertEquals(0, s.baked());
        assertEquals(Map.of("good", 4), s.owned());
        assertEquals(Map.of("fine", 3, "zero_level", 1), s.upgradeLevels()); // a level below 1 is repaired to 1
        assertFalse(loaded.problems().isEmpty());
        assertTrue(loaded.problems().stream().anyMatch(p -> p.contains("pouch")));
        assertTrue(loaded.problems().stream().anyMatch(p -> p.contains("negative")));
    }

    @Test
    void newerSchemaIsRefused() throws Exception {
        sql("UPDATE cc_schema_version SET version = " + (JdbcPlayerStore.SCHEMA_VERSION + 1));
        JdbcPlayerStore other = newStore();
        StorageException e = assertThrows(StorageException.class, other::init);
        assertTrue(e.getMessage().contains("newer"));
        // untouched
        assertEquals(JdbcPlayerStore.SCHEMA_VERSION + 1, store.schemaVersion());
        other.close();
    }

    @Test
    void migratesAVersionOneDatabaseToVersionTwoAndKeepsItsData() throws Exception {
        String v1url = "jdbc:h2:mem:v1db" + COUNTER.incrementAndGet() + ";DB_CLOSE_DELAY=-1";
        UUID old = UUID.randomUUID();
        try (Connection c = DriverManager.getConnection(v1url); Statement st = c.createStatement()) {
            // exactly what a v1 plugin left behind: no house_seen table, schema_version = 1
            st.execute("CREATE TABLE zz_players (uuid VARCHAR(36) NOT NULL PRIMARY KEY, pouch DOUBLE NOT NULL, baked DOUBLE NOT NULL, last_seen BIGINT NOT NULL, prestige DOUBLE NOT NULL DEFAULT 0)");
            st.execute("CREATE TABLE zz_buildings (uuid VARCHAR(36) NOT NULL, building_id VARCHAR(64) NOT NULL, owned INT NOT NULL, PRIMARY KEY (uuid, building_id))");
            st.execute("CREATE TABLE zz_upgrades (uuid VARCHAR(36) NOT NULL, upgrade_id VARCHAR(64) NOT NULL, PRIMARY KEY (uuid, upgrade_id))");
            st.execute("CREATE TABLE zz_schema_version (id INT NOT NULL PRIMARY KEY, version INT NOT NULL)");
            st.execute("INSERT INTO zz_schema_version VALUES (1, 1)");
            st.execute("INSERT INTO zz_players VALUES ('" + old + "', 42, 100, 1, 0)");
            st.execute("INSERT INTO zz_buildings VALUES ('" + old + "', 'baker', 7)");
            st.execute("INSERT INTO zz_upgrades VALUES ('" + old + "', 'mitts')");
        }
        JdbcPlayerStore migrating = new JdbcPlayerStore(() -> DriverManager.getConnection(v1url), "zz_", () -> 99L);
        assertEquals(1, migrating.schemaVersion());
        migrating.init();
        assertEquals(JdbcPlayerStore.SCHEMA_VERSION, migrating.schemaVersion());
        PouchSnapshot s = migrating.load(old).orElseThrow().snapshot();
        assertEquals(42, s.pouch());
        assertEquals(Map.of("baker", 7), s.owned());
        assertEquals(Map.of("mitts", 1), s.upgradeLevels()); // a v1 set of bought upgrades loads as level 1
        // the new table works
        assertFalse(migrating.isHouseSeen(old, "main"));
        migrating.markHouseSeen(old, "main");
        assertTrue(migrating.isHouseSeen(old, "main"));
        migrating.init(); // second start: nothing to do, nothing lost
        assertEquals(JdbcPlayerStore.SCHEMA_VERSION, migrating.schemaVersion());
        assertTrue(migrating.isHouseSeen(old, "main"));
        migrating.close();
        try (Connection c = DriverManager.getConnection(v1url); Statement st = c.createStatement()) {
            st.execute("SHUTDOWN");
        }
    }

    @Test
    void migratesAVersionTwoDatabaseAndKeepsEveryUpgradeAsLevelOne() throws Exception {
        String v2url = "jdbc:h2:mem:v2db" + COUNTER.incrementAndGet() + ";DB_CLOSE_DELAY=-1";
        UUID old = UUID.randomUUID();
        try (Connection c = DriverManager.getConnection(v2url); Statement st = c.createStatement()) {
            // exactly what a v2 plugin left behind: upgrades are a set of IDs (no level column), house_seen exists
            st.execute("CREATE TABLE zz_players (uuid VARCHAR(36) NOT NULL PRIMARY KEY, pouch DOUBLE NOT NULL, baked DOUBLE NOT NULL, last_seen BIGINT NOT NULL, prestige DOUBLE NOT NULL DEFAULT 0)");
            st.execute("CREATE TABLE zz_buildings (uuid VARCHAR(36) NOT NULL, building_id VARCHAR(64) NOT NULL, owned INT NOT NULL, PRIMARY KEY (uuid, building_id))");
            st.execute("CREATE TABLE zz_upgrades (uuid VARCHAR(36) NOT NULL, upgrade_id VARCHAR(64) NOT NULL, PRIMARY KEY (uuid, upgrade_id))");
            st.execute("CREATE TABLE zz_house_seen (uuid VARCHAR(36) NOT NULL, plot_id VARCHAR(64) NOT NULL, seen_at BIGINT NOT NULL, PRIMARY KEY (uuid, plot_id))");
            st.execute("CREATE TABLE zz_schema_version (id INT NOT NULL PRIMARY KEY, version INT NOT NULL)");
            st.execute("INSERT INTO zz_schema_version VALUES (1, 2)");
            st.execute("INSERT INTO zz_players VALUES ('" + old + "', 42, 100, 1, 0)");
            st.execute("INSERT INTO zz_upgrades VALUES ('" + old + "', 'mitts')");
            st.execute("INSERT INTO zz_upgrades VALUES ('" + old + "', 'strength')");
        }
        JdbcPlayerStore migrating = new JdbcPlayerStore(() -> DriverManager.getConnection(v2url), "zz_", () -> 99L);
        migrating.init();
        assertEquals(JdbcPlayerStore.SCHEMA_VERSION, migrating.schemaVersion());
        assertEquals(Map.of("mitts", 1, "strength", 1), migrating.load(old).orElseThrow().snapshot().upgradeLevels());
        // from now on the levels themselves are saved and loaded
        migrating.save(old, new PouchSnapshot(42, 100, Map.of(), Map.of("strength", 4, "mitts", 1)));
        assertEquals(Map.of("strength", 4, "mitts", 1), migrating.load(old).orElseThrow().snapshot().upgradeLevels());
        migrating.close();
        try (Connection c = DriverManager.getConnection(v2url); Statement st = c.createStatement()) {
            st.execute("SHUTDOWN");
        }
    }

    @Test
    void aVersionTwoDatabaseThatAlreadyHasTheLevelColumnStillReachesVersionThree() throws Exception {
        // a v2 -> v3 run that crashed after its ALTER but before the version write: the column exists, the version is 2
        String rerunUrl = "jdbc:h2:mem:rerun" + COUNTER.incrementAndGet() + ";DB_CLOSE_DELAY=-1";
        UUID old = UUID.randomUUID();
        try (Connection c = DriverManager.getConnection(rerunUrl); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE zz_players (uuid VARCHAR(36) NOT NULL PRIMARY KEY, pouch DOUBLE NOT NULL, baked DOUBLE NOT NULL, last_seen BIGINT NOT NULL, prestige DOUBLE NOT NULL DEFAULT 0)");
            st.execute("CREATE TABLE zz_buildings (uuid VARCHAR(36) NOT NULL, building_id VARCHAR(64) NOT NULL, owned INT NOT NULL, PRIMARY KEY (uuid, building_id))");
            st.execute("CREATE TABLE zz_upgrades (uuid VARCHAR(36) NOT NULL, upgrade_id VARCHAR(64) NOT NULL, upgrade_level INT DEFAULT 1 NOT NULL, PRIMARY KEY (uuid, upgrade_id))");
            st.execute("CREATE TABLE zz_house_seen (uuid VARCHAR(36) NOT NULL, plot_id VARCHAR(64) NOT NULL, seen_at BIGINT NOT NULL, PRIMARY KEY (uuid, plot_id))");
            st.execute("CREATE TABLE zz_schema_version (id INT NOT NULL PRIMARY KEY, version INT NOT NULL)");
            st.execute("INSERT INTO zz_schema_version VALUES (1, 2)");
            st.execute("INSERT INTO zz_players VALUES ('" + old + "', 42, 100, 1, 0)");
            st.execute("INSERT INTO zz_upgrades VALUES ('" + old + "', 'mitts', 1)");
        }
        JdbcPlayerStore rerun = new JdbcPlayerStore(() -> DriverManager.getConnection(rerunUrl), "zz_", () -> 99L);
        rerun.init();
        assertEquals(JdbcPlayerStore.SCHEMA_VERSION, rerun.schemaVersion());
        assertEquals(Map.of("mitts", 1), rerun.load(old).orElseThrow().snapshot().upgradeLevels());
        rerun.close();
        try (Connection c = DriverManager.getConnection(rerunUrl); Statement st = c.createStatement()) {
            st.execute("SHUTDOWN");
        }
    }

    @Test
    void houseSeenIsPerPlayerPerPlotAndIdempotent() throws Exception {
        UUID other = UUID.randomUUID();
        assertFalse(store.isHouseSeen(player, "main"));
        store.markHouseSeen(player, "main");
        store.markHouseSeen(player, "main"); // second call must not throw (primary key) or duplicate
        assertTrue(store.isHouseSeen(player, "main"));
        assertFalse(store.isHouseSeen(player, "other-plot"));
        assertFalse(store.isHouseSeen(other, "main"));
        try (Connection c = DriverManager.getConnection(url); Statement st = c.createStatement();
             var rs = st.executeQuery("SELECT COUNT(*), MIN(seen_at) FROM cc_house_seen")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
            assertEquals(1234L, rs.getLong(2));
        }
    }

    @Test
    void clearHouseSeenReplaysOnlyThatPlotForThatPlayer() throws Exception {
        UUID other = UUID.randomUUID();
        store.markHouseSeen(player, "a");
        store.markHouseSeen(player, "b");
        store.markHouseSeen(other, "a");
        store.clearHouseSeen(player, "a");
        store.clearHouseSeen(player, "never-seen"); // harmless
        assertFalse(store.isHouseSeen(player, "a"));
        assertTrue(store.isHouseSeen(player, "b"));
        assertTrue(store.isHouseSeen(other, "a"));
    }

    @Test
    void houseSeenRejectsBadPlotIds() {
        assertThrows(StorageException.class, () -> store.markHouseSeen(player, ""));
        assertThrows(StorageException.class, () -> store.markHouseSeen(player, "x".repeat(65)));
    }

    @Test
    void reconnectsAfterTheConnectionDies() throws Exception {
        store.save(player, new PouchSnapshot(3, 3, Map.of(), Set.of()));
        store.close(); // drops the held connection; next call reopens
        assertEquals(3, store.load(player).orElseThrow().snapshot().pouch());
    }

    @Test
    void connectionFailureSurfacesAsStorageException() {
        JdbcPlayerStore broken = new JdbcPlayerStore(() -> {
            throw new SQLException("db down");
        }, "cc_");
        assertThrows(StorageException.class, broken::init);
        assertThrows(StorageException.class, () -> broken.load(player));
        assertThrows(StorageException.class, () -> broken.save(player, PouchSnapshot.EMPTY));
    }

    @Test
    void databaseSettingsUrlsAndPrefix() {
        DatabaseSettings h2 = new DatabaseSettings(DatabaseSettings.Type.H2, java.nio.file.Path.of("/tmp/x/cookies"),
                "", 0, "", "", "", "", "cc_");
        assertTrue(h2.jdbcUrl().startsWith("jdbc:h2:file:/tmp/x/cookies"));
        DatabaseSettings maria = new DatabaseSettings(DatabaseSettings.Type.MARIADB, java.nio.file.Path.of("x"),
                "db.local", 3307, "cc", "user", "secret", "sslMode=trust", "");
        assertEquals("jdbc:mariadb://db.local:3307/cc?sslMode=trust", maria.jdbcUrl());
        assertFalse(maria.describe().contains("secret"));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseSettings(DatabaseSettings.Type.H2,
                java.nio.file.Path.of("x"), "", 0, "", "", "", "", "bad prefix"));
        assertEquals(Optional.of(DatabaseSettings.Type.MARIADB), DatabaseSettings.Type.parse(" MySQL "));
        assertEquals(Optional.of(DatabaseSettings.Type.H2), DatabaseSettings.Type.parse("H2"));
        assertTrue(DatabaseSettings.Type.parse("sqlite").isEmpty());
        assertTrue(DatabaseSettings.Type.parse(null).isEmpty());
    }

    @Test
    void realDriverFactoryOpensH2File(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        DatabaseSettings settings = new DatabaseSettings(DatabaseSettings.Type.H2, dir.resolve("data/cookies"),
                "", 0, "", "", "", "", "cc_");
        try (JdbcPlayerStore file = new JdbcPlayerStore(new DriverConnectionFactory(settings), "cc_")) {
            file.init();
            file.save(player, new PouchSnapshot(8, 8, Map.of("a", 2), Set.of()));
            assertEquals(8, file.load(player).orElseThrow().snapshot().pouch());
        }
        try (JdbcPlayerStore again = new JdbcPlayerStore(new DriverConnectionFactory(settings), "cc_")) {
            again.init();
            assertEquals(2, again.load(player).orElseThrow().snapshot().owned().get("a"), "survives a reopen");
        }
    }

    @Test
    void pooledH2FileRoundTripSurvivesAReopenAndWritesTheName(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws Exception {
        DatabaseSettings settings = new DatabaseSettings(DatabaseSettings.Type.H2, dir.resolve("data/cookies"),
                "", 0, "", "", "", "", "cc_");
        PouchSnapshot saved = new PouchSnapshot(55.5, 120, Map.of("baker", 2), Map.of("mitts", 3));
        try (PooledConnectionFactory pool = new PooledConnectionFactory(settings, new DriverConnectionFactory(settings));
             JdbcPlayerStore first = new JdbcPlayerStore(pool, "cc_")) {
            first.init();
            first.rememberName(player, "Baker_Bob");
            first.save(player, saved);
        } // pool closed: the H2 file must be closed cleanly
        try (PooledConnectionFactory pool = new PooledConnectionFactory(settings, new DriverConnectionFactory(settings));
             JdbcPlayerStore again = new JdbcPlayerStore(pool, "cc_")) {
            again.init();
            LoadedPouch loaded = again.load(player).orElseThrow();
            assertEquals(saved, loaded.snapshot());
            assertTrue(loaded.problems().isEmpty());
        }
        try (Connection c = DriverManager.getConnection(settings.jdbcUrl()); Statement st = c.createStatement();
             var rs = st.executeQuery("SELECT name FROM cc_players")) {
            assertTrue(rs.next());
            assertEquals("Baker_Bob", rs.getString(1));
        }
    }

    /** The v3 schema exactly as a 1.2.x plugin left it (upgrade_level exists, players.name and the baked index do not). */
    private static void createVersionThreeSchema(Statement st, UUID old) throws SQLException {
        st.execute("CREATE TABLE zz_players (uuid VARCHAR(36) NOT NULL PRIMARY KEY, pouch DOUBLE NOT NULL, baked DOUBLE NOT NULL, last_seen BIGINT NOT NULL, prestige DOUBLE NOT NULL DEFAULT 0)");
        st.execute("CREATE TABLE zz_buildings (uuid VARCHAR(36) NOT NULL, building_id VARCHAR(64) NOT NULL, owned INT NOT NULL, PRIMARY KEY (uuid, building_id))");
        st.execute("CREATE TABLE zz_upgrades (uuid VARCHAR(36) NOT NULL, upgrade_id VARCHAR(64) NOT NULL, upgrade_level INT DEFAULT 1 NOT NULL, PRIMARY KEY (uuid, upgrade_id))");
        st.execute("CREATE TABLE zz_house_seen (uuid VARCHAR(36) NOT NULL, plot_id VARCHAR(64) NOT NULL, seen_at BIGINT NOT NULL, PRIMARY KEY (uuid, plot_id))");
        st.execute("CREATE TABLE zz_schema_version (id INT NOT NULL PRIMARY KEY, version INT NOT NULL)");
        st.execute("INSERT INTO zz_schema_version VALUES (1, 3)");
        st.execute("INSERT INTO zz_players VALUES ('" + old + "', 42, 100, 1, 0)");
        st.execute("INSERT INTO zz_buildings VALUES ('" + old + "', 'baker', 7)");
        st.execute("INSERT INTO zz_upgrades VALUES ('" + old + "', 'mitts', 2)");
        st.execute("INSERT INTO zz_house_seen VALUES ('" + old + "', 'main', 5)");
    }

    @Test
    void migratesAVersionThreeDatabaseToVersionFourKeepingDataAndAddingNameAndIndex() throws Exception {
        String v3url = "jdbc:h2:mem:v3db" + COUNTER.incrementAndGet() + ";DB_CLOSE_DELAY=-1";
        UUID old = UUID.randomUUID();
        try (Connection c = DriverManager.getConnection(v3url); Statement st = c.createStatement()) {
            createVersionThreeSchema(st, old);
        }
        JdbcPlayerStore migrating = new JdbcPlayerStore(() -> DriverManager.getConnection(v3url), "zz_", () -> 99L);
        migrating.init();
        assertEquals(JdbcPlayerStore.SCHEMA_VERSION, migrating.schemaVersion());
        PouchSnapshot before = migrating.load(old).orElseThrow().snapshot();
        assertEquals(42, before.pouch());
        assertEquals(100, before.baked());
        assertEquals(Map.of("baker", 7), before.owned());
        assertEquals(Map.of("mitts", 2), before.upgradeLevels());
        assertTrue(migrating.isHouseSeen(old, "main"));
        try (Connection c = DriverManager.getConnection(v3url); Statement st = c.createStatement()) {
            // the new column is there and empty for players saved before v4
            try (var rs = st.executeQuery("SELECT name FROM zz_players")) {
                assertTrue(rs.next());
                assertNull(rs.getString(1));
            }
            // and the leaderboard index exists
            boolean indexed = false;
            try (var rs = c.getMetaData().getIndexInfo(null, null, "ZZ_PLAYERS", false, true)) {
                while (rs.next()) {
                    indexed |= "zz_players_baked_idx".equalsIgnoreCase(rs.getString("INDEX_NAME"));
                }
            }
            assertTrue(indexed, "zz_players_baked_idx on players(baked)");
        }
        // a second start is a no-op and loses nothing; the name is written from now on
        migrating.rememberName(old, "Alex");
        migrating.save(old, before);
        migrating.init();
        assertEquals(JdbcPlayerStore.SCHEMA_VERSION, migrating.schemaVersion());
        assertEquals(before, migrating.load(old).orElseThrow().snapshot());
        try (Connection c = DriverManager.getConnection(v3url); Statement st = c.createStatement();
             var rs = st.executeQuery("SELECT name FROM zz_players")) {
            assertTrue(rs.next());
            assertEquals("Alex", rs.getString(1));
        }
        migrating.close();
        try (Connection c = DriverManager.getConnection(v3url); Statement st = c.createStatement()) {
            st.execute("SHUTDOWN");
        }
    }

    @Test
    void aVersionThreeDatabaseThatAlreadyHasTheNewStepsStillReachesVersionFour() throws Exception {
        // a v3 -> v4 run that crashed after its ALTER and CREATE INDEX but before the version write
        String rerunUrl = "jdbc:h2:mem:v4rerun" + COUNTER.incrementAndGet() + ";DB_CLOSE_DELAY=-1";
        UUID old = UUID.randomUUID();
        try (Connection c = DriverManager.getConnection(rerunUrl); Statement st = c.createStatement()) {
            createVersionThreeSchema(st, old);
            st.execute("ALTER TABLE zz_players ADD COLUMN name VARCHAR(16)");
            st.execute("CREATE INDEX zz_players_baked_idx ON zz_players (baked)");
        }
        JdbcPlayerStore rerun = new JdbcPlayerStore(() -> DriverManager.getConnection(rerunUrl), "zz_", () -> 99L);
        rerun.init(); // must not fail with "column already exists" / "index already exists"
        assertEquals(JdbcPlayerStore.SCHEMA_VERSION, rerun.schemaVersion());
        assertEquals(Map.of("baker", 7), rerun.load(old).orElseThrow().snapshot().owned());
        rerun.close();
        try (Connection c = DriverManager.getConnection(rerunUrl); Statement st = c.createStatement()) {
            st.execute("SHUTDOWN");
        }
    }

    @Test
    void aVersionFourDatabaseGainsTheIngredientTableAndKeepsItsBuildings() throws Exception {
        String v4url = "jdbc:h2:mem:v4db" + COUNTER.incrementAndGet() + ";DB_CLOSE_DELAY=-1";
        UUID old = UUID.randomUUID();
        try (Connection c = DriverManager.getConnection(v4url); Statement st = c.createStatement()) {
            createVersionThreeSchema(st, old);
            st.execute("ALTER TABLE zz_players ADD COLUMN name VARCHAR(16)");
            st.execute("CREATE INDEX zz_players_baked_idx ON zz_players (baked)");
            st.execute("UPDATE zz_schema_version SET version = 4");
        }
        JdbcPlayerStore migrating = new JdbcPlayerStore(() -> DriverManager.getConnection(v4url), "zz_", () -> 99L);
        migrating.init();
        assertEquals(5, migrating.schemaVersion());
        assertEquals(Map.of("baker", 7), migrating.load(old).orElseThrow().snapshot().owned());
        migrating.save(old, new PouchSnapshot(1, 2, Map.of("baker", 7), Map.of("mitts", 2), Map.of("flour", 3.5)));
        assertEquals(Map.of("flour", 3.5), migrating.load(old).orElseThrow().snapshot().ingredients());
        migrating.close();
        try (Connection c = DriverManager.getConnection(v4url); Statement st = c.createStatement()) {
            st.execute("SHUTDOWN");
        }
    }

    @Test
    void ingredientStockRoundTripsAndTheLatestSaveReplacesIt() throws Exception {
        store.save(player, new PouchSnapshot(1, 2, Map.of(), Map.of(), Map.of("flour", 12.5, "milk", 0.30000000000000004)));
        assertEquals(Map.of("flour", 12.5, "milk", 0.30000000000000004), store.load(player).orElseThrow().snapshot().ingredients());
        // a stock that went to zero is gone from the table, not left as a row
        store.save(player, new PouchSnapshot(1, 2, Map.of(), Map.of(), Map.of("milk", 1.0)));
        assertEquals(Map.of("milk", 1.0), store.load(player).orElseThrow().snapshot().ingredients());
    }

    @Test
    void aSavedBeehiveLoadsAsAChickenFarmAndTheLargerCountWins() throws Exception {
        UUID both = UUID.randomUUID();
        store.save(player, new PouchSnapshot(1, 1, Map.of("beehive", 7), Map.of()));
        store.save(both, new PouchSnapshot(1, 1, Map.of("beehive", 4, "chicken_farm", 9), Map.of()));

        LoadedPouch renamed = store.load(player).orElseThrow();
        assertEquals(Map.of("chicken_farm", 7), renamed.snapshot().owned());
        assertTrue(renamed.problems().stream().anyMatch(p -> p.contains("'beehive' (7) renamed to 'chicken_farm'")));

        LoadedPouch both2 = store.load(both).orElseThrow();
        assertEquals(Map.of("chicken_farm", 9), both2.snapshot().owned(), "the larger of the two counts is kept");
        assertTrue(both2.problems().stream().anyMatch(p -> p.contains("kept the larger of 9 and 4")));
    }
}
