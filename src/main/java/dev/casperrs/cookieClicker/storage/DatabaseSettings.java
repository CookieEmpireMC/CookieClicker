package dev.casperrs.cookieClicker.storage;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * The {@code database.*} section of {@code config.yml}. Changing it needs a server restart
 * (the pool is opened once at enable). h2File is absolute, without extension; tablePrefix only letters, digits
 * and underscores; poolSize is the MariaDB pool's maximum (H2 always uses a small fixed pool).
 */
public record DatabaseSettings(Type type, Path h2File, String host, int port, String database,
                               String username, String password, String parameters, String tablePrefix,
                               int poolSize) {

    /** {@code database.mariadb.pool-size} when the config doesn't set it. */
    public static final int DEFAULT_POOL_SIZE = 4;

    public enum Type {
        H2, MARIADB;

        public static Optional<Type> parse(String raw) {
            if (raw == null) {
                return Optional.empty();
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "h2" -> Optional.of(H2);
                case "mariadb", "mysql" -> Optional.of(MARIADB);
                default -> Optional.empty();
            };
        }
    }

    public DatabaseSettings {
        if (!tablePrefix.matches("[A-Za-z0-9_]{0,32}")) {
            throw new IllegalArgumentException("database.table-prefix may only contain letters, digits and _ (max 32), got '" + tablePrefix + "'");
        }
        if (poolSize < 1) {
            throw new IllegalArgumentException("database.mariadb.pool-size must be at least 1, got " + poolSize);
        }
    }

    /** With the default pool size. */
    public DatabaseSettings(Type type, Path h2File, String host, int port, String database,
                            String username, String password, String parameters, String tablePrefix) {
        this(type, h2File, host, port, database, username, password, parameters, tablePrefix, DEFAULT_POOL_SIZE);
    }

    /** The same settings pointed at the H2 file: the source of {@code /bakery db migrate}. */
    public DatabaseSettings asH2() {
        return new DatabaseSettings(Type.H2, h2File, host, port, database, username, password, parameters,
                tablePrefix, poolSize);
    }

    /** The file H2 writes for {@link #h2File()}. */
    public Path h2DataFile() {
        return h2File.resolveSibling(h2File.getFileName() + ".mv.db");
    }

    /** JDBC URL for this config. */
    public String jdbcUrl() {
        return switch (type) {
            case H2 -> "jdbc:h2:file:" + h2File.toAbsolutePath();
            case MARIADB -> "jdbc:mariadb://" + host + ":" + port + "/" + database
                    + (parameters == null || parameters.isBlank() ? "" : "?" + parameters);
        };
    }

    public String driverClass() {
        return switch (type) {
            case H2 -> "org.h2.Driver";
            case MARIADB -> "org.mariadb.jdbc.Driver";
        };
    }

    /** For log lines: no password. */
    public String describe() {
        return switch (type) {
            case H2 -> "H2 at " + h2DataFile().toAbsolutePath();
            case MARIADB -> "MariaDB at " + host + ":" + port + "/" + database + " as " + username;
        };
    }
}
