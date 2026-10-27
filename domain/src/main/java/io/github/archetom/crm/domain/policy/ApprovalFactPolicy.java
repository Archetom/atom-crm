package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.exception.ApprovalDomainException;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Type-normalizes immutable approval routing facts against governed field metadata. */
public final class ApprovalFactPolicy {

    private static final int DECIMAL_PRECISION = 30;
    private static final int DECIMAL_SCALE = 10;
    private static final int DECIMAL_INTEGER_DIGITS = DECIMAL_PRECISION - DECIMAL_SCALE;
    private static final int MINIMUM_DECIMAL_SCALE = 1 - DECIMAL_INTEGER_DIGITS;
    private static final int MAXIMUM_PLAIN_DECIMAL_LENGTH =
            DECIMAL_INTEGER_DIGITS + DECIMAL_SCALE + 2;

    private ApprovalFactPolicy() {
    }

    public static List<String> normalize(FieldDefinition field, List<String> values) {
        if (field == null || !field.isActive() || !field.isSearchable()) {
            throw new ApprovalDomainException("approval routing field is unavailable");
        }
        return normalizeValue(field, values);
    }

    /** Normalizes governed immutable values before they become approval facts. */
    public static List<String> normalizeStored(FieldDefinition field, List<String> values) {
        if (field == null || !field.isActive()) {
            throw new ApprovalDomainException("approval snapshot field is unavailable");
        }
        return normalizeValue(field, values);
    }

    /** Replays an immutable submission against type metadata whose lifecycle may have changed. */
    public static List<String> normalizeImmutable(FieldDefinition field, List<String> values) {
        if (field == null) {
            throw new ApprovalDomainException("approval snapshot field is unavailable");
        }
        return normalizeValue(field, values);
    }

    private static List<String> normalizeValue(FieldDefinition field, List<String> values) {
        if (values != null && values.stream().anyMatch(java.util.Objects::isNull)) {
            throw new ApprovalDomainException("approval fact values are invalid");
        }
        List<String> source = values == null ? List.of() : List.copyOf(values);
        if (source.isEmpty()) {
            return List.of();
        }
        if (source.size() > 20) {
            throw new ApprovalDomainException("approval fact values exceed the supported limit");
        }
        return switch (field.getFieldType()) {
            case MULTI_SELECT -> choices(source, field, true);
            case SELECT -> choices(source, field, false);
            case TEXT, TEXTAREA, ADDRESS -> single(source, ApprovalFactPolicy::text);
            case EMAIL -> single(source, ApprovalFactPolicy::email);
            case PHONE -> single(source, ApprovalFactPolicy::phone);
            case URL -> single(source, ApprovalFactPolicy::url);
            case DATE -> single(source, value -> LocalDate.parse(text(value)).toString());
            case DATETIME -> single(source, value -> LocalDateTime.parse(text(value)).toString());
            case INTEGER -> single(source, value -> Long.toString(Long.parseLong(text(value))));
            case DECIMAL -> single(source, ApprovalFactPolicy::decimal);
            case BOOLEAN -> single(source, ApprovalFactPolicy::bool);
            case USER, DEPARTMENT, RELATION -> identifiers(source);
            case FILE -> throw new ApprovalDomainException(
                    "file values cannot be used as approval routing facts");
        };
    }

    private static List<String> choices(List<String> values, FieldDefinition field, boolean multiple) {
        if (!multiple && values.size() != 1) {
            throw new ApprovalDomainException("approval select fact requires one value");
        }
        Set<String> allowed = field.getOptions().stream().map(option -> option.value())
                .collect(java.util.stream.Collectors.toSet());
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = text(value);
            if (!allowed.contains(normalized)) {
                throw new ApprovalDomainException("approval select fact is invalid");
            }
            result.add(normalized);
        }
        return List.copyOf(result);
    }

    private static List<String> identifiers(List<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            long parsed = Long.parseLong(text(value));
            if (parsed <= 0) {
                throw new ApprovalDomainException("approval identifier fact is invalid");
            }
            result.add(Long.toString(parsed));
        }
        return List.copyOf(result);
    }

    private static List<String> single(
            List<String> values, java.util.function.Function<String, String> normalizer) {
        if (values.size() != 1) {
            throw new ApprovalDomainException("approval fact requires one value");
        }
        try {
            return List.of(normalizer.apply(values.getFirst()));
        } catch (ApprovalDomainException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ApprovalDomainException("approval fact value is invalid");
        }
    }

    private static String text(String value) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty() || normalized.length() > 500) {
            throw new ApprovalDomainException("approval fact value is invalid");
        }
        return normalized;
    }

    private static String phone(String value) {
        String normalized = text(value);
        if (!normalized.matches("\\+[1-9]\\d{7,14}")) {
            throw new ApprovalDomainException("approval phone fact is invalid");
        }
        return normalized;
    }

    private static String email(String value) {
        String normalized = text(value).toLowerCase(Locale.ROOT);
        if (!normalized.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new ApprovalDomainException("approval email fact is invalid");
        }
        return normalized;
    }

    private static String url(String value) {
        try {
            URI uri = URI.create(text(value));
            if (!("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new ApprovalDomainException("approval URL fact is invalid");
            }
            return uri.toASCIIString();
        } catch (IllegalArgumentException exception) {
            throw new ApprovalDomainException("approval URL fact is invalid");
        }
    }

    private static String decimal(String value) {
        try {
            BigDecimal parsed = new BigDecimal(text(value)).stripTrailingZeros();
            int precision = parsed.precision();
            int scale = parsed.scale();
            long integerDigits = Math.max(0L, (long) precision - scale);
            if (precision > DECIMAL_PRECISION || scale > DECIMAL_SCALE
                    || scale < MINIMUM_DECIMAL_SCALE
                    || integerDigits > DECIMAL_INTEGER_DIGITS
                    || expandedDecimalLength(parsed) > MAXIMUM_PLAIN_DECIMAL_LENGTH) {
                throw new ApprovalDomainException("approval decimal fact is invalid");
            }
            return parsed.toPlainString();
        } catch (NumberFormatException exception) {
            throw new ApprovalDomainException("approval decimal fact is invalid");
        }
    }

    private static long expandedDecimalLength(BigDecimal value) {
        int precision = value.precision();
        int scale = value.scale();
        long unsigned = scale <= 0 ? (long) precision - scale
                : precision > scale ? (long) precision + 1 : (long) scale + 2;
        return unsigned + (value.signum() < 0 ? 1 : 0);
    }

    private static String bool(String value) {
        String normalized = text(value).toLowerCase(Locale.ROOT);
        if (!"true".equals(normalized) && !"false".equals(normalized)) {
            throw new ApprovalDomainException("approval boolean fact is invalid");
        }
        return normalized;
    }
}
