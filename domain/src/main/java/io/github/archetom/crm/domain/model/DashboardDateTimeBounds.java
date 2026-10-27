package io.github.archetom.crm.domain.model;

import java.time.Instant;

/** MySQL-representable UTC DATETIME bounds for one dashboard date window. */
public record DashboardDateTimeBounds(
        Instant fromInclusiveUtc,
        Instant upperBoundUtc,
        UpperBoundType upperBoundType) {

    private static final Instant MINIMUM_MYSQL_DATETIME =
            Instant.parse("1000-01-01T00:00:00Z");
    private static final Instant MAXIMUM_MYSQL_DATETIME =
            Instant.parse("9999-12-31T23:59:59.999999Z");

    public DashboardDateTimeBounds {
        if (fromInclusiveUtc == null || upperBoundUtc == null || upperBoundType == null
                || fromInclusiveUtc.isBefore(MINIMUM_MYSQL_DATETIME)
                || upperBoundUtc.isAfter(MAXIMUM_MYSQL_DATETIME)
                || fromInclusiveUtc.isAfter(upperBoundUtc)
                || (upperBoundType == UpperBoundType.EXCLUSIVE
                    && !fromInclusiveUtc.isBefore(upperBoundUtc))) {
            throw new IllegalArgumentException("dashboard DATETIME bounds are invalid");
        }
    }

    public enum UpperBoundType {
        EXCLUSIVE,
        INCLUSIVE
    }
}
