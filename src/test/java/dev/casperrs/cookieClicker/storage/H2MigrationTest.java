package dev.casperrs.cookieClicker.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.economy.PouchSnapshot;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@code /bakery db migrate}: H2 to H2 stands in for H2 to MariaDB (same SQL, same copy code). */
class H2MigrationTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private String sourceUrl;
    private String targetUrl;
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        int n = COUNTER.incrementAndGet();
        sourceUrl = "jdbc:h2:mem:migsrc" + n + ";DB_CLOSE_DELAY=-1";
        targetUrl = "jdbc:h2:mem:migdst" + n + ";DB_CLOSE_DELAY=-1";
    }

    @AfterEach
    void tearDown() throws SQLException {
        for (String url : new String[] {sourceUrl, targetUrl}) {
            try (Connection c = DriverManager.getConnection(url); Statement st = c.createStatement()) {
                st.execute("SHUTDOWN");
            }
        }
    }

    private static ConnectionFactory factory(String url) {
        return () -> DriverManager.getConnection(url);
    }

    /** A version 3 H2 file, as the 1.2.x plugin left it: two players with buildings, upgrades and one house seen. */
    private void buildVersionThreeSource() throws SQLException {
        try (Connection c = DriverManager.getConnection(sourceUrl); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE cc_players (uuid VARCHAR(36) NOT NULL PRIMARY KEY, pouch DOUBLE NOT NULL, baked DOUBLE NOT NULL, last_seen BIGINT NOT NULL, prestige DOUBLE NOT NULL DEFAULT 0)");
            st.execute("CREATE TABLE cc_buildings (uuid VARCHAR(36) NOT NULL, building_id VARCHAR(64) NOT NULL, owned INT NOT NULL, PRIMARY KEY (uuid, building_id))");
            st.execute("CREATE TABLE cc_upgrades (uuid VARCHAR(36) NOT NULL, upgrade_id VARCHAR(64) NOT NULL, upgrade_level INT DEFAULT 1 NOT NULL, PRIMARY KEY (uuid, upgrade_id))");
            st.execute("CREATE TABLE cc_house_seen (uuid VARCHAR(36) NOT NULL, plot_id VARCHAR(64) NOT NULL, seen_at BIGINT NOT NULL, PRIMARY KEY (uuid, plot_id))");
            st.execute("CREATE TABLE cc_schema_version (id INT NOT NULL PRIMARY KEY, version INT NOT NULL)");
            st.execute("INSERT INTO cc_schema_version VALUES (1, 3)");
            st.execute("INSERT INTO cc_players VALUES ('" + alice + "', 42.5, 100.25, 7, 3)");
            st.execute("INSERT INTO cc_players VALUES ('" + bob + "', 0.5, 0.5, 8, 0)");
            st.execute("INSERT INTO cc_buildings VALUES ('" + alice + "', 'baker', 7)");
            st.execute("INSERT INTO cc_buildings VALUES ('" + bob + "', 'oven', 1)");
            st.execute("INSERT INTO cc_upgrades VALUES ('" + alice + "', 'mitts', 2)");
            st.execute("INSERT INTO cc_upgrades VALUES ('" + alice + "', 'strength', 1)");
            st.execute("INSERT INTO cc_house_seen VALUES ('" + alice + "', 'main', 5)");
        }
    }

    /** Every row of a table as text, sorted, so two databases can be compared column by column. */
    private static List<String> dump(String url, String table) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(url); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM cc_" + table)) {
            int columns = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                StringBuilder row = new StringBuilder();
                for (int i = 1; i <= columns; i++) {
                    row.append(rs.getString(i)).append('|');
                }
                rows.add(row.toString());
            }
        }
        Collections.sort(rows);
        return rows;
    }

    @Test
    void copiesEveryTableIdenticallyAndTheTargetLoadsTheSameProgress() throws Exception {
        buildVersionThreeSource();
        H2Migration.Result result = new H2Migration(factory(sourceUrl), factory(targetUrl), "cc_").run(false);

        assertFalse(result.refused());
        assertEquals(Map.of("players", 2, "buildings", 2, "upgrades", 2, "house_seen", 1), result.copied());
        assertEquals(7, result.total());
        for (String table : JdbcPlayerStore.TABLES) {
            assertEquals(dump(sourceUrl, table), dump(targetUrl, table), table + " differs after the copy");
        }
        PouchSnapshot alices = new JdbcPlayerStore(factory(targetUrl), "cc_").load(alice).orElseThrow().snapshot();
        assertEquals(42.5, alices.pouch());
        assertEquals(100.25, alices.baked());
        assertEquals(Map.of("baker", 7), alices.owned());
        assertEquals(Map.of("mitts", 2, "strength", 1), alices.upgradeLevels());
    }

    @Test
    void refusesANonEmptyTargetUnlessForcedAndForceReplacesIt() throws Exception {
        buildVersionThreeSource();
        JdbcPlayerStore target = new JdbcPlayerStore(factory(targetUrl), "cc_");
        target.init();
        target.save(UUID.randomUUID(), new PouchSnapshot(1, 1, Map.of("x", 9), Set.of()));

        H2Migration.Result refused = new H2Migration(factory(sourceUrl), factory(targetUrl), "cc_").run(false);
        assertTrue(refused.refused());
        assertEquals(1, refused.existingPlayers());
        assertTrue(refused.copied().isEmpty());
        assertTrue(target.load(alice).isEmpty(), "nothing was copied");

        H2Migration.Result forced = new H2Migration(factory(sourceUrl), factory(targetUrl), "cc_").run(true);
        assertFalse(forced.refused());
        assertEquals(2, forced.copied().get("players"));
        assertEquals(Map.of("baker", 7), target.load(alice).orElseThrow().snapshot().owned());
        assertEquals(2, dump(targetUrl, "players").size(), "the target's own player was replaced");
        target.close();
    }

    @Test
    void aTableThatFailsIsRolledBackAndKeepsItsOldRows() throws Exception {
        buildVersionThreeSource();
        try (Connection c = DriverManager.getConnection(sourceUrl); Statement st = c.createStatement()) {
            st.execute("DROP TABLE cc_upgrades"); // the copy of upgrades will fail
        }
        JdbcPlayerStore target = new JdbcPlayerStore(factory(targetUrl), "cc_");
        target.init();
        target.save(bob, new PouchSnapshot(5, 5, Map.of(), Map.of("keep_me", 2)));
        target.close();

        H2Migration migration = new H2Migration(factory(sourceUrl), factory(targetUrl), "cc_");
        assertThrows(StorageException.class, () -> migration.run(true));

        // players and buildings were copied (their own transactions); upgrades rolled back to the old row
        assertEquals(List.of("keep_me|2|"), dumpLevels(targetUrl));
    }

    /** The target's upgrades as "id|level", so the rollback check reads the table directly. */
    private static List<String> dumpLevels(String url) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(url); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT upgrade_id, upgrade_level FROM cc_upgrades")) {
            while (rs.next()) {
                rows.add(rs.getString(1) + "|" + rs.getInt(2) + "|");
            }
        }
        return rows;
    }
}
