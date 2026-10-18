package dev.casperrs.cookieClicker.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void missingPlayerLoadsEmpty() throws Exception {
        assertTrue(store.load(UUID.randomUUID()).isEmpty());
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
    void saveReplacesInsteadOfAccumulating() throws Exception {
        store.save(player, new PouchSnapshot(1, 1, Map.of("a", 1, "b", 2), Set.of("u1", "u2")));
        store.save(player, new PouchSnapshot(5, 9, Map.of("a", 4), Set.of("u2")));
        PouchSnapshot s = store.load(player).orElseThrow().snapshot();
        assertEquals(5, s.pouch());
        assertEquals(9, s.baked());
        assertEquals(Map.of("a", 4), s.owned());
        assertEquals(Set.of("u2"), s.upgrades());
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
    void overlongAndBlankIdsAreSkippedOnSaveNotFatal() throws Exception {
        String tooLong = "x".repeat(JdbcPlayerStore.MAX_ID_LENGTH + 1);
        String maxLen = "y".repeat(JdbcPlayerStore.MAX_ID_LENGTH);
        store.save(player, new PouchSnapshot(1, 1, Map.of(tooLong, 1, maxLen, 2, " ", 3), Set.of(tooLong, "ok")));
        PouchSnapshot s = store.load(player).orElseThrow().snapshot();
        assertEquals(Map.of(maxLen, 2), s.owned());
        assertEquals(Set.of("ok"), s.upgrades());
    }

    @Test
    void infinityAndHugeValuesRoundTripAsFinite() throws Exception {
        store.save(player, new PouchSnapshot(Double.POSITIVE_INFINITY, Double.MAX_VALUE, Map.of(), Set.of()));
        PouchSnapshot s = store.load(player).orElseThrow().snapshot();
        assertEquals(Double.MAX_VALUE, s.pouch());
        assertEquals(Double.MAX_VALUE, s.baked());
    }

    @Test
    void corruptRowsAreRepairedAndReported() throws Exception {
        sql("INSERT INTO cc_players (uuid, pouch, baked, last_seen) VALUES ('" + player + "', CAST('NaN' AS DOUBLE), -7, 0)");
        sql("INSERT INTO cc_buildings VALUES ('" + player + "', 'good', 4)");
        sql("INSERT INTO cc_buildings VALUES ('" + player + "', 'negative', -2)");
        sql("INSERT INTO cc_buildings VALUES ('" + player + "', 'zero', 0)");
        sql("INSERT INTO cc_buildings VALUES ('" + player + "', ' ', 5)");
        sql("INSERT INTO cc_upgrades VALUES ('" + player + "', ' ')");
        sql("INSERT INTO cc_upgrades VALUES ('" + player + "', 'fine')");
        LoadedPouch loaded = store.load(player).orElseThrow();
        PouchSnapshot s = loaded.snapshot();
        assertEquals(0, s.pouch());
        assertEquals(0, s.baked());
        assertEquals(Map.of("good", 4), s.owned());
        assertEquals(Set.of("fine"), s.upgrades());
        assertFalse(loaded.problems().isEmpty());
        assertTrue(loaded.problems().stream().anyMatch(p -> p.contains("pouch")));
        assertTrue(loaded.problems().stream().anyMatch(p -> p.contains("negative")));
    }

    @Test
    void bakedBelowPouchIsRaisedToPouch() throws Exception {
        sql("INSERT INTO cc_players (uuid, pouch, baked, last_seen) VALUES ('" + player + "', 100, 40, 0)");
        LoadedPouch loaded = store.load(player).orElseThrow();
        assertEquals(100, loaded.snapshot().baked());
        assertEquals(1, loaded.problems().size());
    }

    @Test
    void lastSeenUsesTheInjectedClock() throws Exception {
        store.save(player, PouchSnapshot.EMPTY);
        try (Connection c = DriverManager.getConnection(url); Statement st = c.createStatement();
             var rs = st.executeQuery("SELECT last_seen, prestige FROM cc_players")) {
            assertTrue(rs.next());
            assertEquals(1234L, rs.getLong(1));
            assertEquals(0.0, rs.getDouble(2));
        }
    }

    @Test
    void initTwiceIsHarmlessAndKeepsData() throws Exception {
        store.save(player, new PouchSnapshot(5, 5, Map.of("a", 1), Set.of()));
        store.init();
        newStore().init();
        assertEquals(JdbcPlayerStore.SCHEMA_VERSION, store.schemaVersion());
        assertEquals(5, store.load(player).orElseThrow().snapshot().pouch());
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
    void freshDatabaseStartsAtVersionOne() throws Exception {
        assertEquals(1, store.schemaVersion());
    }

    @Test
    void tablePrefixIsValidated() {
        assertThrows(IllegalArgumentException.class, () -> new JdbcPlayerStore(() -> null, "cc_; DROP TABLE x"));
        assertThrows(IllegalArgumentException.class, () -> new JdbcPlayerStore(() -> null, "x".repeat(33)));
    }

    @Test
    void differentPrefixesDoNotShareData() throws Exception {
        JdbcPlayerStore other = new JdbcPlayerStore(() -> DriverManager.getConnection(url), "zz_");
        other.init();
        store.save(player, new PouchSnapshot(9, 9, Map.of(), Set.of()));
        assertTrue(other.load(player).isEmpty());
        other.close();
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
}
