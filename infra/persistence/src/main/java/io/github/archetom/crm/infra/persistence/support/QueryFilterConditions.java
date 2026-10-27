package io.github.archetom.crm.infra.persistence.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.model.FilterOperator;

import java.util.List;
import java.util.stream.Collectors;

/** SQL translation for already-authorized, normalized field conditions. */
public final class QueryFilterConditions {
    private QueryFilterConditions() {
    }

    public static void applyBuiltin(
            QueryWrapper<?> query, String column, FilterCondition condition) {
        List<String> values = condition.values();
        FilterOperator operator = condition.operator();
        if (operator == FilterOperator.EQ) {
            query.eq(column, values.getFirst());
        } else if (operator == FilterOperator.NOT_EQ) {
            query.ne(column, values.getFirst());
        } else if (operator == FilterOperator.CONTAINS) {
            query.apply("LOCATE({0}, COALESCE(" + column + ", '')) > 0", values.getFirst());
        } else if (operator == FilterOperator.NOT_CONTAINS) {
            query.apply("LOCATE({0}, COALESCE(" + column + ", '')) = 0", values.getFirst());
        } else if (operator == FilterOperator.IS_EMPTY) {
            query.apply("COALESCE(CAST(" + column + " AS CHAR), '') = ''");
        } else if (operator == FilterOperator.IS_NOT_EMPTY) {
            query.apply("COALESCE(CAST(" + column + " AS CHAR), '') <> ''");
        } else if (operator == FilterOperator.GT) {
            query.gt(column, values.getFirst());
        } else if (operator == FilterOperator.GTE) {
            query.ge(column, values.getFirst());
        } else if (operator == FilterOperator.LT) {
            query.lt(column, values.getFirst());
        } else if (operator == FilterOperator.LTE) {
            query.le(column, values.getFirst());
        } else if (operator == FilterOperator.IN) {
            query.in(column, values);
        } else if (operator == FilterOperator.BETWEEN) {
            query.between(column, values.get(0), values.get(1));
        }
    }

    public static void applyCustom(
            QueryWrapper<?> query, FilterCondition condition, String rootTable,
            String fieldValueTable, String recordIdColumn) {
        String base = """
                SELECT 1
                FROM %s field_value
                JOIN custom_field_definition field_definition
                  ON field_definition.tenant_id = field_value.tenant_id
                 AND field_definition.object_type = field_value.object_type
                 AND field_definition.id = field_value.field_id
                WHERE field_value.tenant_id = %s.tenant_id
                  AND field_value.%s = %s.id
                  AND field_definition.field_code = {0}
                """.formatted(fieldValueTable, rootTable, recordIdColumn, rootTable);
        String fieldCode = condition.fieldCode().getValue();
        FilterOperator operator = condition.operator();
        if (operator == FilterOperator.IS_EMPTY) {
            query.apply("NOT EXISTS (" + base + ")", fieldCode);
        } else if (operator == FilterOperator.IS_NOT_EMPTY) {
            query.apply("EXISTS (" + base + ")", fieldCode);
        } else if (operator == FilterOperator.EQ) {
            query.apply("EXISTS (" + base
                            + " AND JSON_CONTAINS(field_value.values_json, JSON_QUOTE({1})))",
                    fieldCode, condition.values().getFirst());
        } else if (operator == FilterOperator.NOT_EQ) {
            query.apply("EXISTS (" + base
                            + " AND NOT JSON_CONTAINS(field_value.values_json, JSON_QUOTE({1})))",
                    fieldCode, condition.values().getFirst());
        } else if (operator == FilterOperator.IN) {
            query.apply("EXISTS (" + base
                            + " AND JSON_OVERLAPS(field_value.values_json, CAST({1} AS JSON)))",
                    fieldCode, json(condition.values()));
        } else if (operator == FilterOperator.CONTAINS) {
            query.apply("EXISTS (" + base
                            + " AND LOCATE({1}, COALESCE(field_value.scalar_value, '')) > 0)",
                    fieldCode, condition.values().getFirst());
        } else if (operator == FilterOperator.NOT_CONTAINS) {
            query.apply("NOT EXISTS (" + base
                            + " AND LOCATE({1}, COALESCE(field_value.scalar_value, '')) > 0)",
                    fieldCode, condition.values().getFirst());
        } else if (operator == FilterOperator.BETWEEN) {
            query.apply("EXISTS (" + base + " AND "
                            + orderedExpression("{1}", ">=") + " AND "
                            + orderedExpression("{2}", "<=") + ")",
                    fieldCode, condition.values().get(0), condition.values().get(1));
        } else if (operator == FilterOperator.GT || operator == FilterOperator.GTE
                || operator == FilterOperator.LT || operator == FilterOperator.LTE) {
            String symbol = operator == FilterOperator.GT ? ">"
                    : operator == FilterOperator.GTE ? ">="
                    : operator == FilterOperator.LT ? "<" : "<=";
            query.apply("EXISTS (" + base + " AND "
                            + orderedExpression("{1}", symbol) + ")",
                    fieldCode, condition.values().getFirst());
        }
    }

    private static String orderedExpression(String parameter, String operator) {
        return "((field_definition.field_type IN ('INTEGER','DECIMAL') AND "
                + "CAST(field_value.scalar_value AS DECIMAL(30,10)) " + operator
                + " CAST(" + parameter + " AS DECIMAL(30,10))) OR "
                + "(field_definition.field_type NOT IN ('INTEGER','DECIMAL') AND "
                + "field_value.scalar_value " + operator + " " + parameter + "))";
    }

    private static String json(List<String> values) {
        return values.stream().map(QueryFilterConditions::jsonString)
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String jsonString(String value) {
        StringBuilder escaped = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '"' || character == '\\') {
                escaped.append('\\').append(character);
            } else if (character == '\b') {
                escaped.append("\\b");
            } else if (character == '\f') {
                escaped.append("\\f");
            } else if (character == '\n') {
                escaped.append("\\n");
            } else if (character == '\r') {
                escaped.append("\\r");
            } else if (character == '\t') {
                escaped.append("\\t");
            } else if (character < 0x20) {
                escaped.append(String.format("\\u%04x", (int) character));
            } else {
                escaped.append(character);
            }
        }
        return escaped.append('"').toString();
    }
}
