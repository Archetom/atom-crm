package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.exception.CustomizationDomainException;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.valueobject.FieldCode;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Validates saved filters against active searchable field metadata. */
public final class SavedViewPolicy {

    public static final int MAX_CONDITIONS = 50;
    private static final Set<FilterOperator> EMPTY = EnumSet.of(
            FilterOperator.IS_EMPTY, FilterOperator.IS_NOT_EMPTY);
    private static final Set<FilterOperator> TEXT = EnumSet.of(
            FilterOperator.EQ, FilterOperator.NOT_EQ, FilterOperator.CONTAINS,
            FilterOperator.NOT_CONTAINS, FilterOperator.IS_EMPTY, FilterOperator.IS_NOT_EMPTY);
    private static final Set<FilterOperator> CHOICE = EnumSet.of(
            FilterOperator.EQ, FilterOperator.NOT_EQ, FilterOperator.IN,
            FilterOperator.IS_EMPTY, FilterOperator.IS_NOT_EMPTY);
    private static final Set<FilterOperator> ORDERED = EnumSet.of(
            FilterOperator.EQ, FilterOperator.NOT_EQ, FilterOperator.GT, FilterOperator.GTE,
            FilterOperator.LT, FilterOperator.LTE, FilterOperator.BETWEEN,
            FilterOperator.IS_EMPTY, FilterOperator.IS_NOT_EMPTY);

    private SavedViewPolicy() {
    }

    public static void validate(Collection<FilterCondition> conditions,
                                Map<FieldCode, CustomFieldType> searchableFields) {
        if (conditions == null || conditions.size() > MAX_CONDITIONS || searchableFields == null) {
            throw new CustomizationDomainException("saved view conditions are invalid");
        }
        Set<FieldCode> used = new HashSet<>();
        for (FilterCondition condition : conditions) {
            if (condition == null || !used.add(condition.fieldCode())) {
                throw new CustomizationDomainException("saved view fields must be unique");
            }
            CustomFieldType type = searchableFields.get(condition.fieldCode());
            if (type == null || !operators(type).contains(condition.operator())) {
                throw new CustomizationDomainException("filter operator is incompatible with the field");
            }
        }
    }

    public static boolean isApplicable(Collection<FilterCondition> conditions,
                                       Map<FieldCode, CustomFieldType> searchableFields) {
        try {
            validate(conditions, searchableFields);
            return true;
        } catch (CustomizationDomainException exception) {
            return false;
        }
    }

    public static Set<FilterOperator> supportedOperators(CustomFieldType type) {
        if (type == null) {
            return Set.of();
        }
        return switch (type) {
            case TEXT, TEXTAREA, EMAIL, PHONE, URL, ADDRESS -> TEXT;
            case SELECT, MULTI_SELECT, BOOLEAN, USER, DEPARTMENT, RELATION -> CHOICE;
            case DATE, DATETIME, INTEGER, DECIMAL -> ORDERED;
            case FILE -> EMPTY;
        };
    }

    private static Set<FilterOperator> operators(CustomFieldType type) {
        return supportedOperators(type);
    }
}
