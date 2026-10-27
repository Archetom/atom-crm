package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.ContactDomainException;

import java.math.BigDecimal;

/** Safe numeric criteria for a distance-sorted contact search. */
public record ContactNearbyQuery(
        ContactVisibility visibility,
        BigDecimal longitude,
        BigDecimal latitude,
        int radiusMeters,
        int page,
        int size) {

    public ContactNearbyQuery {
        if (visibility == null || longitude == null || latitude == null
                || longitude.compareTo(new BigDecimal("-180")) < 0
                || longitude.compareTo(new BigDecimal("180")) > 0
                || latitude.compareTo(new BigDecimal("-90")) < 0
                || latitude.compareTo(new BigDecimal("90")) > 0
                || radiusMeters < 1 || radiusMeters > 50_000
                || page < 1 || size < 1 || size > 200) {
            throw new ContactDomainException("nearby contact query is invalid");
        }
    }
}
