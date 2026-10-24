package dev.casperrs.cookieClicker.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

/**
 * A HikariCP pool over another {@link ConnectionFactory}. Hikari gets a {@link DataSource} that calls that factory
 * ({@link DriverConnectionFactory} in production), so it never looks the driver up through
 * {@link java.sql.DriverManager}, which doesn't see the plugin loader's libraries. Closing a borrowed connection
 * returns it to the pool; {@link #close()} closes the pool itself.
 */
public final class PooledConnectionFactory implements ConnectionFactory, AutoCloseable {

    /** H2 is embedded and the store has one thread: two connections are plenty. */
    static final int H2_POOL_SIZE = 2;
    static final long CONNECTION_TIMEOUT_MS = 10_000;
    /** Below MariaDB's default {@code wait_timeout} (8 h), so the server never drops a pooled connection first. */
    static final long MAX_LIFETIME_MS = 30 * 60_000L;

    private final HikariDataSource pool;

    /**
     * Doesn't connect yet (the first {@link #open()} does): check reachability with {@link #probe} first, so a bad
     * login is one clear log line instead of a pool stack trace.
     */
    public PooledConnectionFactory(DatabaseSettings settings, ConnectionFactory raw) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("CookieClicker-" + settings.type().name().toLowerCase(java.util.Locale.ROOT));
        config.setDataSource(new FactoryDataSource(raw));
        config.setMaximumPoolSize(settings.type() == DatabaseSettings.Type.H2 ? H2_POOL_SIZE : settings.poolSize());
        config.setMinimumIdle(1);
        config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        config.setMaxLifetime(MAX_LIFETIME_MS);
        config.setInitializationFailTimeout(-1);
        pool = new HikariDataSource(config);
    }

    /** Opens and closes one connection, to fail at enable instead of at the first save. */
    public static void probe(ConnectionFactory raw) throws SQLException {
        try (Connection c = raw.open()) {
            if (!c.isValid(5)) {
                throw new SQLException("the connection opened but did not answer");
            }
        }
    }

    @Override
    public Connection open() throws SQLException {
        return pool.getConnection();
    }

    @Override
    public void close() {
        pool.close();
    }

    /** Only what Hikari calls: getConnection; the rest are inert. */
    private record FactoryDataSource(ConnectionFactory raw) implements DataSource {

        @Override
        public Connection getConnection() throws SQLException {
            return raw.open();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return raw.open(); // credentials come from config.yml through the factory
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            throw new SQLException("not a wrapper for " + iface.getName());
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }
    }
}
