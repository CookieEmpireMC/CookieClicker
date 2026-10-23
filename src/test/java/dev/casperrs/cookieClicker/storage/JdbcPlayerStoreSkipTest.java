package dev.casperrs.cookieClicker.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.casperrs.cookieClicker.economy.PouchSnapshot;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Autosave skipping (PERF-U3): an unchanged player is not rewritten, but a real change is never skipped. A sentinel
 * value written straight into the database shows whether a save wrote or skipped.
 */
class JdbcPlayerStoreSkipTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final double SENTINEL = -777;

    private String url;
    private final AtomicLong clock = new AtomicLong(1_000);
    private JdbcPlayerStore store;
    private final UUID player = UUID.randomUUID();

    private static PouchSnapshot snap(double pouch, int bakers, Map<String, Integer> upgrades) {
        return new PouchSnapshot(pouch, pouch, Map.of("baker", bakers), upgrades);
    }

    @BeforeEach
    void setUp() throws Exception {
        url = "jdbc:h2:mem:skip" + COUNTER.incrementAndGet() + ";DB_CLOSE_DELAY=-1";
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
        return new JdbcPlayerStore(() -> DriverManager.getConnection(url), "cc_", clock::get);
    }

    private void sql(String statement) throws SQLException {
        try (Connection c = DriverManager.getConnection(url); Statement st = c.createStatement()) {
            st.execute(statement);
        }
    }

    /** What the database holds, read by a separate store so this store's memory is untouched. */
    private PouchSnapshot stored() throws Exception {
        JdbcPlayerStore reader = newStore();
        try {
            return reader.load(player).orElseThrow().snapshot();
        } finally {
            reader.close();
        }
    }

    private double storedPouchColumn() throws SQLException {
        try (Connection c = DriverManager.getConnection(url); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT pouch FROM cc_players")) {
            rs.next();
            return rs.getDouble(1);
        }
    }

    @Test
    void anUnchangedSnapshotIsSkippedAndAChangedOneWrites() throws Exception {
        PouchSnapshot a = snap(100, 2, Map.of("mitts", 1));
        store.save(player, a);
        sql("UPDATE cc_players SET pouch = " + SENTINEL);
        store.save(player, a);
        assertEquals(SENTINEL, storedPouchColumn(), "an identical snapshot is not written again");

        store.save(player, snap(150, 2, Map.of("mitts", 1))); // only the pouch changed
        assertEquals(150, storedPouchColumn());
        store.save(player, snap(150, 3, Map.of("mitts", 1))); // a building was bought
        assertEquals(3, stored().owned().get("baker"));
        store.save(player, snap(150, 3, Map.of("mitts", 2))); // an upgrade level went up
        assertEquals(2, stored().upgradeLevels().get("mitts"));
    }

    @Test
    void anUnchangedPlayerIsStillWrittenAfterAnHour() throws Exception {
        PouchSnapshot a = snap(100, 2, Map.of());
        store.save(player, a);
        sql("UPDATE cc_players SET pouch = " + SENTINEL);
        clock.addAndGet(JdbcPlayerStore.UNCHANGED_SAVE_MS - 1);
        store.save(player, a);
        assertEquals(SENTINEL, storedPouchColumn());
        clock.addAndGet(1);
        store.save(player, a);
        assertEquals(100, storedPouchColumn());
    }

    @Test
    void saveFinalAlwaysWritesAndForgetsTheSavedState() throws Exception {
        PouchSnapshot a = snap(100, 2, Map.of());
        store.save(player, a);
        sql("UPDATE cc_players SET pouch = " + SENTINEL);
        store.saveFinal(player, a);
        assertEquals(100, storedPouchColumn(), "quit/disable writes even when nothing changed");

        sql("UPDATE cc_players SET pouch = " + SENTINEL);
        store.save(player, a); // the entry was dropped, so this is a first save again
        assertEquals(100, storedPouchColumn());
    }

    @Test
    void loadForgetsTheSavedStateSoARejoinWritesAgain() throws Exception {
        PouchSnapshot a = snap(100, 2, Map.of());
        store.save(player, a);
        store.load(player);
        sql("UPDATE cc_players SET pouch = " + SENTINEL);
        store.save(player, a);
        assertEquals(100, storedPouchColumn());
    }

    @Test
    void withoutASaveFinalEveryChangeBeforeTheLastAutosaveIsInTheDatabase() throws Exception {
        store.save(player, snap(100, 1, Map.of()));
        store.save(player, snap(120, 2, Map.of("mitts", 1)));
        store.save(player, snap(120, 2, Map.of("mitts", 1))); // idle: skipped
        store.save(player, snap(130, 2, Map.of("mitts", 2)));
        // crash: a new store (server restart) reads what the autosaves left
        assertEquals(snap(130, 2, Map.of("mitts", 2)), stored());
    }

    @Test
    void aChangeBackToAnEarlierStateIsNotMistakenForUnchanged() throws Exception {
        PouchSnapshot a = snap(100, 2, Map.of());
        store.save(player, a);
        store.save(player, snap(100, 5, Map.of())); // rows changed
        store.save(player, a); // back to the first state: differs from the last save, so it writes
        assertEquals(a, stored());
    }
}
