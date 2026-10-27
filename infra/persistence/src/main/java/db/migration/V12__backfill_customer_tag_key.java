package db.migration;

import io.github.archetom.crm.domain.policy.CustomerTagPolicy;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Backfills canonical tag identities that MySQL cannot derive with NFKC normalization. */
public class V12__backfill_customer_tag_key extends BaseJavaMigration {

    private static final int PAGE_SIZE = 500;

    @Override
    public boolean canExecuteInTransaction() {
        return false;
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        execute(connection, """
                ALTER TABLE crm_customer_tag
                    ADD COLUMN tag_key VARCHAR(500)
                        CHARACTER SET utf8mb4
                        COLLATE utf8mb4_bin
                        NULL
                        AFTER tag_name
                """);
        backfill(connection);
        execute(connection, """
                ALTER TABLE crm_customer_tag
                    DROP PRIMARY KEY,
                    DROP INDEX idx_crm_customer_tag_catalog,
                    MODIFY tag_key VARCHAR(500)
                        CHARACTER SET utf8mb4
                        COLLATE utf8mb4_bin
                        NOT NULL,
                    ADD CONSTRAINT pk_crm_customer_tag
                        PRIMARY KEY (tenant_id, customer_id, tag_key),
                    ADD INDEX idx_crm_customer_tag_catalog
                        (tenant_id, tag_key, customer_id, tag_name)
                """);
    }

    private void backfill(Connection connection) throws SQLException {
        Long lastTenantId = null;
        Long lastCustomerId = null;
        String lastTagName = null;
        Long currentTenantId = null;
        Long currentCustomerId = null;
        Set<String> customerKeys = new HashSet<>();

        while (true) {
            List<TagRow> rows = page(connection, lastTenantId, lastCustomerId, lastTagName);
            if (rows.isEmpty()) {
                return;
            }
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE crm_customer_tag
                    SET tag_key = ?
                    WHERE tenant_id = ? AND customer_id = ? AND tag_name = ?
                    """);
                 PreparedStatement delete = connection.prepareStatement("""
                    DELETE FROM crm_customer_tag
                    WHERE tenant_id = ? AND customer_id = ? AND tag_name = ?
                    """)) {
                for (TagRow row : rows) {
                    if (!row.tenantId().equals(currentTenantId)
                            || !row.customerId().equals(currentCustomerId)) {
                        currentTenantId = row.tenantId();
                        currentCustomerId = row.customerId();
                        customerKeys.clear();
                    }
                    String tagKey;
                    try {
                        tagKey = CustomerTagPolicy.key(row.tagName());
                    } catch (RuntimeException exception) {
                        throw new SQLException("Persisted customer tag cannot be normalized", exception);
                    }
                    if (customerKeys.add(tagKey)) {
                        update.setString(1, tagKey);
                        update.setLong(2, row.tenantId());
                        update.setLong(3, row.customerId());
                        update.setString(4, row.tagName());
                        requireSingleRow(update.executeUpdate());
                    } else {
                        delete.setLong(1, row.tenantId());
                        delete.setLong(2, row.customerId());
                        delete.setString(3, row.tagName());
                        requireSingleRow(delete.executeUpdate());
                    }
                    lastTenantId = row.tenantId();
                    lastCustomerId = row.customerId();
                    lastTagName = row.tagName();
                }
            }
        }
    }

    private List<TagRow> page(
            Connection connection, Long tenantId, Long customerId, String tagName)
            throws SQLException {
        String sql = tenantId == null ? """
                SELECT tenant_id, customer_id, tag_name
                FROM crm_customer_tag
                ORDER BY tenant_id, customer_id, tag_name
                LIMIT ?
                """ : """
                SELECT tenant_id, customer_id, tag_name
                FROM crm_customer_tag
                WHERE tenant_id > ?
                   OR (tenant_id = ? AND customer_id > ?)
                   OR (tenant_id = ? AND customer_id = ? AND tag_name > ?)
                ORDER BY tenant_id, customer_id, tag_name
                LIMIT ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (tenantId == null) {
                statement.setInt(1, PAGE_SIZE);
            } else {
                statement.setLong(1, tenantId);
                statement.setLong(2, tenantId);
                statement.setLong(3, customerId);
                statement.setLong(4, tenantId);
                statement.setLong(5, customerId);
                statement.setString(6, tagName);
                statement.setInt(7, PAGE_SIZE);
            }
            List<TagRow> rows = new ArrayList<>();
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(new TagRow(
                            result.getLong("tenant_id"),
                            result.getLong("customer_id"),
                            result.getString("tag_name")));
                }
            }
            return rows;
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void requireSingleRow(int affectedRows) throws SQLException {
        if (affectedRows != 1) {
            throw new SQLException("Customer tag migration changed an unexpected row count");
        }
    }

    private record TagRow(Long tenantId, Long customerId, String tagName) {
    }
}
