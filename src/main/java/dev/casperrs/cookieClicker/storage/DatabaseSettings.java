package dev.casperrs.cookieClicker.storage;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * The {@code database.*} section of {@code config.yml}. Changing it needs a server restart
 * (the connection is opened once at enable).
 *
 * @param h2File      H2 database file without extension, absolute
 * @param tablePrefix prefix for every table; letters, digits and underscores only
 */
public record DatabaseSettings(Type type, Path h2File, String host, int port, String database,
                               String username, String password, String parameters, String tablePrefix) {

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
            case H2 -> "H2 at " + h2File.toAbsolutePath() + ".mv.db";
            case MARIADB -> "MariaDB at " + host + ":" + port + "/" + database + " as " + username;
        };
    }
}
