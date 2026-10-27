package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Backfills canonical tag identities that MySQL cannot derive with NFKC normalization. */
public class V19__backfill_contact_tag_key extends BaseJavaMigration {

    private static final int PAGE_SIZE = 500;
    private static final int TAG_KEY_MAX_LENGTH = 500;
    private static final int MIGRATION_CHECKSUM = 19_001;
    private static final List<String> PRIMARY_KEY_COLUMNS =
            List.of("tenant_id", "contact_id", "tag_key");
    private static final List<String> CATALOG_INDEX_COLUMNS =
            List.of("tenant_id", "tag_key", "contact_id", "tag_name");

    @Override
    public boolean canExecuteInTransaction() {
        return false;
    }

    @Override
    public Integer getChecksum() {
        return MIGRATION_CHECKSUM;
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        ensureTagKeyColumn(connection);
        backfill(connection);
        enforceFinalSchema(connection);
    }

    private void ensureTagKeyColumn(Connection connection) throws SQLException {
        if (hasColumn(connection, "tag_key")) {
            return;
        }
        execute(connection, """
                ALTER TABLE crm_contact_tag
                    ADD COLUMN tag_key VARCHAR(500)
                        CHARACTER SET utf8mb4
                        COLLATE utf8mb4_bin
                        NULL
                        AFTER tag_name
                """);
    }

    private void enforceFinalSchema(Connection connection) throws SQLException {
        execute(connection, """
                ALTER TABLE crm_contact_tag
                    MODIFY tag_key VARCHAR(500)
                        CHARACTER SET utf8mb4
                        COLLATE utf8mb4_bin
                        NOT NULL
                """);
        ensurePrimaryKey(connection);
        ensureCatalogIndex(connection);
    }

    private void ensurePrimaryKey(Connection connection) throws SQLException {
        List<String> current = primaryKeyColumns(connection);
        if (PRIMARY_KEY_COLUMNS.equals(current)) {
            return;
        }
        if (current.isEmpty()) {
            execute(connection, """
                    ALTER TABLE crm_contact_tag
                        ADD CONSTRAINT pk_crm_contact_tag
                            PRIMARY KEY (tenant_id, contact_id, tag_key)
                    """);
            return;
        }
        execute(connection, """
                ALTER TABLE crm_contact_tag
                    DROP PRIMARY KEY,
                    ADD CONSTRAINT pk_crm_contact_tag
                        PRIMARY KEY (tenant_id, contact_id, tag_key)
                """);
    }

    private void ensureCatalogIndex(Connection connection) throws SQLException {
        List<String> current = indexColumns(connection, "idx_crm_contact_tag_catalog");
        if (CATALOG_INDEX_COLUMNS.equals(current)) {
            return;
        }
        if (!current.isEmpty()) {
            execute(connection, "ALTER TABLE crm_contact_tag "
                    + "DROP INDEX idx_crm_contact_tag_catalog");
        }
        execute(connection, """
                ALTER TABLE crm_contact_tag
                    ADD INDEX idx_crm_contact_tag_catalog
                        (tenant_id, tag_key, contact_id, tag_name)
                """);
    }

    private boolean hasColumn(Connection connection, String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'crm_contact_tag'
                  AND column_name = ?
                """)) {
            statement.setString(1, column);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private List<String> primaryKeyColumns(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT column_name
                FROM information_schema.key_column_usage
                WHERE table_schema = DATABASE()
                  AND table_name = 'crm_contact_tag'
                  AND constraint_name = 'PRIMARY'
                ORDER BY ordinal_position
                """)) {
            return columns(statement);
        }
    }

    private List<String> indexColumns(Connection connection, String indexName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT column_name
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'crm_contact_tag'
                  AND index_name = ?
                ORDER BY seq_in_index
                """)) {
            statement.setString(1, indexName);
            return columns(statement);
        }
    }

    private List<String> columns(PreparedStatement statement) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                columns.add(result.getString("column_name"));
            }
        }
        return List.copyOf(columns);
    }

    private void backfill(Connection connection) throws SQLException {
        Long lastTenantId = null;
        Long lastContactId = null;
        String lastTagName = null;
        Long currentTenantId = null;
        Long currentContactId = null;
        Set<String> contactKeys = new HashSet<>();

        while (true) {
            List<TagRow> rows = page(connection, lastTenantId, lastContactId, lastTagName);
            if (rows.isEmpty()) {
                return;
            }
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE crm_contact_tag
                    SET tag_key = ?
                    WHERE tenant_id = ? AND contact_id = ? AND tag_name = ?
                    """);
                 PreparedStatement delete = connection.prepareStatement("""
                    DELETE FROM crm_contact_tag
                    WHERE tenant_id = ? AND contact_id = ? AND tag_name = ?
                    """)) {
                for (TagRow row : rows) {
                    if (!row.tenantId().equals(currentTenantId)
                            || !row.contactId().equals(currentContactId)) {
                        currentTenantId = row.tenantId();
                        currentContactId = row.contactId();
                        contactKeys.clear();
                    }
                    String tagKey = tagKey(row.tagName());
                    if (contactKeys.add(tagKey)) {
                        update.setString(1, tagKey);
                        update.setLong(2, row.tenantId());
                        update.setLong(3, row.contactId());
                        update.setString(4, row.tagName());
                        requireSingleRow(update.executeUpdate());
                    } else {
                        delete.setLong(1, row.tenantId());
                        delete.setLong(2, row.contactId());
                        delete.setString(3, row.tagName());
                        requireSingleRow(delete.executeUpdate());
                    }
                    lastTenantId = row.tenantId();
                    lastContactId = row.contactId();
                    lastTagName = row.tagName();
                }
            }
        }
    }

    private List<TagRow> page(
            Connection connection, Long tenantId, Long contactId, String tagName)
            throws SQLException {
        String sql = tenantId == null ? """
                SELECT tenant_id, contact_id, tag_name
                FROM crm_contact_tag
                ORDER BY tenant_id, contact_id, tag_name
                LIMIT ?
                """ : """
                SELECT tenant_id, contact_id, tag_name
                FROM crm_contact_tag
                WHERE tenant_id > ?
                   OR (tenant_id = ? AND contact_id > ?)
                   OR (tenant_id = ? AND contact_id = ? AND tag_name > ?)
                ORDER BY tenant_id, contact_id, tag_name
                LIMIT ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (tenantId == null) {
                statement.setInt(1, PAGE_SIZE);
            } else {
                statement.setLong(1, tenantId);
                statement.setLong(2, tenantId);
                statement.setLong(3, contactId);
                statement.setLong(4, tenantId);
                statement.setLong(5, contactId);
                statement.setString(6, tagName);
                statement.setInt(7, PAGE_SIZE);
            }
            List<TagRow> rows = new ArrayList<>();
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(new TagRow(
                            result.getLong("tenant_id"),
                            result.getLong("contact_id"),
                            result.getString("tag_name")));
                }
            }
            return rows;
        }
    }

    /**
     * Frozen V19 normalization contract. Do not delegate historical migrations to evolving domain
     * policies.
     */
    private String tagKey(String tagName) throws SQLException {
        if (tagName == null || tagName.isBlank()) {
            throw new SQLException("Persisted contact tag cannot be normalized");
        }
        String tagKey = Normalizer.normalize(tagName.trim(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        if (tagKey.length() > TAG_KEY_MAX_LENGTH) {
            throw new SQLException("Persisted contact tag cannot be normalized");
        }
        return tagKey;
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void requireSingleRow(int affectedRows) throws SQLException {
        if (affectedRows != 1) {
            throw new SQLException("Contact tag migration changed an unexpected row count");
        }
    }

    private record TagRow(Long tenantId, Long contactId, String tagName) {
    }
}
