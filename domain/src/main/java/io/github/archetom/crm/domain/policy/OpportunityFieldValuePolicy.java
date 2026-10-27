package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.OpportunityFieldValue;
import io.github.archetom.crm.domain.exception.OpportunityDomainException;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.FilterOperator;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Type-aware normalization for values stored by the opportunity aggregate. */
public final class OpportunityFieldValuePolicy {

    private static final int DECIMAL_PRECISION = 30;
    private static final int DECIMAL_SCALE = 10;
    private static final int DECIMAL_INTEGER_DIGITS = DECIMAL_PRECISION - DECIMAL_SCALE;
    private static final int MINIMUM_DECIMAL_SCALE = 1 - DECIMAL_INTEGER_DIGITS;
    private static final int MAXIMUM_PLAIN_DECIMAL_LENGTH =
            DECIMAL_INTEGER_DIGITS + DECIMAL_SCALE + 2;

    private OpportunityFieldValuePolicy() {
    }

    public static OpportunityFieldValue normalize(
            FieldDefinition field, List<String> values) {
        if (field == null || !field.isActive() || field.isBuiltin()) {
            throw new OpportunityDomainException(
                    "opportunity custom field is unavailable");
        }
        List<String> input = values == null ? List.of() : values;
        if (input.isEmpty()) {
            if (field.isRequired()) {
                throw new OpportunityDomainException(
                        "required opportunity field is empty");
            }
            return null;
        }
        List<String> normalized = normalize(field.getFieldType(), input, field);
        if (field.isUnique() && normalized.size() != 1) {
            throw new OpportunityDomainException(
                    "unique opportunity fields require one value");
        }
        return new OpportunityFieldValue(field.getCode(), normalized);
    }

    /** Validates and canonicalizes persisted saved-view values before SQL translation. */
    public static FilterCondition normalizeFilterCondition(
            FieldDefinition field, FilterCondition condition) {
        if (field == null || condition == null || !field.isActive()
                || !field.isSearchable()
                || !field.getCode().equals(condition.fieldCode())) {
            throw new OpportunityDomainException(
                    "opportunity filter field is unavailable");
        }
        if (condition.values().isEmpty()) {
            return condition;
        }
        List<String> values = condition.values().stream()
                .map(value -> normalizeFilterValue(
                        field.getFieldType(), value, field, condition.operator()))
                .toList();
        if (condition.operator() == FilterOperator.IN) {
            values = List.copyOf(new LinkedHashSet<>(values));
        }
        return new FilterCondition(
                condition.fieldCode(), condition.operator(), values);
    }

    private static List<String> normalize(
            CustomFieldType type, List<String> values, FieldDefinition field) {
        if (values.size() > OpportunityFieldValue.MAX_VALUES) {
            throw new OpportunityDomainException(
                    "opportunity field values exceed the supported limit");
        }
        return switch (type) {
            case MULTI_SELECT -> choices(values, field, true);
            case SELECT -> choices(values, field, false);
            case TEXT, TEXTAREA, ADDRESS -> single(values, OpportunityFieldValuePolicy::text);
            case EMAIL -> single(values, OpportunityFieldValuePolicy::email);
            case PHONE -> single(values, OpportunityFieldValuePolicy::phone);
            case URL -> single(values, OpportunityFieldValuePolicy::url);
            case DATE -> single(values, OpportunityFieldValuePolicy::date);
            case DATETIME -> single(values, OpportunityFieldValuePolicy::dateTime);
            case INTEGER -> single(values, OpportunityFieldValuePolicy::integer);
            case DECIMAL -> single(values, OpportunityFieldValuePolicy::decimal);
            case BOOLEAN -> single(values, OpportunityFieldValuePolicy::bool);
            case USER, DEPARTMENT, RELATION -> identifiers(values);
            case FILE -> throw new OpportunityDomainException(
                    "file field values require the governed file module");
        };
    }

    private static List<String> choices(
            List<String> values, FieldDefinition field, boolean multiple) {
        if (!multiple && values.size() != 1) {
            throw new OpportunityDomainException(
                    "opportunity select field requires one value");
        }
        Set<String> allowed = field.getOptions().stream()
                .map(option -> option.value())
                .collect(java.util.stream.Collectors.toSet());
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = text(value);
            if (!allowed.contains(normalized)) {
                throw new OpportunityDomainException(
                        "opportunity select value is invalid");
            }
            result.add(normalized);
        }
        return List.copyOf(result);
    }

    private static String normalizeFilterValue(
            CustomFieldType type, String value, FieldDefinition field,
            FilterOperator operator) {
        if (operator == FilterOperator.CONTAINS
                || operator == FilterOperator.NOT_CONTAINS) {
            return switch (type) {
                case EMAIL -> text(value).toLowerCase(Locale.ROOT);
                case PHONE, URL -> text(value);
                default -> normalizeFilterValue(type, value, field);
            };
        }
        return normalizeFilterValue(type, value, field);
    }

    private static String normalizeFilterValue(
            CustomFieldType type, String value, FieldDefinition field) {
        return switch (type) {
            case MULTI_SELECT, SELECT -> choices(List.of(value), field, false).getFirst();
            case TEXT, TEXTAREA, ADDRESS -> text(value);
            case EMAIL -> email(value);
            case PHONE -> phone(value);
            case URL -> url(value);
            case DATE -> date(value);
            case DATETIME -> dateTime(value);
            case INTEGER -> integer(value);
            case DECIMAL -> decimal(value);
            case BOOLEAN -> bool(value);
            case USER, DEPARTMENT, RELATION -> identifier(value);
            case FILE -> throw new OpportunityDomainException(
                    "file fields do not accept filter values");
        };
    }

    private static List<String> identifiers(List<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            result.add(identifier(value));
        }
        return List.copyOf(result);
    }

    private static List<String> single(
            List<String> values,
            java.util.function.Function<String, String> normalizer) {
        if (values.size() != 1) {
            throw new OpportunityDomainException(
                    "opportunity field requires one value");
        }
        return List.of(normalizer.apply(values.getFirst()));
    }

    private static String text(String value) {
        if (value == null || value.isBlank()) {
            throw new OpportunityDomainException(
                    "opportunity field value must not be empty");
        }
        String normalized = value.trim();
        if (normalized.length() > OpportunityFieldValue.MAX_VALUE_LENGTH) {
            throw new OpportunityDomainException(
                    "opportunity field value is too long");
        }
        return normalized;
    }

    private static String email(String value) {
        String normalized = text(value).toLowerCase(Locale.ROOT);
        int at = normalized.indexOf('@');
        if (at <= 0 || at == normalized.length() - 1
                || normalized.indexOf('@', at + 1) >= 0) {
            throw new OpportunityDomainException(
                    "opportunity email field is invalid");
        }
        return normalized;
    }

    private static String phone(String value) {
        String normalized = text(value);
        if (!normalized.matches("\\+[1-9]\\d{7,14}")) {
            throw new OpportunityDomainException(
                    "opportunity phone field is invalid");
        }
        return normalized;
    }

    private static String url(String value) {
        String normalized = text(value);
        try {
            URI uri = URI.create(normalized);
            if (!("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new OpportunityDomainException(
                        "opportunity URL field is invalid");
            }
            String ascii = uri.toASCIIString();
            if (ascii.length() > OpportunityFieldValue.MAX_VALUE_LENGTH) {
                throw new OpportunityDomainException(
                        "opportunity URL field is too long");
            }
            return ascii;
        } catch (IllegalArgumentException exception) {
            throw new OpportunityDomainException(
                    "opportunity URL field is invalid");
        }
    }

    private static String date(String value) {
        try {
            LocalDate parsed = LocalDate.parse(text(value));
            return OpportunityTemporalValuePolicy.requireDate(
                    parsed, "opportunity date field").toString();
        } catch (DateTimeParseException exception) {
            throw new OpportunityDomainException(
                    "opportunity date field is invalid");
        }
    }

    private static String dateTime(String value) {
        try {
            LocalDateTime parsed = LocalDateTime.parse(text(value));
            return OpportunityTemporalValuePolicy.normalizeDateTime(
                    parsed, "opportunity datetime field").toString();
        } catch (DateTimeParseException exception) {
            throw new OpportunityDomainException(
                    "opportunity datetime field is invalid");
        }
    }

    private static String integer(String value) {
        try {
            return Long.toString(Long.parseLong(text(value)));
        } catch (NumberFormatException exception) {
            throw new OpportunityDomainException(
                    "opportunity integer field is invalid");
        }
    }

    private static String identifier(String value) {
        try {
            long parsed = Long.parseLong(text(value));
            if (parsed <= 0) {
                throw new OpportunityDomainException(
                        "opportunity identifier field is invalid");
            }
            return Long.toString(parsed);
        } catch (NumberFormatException exception) {
            throw new OpportunityDomainException(
                    "opportunity identifier field is invalid");
        }
    }

    private static String decimal(String value) {
        try {
            BigDecimal parsed = new BigDecimal(text(value)).stripTrailingZeros();
            int precision = parsed.precision();
            int scale = parsed.scale();
            long integerDigits = Math.max(0L, (long) precision - scale);
            if (precision > DECIMAL_PRECISION
                    || scale > DECIMAL_SCALE
                    || scale < MINIMUM_DECIMAL_SCALE
                    || integerDigits > DECIMAL_INTEGER_DIGITS
                    || expandedDecimalLength(parsed) > MAXIMUM_PLAIN_DECIMAL_LENGTH) {
                throw new OpportunityDomainException(
                        "opportunity decimal field is invalid");
            }
            String normalized = parsed.toPlainString();
            if (normalized.length() > MAXIMUM_PLAIN_DECIMAL_LENGTH) {
                throw new OpportunityDomainException(
                        "opportunity decimal field is invalid");
            }
            return normalized;
        } catch (NumberFormatException exception) {
            throw new OpportunityDomainException(
                    "opportunity decimal field is invalid");
        }
    }

    private static long expandedDecimalLength(BigDecimal value) {
        int precision = value.precision();
        int scale = value.scale();
        long unsignedLength;
        if (scale <= 0) {
            unsignedLength = (long) precision - scale;
        } else if (precision > scale) {
            unsignedLength = (long) precision + 1;
        } else {
            unsignedLength = (long) scale + 2;
        }
        return unsignedLength + (value.signum() < 0 ? 1 : 0);
    }

    private static String bool(String value) {
        String normalized = text(value).toLowerCase(Locale.ROOT);
        if (!"true".equals(normalized) && !"false".equals(normalized)) {
            throw new OpportunityDomainException(
                    "opportunity boolean field is invalid");
        }
        return normalized;
    }
}
