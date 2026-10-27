package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.exception.ApprovalDomainException;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.valueobject.FieldCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Evaluates bounded typed routing conditions without executable expressions. */
public final class ApprovalConditionEvaluator {

    private ApprovalConditionEvaluator() {
    }

    public static boolean matches(
            Collection<FilterCondition> conditions,
            Map<FieldCode, List<String>> facts,
            Map<FieldCode, CustomFieldType> fieldTypes) {
        if (conditions == null || facts == null || fieldTypes == null) {
            throw new ApprovalDomainException("approval condition input is invalid");
        }
        SavedViewPolicy.validate(conditions, fieldTypes);
        return conditions.stream().allMatch(condition -> match(
                condition, facts.getOrDefault(condition.fieldCode(), List.of()),
                fieldTypes.get(condition.fieldCode())));
    }

    private static boolean match(FilterCondition condition, List<String> actual,
                                 CustomFieldType type) {
        FilterOperator operator = condition.operator();
        List<String> expected = condition.values();
        if (operator == FilterOperator.IS_EMPTY) {
            return actual.isEmpty() || actual.stream().allMatch(String::isBlank);
        }
        if (operator == FilterOperator.IS_NOT_EMPTY) {
            return !actual.isEmpty() && actual.stream().anyMatch(value -> !value.isBlank());
        }
        if (actual.isEmpty()) {
            return false;
        }
        return switch (operator) {
            case EQ -> actual.stream().anyMatch(expected.getFirst()::equals);
            case NOT_EQ -> actual.stream().noneMatch(expected.getFirst()::equals);
            case CONTAINS -> actual.stream().anyMatch(value -> value.contains(expected.getFirst()));
            case NOT_CONTAINS -> actual.stream().noneMatch(value -> value.contains(expected.getFirst()));
            case IN -> actual.stream().anyMatch(expected::contains);
            case GT -> compare(actual.getFirst(), expected.getFirst(), type) > 0;
            case GTE -> compare(actual.getFirst(), expected.getFirst(), type) >= 0;
            case LT -> compare(actual.getFirst(), expected.getFirst(), type) < 0;
            case LTE -> compare(actual.getFirst(), expected.getFirst(), type) <= 0;
            case BETWEEN -> compare(actual.getFirst(), expected.get(0), type) >= 0
                    && compare(actual.getFirst(), expected.get(1), type) <= 0;
            case IS_EMPTY, IS_NOT_EMPTY -> throw new IllegalStateException("handled above");
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int compare(String left, String right, CustomFieldType type) {
        try {
            Comparable leftValue;
            Comparable rightValue;
            switch (type) {
                case INTEGER, DECIMAL -> {
                    leftValue = new BigDecimal(left);
                    rightValue = new BigDecimal(right);
                }
                case DATE -> {
                    leftValue = LocalDate.parse(left);
                    rightValue = LocalDate.parse(right);
                }
                case DATETIME -> {
                    leftValue = LocalDateTime.parse(left);
                    rightValue = LocalDateTime.parse(right);
                }
                default -> throw new ApprovalDomainException(
                        "approval field type is not ordered");
            }
            return leftValue.compareTo(rightValue);
        } catch (RuntimeException exception) {
            if (exception instanceof ApprovalDomainException approvalException) {
                throw approvalException;
            }
            throw new ApprovalDomainException("approval condition value is invalid");
        }
    }
}
