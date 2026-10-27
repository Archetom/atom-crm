package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.exception.OpportunityDomainException;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Persistence-safe temporal normalization shared by opportunity state and query values. */
public final class OpportunityTemporalValuePolicy {

    private static final int NANOS_PER_MICROSECOND = 1_000;
    private static final LocalDate MINIMUM_DATE = LocalDate.of(1000, 1, 1);
    private static final LocalDate MAXIMUM_DATE = LocalDate.of(9999, 12, 31);

    private OpportunityTemporalValuePolicy() {
    }

    public static LocalDate requireDate(LocalDate value, String description) {
        if (value != null && (value.isBefore(MINIMUM_DATE) || value.isAfter(MAXIMUM_DATE))) {
            throw new OpportunityDomainException(description + " is outside the supported range");
        }
        return value;
    }

    public static LocalDateTime normalizeDateTime(
            LocalDateTime value, String description) {
        if (value == null) {
            return null;
        }
        LocalDateTime normalized = value.withNano(
                value.getNano() / NANOS_PER_MICROSECOND * NANOS_PER_MICROSECOND);
        requireDate(normalized.toLocalDate(), description);
        return normalized;
    }
}
