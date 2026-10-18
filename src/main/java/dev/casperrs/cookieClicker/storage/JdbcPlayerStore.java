package dev.casperrs.cookieClicker.storage;

import dev.casperrs.cookieClicker.economy.PouchSnapshot;
import dev.casperrs.cookieClicker.economy.SafeMath;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * JDBC player store for H2 and MariaDB (plain SQL that both accept). Holds one connection and
 * reopens it when it goes stale. Single-threaded by contract (see {@link PlayerStore}).
 *
 * <p>Schema (prefix {@code cc_} by default), versioned in {@code <prefix>schema_version}:
 * <ul>
 *   <li>{@code players(uuid, pouch, baked, last_seen, prestige)} — {@code last_seen} (epoch ms) and
 *       {@code prestige} are reserved for offline earnings / prestige</li>
 *   <li>{@code buildings(uuid, building_id, owned)}</li>
 *   <li>{@code upgrades(uuid, upgrade_id)}</li>
 * </ul>
 */
public final class JdbcPlayerStore implements PlayerStore {

    /** Bump when adding a migration below. */
    public static final int SCHEMA_VERSION = 1;
    static final int MAX_ID_LENGTH = 64;

    private final ConnectionFactory connections;
    private final String p;
    private final LongSupplier clock;
    private Connection connection;

    public JdbcPlayerStore(ConnectionFactory connections, String tablePrefix) {
        this(connections, tablePrefix, System::currentTimeMillis);
    }

    public JdbcPlayerStore(ConnectionFactory connections, String tablePrefix, LongSupplier clock) {
        if (!tablePrefix.matches("[A-Za-z0-9_]{0,32}")) {
            throw new IllegalArgumentException("bad table prefix '" + tablePrefix + "'");
        }
        this.connections = connections;
        this.p = tablePrefix;
        this.clock = clock;
    }

    /** Migration {@code i} brings the schema from version {@code i} to {@code i + 1}. */
    private List<List<String>> migrations() {
        return List.of(
                List.of(
                        "CREATE TABLE IF NOT EXISTS " + p + "players ("
                                + "uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
                                + "pouch DOUBLE NOT NULL, "
                                + "baked DOUBLE NOT NULL, "
                                + "last_seen BIGINT NOT NULL, "
                                + "prestige DOUBLE NOT NULL DEFAULT 0)",
                        "CREATE TABLE IF NOT EXISTS " + p + "buildings ("
                                + "uuid VARCHAR(36) NOT NULL, "
                                + "building_id VARCHAR(" + MAX_ID_LENGTH + ") NOT NULL, "
                                + "owned INT NOT NULL, "
                                + "PRIMARY KEY (uuid, building_id))",
                        "CREATE TABLE IF NOT EXISTS " + p + "upgrades ("
                                + "uuid VARCHAR(36) NOT NULL, "
                                + "upgrade_id VARCHAR(" + MAX_ID_LENGTH + ") NOT NULL, "
                                + "PRIMARY KEY (uuid, upgrade_id))"));
    }

    @Override
    public void init() throws StorageException {
        try {
            Connection c = connection();
            try (Statement st = c.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + p + "schema_version (id INT NOT NULL PRIMARY KEY, version INT NOT NULL)");
            }
            int version = readSchemaVersion(c);
            if (version > SCHEMA_VERSION) {
                throw new StorageException("Database schema is version " + version + " but this plugin only knows "
                        + SCHEMA_VERSION + "; refusing to touch data written by a newer CookieClicker");
            }
            List<List<String>> migrations = migrations();
            for (int v = version; v < SCHEMA_VERSION; v++) {
                c.setAutoCommit(false);
                try (Statement st = c.createStatement()) {
                    for (String sql : migrations.get(v)) {
                        st.executeUpdate(sql);
                    }
                    writeSchemaVersion(c, v + 1);
                    c.commit();
                } catch (SQLException e) {
                    c.rollback();
                    throw e;
                } finally {
                    c.setAutoCommit(true);
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Could not initialise the database schema", e);
        }
    }

    /** Current schema version, 0 for a fresh database. */
    public int schemaVersion() throws StorageException {
        try {
            return readSchemaVersion(connection());
        } catch (SQLException e) {
            throw new StorageException("Could not read the schema version", e);
        }
    }

    private int readSchemaVersion(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT version FROM " + p + "schema_version WHERE id = 1")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void writeSchemaVersion(Connection c, int version) throws SQLException {
        try (PreparedStatement up = c.prepareStatement("UPDATE " + p + "schema_version SET version = ? WHERE id = 1")) {
            up.setInt(1, version);
            if (up.executeUpdate() > 0) {
                return;
            }
        }
        try (PreparedStatement ins = c.prepareStatement("INSERT INTO " + p + "schema_version (id, version) VALUES (1, ?)")) {
            ins.setInt(1, version);
            ins.executeUpdate();
        }
    }

    @Override
    public Optional<LoadedPouch> load(UUID player) throws StorageException {
        String id = player.toString();
        List<String> problems = new ArrayList<>();
        try {
            Connection c = connection();
            double pouch;
            double baked;
            try (PreparedStatement st = c.prepareStatement("SELECT pouch, baked FROM " + p + "players WHERE uuid = ?")) {
                st.setString(1, id);
                try (ResultSet rs = st.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    pouch = repaired(rs.getDouble(1), rs.wasNull(), "pouch", problems);
                    baked = repaired(rs.getDouble(2), rs.wasNull(), "baked", problems);
                }
            }
            if (baked < pouch) {
                problems.add("baked (" + baked + ") was below pouch (" + pouch + "); raised to pouch");
                baked = pouch;
            }

            Map<String, Integer> owned = new HashMap<>();
            try (PreparedStatement st = c.prepareStatement("SELECT building_id, owned FROM " + p + "buildings WHERE uuid = ?")) {
                st.setString(1, id);
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        String building = rs.getString(1);
                        int n = rs.getInt(2);
                        if (building == null || building.isBlank() || rs.wasNull() || n < 0) {
                            problems.add("skipped bad building row '" + building + "' = " + n);
                        } else if (n > 0) {
                            owned.put(building, n);
                        }
                    }
                }
            }

            Set<String> upgrades = new HashSet<>();
            try (PreparedStatement st = c.prepareStatement("SELECT upgrade_id FROM " + p + "upgrades WHERE uuid = ?")) {
                st.setString(1, id);
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        String upgrade = rs.getString(1);
                        if (upgrade == null || upgrade.isBlank()) {
                            problems.add("skipped blank upgrade row");
                        } else {
                            upgrades.add(upgrade);
                        }
                    }
                }
            }
            return Optional.of(new LoadedPouch(new PouchSnapshot(pouch, baked, owned, upgrades), problems));
        } catch (SQLException e) {
            invalidate();
            throw new StorageException("Could not load " + id, e);
        }
    }

    private static double repaired(double v, boolean wasNull, String field, List<String> problems) {
        double clean = wasNull ? 0 : SafeMath.sanitize(v);
        if (wasNull || clean != v) {
            problems.add(field + " was " + (wasNull ? "NULL" : v) + "; repaired to " + clean);
        }
        return clean;
    }

    @Override
    public void save(UUID player, PouchSnapshot snapshot) throws StorageException {
        String id = player.toString();
        double pouch = SafeMath.sanitize(snapshot.pouch());
        double baked = Math.max(pouch, SafeMath.sanitize(snapshot.baked()));
        try {
            Connection c = connection();
            c.setAutoCommit(false);
            try {
                int updated;
                try (PreparedStatement st = c.prepareStatement("UPDATE " + p + "players SET pouch = ?, baked = ?, last_seen = ? WHERE uuid = ?")) {
                    st.setDouble(1, pouch);
                    st.setDouble(2, baked);
                    st.setLong(3, clock.getAsLong());
                    st.setString(4, id);
                    updated = st.executeUpdate();
                }
                if (updated == 0) {
                    try (PreparedStatement st = c.prepareStatement("INSERT INTO " + p + "players (uuid, pouch, baked, last_seen, prestige) VALUES (?, ?, ?, ?, 0)")) {
                        st.setString(1, id);
                        st.setDouble(2, pouch);
                        st.setDouble(3, baked);
                        st.setLong(4, clock.getAsLong());
                        st.executeUpdate();
                    }
                }
                replaceBuildings(c, id, snapshot.owned());
                replaceUpgrades(c, id, snapshot.upgrades());
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            invalidate();
            throw new StorageException("Could not save " + id, e);
        }
    }

    private void deleteRows(Connection c, String table, String id) throws SQLException {
        try (PreparedStatement del = c.prepareStatement("DELETE FROM " + p + table + " WHERE uuid = ?")) {
            del.setString(1, id);
            del.executeUpdate();
        }
    }

    private void replaceBuildings(Connection c, String id, Map<String, Integer> owned) throws SQLException {
        deleteRows(c, "buildings", id);
        try (PreparedStatement ins = c.prepareStatement("INSERT INTO " + p + "buildings (uuid, building_id, owned) VALUES (?, ?, ?)")) {
            int rows = 0;
            for (Map.Entry<String, Integer> e : owned.entrySet()) {
                if (e.getValue() <= 0 || !validId(e.getKey())) {
                    continue;
                }
                ins.setString(1, id);
                ins.setString(2, e.getKey());
                ins.setInt(3, e.getValue());
                ins.addBatch();
                rows++;
            }
            if (rows > 0) {
                ins.executeBatch();
            }
        }
    }

    private void replaceUpgrades(Connection c, String id, Set<String> upgrades) throws SQLException {
        deleteRows(c, "upgrades", id);
        try (PreparedStatement ins = c.prepareStatement("INSERT INTO " + p + "upgrades (uuid, upgrade_id) VALUES (?, ?)")) {
            int rows = 0;
            for (String upgrade : upgrades) {
                if (!validId(upgrade)) {
                    continue;
                }
                ins.setString(1, id);
                ins.setString(2, upgrade);
                ins.addBatch();
                rows++;
            }
            if (rows > 0) {
                ins.executeBatch();
            }
        }
    }

    private static boolean validId(String id) {
        return !id.isBlank() && id.length() <= MAX_ID_LENGTH;
    }

    private Connection connection() throws SQLException {
        if (connection == null || !connection.isValid(2)) {
            invalidate();
            connection = connections.open();
        }
        return connection;
    }

    private void invalidate() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // already broken
            }
            connection = null;
        }
    }

    @Override
    public void close() {
        invalidate();
    }
}
