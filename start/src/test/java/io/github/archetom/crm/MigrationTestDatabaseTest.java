package io.github.archetom.crm;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@RequiresDatabase
class MigrationTestDatabaseTest {

    @Test
    void freshDatabasesShareOnlyTheServerAndKeepIndependentFlywayHistory() throws SQLException {
        try (MigrationTestDatabase first = MigrationTestDatabase.create();
             MigrationTestDatabase second = MigrationTestDatabase.create();
             Connection firstConnection = connect(first);
             Connection secondConnection = connect(second)) {
            assertNotEquals(first.getDatabaseName(), second.getDatabaseName());
            assertNotEquals(first.getUsername(), second.getUsername());
            assertEquals(query(firstConnection, "SELECT @@server_uuid"),
                    query(secondConnection, "SELECT @@server_uuid"));
            String tableCount = "SELECT COUNT(*) FROM information_schema.tables"
                    + " WHERE table_schema = DATABASE()";
            assertEquals("0", query(firstConnection, tableCount));
            assertEquals("0", query(secondConnection, tableCount));

            migrate(first, "1");
            assertEquals("0", query(secondConnection, tableCount));
            migrate(second, "2");

            String historyCount = "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1";
            assertEquals("1", query(firstConnection, historyCount));
            assertEquals("2", query(secondConnection, historyCount));
        }
    }

    @Test
    void sameNamedTablesAndCredentialsAreIsolatedBetweenDatabases() throws SQLException {
        try (MigrationTestDatabase first = MigrationTestDatabase.create();
             MigrationTestDatabase second = MigrationTestDatabase.create();
             Connection firstConnection = connect(first);
             Connection secondConnection = connect(second)) {
            createProbe(firstConnection, "first");
            createProbe(secondConnection, "second");
            assertEquals("first", query(firstConnection, "SELECT marker FROM isolation_probe"));
            assertEquals("second", query(secondConnection, "SELECT marker FROM isolation_probe"));

            SQLException denied = assertThrows(SQLException.class,
                    () -> query(firstConnection,
                            "SELECT marker FROM `" + second.getDatabaseName() + "`.isolation_probe"));
            assertEquals(1142, denied.getErrorCode());
            SQLException deniedConnection = assertThrows(SQLException.class,
                    () -> DriverManager.getConnection(second.getJdbcUrl(),
                            first.getUsername(), first.getPassword()));
            assertEquals(1044, deniedConnection.getErrorCode());
        }
    }

    @Test
    void closeRemovesDatabaseAndAccountWithoutAffectingOtherScenarios() throws SQLException {
        try (MigrationTestDatabase removed = MigrationTestDatabase.create();
             MigrationTestDatabase retained = MigrationTestDatabase.create();
             Connection connection = connect(retained)) {
            createProbe(connection, "retained");

            // close verifies that neither the schema nor its account survives in MySQL metadata.
            assertDoesNotThrow(removed::close);
            assertDoesNotThrow(removed::close);
            SQLException rejectedCredentials = assertThrows(SQLException.class, () -> connect(removed));
            assertEquals(1045, rejectedCredentials.getErrorCode());
            assertEquals("retained", query(connection, "SELECT marker FROM isolation_probe"));
        }
    }

    private static Connection connect(MigrationTestDatabase database) throws SQLException {
        return DriverManager.getConnection(database.getJdbcUrl(),
                database.getUsername(), database.getPassword());
    }

    private static void migrate(MigrationTestDatabase database, String target) {
        Flyway.configure()
                .dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                .locations("classpath:db/migration")
                .target(target)
                .load().migrate();
    }

    private static void createProbe(Connection connection, String marker) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE isolation_probe (marker VARCHAR(20) NOT NULL)");
        }
        try (var statement = connection.prepareStatement("INSERT INTO isolation_probe VALUES (?)")) {
            statement.setString(1, marker);
            statement.executeUpdate();
        }
    }

    private static String query(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery(sql)) {
            if (!result.next()) throw new SQLException("Expected one row");
            return result.getString(1);
        }
    }
}
