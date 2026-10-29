package io.github.archetom.crm;

import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * An empty database and restricted account for one migration scenario.
 *
 * <p>Only the MySQL process is shared. Each scenario keeps its own tables, fixtures and Flyway
 * history, including failed migrations and repair attempts. The server is separate from the
 * application integration database, lives only for this test JVM and is reaped by Testcontainers.
 * Root is used only to provision and remove these databases and accounts.</p>
 */
final class MigrationTestDatabase implements AutoCloseable {

    private final String databaseName;
    private final String username;
    private final String password;
    private final String jdbcUrl;
    private boolean closed;

    private MigrationTestDatabase() {
        String identifier = UUID.randomUUID().toString().replace("-", "");
        // Avoid '_' and '%' because MySQL database-level grants interpret them as wildcards.
        databaseName = "migration" + identifier;
        username = identifier;
        password = UUID.randomUUID().toString().replace("-", "");
        jdbcUrl = Server.MYSQL.getJdbcUrl().replace(
                "/" + Server.MYSQL.getDatabaseName(), "/" + databaseName);
    }

    static MigrationTestDatabase create() {
        MigrationTestDatabase database = new MigrationTestDatabase();
        try (Connection connection = administrativeConnection();
             Statement statement = connection.createStatement()) {
            // All interpolated values are generated here from fixed prefixes and UUID hex digits.
            statement.execute("CREATE DATABASE `" + database.databaseName + "`");
            statement.execute("CREATE USER '" + database.username
                    + "'@'%' IDENTIFIED BY '" + database.password + "'");
            statement.execute("GRANT ALL PRIVILEGES ON `" + database.databaseName
                    + "`.* TO '" + database.username + "'@'%'");
            return database;
        } catch (SQLException exception) {
            try {
                database.close();
            } catch (IllegalStateException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw new IllegalStateException("Failed to create migration test database", exception);
        }
    }

    String getDatabaseName() {
        return databaseName;
    }

    String getJdbcUrl() {
        return jdbcUrl;
    }

    String getUsername() {
        return username;
    }

    String getPassword() {
        return password;
    }

    @Override
    public void close() {
        if (closed) return;
        try (Connection connection = administrativeConnection();
             Statement statement = connection.createStatement()) {
            SQLException failure = null;
            try {
                statement.execute("DROP DATABASE IF EXISTS `" + databaseName + "`");
            } catch (SQLException exception) {
                failure = exception;
            }
            // Attempt account cleanup even if dropping the database failed.
            try {
                statement.execute("DROP USER IF EXISTS '" + username + "'@'%'");
            } catch (SQLException exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
            if (failure != null) throw failure;
            verifyCleanup(connection);
            closed = true;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to remove migration test database", exception);
        }
    }

    private void verifyCleanup(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT
                    (SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?)
                    + (SELECT COUNT(*) FROM mysql.user WHERE user = ? AND host = '%')
                """)) {
            statement.setString(1, databaseName);
            statement.setString(2, username);
            try (var result = statement.executeQuery()) {
                if (!result.next() || result.getInt(1) != 0) {
                    throw new SQLException("Migration test database or account survived cleanup");
                }
            }
        }
    }

    private static Connection administrativeConnection() throws SQLException {
        return DriverManager.getConnection(Server.MYSQL.getJdbcUrl(),
                Server.MYSQL.getUsername(), Server.MYSQL.getPassword());
    }

    /** Lazy initialization lets @RequiresDatabase skip tests without starting a container. */
    private static final class Server {
        private static final MySQLContainer MYSQL = start();

        private static MySQLContainer start() {
            MySQLContainer mysql = new MySQLContainer(DockerImageName.parse("mysql:26.10.0"))
                    .withDatabaseName("migrationcontrol")
                    .withUsername("root")
                    .withPassword(UUID.randomUUID().toString().replace("-", ""))
                    .withUrlParam("useSSL", "false")
                    .withUrlParam("allowPublicKeyRetrieval", "true")
                    .withTmpFs(Map.of("/var/lib/mysql", "rw,size=1g"))
                    .withStartupTimeout(Duration.ofMinutes(2));
            mysql.start();
            return mysql;
        }
    }
}
