package io.github.archetom.crm.infra.persistence.query;

import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.model.ContractAccessMode;
import io.github.archetom.crm.domain.model.ContractVisibility;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.CustomReportQuery;
import io.github.archetom.crm.domain.model.CustomReportRow;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.OpportunityAccessMode;
import io.github.archetom.crm.domain.model.OpportunityVisibility;
import io.github.archetom.crm.domain.model.ReceivableVisibility;
import io.github.archetom.crm.domain.model.ReportDateRange;
import io.github.archetom.crm.domain.model.ReportFieldReference;
import io.github.archetom.crm.domain.model.ReportGranularity;
import io.github.archetom.crm.domain.model.ReportMeasure;
import io.github.archetom.crm.domain.model.ReportMeasureType;
import io.github.archetom.crm.domain.policy.ContractFieldCatalog;
import io.github.archetom.crm.domain.policy.OpportunityFieldCatalog;
import io.github.archetom.crm.domain.policy.ReceivableFieldCatalog;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.support.SqlLikePattern;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Executes only server-described root columns or numeric custom-field joins. No request string is
 * ever used as a table, column, expression, ordering fragment, or alias.
 */
@Component
@RequiredArgsConstructor
public class MySqlCustomReportQuery {

    private final JdbcTemplate jdbcTemplate;

    public List<CustomReportRow> opportunity(
            TenantId tenantId, OpportunityVisibility visibility, CustomReportQuery query) {
        return execute(Source.OPPORTUNITY, tenantId, visibility.dataScope(),
                visibility.callerMemberId(), visibility.ownerDepartmentIds(),
                visibility.accessMode() != OpportunityAccessMode.OWNER_SCOPE,
                query);
    }

    public List<CustomReportRow> contract(
            TenantId tenantId, ContractVisibility visibility, CustomReportQuery query) {
        return execute(Source.CONTRACT, tenantId, visibility.dataScope(),
                visibility.callerMemberId(), visibility.ownerDepartmentIds(),
                visibility.accessMode() != ContractAccessMode.OWNER_SCOPE,
                query);
    }

    public List<CustomReportRow> receivable(
            TenantId tenantId, ReceivableVisibility visibility, CustomReportQuery query) {
        return execute(Source.RECEIVABLE, tenantId, visibility.dataScope(),
                visibility.callerMemberId(), visibility.ownerDepartmentIds(), false, query);
    }

    private List<CustomReportRow> execute(
            Source source, TenantId tenantId, DataScope scope, MemberId memberId,
            Collection<DepartmentId> departments, boolean collaborators,
            CustomReportQuery query) {
        if (tenantId == null || scope == null || memberId == null || query == null) {
            throw new IllegalArgumentException("custom report execution context is invalid");
        }
        Map<FieldCode, String> customAliases = customAliases(query);
        List<Object> parameters = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT ");
        String time = expression(source, query.timeField(), customAliases);
        String period = period(time, query.granularity());
        sql.append(period).append(" AS period_start");
        String dimension = null;
        if (query.dimensionField() != null) {
            dimension = dimensionExpression(source, query.dimensionField(), customAliases);
            sql.append(", ").append(dimension).append(" AS dimension_key");
        } else {
            sql.append(", NULL AS dimension_key");
        }
        for (int index = 0; index < query.measures().size(); index++) {
            ReportMeasure measure = query.measures().get(index);
            if (measure.type() == ReportMeasureType.COUNT) {
                sql.append(", COUNT(*) AS measure_").append(index);
            } else {
                ReportFieldReference field = requireField(query, measure.fieldCode());
                sql.append(", COALESCE(SUM(CAST(")
                        .append(expression(source, field, customAliases))
                        .append(" AS DECIMAL(65,10))), 0) AS measure_").append(index);
            }
        }
        sql.append(" FROM ").append(source.rootTable).append(" report_root");
        customAliases.entrySet().stream().sorted(Map.Entry.comparingByValue())
                .forEach(entry -> {
                    ReportFieldReference field = requireField(query, entry.getKey());
                    sql.append(" LEFT JOIN ").append(source.valueTable).append(' ')
                            .append(entry.getValue())
                            .append(" ON ").append(entry.getValue())
                            .append(".tenant_id = report_root.tenant_id AND ")
                            .append(entry.getValue()).append('.').append(source.valueRootColumn)
                            .append(" = report_root.id AND ").append(entry.getValue())
                            .append(".field_id = ?");
                    parameters.add(field.definitionId());
                });
        TemporalBounds timeBounds = temporalBounds(query.timeField(), query.range());
        sql.append(" WHERE report_root.tenant_id = ?")
                .append(" AND ").append(source.lifecyclePredicate)
                .append(" AND ").append(time).append(" >= ?");
        parameters.add(tenantId.getValue());
        parameters.add(timeBounds.lower());
        if (timeBounds.inclusiveUpper()) {
            sql.append(" AND ").append(time).append(" <= ?");
            parameters.add(timeBounds.upper());
        } else {
            sql.append(" AND ").append(time).append(" < ?");
            parameters.add(timeBounds.upper());
        }
        appendVisibility(sql, parameters, source, scope, memberId, departments, collaborators);
        for (FilterCondition filter : query.filters()) {
            appendFilter(sql, parameters, source, requireField(query, filter.fieldCode()),
                    customAliases, filter);
        }
        sql.append(" GROUP BY ").append(period);
        if (dimension != null) sql.append(", ").append(dimension);
        sql.append(" ORDER BY period_start ASC");
        if (dimension != null) sql.append(", ").append(dimension).append(" ASC");
        sql.append(" LIMIT ?");
        parameters.add(query.rowLimit() + 1);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> row(rs, query),
                parameters.toArray());
    }

    private Map<FieldCode, String> customAliases(CustomReportQuery query) {
        LinkedHashSet<FieldCode> codes = new LinkedHashSet<>();
        if (!query.timeField().builtin()) codes.add(query.timeField().code());
        if (query.dimensionField() != null && !query.dimensionField().builtin()) {
            codes.add(query.dimensionField().code());
        }
        query.measures().stream().map(ReportMeasure::fieldCode)
                .filter(java.util.Objects::nonNull)
                .filter(code -> !requireField(query, code).builtin()).forEach(codes::add);
        query.filters().stream().map(FilterCondition::fieldCode)
                .filter(code -> !requireField(query, code).builtin()).forEach(codes::add);
        List<FieldCode> sorted = codes.stream()
                .sorted(Comparator.comparing(FieldCode::getValue)).toList();
        Map<FieldCode, String> aliases = new LinkedHashMap<>();
        for (int index = 0; index < sorted.size(); index++) {
            aliases.put(sorted.get(index), "report_value_" + index);
        }
        return aliases;
    }

    private void appendVisibility(
            StringBuilder sql, List<Object> parameters, Source source, DataScope scope,
            MemberId memberId, Collection<DepartmentId> departments, boolean collaborators) {
        if (scope == DataScope.TENANT) return;
        sql.append(" AND (");
        if (scope == DataScope.SELF) {
            sql.append("report_root.owner_actor_id = ?");
            parameters.add(memberId.getValue());
        } else {
            List<Long> ids = departments == null ? List.of() : departments.stream()
                    .map(DepartmentId::getValue).sorted().toList();
            if (ids.isEmpty()) throw new IllegalArgumentException("report departments are required");
            sql.append("EXISTS (SELECT 1 FROM organization_membership report_owner ")
                    .append("WHERE report_owner.tenant_id = report_root.tenant_id ")
                    .append("AND report_owner.actor_id = report_root.owner_actor_id ")
                    .append("AND report_owner.status = 'ACTIVE' AND report_owner.department_id IN (")
                    .append(placeholders(ids.size())).append("))");
            parameters.addAll(ids);
        }
        if (collaborators) {
            sql.append(" OR EXISTS (SELECT 1 FROM ").append(source.collaboratorTable)
                    .append(" report_collaborator WHERE report_collaborator.tenant_id = ")
                    .append("report_root.tenant_id AND report_collaborator.")
                    .append(source.collaboratorRootColumn).append(" = report_root.id ")
                    .append("AND report_collaborator.actor_id = ?)");
            parameters.add(memberId.getValue());
        }
        sql.append(')');
    }

    private void appendFilter(
            StringBuilder sql, List<Object> parameters, Source source,
            ReportFieldReference field, Map<FieldCode, String> customAliases,
            FilterCondition filter) {
        if (field.type() == CustomFieldType.RELATION) {
            throw new IllegalArgumentException("report relation filters are unsupported");
        }
        if (field.type() == CustomFieldType.MULTI_SELECT) {
            appendMultiSelectFilter(sql, parameters, field, customAliases, filter);
            return;
        }
        String scalar = comparableExpression(source, field, customAliases);
        List<String> values = filter.values();
        switch (filter.operator()) {
            case IS_EMPTY -> sql.append(" AND (").append(scalar)
                    .append(" IS NULL OR CAST(").append(scalar).append(" AS CHAR) = '')");
            case IS_NOT_EMPTY -> sql.append(" AND (").append(scalar)
                    .append(" IS NOT NULL AND CAST(").append(scalar).append(" AS CHAR) <> '')");
            case EQ -> binary(sql, parameters, scalar, " = ?", values.getFirst());
            case NOT_EQ -> binary(sql, parameters, scalar, " <> ?", values.getFirst());
            case GT -> binary(sql, parameters, scalar, " > ?", values.getFirst());
            case GTE -> binary(sql, parameters, scalar, " >= ?", values.getFirst());
            case LT -> binary(sql, parameters, scalar, " < ?", values.getFirst());
            case LTE -> binary(sql, parameters, scalar, " <= ?", values.getFirst());
            case IN -> {
                sql.append(" AND ").append(scalar).append(" IN (")
                        .append(placeholders(values.size())).append(')');
                parameters.addAll(values);
            }
            case BETWEEN -> {
                sql.append(" AND ").append(scalar).append(" BETWEEN ? AND ?");
                parameters.addAll(values);
            }
            case CONTAINS, NOT_CONTAINS -> {
                sql.append(" AND ");
                if (filter.operator() == io.github.archetom.crm.domain.model.FilterOperator.NOT_CONTAINS) {
                    sql.append('(').append(scalar).append(" IS NULL OR ");
                }
                sql.append("CAST(").append(scalar).append(" AS CHAR) ")
                        .append(filter.operator() == io.github.archetom.crm.domain.model.FilterOperator.CONTAINS
                                ? "LIKE" : "NOT LIKE")
                        .append(" ? ESCAPE '!'");
                if (filter.operator() == io.github.archetom.crm.domain.model.FilterOperator.NOT_CONTAINS) {
                    sql.append(')');
                }
                parameters.add("%" + SqlLikePattern.literal(values.getFirst()) + "%");
            }
        }
    }

    private void appendMultiSelectFilter(
            StringBuilder sql, List<Object> parameters, ReportFieldReference field,
            Map<FieldCode, String> customAliases, FilterCondition filter) {
        if (field.builtin()) {
            throw new IllegalArgumentException("built-in multi-select report field is unsupported");
        }
        String alias = customAliases.get(field.code());
        String json = alias + ".values_json";
        switch (filter.operator()) {
            case IS_EMPTY -> sql.append(" AND (").append(json)
                    .append(" IS NULL OR JSON_LENGTH(").append(json).append(") = 0)");
            case IS_NOT_EMPTY -> sql.append(" AND (").append(json)
                    .append(" IS NOT NULL AND JSON_LENGTH(").append(json).append(") > 0)");
            case EQ -> {
                sql.append(" AND JSON_CONTAINS(").append(json).append(", JSON_QUOTE(?)) = 1");
                parameters.add(filter.values().getFirst());
            }
            case NOT_EQ -> {
                sql.append(" AND (").append(json).append(" IS NOT NULL AND JSON_CONTAINS(")
                        .append(json).append(", JSON_QUOTE(?)) = 0)");
                parameters.add(filter.values().getFirst());
            }
            case IN -> {
                sql.append(" AND (");
                for (int index = 0; index < filter.values().size(); index++) {
                    if (index > 0) sql.append(" OR ");
                    sql.append("JSON_CONTAINS(").append(json).append(", JSON_QUOTE(?)) = 1");
                    parameters.add(filter.values().get(index));
                }
                sql.append(')');
            }
            default -> throw new IllegalArgumentException(
                    "multi-select report filter operator is unsupported");
        }
    }

    private String comparableExpression(
            Source source, ReportFieldReference field, Map<FieldCode, String> aliases) {
        String expression = expression(source, field, aliases);
        return switch (field.type()) {
            case INTEGER, DECIMAL -> "CAST(" + expression + " AS DECIMAL(30,10))";
            case DATE -> "CAST(" + expression + " AS DATE)";
            case DATETIME -> "CAST(" + expression + " AS DATETIME(6))";
            case SELECT -> binarySelectKey(expression);
            default -> expression;
        };
    }

    private String dimensionExpression(
            Source source, ReportFieldReference field, Map<FieldCode, String> aliases) {
        String value = expression(source, field, aliases);
        return field.type() == CustomFieldType.SELECT
                ? binarySelectKey(value) : "CAST(" + value + " AS CHAR)";
    }

    private String binarySelectKey(String expression) {
        return "CONVERT(" + expression + " USING utf8mb4) COLLATE utf8mb4_bin";
    }

    private TemporalBounds temporalBounds(
            ReportFieldReference field, ReportDateRange range) {
        boolean maximumDate = range.to().equals(LocalDate.of(9999, 12, 31));
        if (field.type() == CustomFieldType.DATE) {
            Object lower = field.builtin() ? range.from() : range.from().toString();
            LocalDate upperDate = maximumDate ? range.to() : range.to().plusDays(1);
            Object upper = field.builtin() ? upperDate : upperDate.toString();
            return new TemporalBounds(lower, upper, maximumDate);
        }
        if (field.type() == CustomFieldType.DATETIME) {
            LocalDateTime lowerDateTime = range.from().atStartOfDay();
            LocalDateTime upperDateTime = maximumDate
                    ? range.to().atTime(23, 59, 59, 999_999_000)
                    : range.to().plusDays(1).atStartOfDay();
            Object lower = field.builtin() ? lowerDateTime : lowerDateTime.toString();
            Object upper = field.builtin() ? upperDateTime : upperDateTime.toString();
            return new TemporalBounds(lower, upper, maximumDate);
        }
        throw new IllegalArgumentException("report time field type is unsupported");
    }

    private void binary(StringBuilder sql, List<Object> parameters,
                        String expression, String operator, Object value) {
        sql.append(" AND ").append(expression).append(operator);
        parameters.add(value);
    }

    private String expression(
            Source source, ReportFieldReference field, Map<FieldCode, String> customAliases) {
        if (field.builtin()) {
            String column = source.builtinColumns.get(field.code());
            if (column == null) {
                throw new IllegalArgumentException("report built-in field is not executable");
            }
            return "report_root." + column;
        }
        String alias = customAliases.get(field.code());
        if (alias == null) throw new IllegalArgumentException("report custom field is unresolved");
        return alias + ".scalar_value";
    }

    private String period(String expression, ReportGranularity granularity) {
        return switch (granularity) {
            case DAY -> "DATE(" + expression + ")";
            case WEEK -> "DATE_SUB(DATE(" + expression + "), INTERVAL WEEKDAY("
                    + expression + ") DAY)";
            case MONTH -> "DATE_SUB(DATE(" + expression
                    + "), INTERVAL (DAYOFMONTH(" + expression + ") - 1) DAY)";
        };
    }

    private CustomReportRow row(ResultSet result, CustomReportQuery query) throws SQLException {
        Object date = result.getObject("period_start");
        LocalDate period = date instanceof LocalDate local ? local
                : date instanceof java.sql.Date sqlDate ? sqlDate.toLocalDate()
                : LocalDate.parse(date.toString());
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        for (int index = 0; index < query.measures().size(); index++) {
            BigDecimal value = result.getBigDecimal("measure_" + index);
            values.put(query.measures().get(index).key(), value == null ? BigDecimal.ZERO : value);
        }
        return new CustomReportRow(period, result.getString("dimension_key"), values);
    }

    private ReportFieldReference requireField(CustomReportQuery query, FieldCode code) {
        ReportFieldReference field = query.fields().get(code);
        if (field == null) throw new IllegalArgumentException("report field is unresolved");
        return field;
    }

    private String placeholders(int size) {
        return java.util.stream.IntStream.range(0, size).mapToObj(index -> "?")
                .collect(Collectors.joining(","));
    }

    private enum Source {
        OPPORTUNITY("crm_opportunity", "crm_opportunity_field_value", "opportunity_id",
                "crm_opportunity_collaborator", "opportunity_id",
                "report_root.status = 'ACTIVE'", opportunityColumns()),
        CONTRACT("crm_contract", "crm_contract_field_value", "contract_id",
                "crm_contract_collaborator", "contract_id",
                "report_root.status IN ('ACTIVE', 'VOIDED')", contractColumns()),
        RECEIVABLE("crm_receivable", "crm_receivable_field_value", "receivable_id",
                null, null, "report_root.status = 'ACTIVE'", receivableColumns());

        private final String rootTable;
        private final String valueTable;
        private final String valueRootColumn;
        private final String collaboratorTable;
        private final String collaboratorRootColumn;
        private final String lifecyclePredicate;
        private final Map<FieldCode, String> builtinColumns;

        Source(String rootTable, String valueTable, String valueRootColumn,
               String collaboratorTable, String collaboratorRootColumn,
               String lifecyclePredicate,
               Map<FieldCode, String> builtinColumns) {
            this.rootTable = rootTable;
            this.valueTable = valueTable;
            this.valueRootColumn = valueRootColumn;
            this.collaboratorTable = collaboratorTable;
            this.collaboratorRootColumn = collaboratorRootColumn;
            this.lifecyclePredicate = lifecyclePredicate;
            this.builtinColumns = builtinColumns;
        }
    }

    private record TemporalBounds(Object lower, Object upper, boolean inclusiveUpper) { }

    private static Map<FieldCode, String> opportunityColumns() {
        return columns(Map.ofEntries(
                entry(OpportunityFieldCatalog.NAME, "name"),
                entry(OpportunityFieldCatalog.CUSTOMER, "customer_id"),
                entry(OpportunityFieldCatalog.PIPELINE_CODE, "pipeline_code"),
                entry(OpportunityFieldCatalog.STAGE_CODE, "stage_code"),
                entry(OpportunityFieldCatalog.EXPECTED_CLOSE_DATE, "expected_close_date"),
                entry(OpportunityFieldCatalog.ESTIMATED_AMOUNT, "estimated_amount"),
                entry(OpportunityFieldCatalog.NEXT_CONTACT_TIME, "next_contact_time"),
                entry(OpportunityFieldCatalog.DESCRIPTION, "description"),
                entry(OpportunityFieldCatalog.OWNER_ACTOR, "owner_actor_id"),
                entry(OpportunityFieldCatalog.OUTCOME, "outcome"),
                entry(OpportunityFieldCatalog.OUTCOME_TIME, "outcome_time"),
                entry(OpportunityFieldCatalog.OUTCOME_REMARK, "outcome_remark"),
                entry(OpportunityFieldCatalog.STATUS, "status"),
                entry(OpportunityFieldCatalog.CREATED_TIME, "created_time"),
                entry(OpportunityFieldCatalog.UPDATED_TIME, "updated_time")));
    }

    private static Map<FieldCode, String> contractColumns() {
        return columns(Map.ofEntries(
                entry(ContractFieldCatalog.NAME, "name"), entry(ContractFieldCatalog.NUMBER, "contract_number"),
                entry(ContractFieldCatalog.CUSTOMER, "customer_id"),
                entry(ContractFieldCatalog.OPPORTUNITY, "opportunity_id"),
                entry(ContractFieldCatalog.CONTACT, "contact_id"),
                entry(ContractFieldCatalog.ORDER_DATE, "order_date"),
                entry(ContractFieldCatalog.START_DATE, "start_date"),
                entry(ContractFieldCatalog.END_DATE, "end_date"),
                entry(ContractFieldCatalog.CONTRACT_TYPE, "contract_type"),
                entry(ContractFieldCatalog.PAYMENT_TYPE, "payment_type"),
                entry(ContractFieldCatalog.REMARK, "remark"),
                entry(ContractFieldCatalog.SUBTOTAL, "subtotal"),
                entry(ContractFieldCatalog.DISCOUNT_RATE, "discount_rate"),
                entry(ContractFieldCatalog.TOTAL_AMOUNT, "total_amount"),
                entry(ContractFieldCatalog.OWNER_ACTOR, "owner_actor_id"),
                entry(ContractFieldCatalog.STATUS, "status"),
                entry(ContractFieldCatalog.CREATED_TIME, "created_time"),
                entry(ContractFieldCatalog.UPDATED_TIME, "updated_time")));
    }

    private static Map<FieldCode, String> receivableColumns() {
        return columns(Map.ofEntries(
                entry(ReceivableFieldCatalog.NUMBER, "receipt_number"),
                entry(ReceivableFieldCatalog.CUSTOMER, "customer_id"),
                entry(ReceivableFieldCatalog.CONTRACT, "contract_id"),
                entry(ReceivableFieldCatalog.PLAN, "receivable_plan_id"),
                entry(ReceivableFieldCatalog.RECEIVED_DATE, "received_date"),
                entry(ReceivableFieldCatalog.PAYMENT_METHOD, "payment_method"),
                entry(ReceivableFieldCatalog.AMOUNT, "amount"),
                entry(ReceivableFieldCatalog.REMARK, "remark"),
                entry(ReceivableFieldCatalog.OWNER_ACTOR, "owner_actor_id"),
                entry(ReceivableFieldCatalog.STATUS, "status"),
                entry(ReceivableFieldCatalog.CREATED_TIME, "created_time"),
                entry(ReceivableFieldCatalog.UPDATED_TIME, "updated_time")));
    }

    private static Map.Entry<String, String> entry(String code, String column) {
        return Map.entry(code, column);
    }

    private static Map<FieldCode, String> columns(Map<String, String> source) {
        return source.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                value -> new FieldCode(value.getKey()), Map.Entry::getValue));
    }
}
