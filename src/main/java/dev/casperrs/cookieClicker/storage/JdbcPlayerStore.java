package dev.casperrs.cookieClicker.storage;

import dev.casperrs.cookieClicker.economy.PouchSnapshot;
import dev.casperrs.cookieClicker.economy.SafeMath;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * JDBC player store for H2 and MariaDB (plain SQL that both accept). Borrows a connection from the
 * {@link ConnectionFactory} (a pool in production) per operation and closes it after. Single-threaded by contract
 * (see {@link PlayerStore}).
 *
 * <p>Schema (prefix {@code cc_} by default), versioned in {@code <prefix>schema_version}. The table and column
 * names are a contract for the website (docs/integrations.md, "MariaDB"): only add, never rename.
 * <ul>
 *   <li>{@code players(uuid, pouch, baked, last_seen, prestige, name)} — {@code last_seen} (epoch ms) and
 *       {@code prestige} are reserved for offline earnings / prestige; {@code name} (v4) is the last known player
 *       name; index {@code <prefix>players_baked_idx} on {@code baked} (v4) for leaderboards</li>
 *   <li>{@code buildings(uuid, building_id, owned)}</li>
 *   <li>{@code upgrades(uuid, upgrade_id, upgrade_level)} — {@code upgrade_level} (v3) defaults to 1, so a
 *       v2 save (a set of bought IDs) loads every upgrade as level 1</li>
 *   <li>{@code house_seen(uuid, plot_id, seen_at)} (v2) — first completed house cinematic per plot</li>
 * </ul>
 */
public final class JdbcPlayerStore implements PlayerStore, HouseSeenStore {

    /** Bump when adding a migration below. */
    public static final int SCHEMA_VERSION = 4;
    static final int MAX_ID_LENGTH = 64;
    /** Minecraft names are at most 16 characters ({@code players.name VARCHAR(16)}). */
    static final int MAX_NAME_LENGTH = 16;
    /** Every player-data table this store creates, without the prefix, in copy order. */
    public static final List<String> TABLES = List.of("players", "buildings", "upgrades", "house_seen");
    /** MariaDB/MySQL only (H2 would reject them): InnoDB and full Unicode. */
    private static final String MYSQL_CHARSET = "CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci";
    private static final String MYSQL_TABLE_OPTIONS = " ENGINE=InnoDB DEFAULT " + MYSQL_CHARSET;
    /** An unchanged player is still written this often, so {@code last_seen} stays roughly current. */
    static final long UNCHANGED_SAVE_MS = 60 * 60 * 1000L;

    /** What the database holds for a player after this store's last successful save. */
    private record Saved(double pouch, double baked, Map<String, Integer> owned, Map<String, Integer> levels,
                         long at) {
    }

    private final ConnectionFactory connections;
    private final String p;
    private final LongSupplier clock;
    private final Map<UUID, Saved> lastSaved = new HashMap<>();
    /** Names of joined players, written on their next load/save; dropped with their final save. */
    private final Map<UUID, String> names = new HashMap<>();

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

    /** One statement of a migration, run inside the migration's transaction. */
    @FunctionalInterface
    private interface Step {
        void run(Connection c, Statement st) throws SQLException;
    }

    private static Step sql(String sql) {
        return (c, st) -> st.executeUpdate(sql);
    }

    /**
     * {@code ALTER TABLE <table> ADD COLUMN <column> <definition>}, skipped when the column already exists: a crash
     * between the ALTER and the schema-version write must not block the next start. Checked through the JDBC
     * metadata rather than {@code ADD COLUMN IF NOT EXISTS}, which MySQL servers (type "mysql") don't accept.
     */
    private Step addColumn(String table, String column, String definition) {
        return (c, st) -> {
            if (!columnExists(c, p + table, column)) {
                st.executeUpdate("ALTER TABLE " + p + table + " ADD COLUMN " + column + " " + definition);
            }
        };
    }

    private static boolean columnExists(Connection c, String table, String column) throws SQLException {
        DatabaseMetaData meta = c.getMetaData();
        try (ResultSet rs = meta.getColumns(c.getCatalog(), null, metaPattern(meta, table), null)) {
            while (rs.next()) {
                // '_' in the pattern is a wildcard: compare the real names
                if (table.equalsIgnoreCase(rs.getString("TABLE_NAME"))
                        && column.equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** {@code CREATE INDEX}, skipped when an index of that name exists (MySQL has no {@code IF NOT EXISTS}). */
    private Step addIndex(String table, String index, String column) {
        return (c, st) -> {
            if (!indexExists(c, p + table, p + index)) {
                st.executeUpdate("CREATE INDEX " + p + index + " ON " + p + table + " (" + column + ")");
            }
        };
    }

    private static String metaPattern(DatabaseMetaData meta, String name) throws SQLException {
        return meta.storesUpperCaseIdentifiers() ? name.toUpperCase(Locale.ROOT)
                : meta.storesLowerCaseIdentifiers() ? name.toLowerCase(Locale.ROOT) : name;
    }

    private static boolean indexExists(Connection c, String table, String index) throws SQLException {
        DatabaseMetaData meta = c.getMetaData();
        try (ResultSet rs = meta.getIndexInfo(c.getCatalog(), null, metaPattern(meta, table), false, true)) {
            while (rs.next()) {
                if (index.equalsIgnoreCase(rs.getString("INDEX_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** True for MariaDB and MySQL (type "mysql" uses the MariaDB driver), false for H2. */
    private static boolean mysqlLike(Connection c) throws SQLException {
        String product = c.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        return product.contains("mariadb") || product.contains("mysql");
    }

    private List<String> allTables() {
        List<String> all = new ArrayList<>();
        all.add(p + "schema_version");
        TABLES.forEach(t -> all.add(p + t));
        return all;
    }

    /** Migration {@code i} brings the schema from version {@code i} to {@code i + 1}. */
    private List<List<Step>> migrations(boolean mysql) {
        String options = mysql ? MYSQL_TABLE_OPTIONS : "";
        List<Step> v4 = new ArrayList<>();
        // v3 -> v4 (mariadb): website-ready. Idempotent: a crash before the version write just re-runs it.
        v4.add(addColumn("players", "name", "VARCHAR(" + MAX_NAME_LENGTH + ")"));
        v4.add(addIndex("players", "players_baked_idx", "baked"));
        if (mysql) { // tables created before v4 got the server's default engine and charset
            for (String table : allTables()) {
                v4.add(sql("ALTER TABLE " + table + " ENGINE=InnoDB, CONVERT TO " + MYSQL_CHARSET));
            }
        }
        return List.of(
                List.of(
                        sql("CREATE TABLE IF NOT EXISTS " + p + "players ("
                                + "uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
                                + "pouch DOUBLE NOT NULL, "
                                + "baked DOUBLE NOT NULL, "
                                + "last_seen BIGINT NOT NULL, "
                                + "prestige DOUBLE NOT NULL DEFAULT 0)" + options),
                        sql("CREATE TABLE IF NOT EXISTS " + p + "buildings ("
                                + "uuid VARCHAR(36) NOT NULL, "
                                + "building_id VARCHAR(" + MAX_ID_LENGTH + ") NOT NULL, "
                                + "owned INT NOT NULL, "
                                + "PRIMARY KEY (uuid, building_id))" + options),
                        sql("CREATE TABLE IF NOT EXISTS " + p + "upgrades ("
                                + "uuid VARCHAR(36) NOT NULL, "
                                + "upgrade_id VARCHAR(" + MAX_ID_LENGTH + ") NOT NULL, "
                                + "PRIMARY KEY (uuid, upgrade_id))" + options)),
                // v1 -> v2 (starter-house): who has seen which plot's house cinematic
                List.of(
                        sql("CREATE TABLE IF NOT EXISTS " + p + "house_seen ("
                                + "uuid VARCHAR(36) NOT NULL, "
                                + "plot_id VARCHAR(" + MAX_ID_LENGTH + ") NOT NULL, "
                                + "seen_at BIGINT NOT NULL, "
                                + "PRIMARY KEY (uuid, plot_id))" + options)),
                // v2 -> v3 (upgrade-holograms): upgrades have levels; existing rows (bought once) become level 1.
                // DEFAULT before NOT NULL: the column order both H2 and MariaDB accept. Idempotent (see addColumn).
                List.of(
                        addColumn("upgrades", "upgrade_level", "INT DEFAULT 1 NOT NULL")),
                List.copyOf(v4));
    }

    @Override
    public void init() throws StorageException {
        try (Connection c = connections.open()) {
            boolean mysql = mysqlLike(c);
            try (Statement st = c.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + p + "schema_version (id INT NOT NULL PRIMARY KEY, version INT NOT NULL)"
                        + (mysql ? MYSQL_TABLE_OPTIONS : ""));
            }
            int version = readSchemaVersion(c);
            if (version > SCHEMA_VERSION) {
                throw new StorageException("Database schema is version " + version + " but this plugin only knows "
                        + SCHEMA_VERSION + "; refusing to touch data written by a newer CookieClicker");
            }
            List<List<Step>> migrations = migrations(mysql);
            for (int v = version; v < SCHEMA_VERSION; v++) {
                c.setAutoCommit(false);
                try (Statement st = c.createStatement()) {
                    for (Step step : migrations.get(v)) {
                        step.run(c, st);
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
        try (Connection c = connections.open()) {
            return readSchemaVersion(c);
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
        lastSaved.remove(player);
        String id = player.toString();
        List<String> problems = new ArrayList<>();
        try (Connection c = connections.open()) {
            double pouch;
            double baked;
            String storedName;
            try (PreparedStatement st = c.prepareStatement("SELECT pouch, baked, name FROM " + p + "players WHERE uuid = ?")) {
                st.setString(1, id);
                try (ResultSet rs = st.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    pouch = repaired(rs.getDouble(1), rs.wasNull(), "pouch", problems);
                    baked = repaired(rs.getDouble(2), rs.wasNull(), "baked", problems);
                    storedName = rs.getString(3);
                }
            }
            String name = names.get(player);
            if (name != null && !name.equals(storedName)) {
                try (PreparedStatement st = c.prepareStatement("UPDATE " + p + "players SET name = ? WHERE uuid = ?")) {
                    st.setString(1, name);
                    st.setString(2, id);
                    st.executeUpdate();
                }
            }
            names.remove(player); // written (or already right); a new player keeps it for the first save
            if (baked < pouch) {
                problems.add("baked (" + baked + ") was below pouch (" + pouch + "); raised to pouch");
                baked = pouch;
            }

            Map<String, Integer> owned = loadBuildings(c, id, problems);
            Map<String, Integer> upgrades = loadUpgrades(c, id, problems);
            return Optional.of(new LoadedPouch(new PouchSnapshot(pouch, baked, owned, upgrades), problems));
        } catch (SQLException e) {
            throw new StorageException("Could not load " + id, e);
        }
    }

    private Map<String, Integer> loadBuildings(Connection c, String id, List<String> problems) throws SQLException {
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
        return owned;
    }

    private Map<String, Integer> loadUpgrades(Connection c, String id, List<String> problems) throws SQLException {
        Map<String, Integer> upgrades = new HashMap<>();
        try (PreparedStatement st = c.prepareStatement("SELECT upgrade_id, upgrade_level FROM " + p + "upgrades WHERE uuid = ?")) {
            st.setString(1, id);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    String upgrade = rs.getString(1);
                    int level = rs.getInt(2);
                    if (upgrade == null || upgrade.isBlank()) {
                        problems.add("skipped blank upgrade row");
                    } else if (rs.wasNull() || level < 1) {
                        // a row only exists for a bought upgrade, so never lose it: repair to level 1
                        problems.add("upgrade '" + upgrade + "' had level " + (rs.wasNull() ? "NULL" : level) + "; repaired to 1");
                        upgrades.put(upgrade, 1);
                    } else {
                        upgrades.put(upgrade, level);
                    }
                }
            }
        }
        return upgrades;
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
        write(player, snapshot, false);
    }

    @Override
    public void saveFinal(UUID player, PouchSnapshot snapshot) throws StorageException {
        try {
            write(player, snapshot, true);
        } finally {
            lastSaved.remove(player);
            names.remove(player);
        }
    }

    @Override
    public void rememberName(UUID player, String name) {
        if (name != null && !name.isBlank()) {
            names.put(player, name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name);
        }
    }

    /** Skips what equals the last save: the whole save for an idle player, the building/upgrade rows otherwise. */
    private void write(UUID player, PouchSnapshot snapshot, boolean always) throws StorageException {
        String id = player.toString();
        double pouch = SafeMath.sanitize(snapshot.pouch());
        double baked = Math.max(pouch, SafeMath.sanitize(snapshot.baked()));
        long now = clock.getAsLong();
        Saved last = lastSaved.get(player);
        boolean sameRows = last != null && last.owned().equals(snapshot.owned())
                && last.levels().equals(snapshot.upgradeLevels());
        if (!always && sameRows && last.pouch() == pouch && last.baked() == baked
                && now - last.at() < UNCHANGED_SAVE_MS) {
            return;
        }
        String name = names.get(player);
        try (Connection c = connections.open()) {
            c.setAutoCommit(false);
            try {
                int updated;
                try (PreparedStatement st = c.prepareStatement("UPDATE " + p + "players SET pouch = ?, baked = ?, last_seen = ?, name = COALESCE(?, name) WHERE uuid = ?")) {
                    st.setDouble(1, pouch);
                    st.setDouble(2, baked);
                    st.setLong(3, now);
                    st.setString(4, name);
                    st.setString(5, id);
                    updated = st.executeUpdate();
                }
                if (updated == 0) {
                    try (PreparedStatement st = c.prepareStatement("INSERT INTO " + p + "players (uuid, pouch, baked, last_seen, prestige, name) VALUES (?, ?, ?, ?, 0, ?)")) {
                        st.setString(1, id);
                        st.setDouble(2, pouch);
                        st.setDouble(3, baked);
                        st.setLong(4, now);
                        st.setString(5, name);
                        st.executeUpdate();
                    }
                }
                if (!sameRows) {
                    replaceBuildings(c, id, snapshot.owned());
                    replaceUpgrades(c, id, snapshot.upgradeLevels());
                }
                c.commit();
                lastSaved.put(player, new Saved(pouch, baked, snapshot.owned(), snapshot.upgradeLevels(), now));
                if (name != null) {
                    names.remove(player, name);
                }
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
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

    private void replaceUpgrades(Connection c, String id, Map<String, Integer> levels) throws SQLException {
        deleteRows(c, "upgrades", id);
        try (PreparedStatement ins = c.prepareStatement("INSERT INTO " + p + "upgrades (uuid, upgrade_id, upgrade_level) VALUES (?, ?, ?)")) {
            int rows = 0;
            for (Map.Entry<String, Integer> e : levels.entrySet()) {
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

    private static boolean validId(String id) {
        return !id.isBlank() && id.length() <= MAX_ID_LENGTH;
    }

    @Override
    public boolean isHouseSeen(UUID player, String plotId) throws StorageException {
        try (Connection c = connections.open();
             PreparedStatement st = c.prepareStatement(
                "SELECT 1 FROM " + p + "house_seen WHERE uuid = ? AND plot_id = ?")) {
            st.setString(1, player.toString());
            st.setString(2, plotId);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new StorageException("Could not read house_seen of " + player, e);
        }
    }

    @Override
    public void markHouseSeen(UUID player, String plotId) throws StorageException {
        if (!validId(plotId)) {
            throw new StorageException("bad plot id '" + plotId + "'");
        }
        if (isHouseSeen(player, plotId)) {
            return; // keep the first seen_at; single storage thread, so no race between check and insert
        }
        try (Connection c = connections.open();
             PreparedStatement st = c.prepareStatement(
                "INSERT INTO " + p + "house_seen (uuid, plot_id, seen_at) VALUES (?, ?, ?)")) {
            st.setString(1, player.toString());
            st.setString(2, plotId);
            st.setLong(3, clock.getAsLong());
            st.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Could not write house_seen of " + player, e);
        }
    }

    @Override
    public void clearHouseSeen(UUID player, String plotId) throws StorageException {
        try (Connection c = connections.open();
             PreparedStatement st = c.prepareStatement(
                "DELETE FROM " + p + "house_seen WHERE uuid = ? AND plot_id = ?")) {
            st.setString(1, player.toString());
            st.setString(2, plotId);
            st.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Could not clear house_seen of " + player, e);
        }
    }

    /** Forgets the per-player caches. The connection factory (pool) belongs to whoever made it and stays open. */
    @Override
    public void close() {
        lastSaved.clear();
        names.clear();
    }
}
