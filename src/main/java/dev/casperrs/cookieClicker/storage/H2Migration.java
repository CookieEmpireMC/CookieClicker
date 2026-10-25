package dev.casperrs.cookieClicker.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /bakery db migrate}: copies every player table from one database (the old H2 file) into another (the
 * configured MariaDB), with the same table prefix on both sides. Blocking JDBC: run it on the storage thread.
 *
 * <p>Both schemas are first brought to the current version (so an older H2 file gets its new columns). Each table is
 * replaced in one transaction: the target's rows are deleted and the source's inserted in batches. Refuses a target
 * that already holds player rows unless forced.
 */
public final class H2Migration {

    /** Rows per JDBC batch inside a table's transaction. */
    static final int BATCH_SIZE = 500;

    /** Columns copied per table (the schema of {@link JdbcPlayerStore#SCHEMA_VERSION}). */
    private static final Map<String, List<String>> COLUMNS = columns();

    private static Map<String, List<String>> columns() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("players", List.of("uuid", "pouch", "baked", "last_seen", "prestige", "name"));
        m.put("buildings", List.of("uuid", "building_id", "owned"));
        m.put("upgrades", List.of("uuid", "upgrade_id", "upgrade_level"));
        m.put("house_seen", List.of("uuid", "plot_id", "seen_at"));
        m.put("ingredients", List.of("uuid", "ingredient", "amount"));
        if (!m.keySet().equals(new java.util.LinkedHashSet<>(JdbcPlayerStore.TABLES))) {
            throw new IllegalStateException("H2Migration columns are out of date with JdbcPlayerStore.TABLES");
        }
        return Collections.unmodifiableMap(m);
    }

    /**
     * {@code refused}: the target already had {@code existingPlayers} player rows and the run wasn't forced, so
     * nothing was copied. Otherwise {@code copied} holds the rows written per table, in copy order.
     */
    public record Result(boolean refused, int existingPlayers, Map<String, Integer> copied) {

        public int total() {
            return copied.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    private final ConnectionFactory source;
    private final ConnectionFactory target;
    private final String p;

    public H2Migration(ConnectionFactory source, ConnectionFactory target, String tablePrefix) {
        if (!tablePrefix.matches("[A-Za-z0-9_]{0,32}")) {
            throw new IllegalArgumentException("bad table prefix '" + tablePrefix + "'");
        }
        this.source = source;
        this.target = target;
        this.p = tablePrefix;
    }

    public Result run(boolean force) throws StorageException {
        try (JdbcPlayerStore from = new JdbcPlayerStore(source, p);
             JdbcPlayerStore to = new JdbcPlayerStore(target, p)) {
            from.init();
            to.init();
        }
        try (Connection src = source.open(); Connection dst = target.open()) {
            int existing = count(dst, "players");
            if (existing > 0 && !force) {
                return new Result(true, existing, Map.of());
            }
            Map<String, Integer> copied = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> table : COLUMNS.entrySet()) {
                copied.put(table.getKey(), copy(src, dst, table.getKey(), table.getValue()));
            }
            return new Result(false, existing, Collections.unmodifiableMap(copied));
        } catch (SQLException e) {
            throw new StorageException("Could not copy the player tables", e);
        }
    }

    private int count(Connection c, String table) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + p + table)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private int copy(Connection src, Connection dst, String table, List<String> columns) throws SQLException {
        String list = String.join(", ", columns);
        String marks = String.join(", ", Collections.nCopies(columns.size(), "?"));
        dst.setAutoCommit(false);
        try (Statement clear = dst.createStatement();
             Statement read = src.createStatement();
             PreparedStatement insert = dst.prepareStatement(
                     "INSERT INTO " + p + table + " (" + list + ") VALUES (" + marks + ")")) {
            clear.executeUpdate("DELETE FROM " + p + table);
            int rows = 0;
            try (ResultSet rs = read.executeQuery("SELECT " + list + " FROM " + p + table)) {
                while (rs.next()) {
                    for (int i = 1; i <= columns.size(); i++) {
                        insert.setObject(i, rs.getObject(i));
                    }
                    insert.addBatch();
                    if (++rows % BATCH_SIZE == 0) {
                        insert.executeBatch();
                    }
                }
            }
            if (rows % BATCH_SIZE != 0) {
                insert.executeBatch();
            }
            dst.commit();
            return rows;
        } catch (SQLException e) {
            dst.rollback();
            throw new SQLException("copying " + p + table + " failed (that table was rolled back): " + e.getMessage(), e);
        } finally {
            dst.setAutoCommit(true);
        }
    }
}
